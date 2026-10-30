package com.bandary.presentation.ui.admin

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bandary.data.repository.FreeEventsRepository
import com.bandary.domain.model.FreeEvent
import com.bandary.presentation.ui.components.SectionHeader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

// 後台數據（管理者）：目前以雲端共用的「免費活動」collection 為資料來源——
// 這是 Firestore 規則下唯一跨使用者可讀的資料。各使用者私有的演唱會 / 選歌存在
// users/{uid}/ 底下，僅本人可讀；要做全平台統計需改用 Cloud Functions / Admin SDK。
data class AdminStats(
    val total: Int = 0,
    val approved: Int = 0,
    val pending: Int = 0,
    val rejected: Int = 0,
    val upcoming: Int = 0,
    val past: Int = 0,
    val thisMonth: Int = 0,
    val next30: Int = 0,
    val contributors: Int = 0,
    val withImage: Int = 0,
    val withSource: Int = 0,
    val avgPerMonth: Double = 0.0,
    val cityRanking: List<Pair<String, Int>> = emptyList(),
    val monthRanking: List<Pair<String, Int>> = emptyList(),
    val artistRanking: List<Pair<String, Int>> = emptyList(),
    val venueRanking: List<Pair<String, Int>> = emptyList(),
    val contributorRanking: List<Pair<String, Int>> = emptyList(),
    val weekdayRanking: List<Pair<String, Int>> = emptyList()
)

private val WEEKDAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

