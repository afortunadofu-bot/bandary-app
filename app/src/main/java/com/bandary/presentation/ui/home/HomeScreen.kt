package com.bandary.presentation.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bandary.data.repository.DailySongRepository
import com.bandary.domain.model.DailySong
import com.bandary.presentation.ui.components.swipeMonth
import com.bandary.presentation.ui.theme.BandaryColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

data class SongYearMonth(val year: Int, val month: Month)

/**
 * 選歌 ↔ 演出連動：選取的那天（或前幾天）看過的一場演唱會，把它的歌單變成候選清單。
 * [candidates] 已排除當天記過的歌，[sameDay] 決定文案是「今天看了」還是「N 天前看了」。
 */
/** 歌單候選：歌名 + 這首歌實際屬於哪位藝人（多藝人場次不能全部記成主藝人）。 */
data class SetlistCandidate(val title: String, val artist: String)

data class ConcertSongSuggestion(
    val concertId: String,
    val concertName: String,
    val artistName: String,
    val concertDate: LocalDate,
    val daysAgo: Int,
    val candidates: List<SetlistCandidate>
) { val sameDay: Boolean get() = daysAgo == 0 }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: DailySongRepository,
    private val concertRepo: com.bandary.data.repository.ConcertRepository,
    private val spotify: com.bandary.data.remote.SpotifyService,
    private val firestore: com.bandary.data.remote.FirestoreService,
    private val settings: com.bandary.data.local.SettingsDataStore
) : ViewModel() {

    private val tz = TimeZone.of("Asia/Taipei")
    val today: LocalDate = Clock.System.now().toLocalDateTime(tz).date

    // 跟著登入狀態變動：登入後不必重開 App，搜尋就會生效。
    val spotifyConfigured: StateFlow<Boolean> = spotify.signedIn
    val spotifyError: StateFlow<String?> = spotify.lastError

    private val _searchResults = MutableStateFlow<List<com.bandary.data.remote.SpotifyTrack>>(emptyList())
    val searchResults: StateFlow<List<com.bandary.data.remote.SpotifyTrack>> = _searchResults.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    fun searchSpotify(query: String) {
        if (query.isBlank()) { _searchResults.value = emptyList(); return }
        viewModelScope.launch {
            _searching.value = true
            _searchResults.value = spotify.searchTracks(query, limit = 8)
            _searching.value = false
        }
    }

    fun clearSearch() { _searchResults.value = emptyList() }

    private val _month = MutableStateFlow(SongYearMonth(today.year, today.month))
    val month: StateFlow<SongYearMonth> = _month.asStateFlow()

    private val _selected = MutableStateFlow(today)
    val selected: StateFlow<LocalDate> = _selected.asStateFlow()

    private val _streak = MutableStateFlow(0)
    val streak: StateFlow<Int> = _streak.asStateFlow()

    // 打卡成功時 +1（今天首次記錄且連續天數真的往上跳），給 E-3 的彈跳動畫當觸發訊號。
    private val _streakBump = MutableStateFlow(0)
    val streakBump: StateFlow<Int> = _streakBump.asStateFlow()

    // E-4：連續天數第一次跨過里程碑時顯示徽章卡（非 null = 剛跨過的天數）。
    private val _badgeMilestone = MutableStateFlow<Int?>(null)
    val badgeMilestone: StateFlow<Int?> = _badgeMilestone.asStateFlow()
    fun dismissBadge() { _badgeMilestone.value = null }

    val songsForMonth: StateFlow<List<DailySong>> = _month.flatMapLatest { ym ->
        val isLeap = (ym.year % 4 == 0 && ym.year % 100 != 0) || ym.year % 400 == 0
        val days = ym.month.length(isLeap)
        repo.getSongsBetween(
            LocalDate(ym.year, ym.month, 1),
            LocalDate(ym.year, ym.month, days)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 目前顯示月份的「上一個月」總首數，只給月回顧拿來做增減比較用。
     * 單獨查一次比把整年抓進記憶體便宜，而且跟著月份切換自動更新。
     */
    val previousMonthSongCount: StateFlow<Int> = _month.flatMapLatest { ym ->
        val prev = if (ym.month == Month.JANUARY) SongYearMonth(ym.year - 1, Month.DECEMBER)
            else SongYearMonth(ym.year, Month.entries[ym.month.ordinal - 1])
        val isLeap = (prev.year % 4 == 0 && prev.year % 100 != 0) || prev.year % 400 == 0
        repo.getSongsBetween(
            LocalDate(prev.year, prev.month, 1),
            LocalDate(prev.year, prev.month, prev.month.length(isLeap))
        ).map { it.size }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val songsForSelected: StateFlow<List<DailySong>> =
        combine(_selected, songsForMonth) { date, list ->
            list.filter { it.date == date }.sortedBy { it.rank }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 選歌 ↔ 演出連動（v0）：選取日當天、或往前 3 天內剛看完的那一場，把歌單推成候選。
     * 只看「已經發生」的場次——未來的場次還沒演，歌單記了也沒意義。歌單全部記完（或沒歌單但已連結
     * 過至少一首）就收起卡片，不長期佔版面。
     */
    /**
     * 共用藝人資料庫。只用「自己記過的」做建議時，
     * 第一次記某個團永遠沒建議可選，而且每個人各自打各自的寫法，之後比對不起來。
     * 讀失敗不影響輸入，只是建議清單少了後半段。
     */
    private val _artistDirectory = MutableStateFlow<List<String>>(emptyList())

    init {
        viewModelScope.launch {
            firestore.fetchArtists().onSuccess { list ->
                _artistDirectory.value = list.map { it.name }
            }
        }
    }

    /** 記過的藝人 + 看過的演唱會藝人 + 共用資料庫，供選歌輸入時自動建議。 */
    val knownSongArtists: StateFlow<List<String>> =
        combine(repo.getAllSongs(), concertRepo.getAllConcerts(), _artistDirectory) { songs, concerts, directory ->
            // 一首歌可以有多位歌手（合作、feat.），存成同一個字串用 ／｜ 等分隔。
            // 建議清單要拆開：不拆的話「怕胖團 / 告五人」會變成一個建議項，
            // 下次只打「告五人」反而比不到——而那正是使用者會打的。
            val mine = (songs.flatMap { com.bandary.domain.model.artistNamesForStats(it.artistName) } +
                concerts.flatMap { c -> listOf(c.artistName) + c.festivals.map { it.artistName } })
                .map { it.trim() }.filter { it.isNotBlank() }
                .groupingBy { it }.eachCount()
                .entries.sortedByDescending { it.value }.map { it.key }
            // 自己記過的排前面，後面接資料庫裡其他人加過的
            val seen = mine.map { com.bandary.domain.model.artistFollowKey(it) }.toSet()
            mine + directory.filter { com.bandary.domain.model.artistFollowKey(it) !in seen }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 記過的歌（歌名 → 藝人），供輸入時搜尋／自動帶入。
     * key 用「歌名＋藝人」而不是只有歌名——同名歌不同藝人是常態，只用歌名會把兩首併成一首。
     * 排序照記過的次數，最常記的排前面。
     */
    val knownSongs: StateFlow<List<Pair<String, String>>> =
        repo.getAllSongs().map { songs ->
            songs.mapNotNull { s ->
                val t = s.songTitle.trim()
                val a = s.artistName.trim()
                if (t.isBlank()) null else t to a
            }.groupingBy { it }.eachCount()
                .entries.sortedByDescending { it.value }.map { it.key }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val concertSuggestion: StateFlow<ConcertSongSuggestion?> =
        combine(_selected, concertRepo.getAllConcerts(), songsForSelected) { date, concerts, logged ->
            val c = concerts
                .filter { it.dateTime.date <= date && it.dateTime.date.daysUntil(date) <= 3 }
                .maxByOrNull { it.dateTime }
                ?: return@combine null
            val already = logged.map { it.songTitle.trim().lowercase() }.toSet()
            // 歌單裡的 "▶ 藝人" / "◆ 段落" 是結構標記、不是歌，要濾掉（否則藝人名會變成可點的歌）；
            // 同時記住每首歌屬於哪位藝人，一鍵記歌才不會全部掛到主藝人身上。
            val artistPrefix = com.bandary.presentation.ui.shows.SETLIST_ARTIST_PREFIX
            val sectionPrefix = com.bandary.presentation.ui.shows.SETLIST_SECTION_PREFIX
            var curArtist = c.artistName
            val songsOfSetlist = ArrayList<SetlistCandidate>()
            c.setlist.forEach { raw ->
                val line = raw.trim()
                when {
                    line.isBlank() -> Unit
                    line.startsWith(artistPrefix) ->
                        curArtist = line.removePrefix(artistPrefix).trim().ifBlank { c.artistName }
                    line.startsWith(sectionPrefix) -> Unit
                    else -> songsOfSetlist.add(SetlistCandidate(line, curArtist))
                }
            }
            val remaining = songsOfSetlist.filter { it.title.lowercase() !in already }
            val linked = logged.any { it.concertId == c.id }
            when {
                songsOfSetlist.isNotEmpty() && remaining.isEmpty() -> null   // 歌單都記完了
                songsOfSetlist.isEmpty() && linked -> null                   // 沒歌單、但這場已記過歌
                else -> ConcertSongSuggestion(
                    concertId = c.id,
                    concertName = c.name.ifBlank { c.artistName },
                    artistName = c.artistName,
                    concertDate = c.dateTime.date,
                    daysAgo = c.dateTime.date.daysUntil(date),
                    candidates = remaining.take(10)
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // 近 7 天（含今天）的選歌，供「本週回顧」卡片與分享用。
    val weekSongs: StateFlow<List<DailySong>> =
        repo.getSongsBetween(today.minus(6, DateTimeUnit.DAY), today)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // E2-4 補打卡券：剩餘張數 + 是否可補回昨天（斷簽隔天的挽救網）。
    val freezeTokens: StateFlow<Int> = settings.streakFreezeTokens
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val freezeDates: StateFlow<Set<LocalDate>> = settings.streakFreezeDates
        .map { set -> set.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _rescueAvailable = MutableStateFlow(false)
    val rescueAvailable: StateFlow<Boolean> = _rescueAvailable.asStateFlow()

    /** 被按叉關掉的月回顧（"yyyy-MM"）；關掉的那個月就不再出現。 */
    val dismissedRecaps: StateFlow<Set<String>> = settings.dismissedRecaps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun dismissRecap(year: Int, monthNumber: Int) {
        viewModelScope.launch { settings.dismissRecap("%04d-%02d".format(year, monthNumber)) }
    }

    init {
        viewModelScope.launch {
            // 每月自動發一張補打卡券（同月只發一次）。
            settings.grantMonthlyFreezeIfNeeded("%04d-%02d".format(today.year, today.monthNumber))
            refreshStreak()
        }
    }

    private fun refreshStreak() {
        viewModelScope.launch {
            _streak.value = repo.calculateStreak(freezeDates.value)
            updateRescueAvailability()
        }
    }

    /** 昨天漏打卡、之前有連續紀錄、且還有券 → 顯示「補回昨天」。 */
    private suspend fun updateRescueAvailability() {
        val yesterday = today.minus(1, DateTimeUnit.DAY)
        val frozen = settings.streakFreezeDates.first()
        val tokens = settings.streakFreezeTokens.first()
        val yesterdayMissing = !repo.hasSongOn(yesterday) && yesterday.toString() !in frozen
        val dayBefore = yesterday.minus(1, DateTimeUnit.DAY)
        val hadRunBefore = repo.hasSongOn(dayBefore) || dayBefore.toString() in frozen
        _rescueAvailable.value = tokens > 0 && yesterdayMissing && hadRunBefore
    }

    /** 用掉一張券把昨天補回，斷掉的連續紀錄接回去。 */
    fun rescueYesterday() {
        viewModelScope.launch {
            val yesterday = today.minus(1, DateTimeUnit.DAY)
            if (settings.useFreezeOn(yesterday.toString())) {
                val updated = settings.streakFreezeDates.first()
                    .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
                _streak.value = repo.calculateStreak(updated)
                updateRescueAvailability()
            }
        }
    }

    /** 記今天的歌之後重算連續天數；若真的往上跳才發出打卡成功訊號（編輯過去的日子不算）。 */
    private suspend fun refreshStreakAndMaybeBump(justLoggedToday: Boolean) {
        val old = _streak.value
        val new = repo.calculateStreak(freezeDates.value)
        _streak.value = new
        updateRescueAvailability()
        if (justLoggedToday && new > old) {
            _streakBump.value += 1
            if (new in MILESTONES) _badgeMilestone.value = new
        }
    }

    companion object {
        val MILESTONES = setOf(7, 14, 30, 100)
    }

    fun prevMonth() = _month.update { ym ->
        if (ym.month == Month.JANUARY) SongYearMonth(ym.year - 1, Month.DECEMBER)
        else SongYearMonth(ym.year, Month.entries[ym.month.ordinal - 1])
    }

    fun nextMonth() = _month.update { ym ->
        if (ym.month == Month.DECEMBER) SongYearMonth(ym.year + 1, Month.JANUARY)
        else SongYearMonth(ym.year, Month.entries[ym.month.ordinal + 1])
    }

    fun setYear(year: Int) = _month.update { it.copy(year = year) }
    /** 月格挑選器：直接跳到指定年月。 */
    fun setMonth(year: Int, monthNumber: Int) =
        _month.update { SongYearMonth(year, Month.entries[monthNumber - 1]) }
    /** 一鍵回到今天：切回本月並選取今天。 */
    fun goToToday() {
        _month.update { SongYearMonth(today.year, today.month) }
        _selected.value = today
    }
    fun select(date: LocalDate) { _selected.value = date }

    fun addSong(date: LocalDate, title: String, artist: String, memo: String, concertId: String? = null) {
        viewModelScope.launch {
            val rank = songsForSelected.value.size
            repo.saveDailySong(
                DailySong(date = date, songTitle = title, artistName = artist,
                    memo = memo.ifBlank { null }, rank = rank, concertId = concertId)
            )
            refreshStreakAndMaybeBump(justLoggedToday = date == today)
        }
    }

    /** 從演唱會歌單一鍵記歌：歌名與**該首歌所屬藝人**帶入、concertId 建立連結（v1）。 */
    fun addSongFromSetlist(date: LocalDate, candidate: SetlistCandidate, suggestion: ConcertSongSuggestion) {
        addSong(
            date, candidate.title,
            candidate.artist.ifBlank { suggestion.artistName },
            memo = "", concertId = suggestion.concertId
        )
    }

    fun updateSong(song: DailySong, title: String, artist: String, memo: String) {
        viewModelScope.launch {
            repo.saveDailySong(song.copy(songTitle = title, artistName = artist, memo = memo.ifBlank { null }))
        }
    }

    fun deleteSong(id: String) {
        viewModelScope.launch {
            repo.deleteDailySong(id)
            refreshStreak()
        }
    }

    // Swap rank with the neighbour above/below within the same day.
    fun move(song: DailySong, up: Boolean) {
        viewModelScope.launch {
            val list = songsForSelected.value
            val idx = list.indexOfFirst { it.id == song.id }
            if (idx < 0) return@launch
            val swapIdx = if (up) idx - 1 else idx + 1
            if (swapIdx !in list.indices) return@launch
            val a = list[idx]; val b = list[swapIdx]
            repo.saveDailySong(a.copy(rank = b.rank))
            repo.saveDailySong(b.copy(rank = a.rank))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    embedded: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(),
    // 選歌頁在「日曆中心」裡是內嵌頁，本身沒有 Scaffold／FAB；把「加一首」的動作註冊給
    // 外層（CalendarHubScreen）的 FAB 呼叫，這樣不用滑過整排卡片跟月曆才點得到。
    onRegisterAdd: (() -> Unit) -> Unit = {},
    // 選歌輸入面板打開／關閉。內嵌在日曆頁時，外層要用它把左右滑換頁關掉，
    // 否則在面板上打字時一個側滑就把面板滑走了。
    onSheetOpenChange: (Boolean) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val month by viewModel.month.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val songsForMonth by viewModel.songsForMonth.collectAsState()
    val songsForSelected by viewModel.songsForSelected.collectAsState()
    val weekSongs by viewModel.weekSongs.collectAsState()
    val streak by viewModel.streak.collectAsState()
    val streakBump by viewModel.streakBump.collectAsState()
    val badgeMilestone by viewModel.badgeMilestone.collectAsState()
    val previousMonthSongCount by viewModel.previousMonthSongCount.collectAsState()
    val rescueAvailable by viewModel.rescueAvailable.collectAsState()
    val freezeTokens by viewModel.freezeTokens.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searching by viewModel.searching.collectAsState()
    val concertSuggestion by viewModel.concertSuggestion.collectAsState()
    val knownSongArtists by viewModel.knownSongArtists.collectAsState()
    val knownSongs by viewModel.knownSongs.collectAsState()
    val spotifyConfigured by viewModel.spotifyConfigured.collectAsState()
    val spotifyError by viewModel.spotifyError.collectAsState()
    var showSheet by remember { mutableStateOf(false) }
    var editingSong by remember { mutableStateOf<DailySong?>(null) }
    // 從「記其他歌」進來時預填歌手＋沿用這場的 concertId，讓手動輸入的歌一樣連得到場次。
    var sheetPrefillArtist by remember { mutableStateOf("") }
    var sheetConcertId by remember { mutableStateOf<String?>(null) }
    var showYearPicker by remember { mutableStateOf(false) }
    // 月回顧點藝人 → 列出這個月他的每一首（點進去可編輯）。
    var recapDetail by remember { mutableStateOf<String?>(null) }
    val dismissedRecaps by viewModel.dismissedRecaps.collectAsState()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scrollScope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(Unit) {
        onRegisterAdd { editingSong = null; sheetPrefillArtist = ""; sheetConcertId = null; showSheet = true }
    }
    LaunchedEffect(showSheet) { onSheetOpenChange(showSheet) }

    val content: @Composable (PaddingValues) -> Unit = { pad ->
      Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            // 底部多留一段給右下角的新增鈕。實機截圖上「這個月聽最多的人」最後一列
            // 正好被 + 壓住，數字看不到。pad 是外層傳進來的，這裡只加不改。
            contentPadding = PaddingValues(
                top = pad.calculateTopPadding(),
                bottom = pad.calculateBottomPadding() + 88.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 連續紀錄：只有真的有天數時才單獨一張卡。零狀態的「今天記下第一首歌」
            // 併進下面的本週摘要——兩張同寬的膠囊上下疊著，會互相打架（實機回報）。
            if (streak > 0) {
                item { StreakCard(streak = streak, bumpSignal = streakBump) }
            }

            // E2-4 補打卡券：昨天漏打卡時，用一張券把連續紀錄接回去。
            if (rescueAvailable) {
                item { StreakRescueCard(tokens = freezeTokens, onRescue = { viewModel.rescueYesterday() }) }
            }


            // 本週回顧卡：近 7 天的選歌摘要，可分享成圖片（提高留存與傳播）。
            if (weekSongs.isNotEmpty()) {
                item {
                    WeeklyReviewCard(
                        weekSongs = weekSongs,
                        streak = streak,
                        onStartStreak = { viewModel.select(viewModel.today) }
                    )
                }
            }

            // Month header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.prevMonth() }) {
                        Icon(Icons.Outlined.ChevronLeft, "上個月")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "${month.year} 年 ${month.month.value} 月",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { showYearPicker = true }
                        )
                        // 一鍵回到今天
                        androidx.compose.material3.Surface(
                            onClick = { viewModel.goToToday() },
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                "今天", modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.nextMonth() }) {
                        Icon(Icons.Outlined.ChevronRight, "下個月")
                    }
                }
            }

            // 月回顧：跟著月曆正在顯示的月份走，但「還沒過完的這個月」要等到最後一天才給
            // （月中看到的是半套數字），而且使用者按叉關掉之後就不再出現。
            item {
                val monthKey = "%04d-%02d".format(month.year, month.month.value)
                val ready = isMonthRecapReady(month.year, month.month.value, viewModel.today)
                if (ready && monthKey !in dismissedRecaps) {
                    val recap = remember(songsForMonth, previousMonthSongCount) {
                        buildMonthSongRecap(songsForMonth, previousMonthSongCount.takeIf { it > 0 })
                    }
                    if (!recap.isEmpty) {
                        MonthlyRecapCard(
                            label = "${month.year} 年 ${month.month.value} 月",
                            recap = recap,
                            onOpenArtist = { name -> recapDetail = name },
                            onDismiss = { viewModel.dismissRecap(month.year, month.month.value) }
                        )
                    }
                }
            }

            item {
                SongCalendarGrid(
                    yearMonth = month,
                    today = viewModel.today,
                    selectedDate = selected,
                    daysWithSongs = songsForMonth.groupBy { it.date }.mapValues { it.value.size },
                    onSelect = { viewModel.select(it) },
                    modifier = Modifier.swipeMonth(
                        onPrev = { viewModel.prevMonth() },
                        onNext = { viewModel.nextMonth() }
                    )
                )
            }

            // Selected day header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${selected.monthNumber} 月 ${selected.dayOfMonth} 日的選歌",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Button(
                        onClick = {
                            editingSong = null; sheetPrefillArtist = ""; sheetConcertId = null; showSheet = true
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Outlined.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("加一首", fontSize = 13.sp)
                    }
                }
            }

            // 選歌 ↔ 演出連動：那天看完的場次 → 歌單一鍵記歌（v0）。
            concertSuggestion?.let { sug ->
                item {
                    ConcertSuggestionCard(
                        suggestion = sug,
                        onPick = { song -> viewModel.addSongFromSetlist(selected, song, sug) },
                        onOther = {
                            editingSong = null
                            sheetPrefillArtist = sug.artistName
                            sheetConcertId = sug.concertId
                            showSheet = true
                        }
                    )
                }
            }

            if (songsForSelected.isEmpty()) {
                item {
                    Text(
                        "這天還沒有選歌，點「加一首」開始記錄",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                itemsIndexed(songsForSelected) { index, song ->
                    SongRow(
                        song = song,
                        index = index,
                        total = songsForSelected.size,
                        onEdit = { editingSong = song; showSheet = true },
                        onDelete = { viewModel.deleteSong(song.id) },
                        onUp = { viewModel.move(song, up = true) },
                        onDown = { viewModel.move(song, up = false) }
                    )
                }
            }
        }
        com.bandary.presentation.ui.components.ScrollTopFab(
            listState, scrollScope,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )
      }
    }

    // 選歌輸入面板是「畫在同一個 window 裡」的整頁覆蓋（見 SongInputSheet 的說明），
    // 所以要有一個明確的 Box 當疊放容器，內嵌在日曆頁的 pager 裡也才蓋得住整頁。
    Box(modifier = Modifier.fillMaxSize()) {
    if (embedded) {
        content(contentPadding)
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("選歌行事曆", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            content(
                PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp
                )
            )
        }
    }

    if (showSheet) {
        SongInputSheet(
            date = selected,
            editing = editingSong,
            spotifyConfigured = spotifyConfigured,
            spotifyError = spotifyError,
            searchResults = searchResults,
            searching = searching,
            onSearch = { viewModel.searchSpotify(it) },
            onClearSearch = { viewModel.clearSearch() },
            onDismiss = { showSheet = false; editingSong = null; sheetConcertId = null; viewModel.clearSearch() },
            prefillArtist = sheetPrefillArtist,
            knownArtists = knownSongArtists,
            knownSongs = knownSongs,
            topInset = if (embedded) contentPadding.calculateTopPadding() else 0.dp,
            onSave = { title, artist, memo ->
                val e = editingSong
                if (e == null) viewModel.addSong(selected, title, artist, memo, sheetConcertId)
                else viewModel.updateSong(e, title, artist, memo)
                showSheet = false
                editingSong = null
                sheetConcertId = null
                viewModel.clearSearch()
            }
        )
    }
    }

    // 月回顧點藝人 → 列出這個月他的每一首，點任一首直接開編輯。
    recapDetail?.let { artist ->
        val rows = remember(artist, songsForMonth) {
            songsForMonth.filter { it.artistName.trim() == artist }.sortedBy { it.date }
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { recapDetail = null },
            title = { Text(artist, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    item {
                        Text(
                            "這個月記了 ${rows.size} 首。點任一首可以編輯。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    items(rows) { song ->
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .clickable {
                                    // 編輯要落在那首歌所屬的日期上，不然存回去會跑到目前選取的那天。
                                    viewModel.select(song.date)
                                    editingSong = song
                                    sheetPrefillArtist = ""
                                    sheetConcertId = song.concertId
                                    recapDetail = null
                                    showSheet = true
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(song.songTitle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                "${song.date.monthNumber}/${song.date.dayOfMonth}" +
                                    (song.memo?.takeIf { it.isNotBlank() }?.let { "　·　$it" } ?: ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { recapDetail = null }) { Text("關閉") } }
        )
    }

    if (showYearPicker) {
        com.bandary.presentation.ui.components.MonthYearPickerDialog(
            initialYear = month.year, initialMonth = month.month.value,
            onPick = { y, m -> viewModel.setMonth(y, m); showYearPicker = false },
            onDismiss = { showYearPicker = false }
        )
    }

    // E-4：連續天數跨過里程碑（7/14/30/100）時彈出徽章卡，可一鍵分享/下載。
    badgeMilestone?.let { days ->
        StreakBadgeDialog(days = days, onDismiss = { viewModel.dismissBadge() })
    }
}

/**
 * 選歌 ↔ 演出連動卡：那天（或前幾天）看完的場次，把歌單列成一點就記的 chip。
 * 這是「跟看團生活綁在一起」的主要入口——記下的歌會帶著 concertId，之後在演唱會詳情頁反向看得到。
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ConcertSuggestionCard(
    suggestion: ConcertSongSuggestion,
    onPick: (SetlistCandidate) -> Unit,
    onOther: () -> Unit
) {
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val whenText = when (suggestion.daysAgo) {
                0 -> "這天你去了"
                1 -> "前一天你去了"
                else -> "${suggestion.daysAgo} 天前你去了"
            }
            Text(
                "$whenText《${suggestion.concertName}》",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            if (suggestion.candidates.isEmpty()) {
                Text(
                    "這場沒有記歌單，直接記一首當天在聽的歌吧",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "從那晚的歌單挑一首，一點就記錄",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // 依藝人分組：藝人當小標、歌曲才是可點的 chip，避免藝人名被誤認成歌。
                suggestion.candidates.groupBy { it.artist }.forEach { (artist, songs) ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (artist.isNotBlank()) {
                            Text(
                                artist,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            songs.forEach { cand ->
                                androidx.compose.material3.Surface(
                                    onClick = { onPick(cand) },
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Text(
                                        cand.title,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
            androidx.compose.material3.TextButton(
                onClick = onOther,
                contentPadding = PaddingValues(horizontal = 0.dp)
            ) {
                Text(
                    if (suggestion.candidates.isEmpty()) "記一首 ${suggestion.artistName} 的歌 →" else "記其他歌 →",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/** 連續天數里程碑徽章卡：用 PosterRenderer 的 BLEED 樣式把天數放大成視覺主體，一鍵分享或下載。 */
@Composable
private fun StreakBadgeDialog(days: Int, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val accentHex = remember(primary) {
        String.format("#%06X", 0xFFFFFF and primary.toArgb())
    }
    val bitmap = remember(days) {
        com.bandary.presentation.ui.share.PosterRenderer.render(
            context,
            com.bandary.presentation.ui.share.PosterRenderer.Spec(
                background = com.bandary.presentation.ui.share.PosterRenderer.Background.None,
                accentHex = accentHex,
                style = com.bandary.presentation.ui.share.PosterRenderer.TypeStyle.BLEED,
                title = "$days",
                subtitle = "連續紀錄",
                day = "$days",
                month = "DAYS",
                fullDate = "連續 $days 天沒有斷過",
                venue = "Bandary 聽團曆",
                city = null,
                blocks = listOf(com.bandary.presentation.ui.share.PosterRenderer.Block("", listOf("連續 $days 天沒有斷過")))
            )
        )
    }
    var downloadMsg by remember { mutableStateOf<String?>(null) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("連續 $days 天！", fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.foundation.Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier.heightIn(max = 240.dp).aspectRatio(9f / 16f).clip(RoundedCornerShape(8.dp))
                )
                downloadMsg?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    val uri = com.bandary.data.util.BitmapExporter.downloadToGallery(context, bitmap, "bandary_streak_$days")
                    downloadMsg = if (uri != null) "已下載到相簿" else "下載失敗"
                }) { Text("下載") }
                TextButton(onClick = {
                    com.bandary.data.util.BitmapExporter.shareOnly(context, bitmap, "bandary_streak_$days")
                    onDismiss()
                }) { Text("分享") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("關閉") } }
    )
}

// 本週回顧卡：近 7 天選歌摘要 + 一鍵分享成圖（Wrapped 風，天然傳播、拉留存）。
@Composable
private fun WeeklyReviewCard(
    weekSongs: List<DailySong>,
    streak: Int,
    onStartStreak: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showPreview by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }   // 預設收合成單行摘要（spec Phase 2）
    val totalSongs = weekSongs.size
    val activeDays = weekSongs.map { it.date }.distinct().size
    val topArtist = weekSongs.filter { it.artistName.isNotBlank() }
        .groupingBy { it.artistName }.eachCount().maxByOrNull { it.value }?.key
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        if (!expanded) {
            // 收合：單行摘要「本週 N 首歌 · M 天有記錄 ›」，點一下展開完整卡片與分享。
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { expanded = true }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "本週 $totalSongs 首歌 · $activeDays 天有記錄",
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                }
                // 還沒開始連續紀錄：CTA 當成這張卡的第二行，而不是自己再來一張同寬的膠囊。
                if (streak <= 0) {
                    androidx.compose.material3.HorizontalDivider(
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onStartStreak() }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Outlined.LocalFireDepartment, null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "今天記下第一首歌，開始連續紀錄",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.AutoMirrored.Outlined.KeyboardArrowRight, null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        } else
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("本週回顧", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f))
                Icon(
                    Icons.Outlined.KeyboardArrowUp, "收合",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp).clickable { expanded = false }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                @Composable
                fun stat(value: String, label: String) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(label, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
                    }
                }
                stat("$totalSongs", "首歌")
                stat("$activeDays", "天有記錄")
                if (streak > 0) stat("$streak", "連續天數")
            }
            topArtist?.let {
                Text("最常聽：$it", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                androidx.compose.material3.FilledTonalButton(
                    onClick = { showPreview = true },   // 先預覽，再選下載 / 分享
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Outlined.Share, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("分享回顧")
                }
            }
        }
    }

    if (showPreview) {
        val bmp = remember(totalSongs, activeDays, streak, topArtist) {
            buildWeekReviewBitmap(totalSongs, activeDays, streak, topArtist, weekSongs)
        }
        com.bandary.presentation.ui.components.SharePreviewDialog(
            bitmap = bmp,
            fileName = "bandary_week",
            onDismiss = { showPreview = false }
        )
    }
}

// E2-4 補打卡券卡：斷簽隔天的挽救網，一鍵補回昨天。
@Composable
private fun StreakRescueCard(tokens: Int, onRescue: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text("昨天忘了記錄？", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text("用一張補打卡券補回昨天，別讓連續紀錄斷掉・剩 $tokens 張",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f))
            }
            androidx.compose.material3.FilledTonalButton(onClick = onRescue, shape = RoundedCornerShape(10.dp)) {
                Text("補回")
            }
        }
    }
}

