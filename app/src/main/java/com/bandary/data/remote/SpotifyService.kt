package com.bandary.data.remote

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// ── Domain models for Spotify results ─────────────────────────
data class SpotifyArtist(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val genres: List<String>,
    val followers: Int,
    val popularity: Int
)

data class SpotifyTrack(
    val id: String,
    val title: String,
    val artistName: String,
    val albumName: String,
    val albumImageUrl: String?
)

/**
 * Spotify 搜尋——透過 Cloud Function `spotifySearch` 代理（functions/index.js）。
 *
 * 以前 App 直接帶 Client ID / Secret 呼叫 Spotify，上架後任何人反編譯 APK 都拿得到金鑰。
 * 現在金鑰只存在伺服器端的 Secret Manager，App 這邊沒有任何金鑰。
 * 代理只給登入者用；未登入或呼叫失敗時一律回空清單，畫面照舊退回手動輸入。
 */
@Singleton
class SpotifyService @Inject constructor() {

    private val functions by lazy { FirebaseFunctions.getInstance("asia-east1") }

    /**
     * 有沒有登入（代理只給登入者用）。必須是會變動的狀態：以前畫面只在建立時問一次，
     * 使用者登入後那個值仍是 false，Spotify 搜尋要重開 App 才會生效（雲端紀錄顯示函式從未被呼叫）。
     */
    private val _signedIn = kotlinx.coroutines.flow.MutableStateFlow(FirebaseAuth.getInstance().currentUser != null)
    val signedIn: kotlinx.coroutines.flow.StateFlow<Boolean> = _signedIn

    /** 最近一次呼叫失敗的原因，給畫面顯示；成功或未呼叫為 null。 */
    private val _lastError = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val lastError: kotlinx.coroutines.flow.StateFlow<String?> = _lastError

    init {
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            _signedIn.value = auth.currentUser != null
        }
    }

    fun isConfigured(): Boolean = _signedIn.value

    private suspend fun search(type: String, query: String, limit: Int): List<Map<*, *>> =
        withContext(Dispatchers.IO) {
            if (query.isBlank() || !isConfigured()) return@withContext emptyList()
            runCatching {
                val result = Tasks.await(
                    functions.getHttpsCallable("spotifySearch")
                        .withTimeout(15, TimeUnit.SECONDS)
                        .call(mapOf("type" to type, "q" to query, "limit" to limit))
                )
                ((result.getData() as? Map<*, *>)?.get("items") as? List<*>)
                    ?.filterIsInstance<Map<*, *>>().orEmpty()
            }.onSuccess { _lastError.value = null }
                .onFailure {
                    android.util.Log.w("SpotifyService", "spotifySearch($type) failed", it)
                    // 不要用 it.message：Tasks.await 包的是 ExecutionException，
                    // 它的 message 是內層例外的 toString()，畫面上會變成一串類別名稱，
                    // 而真正有用的 code（UNAUTHENTICATED / NOT_FOUND…）整個拿不到。
                    _lastError.value = CallableFailure.of(it)
                }
                .getOrDefault(emptyList())
        }

    suspend fun searchArtists(query: String, limit: Int = 10): List<SpotifyArtist> =
        search("artist", query, limit).map { m ->
            SpotifyArtist(
                id = m["id"] as? String ?: "",
                name = m["name"] as? String ?: "",
                imageUrl = m["imageUrl"] as? String,
                genres = (m["genres"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
                followers = (m["followers"] as? Number)?.toInt() ?: 0,
                popularity = (m["popularity"] as? Number)?.toInt() ?: 0
            )
        }

    suspend fun searchTracks(query: String, limit: Int = 10): List<SpotifyTrack> =
        search("track", query, limit).map { m ->
            SpotifyTrack(
                id = m["id"] as? String ?: "",
                title = m["title"] as? String ?: "",
                artistName = m["artistName"] as? String ?: "",
                albumName = m["albumName"] as? String ?: "",
                albumImageUrl = m["albumImageUrl"] as? String
            )
        }
}