@HiltViewModel
class AdminDashboardViewModel @Inject constructor(
    private val repo: FreeEventsRepository,
    private val repo2: com.bandary.data.repository.ConcertRepository,
    private val wiki: com.bandary.data.remote.WikipediaArtistService,
    private val firestore: com.bandary.data.remote.FirestoreService,
    private val spotify: com.bandary.data.remote.SpotifyService,
    private val imageSearch: com.bandary.data.remote.ImageSearchService
) : ViewModel() {

    /** 自我檢測的一列結果。 */
    data class CloudCheck(val name: String, val ok: Boolean, val detail: String)

    private val _checks = kotlinx.coroutines.flow.MutableStateFlow<List<CloudCheck>>(emptyList())
    val checks: StateFlow<List<CloudCheck>> = _checks
    private val _checking = kotlinx.coroutines.flow.MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking

    /**
     * 真的去呼叫一次 Spotify 搜尋與圖片搜尋，把結果寫出來。
     *
     * 為什麼要有這張卡：這兩個功能**只會在實機上壞**，而且壞掉的樣子是
     *「安靜地沒有結果」。原因有五種以上（沒登入、函式沒部署、金鑰沒設、
     * 金鑰被撤、額度用完、搜尋引擎沒設網域），每一種要做的事都不同，
     * 可是使用者手上沒有 logcat，雲端 log 又只有專案擁有者看得到。
     * 於是回報永遠只能是「還沒好」，而我只能猜。
     *
     * 這支把猜的部分拿掉：按一下，當場講出是哪一種。
     *
     * 刻意**不自動跑**：圖片搜尋一天只有 100 次額度而且全部使用者共用，
     * 自動檢測等於每次打開後台就吃掉一次。
     */
    fun runCloudSelfTest() {
        if (_checking.value) return
        viewModelScope.launch {
            _checking.value = true
            val out = mutableListOf<CloudCheck>()

            out += if (!spotify.isConfigured()) {
                CloudCheck("Spotify 搜尋", false, "沒有登入——代理只開給登入者，先登入再測")
            } else {
                // 查一個一定有結果的字；回 0 筆又沒有錯誤，那是伺服器端的回應格式有問題。
                val tracks = spotify.searchTracks("告五人", limit = 3)
                val err = spotify.lastError.value
                when {
                    err != null -> CloudCheck("Spotify 搜尋", false, err)
                    tracks.isEmpty() -> CloudCheck(
                        "Spotify 搜尋", false,
                        "呼叫成功但回 0 筆——查「告五人」不該是空的，檢查 functions 的回應欄位"
                    )
                    else -> CloudCheck("Spotify 搜尋", true, "正常，回了 " + tracks.size + " 筆")
                }
            }

            out += if (!imageSearch.isConfigured()) {
                CloudCheck("找海報（圖片搜尋）", false, "沒有登入——代理只開給登入者，先登入再測")
            } else {
                // 同一句查詢在這個 process 裡有快取，連按也只花一次額度。
                val hits = imageSearch.searchImages("演唱會", limit = 1)
                val err = imageSearch.lastError.value
                when {
                    err != null -> CloudCheck("找海報（圖片搜尋）", false, err)
                    hits.isEmpty() -> CloudCheck(
                        "找海報（圖片搜尋）", false,
                        "呼叫成功但回 0 筆——金鑰沒問題，是搜尋引擎（CSE）裡還沒加任何網域，"
                            + "所以它無論搜什麼都是空的"
                    )
                    else -> CloudCheck("找海報（圖片搜尋）", true, "正常，回了 " + hits.size + " 筆")
                }
            }

            _checks.value = out
            _checking.value = false
        }
    }

    val loading: StateFlow<Boolean> = repo.loading

    // 使用者統計（讀 users collection；total = -1 → 尚未取得 / 無讀取權限）。
    private val _userStats = kotlinx.coroutines.flow.MutableStateFlow(
        com.bandary.data.remote.FirestoreService.UserSummary(-1, 0, 0)
    )
    val userStats: StateFlow<com.bandary.data.remote.FirestoreService.UserSummary> = _userStats

    // 全平台彙整（所有使用者的演唱會 / 選歌；需規則允許管理者讀 users/{uid}/ 子集合）。
    private val _global = kotlinx.coroutines.flow.MutableStateFlow<com.bandary.data.remote.FirestoreService.GlobalStats?>(null)
    val global: StateFlow<com.bandary.data.remote.FirestoreService.GlobalStats?> = _global

    // 訪客（裝了但沒登入）。沒有這個數字的話，漏斗最上層就只有登入者，
    // 「註冊轉換率」永遠是 100%——那是整個漏斗最沒用的一種算法。
    private val _guests = kotlinx.coroutines.flow.MutableStateFlow(
        com.bandary.data.remote.FirestoreService.GuestSummary()
    )
    val guests: StateFlow<com.bandary.data.remote.FirestoreService.GuestSummary> = _guests

    // 待審的座位視野圖。審核分散在不同地方，很容易整批被忘掉
    // ——所以要在首頁的待辦卡上出現。
    /** 每個外站連結的點擊統計（key = linkId）。 */
    private val _outbound = kotlinx.coroutines.flow.MutableStateFlow<
        Map<String, com.bandary.data.remote.FirestoreService.LinkClicks>>(emptyMap())
    val outbound: StateFlow<Map<String, com.bandary.data.remote.FirestoreService.LinkClicks>> = _outbound

    private val _pendingViews = kotlinx.coroutines.flow.MutableStateFlow(0)
    val pendingViews: StateFlow<Int> = _pendingViews

    /**
     * 待審的視野圖**整份清單**，不只是數量。
     *
     * 原本只留 `_pendingViews`（一個 Int），所以後台能顯示「有 N 張等審核」，
     * 但**沒有任何地方審得到**——`FirestoreService.reviewVenueView()` 寫好了，
     * 全專案沒有一處呼叫它。結果是：使用者傳了視野圖 → 存成 pending →
     * 永遠不會被核准 → 永遠不會出現在場館頁 → 他的照片進了黑洞，而他不會知道。
     *
     * 同一份警示清單裡，活動審核那則寫得出「到『活動』頁右上角 ⋮ → 審核投稿」，
     * 視野圖那則只寫「審過才會出現」——寫不出去哪裡，因為沒有地方可去。
     */
    private val _pendingViewList =
        kotlinx.coroutines.flow.MutableStateFlow<List<com.bandary.domain.model.VenueView>>(emptyList())
    val pendingViewList: StateFlow<List<com.bandary.domain.model.VenueView>> = _pendingViewList

    /** 通過 / 退掉一張視野圖。通過後它才會出現在對應場館的視野圖裡。 */
    fun reviewVenueView(id: String, approve: Boolean) = viewModelScope.launch {
        firestore.reviewVenueView(id, approve)
            .onSuccess {
                // 就地從清單移除，不等重新整理——審一張就要看到少一張，
                // 否則連點兩次會送出兩筆審核。
                _pendingViewList.value = _pendingViewList.value.filterNot { it.id == id }
                _pendingViews.value = _pendingViewList.value.size
                ok(if (approve) "已通過，這張圖會出現在場館的視野圖裡" else "已退掉，不會出現在視野圖裡")
            }
            .onFailure { fail("審核失敗：${it.message}") }
    }

    private val now: LocalDateTime =
        Clock.System.now().toLocalDateTime(TimeZone.of("Asia/Taipei"))

    val stats: StateFlow<AdminStats> =
        combine(repo.events, repo.pending) { approved, pending ->
            val all = approved + pending
            val thirtyDays = now.date.plus(30, kotlinx.datetime.DateTimeUnit.DAY)
            val months = all.map { "${it.dateTime.year}/${it.dateTime.monthNumber}" }.distinct().size.coerceAtLeast(1)
            AdminStats(
                total = all.size,
                approved = approved.size,
                pending = pending.size,
                rejected = 0,
                upcoming = approved.count { it.dateTime >= now },
                past = approved.count { it.dateTime < now },
                thisMonth = approved.count { it.dateTime.year == now.year && it.dateTime.monthNumber == now.monthNumber },
                next30 = approved.count { it.dateTime >= now && it.dateTime.date <= thirtyDays },
                contributors = all.mapNotNull { it.createdBy?.takeIf { e -> e.isNotBlank() } }.distinct().size,
                withImage = all.count { !it.imageUri.isNullOrBlank() },
                withSource = all.count { !it.sourceUrl.isNullOrBlank() },
                avgPerMonth = approved.size.toDouble() / months,
                cityRanking = rank(all) { it.city?.takeIf { c -> c.isNotBlank() } },
                monthRanking = rank(all) {
                    "${it.dateTime.year}/${it.dateTime.monthNumber.toString().padStart(2, '0')}"
                },
                // 展開卡司再排行。原本直接拿 artistName 當 key，音樂節那種
                // 「A / B / C / …」整串卡司會變成排行榜上的一位「藝人」
                // ——實機回報的「掃描出來都是同一個藝人」就是這個。
                artistRanking = com.bandary.domain.model.rankArtists(all.map { it.artistName }),
                venueRanking = rank(all) { it.venue.takeIf { v -> v.isNotBlank() } },
                contributorRanking = rank(all) { it.createdBy?.takeIf { e -> e.isNotBlank() } },
                weekdayRanking = run {
                    val counts = IntArray(7)
                    all.forEach { counts[it.dateTime.dayOfWeek.isoDayNumber - 1]++ }
                    WEEKDAY_LABELS.mapIndexed { i, lbl -> "週$lbl" to counts[i] }
                }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AdminStats())

    private fun rank(events: List<FreeEvent>, key: (FreeEvent) -> String?): List<Pair<String, Int>> =
        events.mapNotNull(key)
            .groupingBy { it }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key to it.value }

    // 聯絡信箱清單（管理員維護）。
    private val _contactEmails = kotlinx.coroutines.flow.MutableStateFlow<List<String>>(emptyList())
    val contactEmails: StateFlow<List<String>> = _contactEmails

    // 對外公告的最新版本（管理員維護；使用者設定頁的「最新版本」讀這個）。
    private val _latestVersion =
        kotlinx.coroutines.flow.MutableStateFlow<com.bandary.data.remote.FirestoreService.AppVersionInfo?>(null)
    val latestVersion: StateFlow<com.bandary.data.remote.FirestoreService.AppVersionInfo?> = _latestVersion

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        repo.refresh()
        firestore.userStats().onSuccess { _userStats.value = it }
        firestore.globalStats().onSuccess { _global.value = it }
        firestore.fetchContactEmails().onSuccess { _contactEmails.value = it }
        firestore.fetchLatestVersion().onSuccess { _latestVersion.value = it }
        firestore.guestStats().onSuccess { _guests.value = it }
        firestore.outboundClickStats().onSuccess { _outbound.value = it }
        firestore.pendingVenueViews().onSuccess {
            _pendingViewList.value = it
            _pendingViews.value = it.size
        }
        // 舊公告沒有 targetUids，收信匣的兩段查詢都查不到它們。
        // 只有管理員寫得動，所以趁開後台時補一次；已經有欄位的不寫回。
        firestore.backfillAnnouncementTargets()
        firestore.fetchAnnouncements().onSuccess { _sentAnnouncements.value = it }
    }

    /**
     * 公告最新版本。管理員只填 versionName，versionCode 由這裡依**專案的同一套公式**推導。
     *
     * ## 這裡原本是壞的
     * 之前用的是舊公式 `MAJOR * 10000 + MINOR * 100 + PATCH`，但 app/build.gradle.kts
     * 早就改成 `MAJOR * 1000000 + MINOR * 1000 + PATCH`（舊公式下 1.100.0 會撞到 2.0.0）。
     * 兩邊不一致的後果不是「數字難看」，而是**更新提示從來不會亮**：
     * 公告 1.108.0 會存成 20800，而實機的 versionCode 是 1108000，
     * `雲端版號 > 本機版號` 永遠不成立。按了「公告」，使用者端什麼都不會發生。
     *
     * 公式現在只有一份（[com.bandary.domain.model.AppVersionCode]），由測試綁住 build.gradle.kts；
     * 範圍檢查也跟著一致：MINOR 與 PATCH 各限 0–999。
     *
     * [alsoNotify] 為 true 時同時發一則官方通知，使用者會收到推播、開 App 也會跳出來。
     * 預設開啟——公告版號的目的就是要讓人知道，不然這個動作沒有意義。
     */
    fun saveLatestVersion(versionName: String, releaseNote: String, alsoNotify: Boolean = true) = viewModelScope.launch {
        val name = versionName.trim().removePrefix("v").removePrefix("V")
        // 公式只有一份，在 domain/model/AppVersionCode.kt，並由測試綁住 build.gradle.kts。
        val code = com.bandary.domain.model.AppVersionCode.of(name)
        if (code == null) {
            fail("版號格式需為 MAJOR.MINOR.PATCH，且 MINOR / PATCH 各限 0–999，例如 1.108.0")
            return@launch
        }
        val note = releaseNote.trim()
        firestore.setLatestVersion(name, code, note)
            .onSuccess {
                _latestVersion.value =
                    com.bandary.data.remote.FirestoreService.AppVersionInfo(name, code, note)
                if (!alsoNotify) {
                    ok("已公告最新版本 v$name（沒有發通知）")
                    return@onSuccess
                }
                firestore.postAnnouncement(
                    "新版 v$name 可以更新了",
                    note.ifBlank { "到 Google Play 更新就能用新版。" },
                    emptyList()
                )
                    .onSuccess { ok("已公告 v$name，並通知所有使用者") }
                    .onFailure { fail("已公告 v$name，但通知沒發出去：${it.message}") }
            }
            .onFailure { fail("更新失敗：${it.message}") }
    }

    /**
     * 已發出的官方通知。**為了能把發錯的收回來。**
     *
     * `FirestoreService.deleteAnnouncement()` 早就寫好了，但全專案沒有一處呼叫——
     * 也就是說公告發出去就拿不回來。v1.109.0 之後這件事變得更嚴重：
     * 公告現在會**跳到每個人臉上**，一則打錯字的公告會去敲每一個使用者。
     *
     * 只列得出**全體公告**。指定對象的公告只有收件人自己查得到
     * （`fetchAnnouncements` 走的是「公開的 + 指定給我的」兩個查詢），
     * 要列出別人的得用 Admin SDK 在伺服器端撈。UI 上照實說，不要讓人以為這是全部。
     */
    private val _sentAnnouncements =
        kotlinx.coroutines.flow.MutableStateFlow<List<com.bandary.data.remote.FirestoreService.Announcement>>(emptyList())
    val sentAnnouncements: StateFlow<List<com.bandary.data.remote.FirestoreService.Announcement>> = _sentAnnouncements

    fun loadSentAnnouncements() = viewModelScope.launch {
        firestore.fetchAnnouncements().onSuccess { _sentAnnouncements.value = it }
    }

    /**
     * 刪掉一則公告。**推播收不回來**——那封通知已經在使用者的通知欄裡了，
     * 刪除只會讓它從收信匣消失、不再跳出來。UI 上要講清楚，不要讓人以為發錯可以當沒發生。
     */
    fun deleteAnnouncement(id: String) = viewModelScope.launch {
        firestore.deleteAnnouncement(id)
            .onSuccess {
                _sentAnnouncements.value = _sentAnnouncements.value.filterNot { it.id == id }
                ok("已刪除。它不會再出現在收信匣，也不會再跳出來")
            }
            .onFailure { fail("刪除失敗：${it.message}") }
    }

    /** 儲存聯絡信箱清單（新增 / 編輯 / 移除後整批寫回）。 */
    fun saveContactEmails(list: List<String>) = viewModelScope.launch {
        val cleaned = list.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        firestore.setContactEmails(cleaned)
            .onSuccess { _contactEmails.value = cleaned; ok("已更新聯絡信箱") }
            .onFailure { fail("更新失敗：${it.message}") }
    }

    /**
     * 後台操作的結果。**要分得出成功與失敗**，因為兩者該用不同的方式呈現：
     * 成功只需要掃一眼，失敗的訊息裡有「你要改什麼」，必須留在畫面上讓人讀完。
     */
    data class AdminResult(val message: String, val isError: Boolean)

    private val _postResult = kotlinx.coroutines.flow.MutableStateFlow<AdminResult?>(null)
    val postResult: StateFlow<AdminResult?> = _postResult
    fun clearPostResult() { _postResult.value = null }
    private fun ok(message: String) { _postResult.value = AdminResult(message, isError = false) }
    private fun fail(message: String) { _postResult.value = AdminResult(message, isError = true) }

    private val _seedResult = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val seedResult: StateFlow<String?> = _seedResult
    fun clearSeedResult() { _seedResult.value = null }

    private val _seeding = kotlinx.coroutines.flow.MutableStateFlow(false)
    val seeding: StateFlow<Boolean> = _seeding

    /**
     * 用已核准的公開活動，把出現過的藝人名灌進共用藝人資料庫。
     *
     * 只取公開活動的卡司——不碰任何人的私人演唱會紀錄。把別人記在自己頁面裡的東西
     * 默默發布成公開資料不是該做的事，就算那只是團名。
     */
    private val _normalizing = kotlinx.coroutines.flow.MutableStateFlow(false)
    val normalizing: StateFlow<Boolean> = _normalizing

    /**
     * 把既有活動的 `artistName` 分隔符統一成 `" / "`。
     * 只動分隔符與空白，不動任何名字——所以這個動作重複按也不會愈跑愈歪。
     */
    /**
     * 把我自己的演唱會紀錄裡「artistName 塞了整串卡司」的那些拆開。
     * 這是本機資料（Room），不是雲端——所以每台裝置各自要跑一次。
     */
    fun splitMyLineups() = viewModelScope.launch {
        if (_normalizing.value) return@launch
        _normalizing.value = true
        runCatching { repo2.splitLineupArtists() }
            .onSuccess { (changed, scanned) ->
                _seedResult.value =
                    if (changed == 0) "掃了 $scanned 場，沒有需要拆的"
                    else "拆開了 $changed 場的卡司（共掃 $scanned 場）"
            }
            .onFailure { _seedResult.value = "拆解失敗：" + (it.message ?: "請再試一次") }
        _normalizing.value = false
    }

    // 可合併的藝人寫法。掃出來先給管理員看，**不自動合併**——
    // 合併錯了是把兩個團的紀錄混在一起，使用者自己救不回來，
    // 這種不可逆的動作一定要有人點頭。
    private val _mergeGroups =
        kotlinx.coroutines.flow.MutableStateFlow<List<com.bandary.domain.model.ArtistMerge.Group>>(emptyList())
    val mergeGroups: StateFlow<List<com.bandary.domain.model.ArtistMerge.Group>> = _mergeGroups

    fun scanArtistMerges() = viewModelScope.launch {
        if (_normalizing.value) return@launch
        _normalizing.value = true
        // 帶 wiki 進去核對正式團名。慢（每組最多幾次請求、而且要間隔以免被維基擋），
        // 但這一步正是「哪個寫法才是對的」唯一有外部依據的來源。
        firestore.findArtistMergeGroups(wiki)
            .onSuccess {
                _mergeGroups.value = it
                val verified = it.count { g -> g.wikiTitle != null }
                _seedResult.value = when {
                    it.isEmpty() -> "沒有找到可以合併的寫法"
                    verified > 0 -> "找到 ${it.size} 組，其中 $verified 組在維基上核對到正式名稱"
                    else -> "找到 ${it.size} 組（維基上都查不到，代表名取最完整的那個）"
                }
            }
            .onFailure { _seedResult.value = "掃描失敗：" + (it.message ?: "請再試一次") }
        _normalizing.value = false
    }

    fun mergeArtistGroup(group: com.bandary.domain.model.ArtistMerge.Group) = viewModelScope.launch {
        if (_normalizing.value) return@launch
        _normalizing.value = true
        firestore.mergeArtists(group)
            .onSuccess { touched ->
                _seedResult.value = "已合併成「${group.preferred}」，更新了 $touched 場活動"
                _mergeGroups.value = _mergeGroups.value.filterNot { it.canonical == group.canonical }
            }
            .onFailure { _seedResult.value = "合併失敗：" + (it.message ?: "請再試一次") }
        _normalizing.value = false
    }

    fun normalizeArtists() = viewModelScope.launch {
        if (_normalizing.value) return@launch
        _normalizing.value = true
        firestore.normalizeEventArtists()
            .onSuccess { (changed, scanned) ->
                _seedResult.value =
                    if (changed == 0) "掃了 $scanned 筆，分隔符本來就都是對的"
                    else "整理了 $changed 筆（共掃 $scanned 筆）"
            }
            .onFailure { _seedResult.value = "整理失敗：" + (it.message ?: "請再試一次") }
        _normalizing.value = false
        refresh()
    }

    fun seedArtistDirectory() = viewModelScope.launch {
        _seeding.value = true
        val names = repo.events.value
            .flatMap { e ->
                com.bandary.domain.model.splitArtistNames(e.artistName) +
                    e.guestsByArtist.values.flatten()
            }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { com.bandary.domain.model.artistFollowKey(it) }
        if (names.isEmpty()) {
            _seedResult.value = "活動頁還沒有資料可以取名字"
        } else {
            firestore.seedArtists(names)
                .onSuccess { n ->
                    _seedResult.value =
                        if (n == 0) "掃過 ${names.size} 個名字，資料庫裡都已經有了"
                        else "新增 $n 位藝人（掃過 ${names.size} 個名字）"
                }
                .onFailure { _seedResult.value = "失敗：${it.message}" }
        }
        _seeding.value = false
    }

    /**
     * 發布官方通知。[emails] 留空＝全體；有值＝只送給那些人。
     *
     * [onDone] 會收到「成功了沒」。畫面需要這個才能做對一件事：
     * **失敗的時候不要把對話框關掉**。原本是按下送出就無條件關閉，
     * 於是信箱打錯一個字 → 整則被擋下來 → 對話框已經不見了 → 標題、內容、收件人全部重打。
     * 那個「寧可退回去讓你改」的設計，在輸入被丟掉的情況下等於沒有。
     */
    fun postAnnouncement(
        title: String,
        body: String,
        emails: List<String> = emptyList(),
        onDone: (Boolean) -> Unit = {}
    ) = viewModelScope.launch {
        val targets = if (emails.isEmpty()) emptyList() else {
            val (uids, missing) = firestore.resolveUidsByEmail(emails).getOrDefault(emptyList<String>() to emails)
            if (missing.isNotEmpty()) {
                // 查不到的要講出來。靜悄悄少送幾個人，等於公告沒發到，
                // 而發的人完全不會知道。
                fail("這些信箱找不到帳號，沒有送出：" + missing.joinToString("、"))
                onDone(false)
                return@launch
            }
            if (uids.isEmpty()) {
                fail("沒有解析到任何收件者")
                onDone(false)
                return@launch
            }
            uids
        }
        firestore.postAnnouncement(title.trim(), body.trim(), targets)
            .onSuccess { ok("已發送官方通知"); onDone(true) }
            .onFailure { fail("發送失敗：${it.message}"); onDone(false) }
    }
}