// E2-5 月結小回顧卡：每月前 7 天在選歌頁頂端提醒上個月的紀錄。
@Composable
private fun MonthlyRecapCard(
    label: String,
    recap: MonthSongRecap,
    onOpenArtist: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val onCard = scheme.onSurface
    val dim = scheme.onSurfaceVariant
    // ── 配色 ──────────────────────────────────────────────────────
    // 之前這張卡是 secondaryContainer 的實心色塊，跟它上面的「本週摘要」、
    // 「開始連續紀錄」疊成三塊同色同寬的灰綠，整頁沒有一個地方是重點（實機回報）。
    // 改成：上面兩張維持實心，這張換成「上淺下白」的漸層 + 細框，
    // 色塊只留在有大數字的上半部，下半部的清單落在近白底上，字才看得清楚。
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to scheme.primaryContainer.copy(alpha = 0.55f),
                    0.55f to scheme.surface,
                    1f to scheme.surface
                )
            ).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            // ── 標題列：月份 + 主數字放在同一行 ────────────────────────
            // 原本標題、三個大數字、一句解讀各佔一列，光是「這個月記了幾首」
            // 就吃掉三行。主數字併進標題列之後省掉整整一列，而且一眼就看到重點。
            Row(verticalAlignment = Alignment.Bottom) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = dim,
                        letterSpacing = 0.08.em
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        // 整頁唯一一個上了彩度的東西。灰綠裡面需要有一個落點，
                        // 不然眼睛沒有地方停。
                        Text(
                            "${recap.totalSongs}",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = scheme.primary
                        )
                        Text(
                            " 首歌",
                            style = MaterialTheme.typography.titleSmall,
                            color = onCard,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Outlined.Close, "關閉這個月的回顧",
                        tint = dim, modifier = Modifier.size(18.dp)
                    )
                }
            }

            // ── 次要數字壓成一行 ─────────────────────────────────────
            // 「位藝人 / 天有記」本來各佔一個大數字欄位，但它們不是這張卡的重點；
            // 和那句解讀併成一行小字，直接省下一整塊高度。
            Text(
                buildString {
                    append("${recap.artistCount} 位藝人 · ${recap.activeDays} 天有記")
                    recap.headline?.let { append(" · ").append(it.removeSuffix("。")) }
                },
                style = MaterialTheme.typography.bodySmall,
                color = dim
            )

            // ── 兩欄並排：聽最多的人 / 最常回放 ───────────────────────
            // 原本是上下兩個清單，加起來六列。並排之後高度砍半，
            // 而且「誰」與「哪首」本來就是互相對照的兩件事，並排比上下更好讀。
            if (recap.topArtists.isNotEmpty() || recap.topSongs.isNotEmpty()) {
                androidx.compose.material3.HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.6f))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        RecapColumnTitle("聽最多")
                        recap.topArtists.take(3).forEachIndexed { i, a ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { onOpenArtist(a.name) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 名次做成小圓底：純灰數字在一排文字裡會被當成內文，
                                // 看不出來是排名。
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(
                                            scheme.primary.copy(alpha = if (i == 0) 0.9f else 0.18f),
                                            androidx.compose.foundation.shape.CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "${i + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (i == 0) scheme.onPrimary else scheme.primary
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    a.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = onCard,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "${a.count}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = dim
                                )
                            }
                        }
                    }
                    androidx.compose.material3.VerticalDivider(
                        modifier = Modifier.height(58.dp),
                        color = scheme.outlineVariant.copy(alpha = 0.6f)
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        RecapColumnTitle("最常回放")
                        recap.topSongs.take(3).forEach { song ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    song.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = onCard,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                // 只記過一次的歌標「×1」沒有資訊量，多次才值得標出來。
                                if (song.count > 1) {
                                    Text(
                                        "×${song.count}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = scheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 月回顧兩欄的欄名。拉開字距、上品牌色，才不會跟底下的內容糊成一片。 */
@Composable
private fun RecapColumnTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.14.em,
        color = MaterialTheme.colorScheme.primary
    )
}

/** 把「本週回顧」畫成一張 1080×1080 方形圖，供系統分享單分享。 */
private fun buildWeekReviewBitmap(
    totalSongs: Int, activeDays: Int, streak: Int, topArtist: String?, songs: List<com.bandary.domain.model.DailySong>
): android.graphics.Bitmap {
    val w = 1080; val h = 1080
    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    canvas.drawColor(android.graphics.Color.parseColor("#111214"))
    val accent = android.graphics.Color.parseColor("#6B9080")
    val white = android.graphics.Color.WHITE
    val grey = android.graphics.Color.parseColor("#AAB0B6")
    fun paint(size: Float, color: Int, bold: Boolean = false, align: android.graphics.Paint.Align = android.graphics.Paint.Align.LEFT) =
        android.graphics.Paint().apply {
            this.color = color; textSize = size; isAntiAlias = true; textAlign = align
            isFakeBoldText = bold
        }
    // 標題
    canvas.drawText("本週回顧", 80f, 150f, paint(64f, white, true))
    canvas.drawText("Bandary 聽團曆", 80f, 210f, paint(34f, accent, true))
    // 三個大數字
    val cols = listOf(Triple("$totalSongs", "首歌", 200f), Triple("$activeDays", "天", 540f), Triple("${streak}", "連續天", 860f))
    cols.forEach { (num, label, cx) ->
        canvas.drawText(num, cx, 400f, paint(96f, accent, true, android.graphics.Paint.Align.CENTER))
        canvas.drawText(label, cx, 460f, paint(36f, grey, false, android.graphics.Paint.Align.CENTER))
    }
    topArtist?.let {
        canvas.drawText("最常聽　$it", 80f, 580f, paint(44f, white, true))
    }
    // 歌單（最多 8 首，去重）
    val titles = songs.map { it.songTitle }.filter { it.isNotBlank() }.distinct().take(8)
    var y = 680f
    titles.forEachIndexed { i, t ->
        val line = "${i + 1}. " + if (t.length > 22) t.take(21) + "…" else t
        canvas.drawText(line, 80f, y, paint(40f, grey))
        y += 56f
    }
    return bmp
}

@Composable
private fun StreakCard(streak: Int, bumpSignal: Int = 0) {
    // 只在真的有連續天數時才畫。零狀態的「今天記下第一首歌」已經併進本週摘要那張卡，
    // 不再由這裡負責——兩張同寬的膠囊上下疊著會互相打架（實機回報）。
    if (streak <= 0) return
    // 打卡成功（連續天數真的往上跳）：火焰 icon 放大彈跳一下 + 震動回饋。
    val scale = remember { androidx.compose.animation.core.Animatable(1f) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    LaunchedEffect(bumpSignal) {
        if (bumpSignal > 0) {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            scale.animateTo(1.4f, androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessLow
            ))
            scale.animateTo(1f, androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
            ))
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Outlined.LocalFireDepartment, null, tint = BandaryColors.Red,
                modifier = Modifier.size(18.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            )
            Text("$streak", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = BandaryColors.Red)
            Text("天連續紀錄", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SongCalendarGrid(
    yearMonth: SongYearMonth,
    today: LocalDate,
    selectedDate: LocalDate,
    daysWithSongs: Map<LocalDate, Int>,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    // 與「演出月曆」同一套視覺：圓形色塊格、相同配色與單點標記，兩邊看起來一致。
    val isLeap = (yearMonth.year % 4 == 0 && yearMonth.year % 100 != 0) || yearMonth.year % 400 == 0
    val daysInMonth = yearMonth.month.length(isLeap)
    val firstDow = LocalDate(yearMonth.year, yearMonth.month, 1).dayOfWeek.ordinal // Mon=0
    val sundayStartOffset = (firstDow + 1) % 7

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("日", "一", "二", "三", "四", "五", "六").forEach { d ->
                Text(
                    d,
                    modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        val totalCells = sundayStartOffset + daysInMonth
        val rows = (totalCells + 6) / 7
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val dayNum = row * 7 + col - sundayStartOffset + 1
                    if (dayNum in 1..daysInMonth) {
                        val date = LocalDate(yearMonth.year, yearMonth.month, dayNum)
                        val count = daysWithSongs[date] ?: 0
                        val isToday = date == today
                        val isSelected = date == selectedDate
                        val hasSongs = count > 0
                        Surface(
                            onClick = { onSelect(date) },
                            modifier = Modifier.weight(1f).aspectRatio(1f).padding(2.dp),
                            shape = CircleShape,
                            color = when {
                                isSelected -> MaterialTheme.colorScheme.primary
                                isToday -> MaterialTheme.colorScheme.primaryContainer
                                hasSongs -> MaterialTheme.colorScheme.tertiaryContainer
                                else -> MaterialTheme.colorScheme.surface
                            },
                            // 今天：加一圈主色外框，即使被選取 / 有歌也能一眼辨識。
                            border = if (isToday && !isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "$dayNum",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isToday || isSelected || hasSongs) FontWeight.Bold else FontWeight.Normal,
                                        color = when {
                                            isSelected -> Color.White
                                            isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                                            hasSongs -> MaterialTheme.colorScheme.onTertiaryContainer
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    if (hasSongs) {
                                        Spacer(Modifier.height(2.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                            repeat(count.coerceAtMost(3)) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(5.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isSelected) Color.White else MaterialTheme.colorScheme.primary)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SongRow(
    song: DailySong,
    index: Int,
    total: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // rank badge — index 0 is top favourite
            Box(
                modifier = Modifier.size(24.dp).clip(CircleShape)
                    .background(if (index == 0) BandaryColors.Yellow else MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Text("${index + 1}", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = if (index == 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(modifier = Modifier.weight(1f).clickable { onEdit() }) {
                Text(song.songTitle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artistName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onUp, enabled = index > 0, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.ArrowUpward, "上移", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDown, enabled = index < total - 1, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.ArrowDownward, "下移", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.Delete, "刪除", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * 選歌輸入面板。
 *
 * 為什麼不是 ModalBottomSheet 了：sheet 有自己的 dialog window，那個 window 量不到
 * IME（鍵盤）inset，所以 imePadding() / navigationBarsPadding() 在裡面等於 0——
 * 「儲存鈕被導覽列蓋住」「備忘打字看不到自己寫什麼」修過兩輪（先調修飾符順序、再直接去
 * 設那個 dialog window 的 decorFitsSystemWindows）都沒好。這版直接畫在 Activity 自己的
 * window 裡（就是 NavHost 內容區），鍵盤 inset 量得到，問題才有辦法真的解掉。
 *
 * 面板本身只佔「底部導覽列以上」那一塊，所以不能直接用 imePadding()（會多墊掉導覽列那段
 * 高度，鍵盤上面會空一大塊）；改成量「面板底部到視窗底部的距離」再去扣，得到鍵盤真正蓋住
 * 這個面板的高度。
 */
@Composable
private fun SongInputSheet(
    date: LocalDate,
    editing: DailySong?,
    spotifyConfigured: Boolean,
    spotifyError: String?,
    searchResults: List<com.bandary.data.remote.SpotifyTrack>,
    searching: Boolean,
    onSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    onDismiss: () -> Unit,
    prefillArtist: String = "",
    knownArtists: List<String> = emptyList(),
    knownSongs: List<Pair<String, String>> = emptyList(),
    // 面板上緣要讓開多少：內嵌在行事曆頁時，pager 其實延伸到「行事曆＋演出/選歌」那排底下
    // （外層只把留白傳進來當 contentPadding），面板不讓開的話標題、關閉鈕和歌名欄會被蓋住。
    topInset: androidx.compose.ui.unit.Dp = 0.dp,
    onSave: (String, String, String) -> Unit
) {
    var title by remember { mutableStateOf(editing?.songTitle ?: "") }
    var artist by remember { mutableStateOf(editing?.artistName ?: prefillArtist) }
    var memo by remember { mutableStateOf(editing?.memo ?: "") }


    // Spotify 搜尋不能用時要講原因：以前只是靜靜地不出結果，看起來像壞掉。
    //
    // 但**不要把伺服器端的內部訊息原樣丟給使用者**。實機截圖上出現的是
    // 「Spotify 搜尋暫時失敗（pq1: Spotify token HTTP 400），可以先手動輸入」——
    // 「pq1」是 Cloud Function 裡的階段代號，對使用者沒有任何意義，只會讓人以為自己弄壞了什麼。
    // 使用者要知道的只有兩件事：現在用不了、可以手動打。
    // 技術細節已經由 SpotifyService 的 Log.w 記進 logcat，畫面上不必再出現一次。
    val spotifyHint = when {
        !spotifyConfigured -> "登入後可用 Spotify 搜尋歌曲，自動帶入歌名與藝人"
        spotifyError != null -> "Spotify 搜尋暫時無法使用，直接手動輸入就可以了"
        else -> null
    }

    // 邊打歌名邊查 Spotify（有填藝人就一起送進查詢，結果更準）。
    androidx.compose.runtime.LaunchedEffect(title, artist, spotifyConfigured) {
        val q = listOf(title.trim(), artist.trim()).filter { it.isNotBlank() }.joinToString(" ")
        if (spotifyConfigured && title.trim().length >= 2) {
            kotlinx.coroutines.delay(350)
            onSearch(q)
        } else onClearSearch()
    }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val rootHeightPx = androidx.compose.ui.platform.LocalView.current.rootView.height
    var panelTopPx by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var panelBottomPx by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val imeBottomPx = androidx.compose.foundation.layout.WindowInsets.ime.getBottom(density)
    // 面板底部本來就離視窗底部有一段（導覽列＋底部分頁列），鍵盤先吃掉的是那一段。
    val keyboardPad = with(density) {
        (imeBottomPx - (rootHeightPx - panelBottomPx)).coerceAtLeast(0).toDp()
    }
    // 同一招處理狀態列：面板嵌在有 TopAppBar 的頁面裡時本來就在狀態列下面（不用再讓），
    // 直接貼在視窗最上緣時才要讓開，否則標題與關閉鈕會躲到時鐘底下。
    val statusPad = with(density) {
        (androidx.compose.foundation.layout.WindowInsets.statusBars.getTop(density) - panelTopPx)
            .coerceAtLeast(0).toDp()
    }

    androidx.activity.compose.BackHandler(onBack = onDismiss)

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                panelTopPx = it.positionInWindow().y.toInt()
                panelBottomPx = panelTopPx + it.size.height
            },
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(top = maxOf(statusPad, topInset), bottom = keyboardPad)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "關閉")
                }
                Text(
                    "${date.year}/${date.monthNumber.toString().padStart(2, '0')}/${date.dayOfMonth.toString().padStart(2, '0')} " +
                        if (editing == null) "新增選歌" else "編輯選歌",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 歌名自動建議：以前只有藝人欄有，歌名每次都要重打。
                // 選了建議就連藝人一起帶入——同名歌不同藝人也分得開（配對存的是「歌名＋藝人」）。
                // 建議清單一律「排在欄位正下方、把後面的欄位往下推」，不用浮動的 DropdownMenu：
                // 浮動選單會蓋在下一個欄位上，打完歌名要點「藝人」時點到的其實是建議，
                // 藝人欄根本點不進去（v1.64.0 實機回報）。也只在該欄位有焦點時才顯示。
                var titleFocused by remember { mutableStateOf(false) }
                val songSuggestions = remember(title, knownSongs) {
                    val q = title.trim()
                    if (q.isBlank()) emptyList()
                    else knownSongs
                        .filter { (t, a) ->
                            (t.contains(q, ignoreCase = true) || a.contains(q, ignoreCase = true)) &&
                                !t.equals(q, ignoreCase = true)
                        }
                        .take(4)
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("歌曲名稱") },
                    modifier = Modifier.fillMaxWidth()
                        .onFocusChanged { titleFocused = it.isFocused },
                    shape = RoundedCornerShape(12.dp), singleLine = true
                )
                if (titleFocused && songSuggestions.isNotEmpty()) {
                    InlineSuggestions(songSuggestions, header = "點一下帶入歌名與藝人") { (t, a) -> title = t; artist = a }
                }
                // Spotify 搜尋結果：記過的歌之外，也能直接從 Spotify 帶入正確的歌名與藝人。
                if (titleFocused && title.trim().length >= 2) {
                    when {
                        searchResults.isNotEmpty() -> InlineSuggestions(
                            searchResults.map { it.title to it.artistName },
                            header = "Spotify 搜尋結果・點一下帶入"
                        ) { (t, a) -> title = t; artist = a }
                        searching -> Text(
                            "Spotify 搜尋中…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        spotifyHint != null -> Text(
                            spotifyHint,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // 藝人自動建議：比對記過的藝人與看過的演唱會藝人，不用每次重打。
                var artistFocused by remember { mutableStateOf(false) }
                // 多位歌手：整個欄位可能是「怕胖團 ／ 告五人」，使用者正在打的是最後那一段。
                // 拿整串去比對的話，打完第一位之後建議就全部消失——因為庫裡沒有
                // 「怕胖團 ／ 告」這個名字。所以只比對最後一段，選中也只替換那一段。
                val artistSep = " ／ "
                val artistHead = artist.substringBeforeLast('／', "")
                    .let { if (it.isBlank()) "" else it.trimEnd().trimEnd('／').trimEnd() + artistSep }
                val artistTail = artist.substringAfterLast('／').trim()
                val artistSuggestions = remember(artist, knownArtists) {
                    // 還沒打字就先列最近用過的：以前一定要先打字才看得到建議，等於不知道有這功能（實機回報）。
                    // 比對改用 NameSuggest：原本的 contains 是照順序比的，
                    // 打「青少年公館」比不到「公館青少年GGteen」（實機回報）。
                    val already = artist.split('／').map { it.trim() }.filter { it.isNotBlank() }
                    if (artistTail.isBlank() && already.isEmpty()) knownArtists.take(6)
                    else com.bandary.domain.model.NameSuggest.suggest(
                        artistTail, knownArtists, exclude = already
                    )
                }
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("藝人 / 樂團") },
                    placeholder = { Text("多位合作用 ／ 分隔") },
                    modifier = Modifier.fillMaxWidth()
                        .onFocusChanged { artistFocused = it.isFocused },
                    shape = RoundedCornerShape(12.dp), singleLine = true
                )
                if (artistFocused && artistSuggestions.isNotEmpty()) {
                    InlineSuggestions(
                        artistSuggestions.map { it to "" },
                        header = when {
                            artist.isBlank() -> "最近記過的藝人・點一下帶入"
                            artistHead.isNotBlank() -> "再加一位・點一下帶入"
                            else -> "點一下帶入"
                        }
                        // 只替換正在打的那一段，前面已經選好的保留。
                    ) { (s, _) -> artist = artistHead + s }
                }
                OutlinedTextField(
                    value = memo, onValueChange = { memo = it },
                    label = { Text("備忘") },
                    // 固定 90dp 只放得下兩行，打長一點就看不到自己寫了什麼；改成可長高、上限六行。
                    minLines = 3, maxLines = 6,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(4.dp))
            }

            // 儲存鈕釘在面板底部：不用捲到最下面才按得到，鍵盤打開時整個面板已經往上讓，
            // 所以它永遠在鍵盤與導覽列上面。
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
                Button(
                    onClick = { if (title.isNotBlank() && artist.isNotBlank()) onSave(title, artist, memo) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = title.isNotBlank() && artist.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("儲存", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

/**
 * 輸入欄下方的建議清單（排在版面裡，不浮動）。每列：主文字，右側可選的次要文字（例如歌名旁的藝人）。
 * 點一列只呼叫 [onPick]，不搶焦點——使用者可以接著在同一欄繼續打字。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun InlineSuggestions(
    items: List<Pair<String, String>>,
    header: String? = null,
    onPick: (Pair<String, String>) -> Unit
) {
    // 建議出現時把自己捲進可視範圍：欄位在面板下半部時，建議會整片落在鍵盤後面（實機回報）。
    val bringIntoView = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    LaunchedEffect(items.size, items.firstOrNull()) {
        runCatching { bringIntoView.bringIntoView() }
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        // 底色改用主題色淡底＋外框：原本的 surfaceVariant 跟輸入欄幾乎同色，看起來不像可以點的東西。
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoView)
    ) {
        Column {
            if (header != null) {
                Text(
                    header,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 14.dp, top = 8.dp)
                )
            }
            items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(item) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        item.first,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (item.second.isNotBlank()) {
                        Text(
                            item.second,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun YearPickerDialog(
    current: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("選擇年份") },
        text = {
            LazyColumn {
                items((current - 5..current + 1).toList().reversed()) { y ->
                    Text(
                        "$y 年",
                        modifier = Modifier.fillMaxWidth().clickable { onPick(y) }.padding(vertical = 10.dp, horizontal = 8.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (y == current) FontWeight.Bold else FontWeight.Normal,
                        color = if (y == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("關閉") } }
    )
}
