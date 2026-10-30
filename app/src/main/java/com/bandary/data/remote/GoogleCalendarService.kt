package com.bandary.data.remote

import android.content.Context
import android.content.Intent
import com.bandary.domain.model.Concert
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class ImportedEvent(
    val title: String,
    val location: String?,
    val startIso: String,        // e.g. 2026-07-01T19:30:00 或全天事件的 2026-07-01
    val endIso: String? = null,  // 結束（多日活動用）
    val allDay: Boolean = false,
    val sourceEventId: String
)

/**
 * Google Calendar two-way sync via REST + on-device OAuth token.
 *
 * Auth: com.google.android.gms.auth.api.signin.GoogleSignIn (play-services-auth) to pick the account + grant the Calendar
 * scope, then GoogleAuthUtil to mint a short-lived OAuth access token, used as a
 * Bearer token against the Calendar v3 REST API (no heavy google-api-client libs).
 *
 * Requires Google Cloud setup: Calendar API enabled, OAuth consent screen + Android
 * OAuth client (package com.bandary.app + debug SHA-1), and a device signed in to a
 * test-user Google account.
 */
@Singleton
class GoogleCalendarService @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val scope = "https://www.googleapis.com/auth/calendar"
    private val tz = TimeZone.of("Asia/Taipei")

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // Google Sign-In 整套已棄用，正式替代是 Credential Manager。
    // 那是會改到登入行為的遷移，必須單獨一批 + 實機驗證，不能併進「清警告」。
    // 在這裡標明決定，新出現的棄用才看得見。
    @Suppress("DEPRECATION")
    fun signInClient(): com.google.android.gms.auth.api.signin.GoogleSignInClient {
        val opts = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(scope))
            .build()
        return com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, opts)
    }

    /** Intent to launch the sign-in flow from a UI launcher. */
    fun signInIntent(): Intent = signInClient().signInIntent

    @Suppress("DEPRECATION")  // 同上：等 Credential Manager 遷移
    fun isConnected(): Boolean = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context) != null

    @Suppress("DEPRECATION")  // 同上：等 Credential Manager 遷移
    fun connectedEmail(): String? = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)?.email

    suspend fun disconnect() {
        runCatching { signInClient().signOut() }
    }

    @Suppress("DEPRECATION")  // 同上：等 Credential Manager 遷移
    private suspend fun accessToken(): String? = withContext(Dispatchers.IO) {
        val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)?.account ?: return@withContext null
        runCatching { GoogleAuthUtil.getToken(context, account, "oauth2:$scope") }.getOrNull()
    }

    /** Push a concert as a calendar event. Returns the created event id, or null. */
    suspend fun pushConcert(concert: Concert): String? = withContext(Dispatchers.IO) {
        val token = accessToken() ?: return@withContext null
        runCatching {
            val multiDay = concert.endDate?.let { it > concert.dateTime.date } ?: false
            val body = JSONObject().apply {
                put("summary", concert.name)
                put("location", buildString {
                    concert.city?.let { append(it).append(" ") }
                    append(concert.venue)
                })
                put("description", "藝人：${concert.artistName}\n由 Bandary 同步")
                if (multiDay) {
                    // 多日活動：以全天事件呈現。Google 全天事件 end.date 為排他，需 +1 天。
                    val endExclusive = concert.endDate!!.plus(1, DateTimeUnit.DAY)
                    put("start", JSONObject().put("date", concert.dateTime.date.toString()))
                    put("end", JSONObject().put("date", endExclusive.toString()))
                } else {
                    val startInstant = concert.dateTime.toInstant(tz)
                    val endInstant = concert.dateTime.toInstant(tz).plus(3, DateTimeUnit.HOUR, tz)
                    put("start", JSONObject().apply {
                        put("dateTime", startInstant.toString())
                        put("timeZone", "Asia/Taipei")
                    })
                    put("end", JSONObject().apply {
                        put("dateTime", endInstant.toString())
                        put("timeZone", "Asia/Taipei")
                    })
                }
            }
            val req = Request.Builder()
                .url("https://www.googleapis.com/calendar/v3/calendars/primary/events")
                .header("Authorization", "Bearer $token")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                JSONObject(resp.body?.string() ?: return@use null).optString("id").ifBlank { null }
            }
        }.getOrNull()
    }

    /**
     * 一份日曆。[id] 是 Google 的 calendarId（主日曆是 "primary"），
     * [primary] 標出使用者自己的主日曆，[owned] 區分「我的」與「別人分享給我的」。
     */
    data class CalendarInfo(
        val id: String,
        val name: String,
        val primary: Boolean,
        val owned: Boolean
    )

    /**
     * 使用者訂閱的所有日曆。
     * 以前匯入只讀 `calendars/primary`，別人分享給你的日曆在 Google 那邊是獨立的一份，
     * 不會併進主日曆，所以在這裡完全看不到（實機回報「想從別人的共用日曆匯入」）。
     * 排序：主日曆 → 自己的其他日曆 → 別人分享的。
     */
    suspend fun listCalendars(): Result<List<CalendarInfo>> = withContext(Dispatchers.IO) {
        if (!isConnected()) return@withContext Result.failure(IllegalStateException("尚未連結 Google 日曆"))
        val token = accessToken()
            ?: return@withContext Result.failure(IllegalStateException("取得授權失敗，請重新連結 Google 日曆"))
        runCatching {
            val req = Request.Builder()
                .url("https://www.googleapis.com/calendar/v3/users/me/calendarList?minAccessRole=reader&maxResults=250")
                .header("Authorization", "Bearer $token")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("讀取日曆清單失敗・${resp.code}")
                val items: JSONArray = JSONObject(resp.body?.string() ?: "{}")
                    .optJSONArray("items") ?: JSONArray()
                (0 until items.length()).mapNotNull { i ->
                    val o = items.getJSONObject(i)
                    val id = o.optString("id").ifBlank { return@mapNotNull null }
                    val isPrimary = o.optBoolean("primary", false)
                    CalendarInfo(
                        id = if (isPrimary) "primary" else id,
                        name = o.optString("summaryOverride").ifBlank { o.optString("summary", id) },
                        primary = isPrimary,
                        owned = o.optString("accessRole") == "owner"
                    )
                }.sortedWith(
                    compareByDescending<CalendarInfo> { it.primary }
                        .thenByDescending { it.owned }
                        .thenBy { it.name }
                )
            }
        }.onFailure { android.util.Log.w("GoogleCalendarService", "listCalendars failed", it) }
    }

    /** Import upcoming events from the user's primary calendar (向後相容：免費活動快速匯入用)。 */
    suspend fun importUpcomingEvents(maxResults: Int = 30): List<ImportedEvent> =
        importEvents(pastDays = 0, futureDays = 365, maxResults = maxResults).getOrDefault(emptyList())

    /**
     * 從主日曆抓事件。可含過往（pastDays）與未來（futureDays）。回傳 Result 以便 UI 顯示
     * 失敗原因（修「有時跳不出來」：未連結 / 無權杖 / HTTP 失敗時不再靜默回空）。
     */
    suspend fun importEvents(
        pastDays: Int,
        futureDays: Int,
        maxResults: Int = 250,
        calendarId: String = "primary"
    ): Result<List<ImportedEvent>> = withContext(Dispatchers.IO) {
        if (!isConnected()) {
            return@withContext Result.failure(IllegalStateException("尚未連結 Google 日曆"))
        }
        val token = accessToken()
            ?: return@withContext Result.failure(IllegalStateException("取得授權失敗，請重新連結 Google 日曆"))
        runCatching {
            val now = kotlinx.datetime.Clock.System.now()
            val timeMin = now.minus(pastDays, DateTimeUnit.DAY, tz)
            val timeMax = now.plus(futureDays, DateTimeUnit.DAY, tz)
            val url = "https://www.googleapis.com/calendar/v3/calendars/" +
                java.net.URLEncoder.encode(calendarId, "UTF-8") + "/events" +
                "?singleEvents=true&orderBy=startTime&maxResults=$maxResults" +
                "&timeMin=" + java.net.URLEncoder.encode(timeMin.toString(), "UTF-8") +
                "&timeMax=" + java.net.URLEncoder.encode(timeMax.toString(), "UTF-8")
            val req = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Google 日曆讀取失敗・${resp.code}")
                }
                val items: JSONArray = JSONObject(resp.body?.string() ?: "{}")
                    .optJSONArray("items") ?: JSONArray()
                (0 until items.length()).mapNotNull { i ->
                    val o = items.getJSONObject(i)
                    val start = o.optJSONObject("start") ?: return@mapNotNull null
                    val end = o.optJSONObject("end")
                    val dateTime = start.optString("dateTime")
                    val allDay = dateTime.isBlank()
                    val startIso = dateTime.ifBlank { start.optString("date") }
                    if (startIso.isBlank()) return@mapNotNull null
                    val endIso = (end?.optString("dateTime")?.ifBlank { end.optString("date") })
                        ?.takeIf { it.isNotBlank() }
                    ImportedEvent(
                        title = o.optString("summary", "(無標題)"),
                        location = o.optString("location").ifBlank { null },
                        startIso = startIso,
                        endIso = endIso,
                        allDay = allDay,
                        sourceEventId = o.optString("id")
                    )
                }
            }
        }
    }
}