// 後台數據分類。「全部」＝舊行為，其餘各留一類。
// 「全部」已移除：它把五頁的內容全部倒在同一條清單上，變成一頁捲不完的大雜燴
// ——那正好是分頁要解決的問題，留著等於分了又沒分（實機回報「頁籤劃分好，不要互通」）。
// 現在每個區塊只屬於一個頁籤，切過去就只看得到那一類。
private const val ADMIN_CAT_KPI = "數據分析"      // 漏斗 / 轉換 / 活躍 / 趨勢
private const val ADMIN_CAT_OPS = "營運"          // 官方通知 / 聯絡信箱 / 版本公告
private const val ADMIN_CAT_USERS = "使用者"      // 註冊數與活躍度
private const val ADMIN_CAT_PLATFORM = "平台"     // 全平台演唱會 / 選歌 / 洞察 / 各種 TOP
private const val ADMIN_CAT_EVENTS = "活動"   // 免費活動總覽 / 審核 / 各種 TOP
private val ADMIN_CATS = listOf(
    ADMIN_CAT_KPI, ADMIN_CAT_OPS, ADMIN_CAT_USERS, ADMIN_CAT_PLATFORM, ADMIN_CAT_EVENTS
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    onBack: () -> Unit,
    onOpenFeedback: () -> Unit = {},
    viewModel: AdminDashboardViewModel = hiltViewModel()
) {
    val stats by viewModel.stats.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val users by viewModel.userStats.collectAsState()
    val global by viewModel.global.collectAsState()
    val guests by viewModel.guests.collectAsState()
    val pendingViews by viewModel.pendingViews.collectAsState()
    val postResult by viewModel.postResult.collectAsState()
    var showCompose by remember { mutableStateOf(false) }
    // 結果改用 Snackbar 而不是 Toast。
    //
    // Toast 是 LENGTH_SHORT（約 2 秒）就消失，而這一頁最重要的那幾句訊息是**失敗原因**，
    // 例如「這些信箱找不到帳號，沒有送出：a@x.com、b@y.com」——那是要照著改的資訊，
    // 2 秒鐘讀不完一串信箱，而且 Toast 的字不能選取、看漏了也叫不回來。
    //
    // 所以分成兩種：成功掃一眼就好（Short），**失敗不自動消失**（Indefinite + 「知道了」），
    // 你讀完、改完再自己關掉。
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    androidx.compose.runtime.LaunchedEffect(postResult) {
        postResult?.let { r ->
            snackbarHostState.showSnackbar(
                message = r.message,
                // 「知道了」本身就會關掉，再加一個 X 是同一件事給兩顆按鈕。
                actionLabel = if (r.isError) "知道了" else null,
                duration = if (r.isError) androidx.compose.material3.SnackbarDuration.Indefinite
                else androidx.compose.material3.SnackbarDuration.Short
            )
            // showSnackbar 會停在這裡等到關掉，所以清除放在後面才對：
            // 先清的話同一則訊息再次出現時 LaunchedEffect 不會重跑。
            viewModel.clearPostResult()
        }
    }

    // 後台是全 App 最長的一頁，捲到底之後要回頂部換頁籤得滑很久（實機回報）。
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scrollScope = androidx.compose.runtime.rememberCoroutineScope()

    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            com.bandary.presentation.ui.components.ScrollTopFab(listState, scrollScope)
        },
        topBar = {
            TopAppBar(
                title = { Text("後台數據", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "重新整理")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        // 這頁原本是 20 幾個區塊一路平鋪下去，找一項要捲很久。
        // 現在每個區塊只屬於一個頁籤，而且**互斥**——切到哪一頁就只有那一頁的東西。
        // 預設停在「數據分析」：那是每天真的會看的一頁，其他幾頁是需要時才去。
        var cat by rememberSaveable { mutableStateOf(ADMIN_CAT_KPI) }
        fun show(c: String) = cat == c

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                // Scaffold 有 FAB 時 bottom 已含 FAB 高度，這裡只補呼吸空間。
                bottom = padding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ADMIN_CATS) { c ->
                        androidx.compose.material3.FilterChip(
                            selected = cat == c,
                            onClick = {
                                cat = c
                                // 切頁籤一律回頂部。不這樣做的話，從捲到一半的 A 頁
                                // 切到比較短的 B 頁，會停在 B 頁根本不存在的位置上，
                                // 看起來像空白頁。
                                scrollScope.launch { listState.animateScrollToItem(0) }
                            },
                            label = { Text(c) }
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(4.dp)) }

            if (show(ADMIN_CAT_KPI)) {
            item { OutboundClicksCard(viewModel) }
                item { SectionHeader("成效總覽") }
                item { Spacer(Modifier.height(4.dp)) }
                item {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AdminAnalyticsSection(
                            users = users, guests = guests, g = global,
                            stats = stats, pendingVenueViews = pendingViews
                        )
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }

            if (show(ADMIN_CAT_OPS)) {
            item { SentAnnouncementsCard(viewModel) }
            // ── 官方通知：獨立成一個區塊，不再只是頁面最上方一顆孤零零的按鈕 ──
            item { SectionHeader("官方通知") }
            item {
                androidx.compose.material3.Button(
                    onClick = { showCompose = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.Campaign, null, modifier = Modifier.size(18.dp))
                    Text("發送官方通知", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
            // ── 聯絡信箱管理 ──
            item { SectionHeader("藝人資料庫") }
            item {
                val seeding by viewModel.seeding.collectAsState()
                val seedResult by viewModel.seedResult.collectAsState()
                val seedCtx = androidx.compose.ui.platform.LocalContext.current
                LaunchedEffect(seedResult) {
                    seedResult?.let {
                        android.widget.Toast.makeText(seedCtx, it, android.widget.Toast.LENGTH_LONG).show()
                        viewModel.clearSeedResult()
                    }
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "從活動頁補齊藝人名單",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "掃過所有已核准的公開活動，把卡司裡的藝人名加進共用資料庫（已經有的不會重複加）。" +
                                "不會碰任何人的私人演唱會紀錄。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        androidx.compose.material3.Button(
                            onClick = { viewModel.seedArtistDirectory() },
                            enabled = !seeding,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (seeding) "處理中…" else "開始補齊") }
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }

            item {
                // 藝人欄位整理：既有資料的分隔符五花八門（、｜ 沒空格的 /），
                // castLabel 只認 " / "，所以嘉賓會對不上主藝人。
                val normalizing by viewModel.normalizing.collectAsState()
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "整理既有活動的藝人欄位",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "把卡司的分隔符統一成「 / 」。只動分隔符與空白，不會改到任何名字，" +
                                "重複執行也不會愈跑愈歪。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.OutlinedButton(
                                onClick = { viewModel.normalizeArtists() },
                                enabled = !normalizing,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(if (normalizing) "處理中…" else "整理活動")
                            }
                            // 本機紀錄的卡司拆解。這條是 Room 不是雲端，
                            // 所以每台裝置各自要跑一次——按鈕文字要講清楚是「我的紀錄」。
                            androidx.compose.material3.OutlinedButton(
                                onClick = { viewModel.splitMyLineups() },
                                enabled = !normalizing,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(if (normalizing) "處理中…" else "拆開我的卡司")
                            }
                        }
                        Text(
                            "「拆開我的卡司」處理的是這台裝置上自己的演唱會紀錄：" +
                                "把 artistName 裡黏成一串的卡司拆開，第一位留著、其餘移進音樂節名單。" +
                                "只動拆得開的，拆不出兩位以上的不碰。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item {
                val normalizing by viewModel.normalizing.collectAsState()
                val groups by viewModel.mergeGroups.collectAsState()
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "合併同一個團的不同寫法",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "例如「ggteen」「公館青少年」「公館青少年GGteen」其實是同一個團。" +
                                "分成三筆的話，追蹤只追得到其中一種、排行會把一個團拆成三份。" +
                                "掃出來之後要你點頭才會合併——合併是不可逆的。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        androidx.compose.material3.OutlinedButton(
                            onClick = { viewModel.scanArtistMerges() },
                            enabled = !normalizing,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(if (normalizing) "核對中…（要跑一會）" else "掃描並跟維基核對")
                        }
                        Text(
                            "會跟中文維基百科核對正式團名。維基的全文搜尋不可盡信" +
                                "（實測查「公館青少年」第一筆是中國的古蹟「蔡锷公馆」），" +
                                "所以只有在條目開頭真的提到該名字時才採用。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        groups.forEach { g ->
                            androidx.compose.material3.HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    g.preferred,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                // 代表名是哪裡來的要講清楚——維基核對過的可信度
                                // 跟「我們挑了最長的那個」差很多，管理員要看得出差別。
                                Text(
                                    if (g.wikiTitle != null) "依維基百科條目名稱"
                                    else "維基查不到，取最完整的寫法",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (g.wikiTitle != null) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "併入：" + g.others.joinToString("、"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row {
                                    Spacer(Modifier.weight(1f))
                                    androidx.compose.material3.TextButton(
                                        onClick = { viewModel.mergeArtistGroup(g) },
                                        enabled = !normalizing
                                    ) { Text("合併成這個") }
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
            // 使用者回饋原本只在「設定 → 管理者」底下，後台這裡完全沒有入口
            // ——而它本來就屬於營運（實機回報「管理員要怎麼看到回饋意見」）。
            item { SectionHeader("使用者回饋") }
            item {
                androidx.compose.material3.Button(
                    onClick = onOpenFeedback,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(Icons.Outlined.Feedback, null, modifier = Modifier.size(18.dp))
                    Text(
                        "查看使用者回饋",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
            item { SectionHeader("聯絡信箱") }
            item { ContactEmailsCard(viewModel) }
            item { Spacer(Modifier.height(8.dp)) }
            // ── 版本公告 ──
            item { SectionHeader("最新版本") }
            item { LatestVersionCard(viewModel) }
            item { Spacer(Modifier.height(8.dp)) }
            // ── 雲端功能自我檢測 ──
            item { SectionHeader("雲端功能自我檢測") }
            item { CloudSelfTestCard(viewModel) }
            item { Spacer(Modifier.height(8.dp)) }
            }  // ── 營運 結束 ──

            if (show(ADMIN_CAT_USERS)) {
            // ── 使用者 ──
            item { SectionHeader("使用者") }
            if (users.total >= 0) {
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ChartCard("活躍概況", "訪客安裝 ${guests.total} · 已登入 ${users.total}") {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row {
                                    BigMetric("${users.total}", "註冊使用者", modifier = Modifier.weight(1f))
                                    BigMetric("${global?.activeToday ?: 0}", "今日上線", modifier = Modifier.weight(1f))
                                    BigMetric("${users.active7d}", "7 日活躍", modifier = Modifier.weight(1f))
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                // 四個並排的數字看不出彼此的關係；畫成構成圖才讀得出
                                // 「大部分人其實已經 30 天沒回來了」這種結論。
                                DonutChart(
                                    slices = listOf(
                                        "今日" to (global?.activeToday ?: 0),
                                        "7 日內" to (users.active7d - (global?.activeToday ?: 0)).coerceAtLeast(0),
                                        "30 日內" to (users.active30d - users.active7d).coerceAtLeast(0),
                                        "沉睡" to (users.total - users.active30d).coerceAtLeast(0)
                                    ),
                                    centerLabel = "使用者"
                                )
                            }
                        }
                        if (users.total > 0) {
                            ChartCard("回訪", "沉睡＝超過 30 天沒開過 App") {
                                KpiGauge(
                                    com.bandary.domain.model.RatioKpi(
                                        label = "30 日回訪率",
                                        value = users.active30d.toDouble() / users.total,
                                        caption = "${users.active30d} / ${users.total} 位近 30 天開過",
                                        target = 0.50
                                    )
                                )
                            }
                        }
                    }
                }
            } else {
                item {
                    Card(
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(
                            "尚未取得使用者資料。請確認 Firestore 規則已允許管理者讀取 users collection，詳見 CONSOLE_SETUP.md。",
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            }  // ── 使用者 結束 ──

            if (show(ADMIN_CAT_PLATFORM)) {
            // ── 全平台（所有使用者的演唱會 / 選歌）──
            global?.let { g ->
                item { Spacer(Modifier.height(12.dp)) }
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ChartCard(
                            "全平台紀錄",
                            "抽樣 ${g.scannedUsers} 位使用者"
                        ) {
                            Row {
                                BigMetric(
                                    "${g.totalConcerts}", "演唱會",
                                    delta = com.bandary.domain.model.momGrowth(g.monthlyConcertTrend),
                                    modifier = Modifier.weight(1f)
                                )
                                BigMetric("${g.totalSongs}", "選歌", modifier = Modifier.weight(1f))
                                BigMetric(
                                    "%.1f".format(g.avgConcertsPerActiveUser),
                                    "每人平均場數", modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        if (g.monthlyConcertTrend.size >= 2) {
                            val slope = com.bandary.domain.model.trendSlope(g.monthlyConcertTrend)
                            ChartCard(
                                "近 12 個月新增場次",
                                when {
                                    slope > 0.5 -> "整體向上，平均每月多 ${"%.1f".format(slope)} 筆"
                                    slope < -0.5 -> "整體向下，平均每月少 ${"%.1f".format(-slope)} 筆"
                                    else -> "大致持平"
                                }
                            ) {
                                // 月份是時間序列——以前用橫條排行畫，等於把「時間」
                                // 當成可以照大小排序的類別，走勢完全讀不出來。
                                TrendChart(g.monthlyConcertTrend)
                            }
                        }

                        if (g.scannedUsers > 0) {
                            ChartCard("使用者結構", "都以抽樣到的 ${g.scannedUsers} 位為分母") {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    KpiGauge(
                                        com.bandary.domain.model.RatioKpi(
                                            label = "Pro 轉換率",
                                            value = g.proUsers.toDouble() / g.scannedUsers,
                                            caption = "${g.proUsers} 位已解鎖 Pro",
                                            target = 0.05
                                        )
                                    )
                                    KpiGauge(
                                        com.bandary.domain.model.RatioKpi(
                                            label = "重度使用者占比",
                                            value = g.powerUsers.toDouble() / g.scannedUsers,
                                            caption = "記滿 10 場以上的有 ${g.powerUsers} 位",
                                            target = 0.15
                                        )
                                    )
                                    KpiGauge(
                                        com.bandary.domain.model.RatioKpi(
                                            label = "有記過演出的比例",
                                            value = g.usersWithConcerts.toDouble() / g.scannedUsers,
                                            caption = "${g.usersWithConcerts} 位至少記過一場",
                                            target = 0.40
                                        )
                                    )
                                }
                            }
                        }

                        if (g.paidConcerts + g.freeConcerts > 0) {
                            ChartCard(
                                "票價結構",
                                "這是**使用者的購票支出**，不是 App 的營收"
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    DonutChart(
                                        slices = listOf(
                                            "付費場次" to g.paidConcerts,
                                            "免費 / 未填" to g.freeConcerts
                                        ),
                                        centerLabel = "場次"
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    Row {
                                        BigMetric(
                                            "NT$" + (if (g.paidConcerts > 0) g.totalSpend / g.paidConcerts else 0),
                                            "平均票價", modifier = Modifier.weight(1f)
                                        )
                                        BigMetric(
                                            "NT$" + "%,d".format(g.totalSpend),
                                            "支出總額（估算）", modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }

                        if (g.concertArtistRanking.isNotEmpty()) {
                            ChartCard("看最多的藝人", "卡司已展開成個別藝人") {
                                RankBarList(g.concertArtistRanking, limit = 10, valueSuffix = " 場")
                            }
                        }
                        if (g.concertCityRanking.isNotEmpty()) {
                            ChartCard("演出縣市") {
                                RankBarList(g.concertCityRanking, limit = 8, valueSuffix = " 場")
                            }
                        }
                        if (g.songArtistRanking.isNotEmpty()) {
                            ChartCard("選歌最多的藝人") {
                                RankBarList(g.songArtistRanking, limit = 10, valueSuffix = " 首")
                            }
                        }
                        if (g.songRanking.isNotEmpty()) {
                            ChartCard("最常被選的歌") {
                                RankBarList(g.songRanking, limit = 10, valueSuffix = " 次")
                            }
                        }
                    }
                }
            }
            if (global == null && users.total >= 0) {
                item {
                    Card(
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(
                            "全平台演唱會 / 選歌統計讀取中或尚無權限。需在 Firestore 規則允許管理者讀取 users/{uid} 底下的子集合，詳見 CONSOLE_SETUP.md。",
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            }  // ── 平台 結束 ──

            if (show(ADMIN_CAT_EVENTS)) {
            item { Spacer(Modifier.height(4.dp)) }
            item { PendingVenueViewsCard(viewModel) }
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ChartCard(
                        "活動總覽",
                        if (stats.pending > 0) "有 ${stats.pending} 筆待審" else "沒有待審的投稿"
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row {
                                BigMetric("${stats.total}", "活動總數", modifier = Modifier.weight(1f))
                                BigMetric("${stats.upcoming}", "即將到來", modifier = Modifier.weight(1f))
                                BigMetric("${stats.contributors}", "投稿者", modifier = Modifier.weight(1f))
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row {
                                BigMetric("${stats.thisMonth}", "本月", modifier = Modifier.weight(1f))
                                BigMetric("${stats.next30}", "未來 30 天", modifier = Modifier.weight(1f))
                                BigMetric(
                                    "${kotlin.math.round(stats.avgPerMonth).toInt()}",
                                    "平均每月", modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // 審核狀態是「構成」，不是三個不相干的數字——
                    // 畫成一張圖才看得出待審佔了多少。
                    if (stats.total > 0) {
                        ChartCard("審核狀態") {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                DonutChart(
                                    slices = listOf(
                                        "已核准" to stats.approved,
                                        "待審核" to stats.pending
                                    ),
                                    centerLabel = "活動"
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                KpiGauge(
                                    com.bandary.domain.model.RatioKpi(
                                        label = "有附活動圖",
                                        value = stats.withImage.toDouble() / stats.total,
                                        caption = "有海報的活動在列表上點擊率明顯比較高"
                                    )
                                )
                                KpiGauge(
                                    com.bandary.domain.model.RatioKpi(
                                        label = "有附來源網址",
                                        value = stats.withSource.toDouble() / stats.total,
                                        caption = "爬蟲與維基匯入都會帶來源"
                                    )
                                )
                            }
                        }
                    }

                    if (stats.monthRanking.size >= 2) {
                        // 月份照大小排序的橫條完全讀不出走勢；時間序列就該用折線。
                        val byMonth = stats.monthRanking.sortedBy { it.first }.takeLast(12)
                        ChartCard("各月份活動數", "近 ${byMonth.size} 個月") {
                            TrendChart(byMonth)
                        }
                    }

                    if (stats.weekdayRanking.any { it.second > 0 }) {
                        ChartCard("星期分布", "排檔期用：看哪幾天本來就擠") {
                            // 星期有固定順序，不能照大小排——「週六最多」要在
                            // 週一到週日排好的時候才讀得出來。
                            CycleBarChart(stats.weekdayRanking)
                        }
                    }

                    if (stats.artistRanking.isNotEmpty()) {
                        ChartCard("熱門藝人 / 樂團", "卡司已展開成個別藝人") {
                            RankBarList(stats.artistRanking, limit = 10, valueSuffix = " 場")
                        }
                    }
                    if (stats.venueRanking.isNotEmpty()) {
                        ChartCard("熱門場地") {
                            RankBarList(stats.venueRanking, limit = 8, valueSuffix = " 場")
                        }
                    }
                    if (stats.cityRanking.isNotEmpty()) {
                        ChartCard("各縣市分布") {
                            RankBarList(stats.cityRanking, limit = 8, valueSuffix = " 場")
                        }
                    }
                    if (stats.contributorRanking.isNotEmpty()) {
                        val top3 = com.bandary.domain.model.concentration(stats.contributorRanking, 3)
                        ChartCard(
                            "投稿者",
                            if (top3 >= 0.8)
                                "前三名佔了 ${com.bandary.domain.model.formatPercent(top3)}——內容過度依賴少數人"
                            else "前三名佔 ${com.bandary.domain.model.formatPercent(top3)}"
                        ) {
                            RankBarList(stats.contributorRanking, limit = 8, valueSuffix = " 筆")
                        }
                    }
                }
            }
            }  // ── 活動 結束 ──

            item { Spacer(Modifier.height(16.dp)) }
            item {
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        "說明：活動類統計來自雲端共用的「活動」；使用者數 / 活躍度來自 users collection。" +
                            "各使用者私有的演唱會 / 選歌僅本人可讀，要做全平台所有使用者的演唱會統計需改用 Cloud Functions / Admin SDK 在伺服器端彙整。",
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                    }
                }
            }
        }
    }

    if (showCompose) {
        var title by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        // 收件人：留空＝全體。用 email 而不是 uid——uid 是你看不懂也記不住的亂碼。
        var recipients by remember { mutableStateOf("") }
        // 送出中：擋掉連點兩次送出兩則。
        var sending by remember { mutableStateOf(false) }
        val emailList = recipients.split(',', '，', '\n', ';', '；')
            .map { it.trim() }.filter { it.isNotBlank() }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCompose = false },
            title = { Text("發送官方通知") },
            text = {
                com.bandary.presentation.ui.components.DialogBody(spacing = 10.dp) {
                    Text(
                        if (emailList.isEmpty())
                            "送給全部使用者。他們會收到系統通知，並在「我的」頁的通知收信匣看到。"
                        else "只送給這 ${emailList.size} 位。其他人看不到這則公告。",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (emailList.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.primary
                    )
                    androidx.compose.material3.OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("標題") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    androidx.compose.material3.OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("內容") }, minLines = 5,
                        modifier = Modifier.fillMaxWidth())
                    androidx.compose.material3.OutlinedTextField(
                        value = recipients,
                        onValueChange = { recipients = it },
                        label = { Text("收件人信箱（留空＝全體）") },
                        placeholder = { Text("多位用逗號或換行分隔") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "找不到帳號的信箱會整則擋下來不送——寧可退回去讓你改，" +
                            "也不要少送給其中一個人而你完全不知道。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                // 只有**成功**才關掉對話框。失敗就留在原地，標題 / 內容 / 收件人都還在，
                // 照 Snackbar 講的原因改一個字再送就好——原本是按下去就關，
                // 信箱打錯一個字就要整則重打。
                androidx.compose.material3.TextButton(
                    onClick = {
                        if (title.isNotBlank() && body.isNotBlank() && !sending) {
                            sending = true
                            viewModel.postAnnouncement(title, body, emailList) { success ->
                                sending = false
                                if (success) showCompose = false
                            }
                        }
                    },
                    enabled = title.isNotBlank() && body.isNotBlank() && !sending
                ) {
                    Text(
                        when {
                            sending -> "發送中…"
                            emailList.isEmpty() -> "發送給全體"
                            else -> "發送給 ${emailList.size} 位"
                        }
                    )
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showCompose = false },
                    enabled = !sending
                ) { Text("取消") }
            }
        )
    }
}

/**
 * 外站連結的點擊與轉換率。
 *
 * ## 三個數字，因為一個數字會被誤讀
 * 只給「點擊次數」的話，10 個人各點一次和 1 個人點了 10 次長得一模一樣，
 * 但那兩件事對「要不要留這個入口」是完全相反的結論。所以三個都給：
 * 點擊次數、點過的安裝數、以及**點過的 ÷ 全部安裝**＝轉換率。
 *
 * 分母用後台本來就有的「訪客安裝 + 註冊使用者」，不另外造一個數字。
 * 分母是 0 的時候不顯示百分比——「0 分之 0」算出來的任何數字都是假的。
 *
 * ## 這些數字不是下載數也不是贊助筆數
 * 它算的是**App 裡那一列被點了幾次**，不是對方網站的瀏覽量、下載數或贊助筆數——
 * 那些在別人的後台。標籤照實寫，否則看到數字會以為有人真的裝了。
 *
 * ## 連結清單照 OutboundLinks 排，沒資料的也列出來
 * 「這個連結零點擊」本身就是答案（放錯位置、文案沒人想點），
 * 只列有資料的會讓那個答案消失。
 */
/**
 * Spotify 搜尋 / 找海報 當場測一次，講出壞在哪。
 *
 * 這兩支是整個 App 裡最難回報的功能：它們壞掉的樣子就是「沒有結果」，
 * 跟真的沒有結果一模一樣。原因卻有好幾種，而且分別要去不同的地方修
 *（Secret Manager、Programmable Search Engine、firebase deploy、額度等明天）。
 * 以前只能靠猜，現在按一下就知道。
 */
@Composable
private fun CloudSelfTestCard(viewModel: AdminDashboardViewModel) {
    val checks by viewModel.checks.collectAsState()
    val checking by viewModel.checking.collectAsState()
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Spotify 搜尋 ‧ 找海報",
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
            )
            Text(
                "這兩支壞掉時的樣子就是「沒有結果」，看不出原因。按一下當場測，直接講是哪一種。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            checks.forEach { c ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        if (c.ok) "OK" else "!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (c.ok) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.size(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            c.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            c.detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Button(
                onClick = { viewModel.runCloudSelfTest() },
                enabled = !checking,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when {
                        checking -> "檢測中…"
                        checks.isEmpty() -> "開始檢測"
                        else -> "再測一次"
                    }
                )
            }
            Text(
                "圖片搜尋一天只有 100 次額度、而且是全部使用者共用，所以這張卡不會自動跑"
                    + "——同一次開啟 App 連按也只花一次。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OutboundClicksCard(viewModel: AdminDashboardViewModel) {
    val stats by viewModel.outbound.collectAsState()
    val users by viewModel.userStats.collectAsState()
    val guests by viewModel.guests.collectAsState()
    val base = (if (users.total > 0) users.total else 0) + guests.total
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "外站連結・轉換率",
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
            )
            Text(
                if (base > 0) "轉換率＝點過的安裝數 ÷ 全部安裝 $base（訪客 + 已登入）"
                else "還沒有安裝數可以當分母，所以不算轉換率",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            com.bandary.domain.model.OutboundLinks.ALL.forEach { link ->
                val c = stats[link.id]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            link.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            if (c == null) "還沒有人點過"
                            else "${c.taps} 次 ‧ ${c.installs} 個安裝 ‧ 近 7 天 ${c.installs7d}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        if (base > 0 && c != null) "${c.installs * 100 / base}%" else "—",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (c != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            Text(
                "這是「那一列被點了幾次」，不是對方網站的瀏覽量、下載數或贊助筆數"
                    + "——那些在別人的後台，App 拿不到。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 已發出的公告 + 刪除。**這張卡以前不存在**，公告發出去就拿不回來。
 *
 * 刪除要再確認一次：它是不可逆的，而且會從**所有人**的收信匣消失。
 * 確認框裡同時講明「推播收不回來」——發錯的那封通知已經在對方手機的通知欄裡了，
 * 刪除不會讓它當作沒發生過。這件事不講的話，審的人會以為按了就乾淨了。
 */
@Composable
private fun SentAnnouncementsCard(viewModel: AdminDashboardViewModel) {
    val sent by viewModel.sentAnnouncements.collectAsState()
    var confirming by remember {
        mutableStateOf<com.bandary.data.remote.FirestoreService.Announcement?>(null)
    }
    if (sent.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "已發出的公告",
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
            )
            Text(
                "只列得出全體公告。指定對象的公告只有收件人自己看得到，這裡撈不到。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            sent.forEach { a ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            a.title.ifBlank { "（沒有標題）" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.TAIWAN)
                                .format(java.util.Date(a.createdAt)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { confirming = a }) {
                        Icon(
                            Icons.Outlined.Delete, contentDescription = "刪除這則公告",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
    confirming?.let { target ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text("刪除這則公告？") },
            text = {
                com.bandary.presentation.ui.components.DialogBody(spacing = 8.dp) {
                    Text(target.title.ifBlank { "（沒有標題）" }, fontWeight = FontWeight.Bold)
                    Text(
                        "它會從所有人的收信匣消失，也不會再跳出來。這個動作不能復原。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "推播收不回來——那封通知已經在使用者的通知欄裡了。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    viewModel.deleteAnnouncement(target.id); confirming = null
                }) { Text("刪除") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirming = null }) { Text("取消") }
            }
        )
    }
}

/**
 * 座位視野圖的審核。**這張卡以前不存在。**
 *
 * 一張圖要看三件事才審得下去：照片本身、是哪個場館的哪一區、投稿者寫的心得。
 * 所以不做成一行一行的清單——只有名字和按鈕的清單，審的人只能憑場館名字猜，
 * 那不是審核，是亂按。照片直接放大張一點，心得完整顯示。
 *
 * 沒有待審的時候整張卡不顯示（而不是顯示「目前沒有待審」）：
 * 後台已經夠長了，常態是零，為了零而固定佔一塊版面不值得。
 */
@Composable
private fun PendingVenueViewsCard(viewModel: AdminDashboardViewModel) {
    val pending by viewModel.pendingViewList.collectAsState()
    if (pending.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "座位視野圖待審 ${pending.size} 張",
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
            )
            Text(
                "通過之後才會出現在對應場館的視野圖裡。退掉的不會刪除，只是不顯示。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            pending.forEach { v ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.bandary.presentation.ui.components.EventImage(
                        data = v.image,
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                    Text(
                        buildString {
                            append(v.venue.ifBlank { "（沒寫場館）" })
                            if (v.section.isNotBlank()) append(" · ").append(v.section)
                        },
                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold
                    )
                    if (v.note.isNotBlank()) {
                        Text(
                            v.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.reviewVenueView(v.id, true) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text("通過") }
                        androidx.compose.material3.OutlinedButton(
                            onClick = { viewModel.reviewVenueView(v.id, false) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text("退掉") }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

// 管理員維護聯絡信箱：每個信箱一列可就地編輯 + 移除；可新增；按「儲存」整批寫回 Firestore。
@Composable
private fun ContactEmailsCard(viewModel: AdminDashboardViewModel) {
    val emails by viewModel.contactEmails.collectAsState()
    val local = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    var hasSynced by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(emails) {
        // 只在首次載入 / 儲存後同步，避免打字中被覆蓋。
        if (!hasSynced && emails.isNotEmpty()) { local.clear(); local.addAll(emails); hasSynced = true }
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("使用者在「聯絡我們」會看到這些信箱。可新增、編輯或移除。",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (local.isEmpty()) {
                Text("目前沒有設定信箱，將沿用 App 內建預設值。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            local.forEachIndexed { i, email ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    androidx.compose.material3.OutlinedTextField(
                        value = email,
                        onValueChange = { local[i] = it },
                        singleLine = true,
                        placeholder = { Text("name@example.com") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { local.removeAt(i) }) {
                        Icon(Icons.Outlined.Delete, "移除", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedButton(
                    onClick = { local.add("") },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
                    Text("新增信箱", modifier = Modifier.padding(start = 4.dp))
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { viewModel.saveContactEmails(local.toList()) },
                    shape = RoundedCornerShape(10.dp)
                ) { Text("儲存") }
            }
        }
    }
}

// 管理員公告最新版本：上架新版後填這裡。
// **彈出式通知只會因為你按了發送而出現**，App 不會自己比對版號跳出來——
// 開著「同時通知所有人」按下去，使用者才會收到推播、下次開 App 也才會跳
// （見 ui/notice/AppNoticeHost.kt 的說明）。關掉就只是把版號寫進雲端，不會攔任何人。
// 只填 versionName，versionCode 由 ViewModel 依同一套規則推導，兩者不會對不上。
@Composable
private fun LatestVersionCard(viewModel: AdminDashboardViewModel) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val published by viewModel.latestVersion.collectAsState()
    val installed = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "—"
    }
    var name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    // 預設要通知。公告版號的目的就是讓人知道有新版，
    // 預設不通知等於預設「做了但沒人看到」。
    var notify by remember { mutableStateOf(true) }
    var hasSynced by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(published) {
        // 只在首次載入時帶入雲端現值，避免打字中被覆蓋。
        if (!hasSynced && published != null) {
            name = published!!.versionName; note = published!!.releaseNote; hasSynced = true
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "上架新版後在這裡公告版號。格式 MAJOR.MINOR.PATCH，例如 1.108.0（MINOR / PATCH 各限 0–999）。",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "你這台裝上的版本：v$installed" +
                    (published?.let { " · 目前公告：v${it.versionName}" } ?: " · 尚未公告任何版本"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            androidx.compose.material3.OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("最新版號") },
                placeholder = { Text("1.3.1") },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )
            androidx.compose.material3.OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("更新摘要") },
                placeholder = { Text("修好節目表多張圖、統計標題釘頂") },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("同時通知所有人", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (notify) "會收到推播，開 App 也會跳出來"
                        else "只寫入版號，不會跳出來；使用者要自己到設定頁才看得到",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                androidx.compose.material3.Switch(checked = notify, onCheckedChange = { notify = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { viewModel.saveLatestVersion(name, note, notify) },
                    enabled = name.isNotBlank(),
                    shape = RoundedCornerShape(10.dp)
                ) { Text(if (notify) "公告並通知" else "只公告") }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: Int, modifier: Modifier = Modifier, highlight: Boolean = false) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "$value",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (highlight) MaterialTheme.colorScheme.onTertiaryContainer
                else MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(2.dp))
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InsightRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun RatioCard(label: String, count: Int, total: Int) {
    val pct = if (total > 0) count.toFloat() / total else 0f
    Card(
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    "$count / $total・${(pct * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            LinearProgressIndicator(
                progress = { pct },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
private fun RankCard(items: List<Pair<String, Int>>) {
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val max = (items.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    val shown = if (expanded) items else items.take(5)
    Card(
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shown.forEach { (name, count) ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "$count",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    LinearProgressIndicator(
                        progress = { count.toFloat() / max },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
            if (items.size > 5) {
                androidx.compose.material3.TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (expanded) "收合" else "展開全部 ${items.size} 項", fontSize = 13.sp)
                }
            }
        }
    }
}
