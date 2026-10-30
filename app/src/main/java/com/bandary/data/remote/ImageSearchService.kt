package com.bandary.data.remote

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class ImageResult(
    val thumbnailUrl: String,
    val fullUrl: String,
    val width: Int,
    val height: Int
)

/**
 * 找海報用的圖片搜尋——透過 Cloud Function `imageSearch` 代理（functions/index.js）。
 *
 * **為什麼改成代理**：這支原本在 `companion object` 裡明碼寫著 Google Custom Search 的
 * `API_KEY` 與 `SEARCH_ENGINE_ID`。那等於
 *   1. 跟著 APK 發給每一個使用者，反編譯就拿得到；
 *   2. 躺在公開的 git 歷史裡（從 v7-2 那次 commit 就在了）。
 * 別人可以拿去用光一天 100 次的免費額度，或讓專案被收費。
 * Spotify 金鑰在 v1.67.0 已經搬過一次，這支走同一條路：金鑰只存在 Secret Manager，
 * App 這邊沒有任何金鑰。
 *
 * 代理只給登入者用；未登入或呼叫失敗一律回空清單，畫面會退回手動選圖。
 *
 * [download] 沒有走代理：下載一張公開圖片的網址不需要任何金鑰，
 * 多繞一層只是讓雲端幫忙轉傳流量。
 */
@Singleton
class ImageSearchService @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val functions by lazy { FirebaseFunctions.getInstance("asia-east1") }

    /**
     * 有沒有登入。必須是會變動的狀態——Spotify 那支踩過這個坑：
     * 畫面只在建立時問一次，使用者登入後那個值還是 false，
     * 要重開 App 搜尋才會生效。
     */
    private val _signedIn = kotlinx.coroutines.flow.MutableStateFlow(
        FirebaseAuth.getInstance().currentUser != null
    )
    val signedIn: kotlinx.coroutines.flow.StateFlow<Boolean> = _signedIn

    /**
     * 最近一次搜尋失敗的原因；成功或還沒搜過是 null。
     *
     * 為什麼非有不可：這支以前失敗只 `Log.w`，呼叫端拿到的空清單
     * 跟「真的沒有這張海報」完全分不出來。於是「找海報」會說
     * 「搜過『某某』都沒有結果」——金鑰沒設、額度用完、沒部署，
     * 一律都講這句話，而那是假的。使用者手上沒有 logcat，
     * 回報就只能停在「還沒好」。
     *
     * 存的是**最近一次**，不是累積：呼叫端每次搜完立刻讀，
     * 留著舊的只會讓下一次成功的搜尋旁邊掛著上次的錯誤。
     */
    private val _lastError = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val lastError: kotlinx.coroutines.flow.StateFlow<String?> = _lastError

    init {
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            _signedIn.value = auth.currentUser != null
        }
    }

    fun isConfigured(): Boolean = _signedIn.value

    /**
     * 同一句查詢在這個 process 裡只打一次。
     *
     * 為什麼非做不可：演唱會詳情頁與票根分享頁在**每次開啟**時，只要那場沒有封面
     * 就會自動搜背景圖，而且是「藝人沒結果就再搜場地」兩次。沒有快取的話，
     * 翻十場沒有封面的紀錄就是二十次查詢——免費額度一天 100 次而且是
     * 全部使用者共用一把金鑰，這條路徑自己就能在一個早上把額度吃乾，
     * 讓真正需要它的「找海報」整天都搜不到東西。
     *
     * 空結果也要記（負向快取）。不記的話「這句查不到」會被一而再地重問，
     * 那是最浪費的一種——每次都付出額度、每次都拿到空的。
     */
    private val cache = java.util.concurrent.ConcurrentHashMap<String, List<ImageResult>>()

    /**
     * 海報取向的搜尋。
     *
     * 只補「演唱會」不補「海報」：搜尋引擎已經限定在售票平台那幾個網域
     * （Google 2026-01-20 起不再開放新的「搜尋整個網路」，舊的那個也沒開），
     * 那些站上每一頁本來就是演唱會，而頁面文字幾乎不會出現「海報」兩個字
     * ——多加這個詞只會把本來找得到的圖濾掉。
     */
    suspend fun searchPosters(query: String, limit: Int = 10): List<ImageResult> =
        searchImages("$query 演唱會", limit)

    /** 一般圖片搜尋——呼叫端自己決定完整的關鍵字。 */
    suspend fun searchImages(query: String, limit: Int = 10): List<ImageResult> =
        withContext(Dispatchers.IO) {
            if (query.isBlank() || !isConfigured()) return@withContext emptyList()
            cache[query]?.let {
                _lastError.value = null
                return@withContext it
            }
            runCatching {
                val result = Tasks.await(
                    functions.getHttpsCallable("imageSearch")
                        .withTimeout(20, TimeUnit.SECONDS)
                        .call(mapOf("q" to query, "limit" to limit))
                )
                ((result.getData() as? Map<*, *>)?.get("items") as? List<*>)
                    ?.filterIsInstance<Map<*, *>>()
                    .orEmpty()
                    .map { m ->
                        ImageResult(
                            thumbnailUrl = m["thumbnailUrl"] as? String ?: "",
                            fullUrl = m["fullUrl"] as? String ?: "",
                            width = (m["width"] as? Number)?.toInt() ?: 0,
                            height = (m["height"] as? Number)?.toInt() ?: 0
                        )
                    }
                    .filter { it.fullUrl.isNotBlank() }
            }.onSuccess {
                cache[query] = it
                _lastError.value = null
            }
                .onFailure {
                    // 失敗不進快取：額度用完、網路斷掉這種是暫時的，
                    // 記起來等於把一次意外變成這次開 App 都不能再搜。
                    android.util.Log.w("ImageSearchService", "imageSearch failed", it)
                    _lastError.value = CallableFailure.of(it)
                }
                .getOrDefault(emptyList())
        }

    /**
     * 把搜尋結果的原圖抓下來。找到海報之後一定要落地成本機檔，
     * 不能只存網址——對方撤圖就變破圖，離線時也看不到。
     *
     * 回傳 `bytes to 副檔名`；抓不到回 null，呼叫端要保留原本的照片不覆蓋。
     */
    suspend fun download(url: String): Pair<ByteArray, String>? = withContext(Dispatchers.IO) {
        if (!url.startsWith("http", ignoreCase = true)) return@withContext null
        runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body ?: return@use null
                val type = body.contentType()?.subtype?.lowercase().orEmpty()
                val ext = when {
                    type.contains("png") -> "png"
                    type.contains("webp") -> "webp"
                    else -> "jpg"
                }
                val bytes = body.bytes()
                // 8MB 以上多半不是海報而是原始相片／誤判的檔案，擋掉免得塞爆本機空間。
                if (bytes.isEmpty() || bytes.size > 8 * 1024 * 1024) null else bytes to ext
            }
        }.onFailure { android.util.Log.w("ImageSearchService", "download error", it) }.getOrNull()
    }
}
