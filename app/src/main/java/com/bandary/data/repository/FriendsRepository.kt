package com.bandary.data.repository

import com.bandary.data.remote.FirestoreService
import com.bandary.domain.model.Friend
import com.bandary.domain.model.FriendRequest
import com.bandary.domain.model.SharedCalendar
import com.bandary.domain.model.SharedShow
import com.bandary.domain.model.SharedSongProfile
import com.bandary.domain.model.SharedTopSong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 好友 / 共享日曆（v3，好友代碼 + 雙向請求）：
 * - 「發布」把自己即將到來的演出（精簡版）寫到 shared_calendars/{uid}。
 * - 每位使用者有唯一「好友代碼（handle）」；以代碼（或對方開放時用 email）搜尋 → 送出好友請求。
 * - 對方接受後雙方互列為好友；檢視好友 = 讀 shared_calendars/{friendUid}。
 */
@Singleton
class FriendsRepository @Inject constructor(
    private val firestore: FirestoreService,
    private val concertRepo: ConcertRepository,
    private val songRepo: DailySongRepository,
    private val settings: com.bandary.data.local.SettingsDataStore
) {
    private val _friends = MutableStateFlow<List<Friend>>(emptyList())
    val friends: StateFlow<List<Friend>> = _friends.asStateFlow()

    private val _requests = MutableStateFlow<List<FriendRequest>>(emptyList())
    val requests: StateFlow<List<FriendRequest>> = _requests.asStateFlow()

    private val _myHandle = MutableStateFlow<String?>(null)
    val myHandle: StateFlow<String?> = _myHandle.asStateFlow()

    private val _discoverableByEmail = MutableStateFlow(false)
    val discoverableByEmail: StateFlow<Boolean> = _discoverableByEmail.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /**
     * 把「我的」頁自訂暱稱同步給 FirestoreService，讓好友看到的顯示名稱與 App 內一致。
     * 同時寫一份到雲端（users/{uid}.nickname），網頁版才知道要顯示哪個名字——
     * 暱稱本來只存在手機本機的 DataStore，網頁完全讀不到，排行榜就會出現兩種名字。
     */
    private suspend fun syncDisplayName() {
        val nick = settings.nickname.first()?.takeIf { it.isNotBlank() }
        firestore.preferredDisplayName = nick
        runCatching { firestore.publishNickname(nick) }
    }

    suspend fun refresh() {
        if (_loading.value) return
        syncDisplayName()
        _loading.update { true }
        firestore.fetchFriends()
            .onSuccess { list -> _friends.update { list } }
            .onFailure { e -> _message.update { "讀取好友失敗：${e.message}" } }
        firestore.fetchFriendRequests().onSuccess { list -> _requests.update { list } }
        // 好友代碼是系統配發的，沒有就在這裡補上——使用者不需要（也不能）自己取，
        // 全 App 唯一要取的名字是「我的」頁的暱稱。
        firestore.ensureHandle().onSuccess { h -> _myHandle.update { h } }
        firestore.currentUid()?.let { me ->
            firestore.fetchSharedCalendar(me).onSuccess { cal -> _discoverableByEmail.update { cal?.discoverableByEmail ?: false } }
        }
        migrateSharedCalendarIfNeeded()
        _loading.update { false }
    }

    /**
     * 一次性搬家：把已發布的共享日曆從「名片文件的欄位」移到 private/payload 子文件，
     * 並刪掉名片上舊的 shows / interestedFreeEventIds / songProfile。
     *
     * 為什麼要自動做：那些舊欄位是**任何登入者**都讀得到的，只靠使用者哪天自己按「更新我的
     * 共享日曆」才清掉的話，收緊規則對既有使用者等於沒生效。這裡只是把「使用者早已選擇發布
     * 的同一份內容」搬到更嚴格的位置並刪除公開副本，不會揭露任何新東西。
     */
    private suspend fun migrateSharedCalendarIfNeeded() {
        if (!firestore.isSignedIn()) return
        if (settings.sharedCalendarMigrated.first()) return
        val me = firestore.currentUid() ?: return
        // 沒發布過（雲端沒有名片）就不用搬，直接標記完成。
        val published = firestore.fetchSharedCalendar(me).getOrNull() != null
        if (published) publishMyCalendar(quiet = true)
        settings.setSharedCalendarMigrated(true)
    }

    /**
     * 改成自己想要的好友代碼（唯一性由後端 transaction 保證，撞號會失敗並回訊息）。
     * 沒改的人不必管：[FirestoreService.ensureHandle] 已經在 refresh 時自動配過一組。
     */
    suspend fun setHandle(raw: String) {
        val handle = raw.trim().removePrefix("@")
        firestore.claimHandle(handle)
            .onSuccess { _myHandle.update { handle }; _message.update { "好友代碼已改為 @$handle" } }
            .onFailure { e -> _message.update { e.message ?: "設定好友代碼失敗" } }
    }

    /** 是否允許他人用 email 搜尋到我。 */
    suspend fun setDiscoverableByEmail(v: Boolean) {
        firestore.setDiscoverableByEmail(v)
            .onSuccess { _discoverableByEmail.update { v } }
            .onFailure { e -> _message.update { "設定失敗：${e.message}" } }
    }

    /** 把自己即將到來的演出（含「想搶的票」標記）+ 有興趣的免費活動發布成共享日曆。 */
    suspend fun publishMyCalendar(quiet: Boolean = false) {
        syncDisplayName()
        val now = Clock.System.now().toLocalDateTime(TimeZone.of("Asia/Taipei"))
        val shows = concertRepo.getAllConcerts().first()
            .filter { it.dateTime >= now }
            .sortedBy { it.dateTime }
            .map {
                SharedShow(
                    name = it.name,
                    artistName = it.artistName,
                    venue = it.venue,
                    city = it.city,
                    dateTime = it.dateTime,
                    type = it.type.name,
                    wantTicket = it.ticketStatus == com.bandary.domain.model.TicketStatus.NONE
                )
            }
        val interested = settings.interestedEvents.first().toList()
        firestore.publishMyCalendar(shows, interested, buildSongProfile())
            .onSuccess { if (!quiet) _message.update { "已發布我的日曆・${shows.size} 場即將到來" } }
            .onFailure { e -> if (!quiet) _message.update { "發布失敗：${e.message}" } }
    }

    /** 已看完的判定：多日場次以最後一天（endDate / extraDates 最大值）為準，跟統計頁同一套。 */
    private fun com.bandary.domain.model.Concert.lastOccurrence(): kotlinx.datetime.LocalDate {
        val dates = buildList {
            add(dateTime.date)
            endDate?.let { add(it) }
            addAll(extraDates)
        }
        return dates.maxOrNull() ?: dateTime.date
    }

    /**
     * 上傳「今年看了幾場」給全站年度排行榜——只上傳一個數字＋暱稱，不是完整演出清單。
     * 預設公開，退出設定（leaderboardOptOut）就整筆刪掉。靜默失敗，不影響呼叫端的主流程。
     */
    suspend fun publishYearlyLeaderboard() {
        // 少了這行，排行榜寫上去的會是 Google 帳號名稱而不是使用者自訂的暱稱——
        // publishMyCalendar 有呼叫、這裡漏掉，於是同一個人在好友頁與排行榜顯示成兩個名字。
        syncDisplayName()
        val today = Clock.System.now().toLocalDateTime(TimeZone.of("Asia/Taipei")).date
        val year = today.year
        // 只算「已經看完」的場次；還沒發生的不該計入排名。
        val seen = concertRepo.getAllConcerts().first()
            .filter { it.dateTime.date.year == year && it.lastOccurrence() < today }
        val optedOut = settings.leaderboardOptOut.first()

        // 常聽藝人：聚合結果（只有名字與排序），預設公開，可單獨關閉。
        val artistNames = seen.flatMap { leaderboardArtistNames(it) }
        val topArtists = if (settings.lbShareArtists.first()) {
            artistNames.groupingBy { it }.eachCount().entries
                .sortedByDescending { it.value }.take(5).map { it.key }
        } else emptyList()

        firestore.publishYearlyCount(year, seen.size, optedOut, topArtists)
        if (optedOut) return   // 退出時 publishYearlyCount 已經把 detail 一起刪掉了

        // 重疊比對：上傳的是雜湊，別人無法從中列舉你看了哪些場次；
        // 但你能算出自己的雜湊，所以「你們重疊了什麼」算得出來——只揭露交集，不揭露對方全貌。
        val shareOverlap = settings.lbShareOverlap.first()
        val artistKeys = artistNames.map { firestore.overlapKey(it) }.distinct()
        val showKeys = seen.map {
            firestore.overlapKey("${it.dateTime.date}|${it.venue.trim()}")
        }.distinct()
        firestore.publishLeaderboardDetail(year, artistKeys, showKeys, shareOverlap)
    }

    /**
     * 排行榜要算進去的藝人。與年度回顧同一條規則：
     * 音樂節只算有勾選的卡司，否則主辦名稱會被當成「你最常看的藝人」。
     */
    private fun leaderboardArtistNames(c: com.bandary.domain.model.Concert): List<String> {
        val fest = c.festivals.filter { it.isSelected && it.artistName.isNotBlank() }.map { it.artistName }
        return if (c.type == com.bandary.domain.model.ConcertType.FESTIVAL) fest
        else (listOfNotNull(c.artistName.takeIf { it.isNotBlank() }) + fest).distinct()
    }

    /** 我自己的比對鍵，用來跟別人的雜湊算交集（不需要上傳，本機算就好）。 */
    suspend fun myOverlapKeys(year: Int): Pair<Set<String>, Map<String, String>> {
        val today = Clock.System.now().toLocalDateTime(TimeZone.of("Asia/Taipei")).date
        val seen = concertRepo.getAllConcerts().first()
            .filter { it.dateTime.date.year == year && it.lastOccurrence() < today }
        val artistKeys = seen.flatMap { leaderboardArtistNames(it) }
            .associateBy { firestore.overlapKey(it) }          // 雜湊 → 原名，用來顯示「你們都看過 X」
        val showKeys = seen.map { firestore.overlapKey("${it.dateTime.date}|${it.venue.trim()}") }.toSet()
        return showKeys to artistKeys
    }

    /** 我與某人的重疊。對方沒開比對時回 null（要能跟「重疊 0」分得出來）。 */
    suspend fun overlapWith(year: Int, targetUid: String): Overlap? {
        val theirs = firestore.leaderboardDetail(year, targetUid) ?: return null
        val (myShowKeys, myArtistByKey) = myOverlapKeys(year)
        val sharedArtists = theirs.first.mapNotNull { myArtistByKey[it] }.distinct().sorted()
        val sharedShows = theirs.second.count { it in myShowKeys }
        return Overlap(sharedArtists, sharedShows)
    }

    /** 你和對方的交集。只包含「你自己也有」的部分。 */
    data class Overlap(val sharedArtists: List<String>, val sharedShows: Int)

    /** 我在某位藝人的粉絲榜上的位置。 */
    data class ArtistRank(
        val artist: String,
        val myCount: Int,
        val rank: Int,        // 1 起算；同分並列同名次
        val totalFans: Int
    )

    /**
     * 我最常看的幾位藝人，以及我在每位藝人粉絲榜上的名次。
     *
     * 全站排行榜在使用者還少的時候只會有一兩筆，光看那個沒有資訊量；
     * 這份「我在每位藝人裡排第幾」即使只有我一個人也講得出東西，
     * 而且直接對應「想看個別樂手的排行榜」這個需求。
     *
     * 一位藝人一次讀取，所以限制在前 [limit] 位——全部藝人都查會變成幾十次讀取。
     */
    suspend fun myArtistRanks(year: Int, limit: Int = 5): List<ArtistRank> {
        val me = firestore.currentUid() ?: return emptyList()
        val today = Clock.System.now().toLocalDateTime(TimeZone.of("Asia/Taipei")).date
        val seen = concertRepo.getAllConcerts().first()
            .filter { it.dateTime.date.year == year && it.lastOccurrence() < today }
        val counts = seen.flatMap { leaderboardArtistNames(it) }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.take(limit)

        return counts.mapNotNull { (artist, myCount) ->
            // 先把自己這位藝人的次數推上去，否則榜上可能還沒有我、名次會算錯。
            runCatching { firestore.publishArtistCount(artist, myCount) }
            val fans = firestore.artistLeaderboard(artist)
            if (fans.isEmpty()) return@mapNotNull null
            // 名次由數值算、同分並列同名次，與統計頁的 chartDetailOf 同一套規則。
            val sorted = fans.map { it.count }.sortedDescending()
            val myActual = fans.firstOrNull { it.uid == me }?.count ?: myCount
            ArtistRank(
                artist = artist,
                myCount = myActual,
                rank = sorted.indexOfFirst { it == myActual } + 1,
                totalFans = fans.size
            )
        }
    }

    /**
     * 年度歌單（好友可見）。**只輸出聚合結果**：總數、現場記的數量、前 5 藝人、前 10 首歌。
     * 每天的備忘、日期與感受標籤一律不出門——日記要能誠實寫，就不能讓人逐日看。
     * 沒開分享（預設）時回 null，發布端會把雲端既有的資料刪掉。
     */
    private suspend fun buildSongProfile(): SharedSongProfile? {
        if (!settings.shareSongProfile.first()) return null
        val year = Clock.System.now().toLocalDateTime(TimeZone.of("Asia/Taipei")).year
        val songs = songRepo.getAllSongs().first().filter { it.date.year == year }
        if (songs.isEmpty()) return null
        return SharedSongProfile(
            year = year,
            totalSongs = songs.size,
            liveSongs = songs.count { it.concertId != null },
            topArtists = songs.filter { it.artistName.isNotBlank() }
                .groupingBy { it.artistName }.eachCount()
                .entries.sortedByDescending { it.value }.take(5).map { it.key },
            topSongs = songs.filter { it.songTitle.isNotBlank() }
                .groupingBy { it.songTitle to it.artistName }.eachCount()
                .entries.sortedByDescending { it.value }.take(10)
                .map { (k, n) -> SharedTopSong(title = k.first, artist = k.second, count = n) }
        )
    }

    /**
     * 搜尋並送出好友請求。輸入含「@」且像 email 時走 email 路徑（僅限對方開放 email 搜尋）；
     * 否則一律當作好友代碼搜尋。找到對方後送出好友請求（非直接加入）。
     */
    suspend fun searchAndRequest(rawQuery: String) {
        syncDisplayName()
        val query = rawQuery.trim()
        if (query.isBlank()) { _message.update { "請輸入好友代碼或 email" }; return }
        val myEmail = firestore.currentEmail()?.lowercase()
        _loading.update { true }
        val looksLikeEmail = query.contains('@') && query.substringAfter('@').contains('.')
        val result = if (looksLikeEmail) {
            if (query.lowercase() == myEmail) { _message.update { "不能加自己為好友" }; _loading.update { false }; return }
            firestore.findSharedCalendarByEmail(query)
        } else {
            firestore.findByHandle(query)
        }
        result
            .onSuccess { cal ->
                when {
                    cal == null && looksLikeEmail ->
                        _message.update { "找不到，對方需開放 email 搜尋，或改用好友代碼" }
                    cal == null ->
                        _message.update { "找不到好友代碼 @${query.removePrefix("@")}" }
                    cal.uid == firestore.currentUid() ->
                        _message.update { "不能加自己為好友" }
                    else -> firestore.sendFriendRequest(cal)
                        .onSuccess {
                            val who = cal.handle.takeIf { it.isNotBlank() }?.let { "@$it" } ?: cal.displayName.ifBlank { "對方" }
                            _message.update { "已送出好友請求給 $who" }
                        }
                        .onFailure { e -> _message.update { "送出請求失敗：${e.message}" } }
                }
            }
            .onFailure { e -> _message.update { "搜尋失敗：${e.message}" } }
        _loading.update { false }
    }

    /** 接受好友請求（雙方互為好友）。 */
    suspend fun acceptRequest(req: FriendRequest) {
        syncDisplayName()
        firestore.acceptFriendRequest(req)
            .onSuccess {
                val who = req.fromHandle.takeIf { it.isNotBlank() }?.let { "@$it" } ?: req.fromDisplayName
                _message.update { "已與 $who 成為好友" }
                refreshInternal()
            }
            .onFailure { e -> _message.update { "接受失敗：${e.message}" } }
    }

    /** 婉拒好友請求。 */
    suspend fun declineRequest(fromUid: String) {
        firestore.declineFriendRequest(fromUid)
            .onSuccess { _message.update { "已婉拒" }; refreshInternal() }
            .onFailure { e -> _message.update { "操作失敗：${e.message}" } }
    }

    suspend fun removeFriend(uid: String) {
        firestore.removeFriend(uid)
            .onSuccess { _message.update { "已移除好友" }; refreshInternal() }
            .onFailure { e -> _message.update { "移除失敗：${e.message}" } }
    }

    /** 讀取某位好友的共享日曆。 */
    suspend fun viewCalendar(friendUid: String): SharedCalendar? =
        firestore.fetchSharedCalendar(friendUid).getOrNull()

    private suspend fun refreshInternal() {
        firestore.fetchFriends().onSuccess { list -> _friends.update { list } }
        firestore.fetchFriendRequests().onSuccess { list -> _requests.update { list } }
    }

    fun clearMessage() = _message.update { null }
}
