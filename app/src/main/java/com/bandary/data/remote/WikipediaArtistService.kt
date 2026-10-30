package com.bandary.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 拿中文維基百科核對團名，找出「正式的那個寫法」。
 *
 * ## 為什麼需要
 * 使用者打的團名會缺漏或只有一半（`ggteen`、`公館青少年`、`公館青少年GGteen`）。
 * 光靠字串比對只能挑「最長的那個」，但最長的不一定是對的——
 * 維基的條目標題才是公認的正式名稱。
 *
 * ## 兩條路，可信度差很多
 * 1. **標題直接命中（含維基自己的重新導向）**：可信。
 *    維基的重新導向本來就是「別名 → 正式名稱」的人工整理，
 *    `Fire EX.` 這種別名很多時候直接就會跳到正式條目。
 * 2. **全文搜尋**：**不可信，一定要驗**。
 *    實測 `公館青少年` 的第一筆搜尋結果是「蔡锷公馆」——中國的一棟古蹟，
 *    完全無關，但 API 回得非常有自信。直接採用的話，
 *    後台會把一個樂團合併成一棟建築物的名字。
 *
 * ## 驗證規則：條目開頭必須提到我們查的那個名字
 * 這條規則在實測上剛好把對的留下、錯的擋掉：
 *
 * | 查詢 | 搜到 | 開頭有提到？ | 結果 |
 * |---|---|---|---|
 * | `Fire EX.` | 滅火器樂團 | ✓ | 採用 |
 * | `Accusefive` | 告五人 | ✓ | 採用 |
 * | `AmazingShow` | 美秀集團 | ✓ | 採用 |
 * | `公館青少年` | 蔡锷公馆 | ✗ | **擋掉** |
 *
 * 用「條目開頭有沒有提到」而不是「標題像不像」，是因為最有價值的那幾筆
 * （英文別名 → 中文團名）標題跟查詢根本沒有共同字元，
 * 用字面相似度會把它們全部誤殺。
 *
 * ## 有禮貌
 * 每次查詢之間隔 [PAUSE_MS]。實測連續打會被回 429；
 * 這不是我們的服務，被擋是應該的，不該靠重試硬塞。
 * 查過的結果記在記憶體裡，同一次合併掃描不會重複問同一個名字。
 */
@Singleton
class WikipediaArtistService @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // 查過就記著。一次合併掃描裡同一個名字可能出現在好幾組。
    private val cache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val misses = java.util.Collections.synchronizedSet(HashSet<String>())

    /**
     * 回傳維基上的正式條目名稱；查不到或驗不過回 null。
     *
     * **回 null 是正常結果**，不是錯誤——很多獨立樂團本來就沒有維基條目
     * （實測 `公館青少年GGteen` 就沒有）。呼叫端要能在沒有維基資料時照常運作。
     */
    suspend fun canonicalName(name: String): String? = withContext(Dispatchers.IO) {
        val q = name.trim()
        if (q.isBlank()) return@withContext null
        cache[q]?.let { return@withContext it }
        if (q in misses) return@withContext null

        val resolved = runCatching { lookup(q) }.getOrNull()
        if (resolved != null) cache[q] = resolved else misses += q
        resolved
    }

    private suspend fun lookup(q: String): String? {
        // ① 標題直接命中（含重新導向）。這條可信，不用再驗。
        titleOf(q)?.let { return it }

        delay(PAUSE_MS)
        // ② 全文搜尋。**一定要驗**，見類別說明的「蔡锷公馆」案例。
        val hit = searchTop(q) ?: return null
        delay(PAUSE_MS)
        val intro = introOf(hit).orEmpty()
        val needle = normalize(q)
        return if (needle.isNotBlank() &&
            (normalize(hit).contains(needle) || normalize(intro).contains(needle))
        ) hit else null
    }

    /** 標題查詢；維基的重新導向會自動跟到正式條目。不存在回 null。 */
    private fun titleOf(q: String): String? {
        val json = get(
            "action=query&prop=info&redirects=1&format=json&formatversion=2&titles=" + enc(q)
        ) ?: return null
        val pages = JSONObject(json).optJSONObject("query")?.optJSONArray("pages") ?: return null
        if (pages.length() == 0) return null
        val p = pages.getJSONObject(0)
        if (p.optBoolean("missing", false)) return null
        return p.optString("title").takeIf { it.isNotBlank() }
    }

    private fun searchTop(q: String): String? {
        val json = get(
            "action=query&list=search&srlimit=1&format=json&formatversion=2&srsearch=" + enc(q)
        ) ?: return null
        val arr = JSONObject(json).optJSONObject("query")?.optJSONArray("search") ?: return null
        if (arr.length() == 0) return null
        return arr.getJSONObject(0).optString("title").takeIf { it.isNotBlank() }
    }

    /** 條目開頭純文字。用它來驗搜尋結果是不是真的在講我們查的東西。 */
    private fun introOf(title: String): String? {
        val json = get(
            "action=query&prop=extracts&exintro=1&explaintext=1&redirects=1" +
                "&format=json&formatversion=2&titles=" + enc(title)
        ) ?: return null
        val pages = JSONObject(json).optJSONObject("query")?.optJSONArray("pages") ?: return null
        if (pages.length() == 0) return null
        return pages.getJSONObject(0).optString("extract")
    }

    private fun get(query: String): String? = runCatching {
        val req = Request.Builder()
            .url("$API?$query")
            // 維基要求帶可識別的 User-Agent，否則會被擋。
            .header("User-Agent", UA)
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            resp.body?.string()
        }
    }.onFailure { android.util.Log.w("WikipediaArtistService", "query failed", it) }.getOrNull()

    private fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")

    /** 比對用：只留英數與中日韓字元，轉小寫。標點與空白的差異不該影響判斷。 */
    private fun normalize(s: String): String =
        s.lowercase().filter { it.isLetterOrDigit() }

    private companion object {
        const val API = "https://zh.wikipedia.org/w/api.php"
        const val UA = "BandaryBot/1.0 (https://bandary.web.app; artist name check)"
        /** 查詢之間的間隔。實測連續打會被回 429。 */
        const val PAUSE_MS = 1200L
    }
}
