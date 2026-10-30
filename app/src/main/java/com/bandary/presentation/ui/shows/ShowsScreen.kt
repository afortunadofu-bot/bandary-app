package com.bandary.presentation.ui.shows

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Festival
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.HideImage
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.SmallFloatingActionButton
import com.bandary.presentation.ui.components.isScrollingUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.bandary.data.repository.ConcertRepository
import com.bandary.domain.model.Concert
import com.bandary.domain.model.hasEnded
import com.bandary.presentation.ui.components.ConcertCoverCard
import com.bandary.presentation.ui.components.EmptyState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

data class ShowsUiState(
    val allConcerts: List<Concert> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class ShowsViewModel @Inject constructor(
    private val repo: ConcertRepository,
    private val settings: com.bandary.data.local.SettingsDataStore,
    private val friendsRepo: com.bandary.data.repository.FriendsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ShowsUiState())
    val state: StateFlow<ShowsUiState> = _state.asStateFlow()

    // 「我的演唱會」上次停留的顯示模式 / 時間範圍，重開 App 也記得。
    val savedDisplay: StateFlow<String?> = settings.showsDisplay
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val savedScope: StateFlow<String?> = settings.showsScope
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val savedDir: StateFlow<String?> = settings.showsDir
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    fun saveDisplay(v: String) = viewModelScope.launch { settings.setShowsDisplay(v) }
    fun saveScope(v: String) = viewModelScope.launch { settings.setShowsScope(v) }
    fun saveDir(v: String) = viewModelScope.launch { settings.setShowsDir(v) }

    init {
        viewModelScope.launch {
            repo.getAllConcerts().collect { list ->
                _state.update { it.copy(allConcerts = list, isLoading = false) }
            }
        }
    }

    fun deleteConcert(id: String) {
        viewModelScope.launch {
            repo.deleteConcert(id)
            // 刪掉的場次也該從好友看到的共享日曆消失，不用等使用者自己開「好友」分頁才重發。
            runCatching { friendsRepo.publishMyCalendar(quiet = true) }
            runCatching { friendsRepo.publishYearlyLeaderboard() }
        }
    }

    /**
     * 快速新增：只要「藝人 + 日期」就能先記下來（省去長表單），時間預設 19–21、類型演唱會。
     * 演唱會名稱是分開的欄位，留空時沿用藝人名（很多場次本來就沒有另外的標題）。
     * 之後可再點進去補其他欄位。回呼帶回新場次 id 供直接開編輯 / 詳情。
     */
    fun quickAdd(
        artist: String,
        name: String,
        venue: String,
        date: kotlinx.datetime.LocalDate,
        onSaved: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val saved = repo.saveConcert(
                Concert(
                    name = name.trim().ifBlank { artist.trim() },
                    artistName = artist.trim(),
                    venue = venue.trim(),
                    dateTime = LocalDateTime(date, kotlinx.datetime.LocalTime(19, 0)),
                    endTime = kotlinx.datetime.LocalTime(21, 0),
                    type = com.bandary.domain.model.ConcertType.CONCERT
                )
            )
            onSaved(saved.id)
        }
    }
}

// 頁面分流：即將到來 / 歷史紀錄（取代原本兩行篩選器）。
private enum class ShowsTab(val label: String) { UPCOMING("即將到來"), HISTORY("歷史紀錄") }
// 顯示模式：不規則方塊 / 清單 / 完整大方塊，點 icon 循環切換。
private enum class ShowDisplay { MASONRY, LIST }   // 海報 / 清單（卡片模式已移除）

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ShowsScreen(
    onAddShow: () -> Unit,
    onShowDetail: (String) -> Unit,
    onDuplicateConcert: (String) -> Unit,
    onEditConcert: (String) -> Unit = {},
    onImportCalendar: () -> Unit = {},
    onOpenOverview: () -> Unit = {},
    onScanPoster: () -> Unit = {},
    onBulkImport: () -> Unit = {},
    viewModel: ShowsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val now = remember { Clock.System.now().toLocalDateTime(TimeZone.of("Asia/Taipei")) }
    // 兩分頁改用 HorizontalPager，可左右滑切換；tab 由目前頁面推導。
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { 2 })
    val tab = if (pagerState.currentPage == 0) ShowsTab.UPCOMING else ShowsTab.HISTORY
    var historyYear by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<Int?>(null) }  // null = 全部時間
    var display by remember { mutableStateOf(ShowDisplay.MASONRY) }  // 顯示模式（循環切換）
    // 重開 App 記住上次的顯示模式。
    val savedDisplay by viewModel.savedDisplay.collectAsState()
    var restoredPrefs by remember { mutableStateOf(false) }
    LaunchedEffect(savedDisplay) {
        savedDisplay?.let { v -> runCatching { ShowDisplay.valueOf(v) }.getOrNull()?.let { display = it } }
        restoredPrefs = true
    }
    LaunchedEffect(display) { if (restoredPrefs) viewModel.saveDisplay(display.name) }
    // 卡片資訊層級：0 只留圖片 / 1 名稱+日期 / 2 完整 / 3 再加票價與售票平台
    var infoLevel by remember { mutableStateOf(2) }
    var showQuickAdd by remember { mutableStateOf(false) }
    // 用 rememberSaveable：點進場次詳情再返回時，這個 composable 會被丟棄重建，
    // 用一般 remember 的話搜尋字串與展開狀態都會消失，看起來就像「返回跳回主頁」。
    var searchActive by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var searchQuery by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val upcomingListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val historyListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val currentListState = if (tab == ShowsTab.UPCOMING) upcomingListState else historyListState
    val coScope = rememberCoroutineScope()
    val showScrollTop by remember(currentListState) { derivedStateOf { currentListState.firstVisibleItemIndex > 1 } }
    // 快速新增後的 snackbar：帶「編輯」動作可直接補完整資料（spec §6.7.2）。
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

    // 搜尋：比對名稱 / 藝人（含音樂祭各團）/ 場地 / 縣市。空字串＝不過濾。
    val q = searchQuery.trim()
    fun matchesSearch(c: Concert): Boolean {
        if (q.isBlank()) return true
        val hay = buildString {
            append(c.name); append(' '); append(c.artistName); append(' ')
            append(c.venue); append(' '); append(c.city ?: "")
            c.festivals.forEach { append(' '); append(it.artistName) }
        }
        return hay.contains(q, ignoreCase = true)
    }
    // 即將到來：還沒結束的，由近到遠。歷史：已經結束的，最近的在最上面。
    //
    // 以前是用「本月第一天」分流，結果本月已經看完的場次會留在「即將到來」直到換月
    // ——實機回報「不知道怎麼讓它移去歷史紀錄」。他確實沒辦法，那個判定跟他填的
    // 任何欄位都無關。改用 Concert.hasEnded()：看最後一個發生日 + 結束時間（見那支的說明）。
    val upcoming = state.allConcerts.filter { !it.hasEnded(now) && matchesSearch(it) }.sortedBy { it.dateTime }
    val past = state.allConcerts.filter { it.hasEnded(now) && matchesSearch(it) }
    val historyYears = past.map { it.dateTime.year }.distinct().sortedDescending()
    // 「去年的今天」回顧：同月同日、往年看過的場次（不含今年）。
    val throwbacks = state.allConcerts.filter {
        it.dateTime.monthNumber == now.monthNumber && it.dateTime.dayOfMonth == now.dayOfMonth && it.dateTime.year < now.year
    }.sortedByDescending { it.dateTime }
    val history = past
        .let { list -> if (historyYear != null) list.filter { it.dateTime.year == historyYear } else list }
        .sortedByDescending { it.dateTime }
    // 依「年-月」分組（保留排序），供 sticky 月份標題用。各分頁各自分組。
    val upcomingGrouped = remember(upcoming, now.year) { groupByMonth(upcoming, historyStyle = false, nowYear = now.year) }
    val historyGrouped = remember(history, now.year) { groupByMonth(history, historyStyle = true, nowYear = now.year) }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                TopAppBar(
                    title = { Text("我的演唱會", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Visible) },
                    actions = {
                        // 歷史紀錄的年份篩選：移到標題列右邊（在資訊 / 顯示 icon 左側）。
                        if (tab == ShowsTab.HISTORY && historyYears.isNotEmpty()) {
                            var yearMenu by remember { mutableStateOf(false) }
                            Box {
                                androidx.compose.material3.Surface(
                                    onClick = { yearMenu = true },
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)) {
                                        Text(historyYear?.let { "$it" } ?: "全部", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                        Icon(Icons.Outlined.KeyboardArrowDown, null, modifier = Modifier.size(16.dp))
                                    }
                                }
                                androidx.compose.material3.DropdownMenu(expanded = yearMenu, onDismissRequest = { yearMenu = false }) {
                                    androidx.compose.material3.DropdownMenuItem(text = { Text("全部時間") }, onClick = { historyYear = null; yearMenu = false })
                                    historyYears.forEach { y ->
                                        androidx.compose.material3.DropdownMenuItem(text = { Text("$y 年") }, onClick = { historyYear = y; yearMenu = false })
                                    }
                                }
                            }
                        }
                        // 搜尋：點一下展開搜尋列，可依名稱 / 藝人 / 場地 / 縣市過濾。
                        IconButton(onClick = { searchActive = !searchActive; if (!searchActive) searchQuery = "" }) {
                            Icon(if (searchActive) Icons.Outlined.SearchOff else Icons.Outlined.Search, contentDescription = "搜尋")
                        }
                        // 資訊多寡：只切換「完整資訊(2) ↔ 全圖片(0)」兩種；海報模式才有意義。
                        if (display != ShowDisplay.LIST) {
                            // 循環：完整 → 含票價 → 只留圖片 → 完整。
                            IconButton(onClick = { infoLevel = when (infoLevel) { 2 -> 3; 3 -> 0; else -> 2 } }) {
                                Icon(
                                    if (infoLevel == 0) Icons.Outlined.HideImage else Icons.AutoMirrored.Outlined.Notes,
                                    contentDescription = "資訊多寡：完整資訊 / 全圖片"
                                )
                            }
                        }
                        // 顯示模式：只在「海報 ↔ 清單」兩種間切換。
                        IconButton(onClick = {
                            display = if (display == ShowDisplay.MASONRY) ShowDisplay.LIST else ShowDisplay.MASONRY
                        }) {
                            Icon(
                                if (display == ShowDisplay.MASONRY) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.ViewAgenda,
                                contentDescription = "切換顯示模式：海報 / 清單"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
                // 搜尋列：展開時顯示，過濾兩個分頁。
                if (searchActive) {
                    androidx.compose.material3.OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("搜尋演唱會 · 藝人 · 場地 · 縣市") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Outlined.Close, "清除") }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
                // 中性雙分頁：即將到來 / 歷史紀錄。
                androidx.compose.material3.TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    ShowsTab.entries.forEach { t ->
                        androidx.compose.material3.Tab(
                            selected = tab == t,
                            onClick = { coScope.launch { pagerState.animateScrollToPage(t.ordinal) } },
                            text = { Text(t.label, fontWeight = if (tab == t) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            // 往下捲（正在看卡片）時整組讓位，不再蓋住卡片上的日期、地點；往上捲或回到頂部再出現（實機回報）。
            val fabVisible = currentListState.isScrollingUp()
            androidx.compose.animation.AnimatedVisibility(
                visible = fabVisible,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it / 2 },
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it / 2 }
            ) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (showScrollTop) {
                    SmallFloatingActionButton(
                        onClick = { coScope.launch { currentListState.animateScrollToItem(0) } },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) { Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "回到頂部") }
                }
                // 批量匯入：以前只藏在「我的 → 雲端備份」最底下、跟空白首頁（實機回報找不到）。
                // 放在「新增」正上方，一次加一大批場次的人第一眼就看得到。
                ExtendedFloatingActionButton(
                    onClick = onBulkImport,
                    expanded = !showScrollTop,
                    icon = { Icon(Icons.Outlined.UploadFile, null) },
                    text = { Text("批量匯入", fontWeight = FontWeight.SemiBold) },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
                // 單一新增入口（spec §6.7.1）：Extended FAB「＋ 新增」→ 快速新增 dialog（內含「填完整資料 →」）。
                // 捲動時收縮成圓形 icon。
                ExtendedFloatingActionButton(
                    onClick = { showQuickAdd = true },
                    expanded = !showScrollTop,
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("新增", fontWeight = FontWeight.SemiBold) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                )
            }
            }
        },
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        val listPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = padding.calculateTopPadding() + 8.dp,
            // Scaffold 設了 floatingActionButton 時，這裡拿到的 bottom
            // **已經含了 FAB 的高度與間距**（M3 的 Scaffold 自己算進去）。
            // 再加 96dp 就是加第二次，捲到底會出現一大塊空白（實機回報）。
            // 只留一點呼吸空間就好。
            bottom = padding.calculateBottomPadding() + 16.dp
        )
        val config = androidx.compose.ui.platform.LocalConfiguration.current
        // 海報式（單欄）：卡片寬≈整個內容寬（左右各 16dp）。
        val cardWidthDp = config.screenWidthDp - 32
        // 演出還少（< 5 場）時保留票根範例入口，讓人先看到記下來之後能做出什麼。
        var showSampleTicket by remember { mutableStateOf(false) }
        if (showSampleTicket) com.bandary.presentation.ui.components.SampleTicketDialog(onDismiss = { showSampleTicket = false })
        val fewShows = state.allConcerts.size < 5
        // 即將到來 / 歷史紀錄兩頁，可左右滑切換；各自一個 LazyColumn + sticky 月份標題。
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val pageShown = if (page == 0) upcoming else history
            val pageGrouped = if (page == 0) upcomingGrouped else historyGrouped
            val pageListState = if (page == 0) upcomingListState else historyListState
            if (pageShown.isEmpty()) {
                // 空狀態改成可捲動、從上方開始：原本是 Arrangement.Center，標題會貼著上面的分頁列，
                // 而且加了範例之後在小螢幕上會被裁掉。
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding)
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(28.dp))
                    EmptyState(
                        title = if (page == 0) "還沒有即將到來的演唱會" else "還沒有歷史紀錄",
                        subtitle = "看過的、買了票的、還在搶的都可以先記下來",
                        onClick = onAddShow,
                        actionLabel = "手動新增・完整表單"
                    )
                    // 三場範例：文字說明再多也不如直接把卡片畫出來。點一下就看得到票根範例。
                    Spacer(Modifier.height(20.dp))
                    com.bandary.presentation.ui.components.SampleBanner(
                        "記下來之後，你的演唱會會長這樣（以下為示意資料）"
                    )
                    Spacer(Modifier.height(8.dp))
                    // 半透明：實機回報「第一眼看不出來是範例」。
                    com.bandary.presentation.ui.components.SampleContent(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            com.bandary.presentation.ui.components.sampleConcerts(now.date).forEach { s ->
                                ShowListRow(concert = s, now = now, onClick = { showSampleTicket = true })
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { showSampleTicket = true }) { Text("看看票根分享長怎樣（範例）") }
                    // 第一次記錄的快速入口：掃海報自動帶入 / 批量匯入 / 快速新增。
                    if (page == 0) {
                        Spacer(Modifier.height(20.dp))
                        Text("更快的方式", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.material3.FilledTonalButton(onClick = onScanPoster, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                            Icon(Icons.Outlined.PhotoCamera, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("掃海報自動帶入")
                        }
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.material3.FilledTonalButton(onClick = onBulkImport, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                            Icon(Icons.Outlined.UploadFile, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("批量匯入・CSV 或貼清單")
                        }
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.material3.FilledTonalButton(onClick = { showQuickAdd = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                            Icon(Icons.Outlined.Bolt, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("快速新增・只填名稱與日期")
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                }
            } else {
                Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = pageListState,
                    modifier = Modifier.fillMaxSize().verticalScrollbar(pageListState),
                    contentPadding = listPadding,
                    verticalArrangement = Arrangement.spacedBy(if (display == ShowDisplay.LIST) 8.dp else 12.dp)
                ) {
                    // 「去年的今天」回顧卡：只在「即將到來」分頁最上方，且今天真的有往年紀錄時才出現。
                    if (page == 0 && throwbacks.isNotEmpty()) {
                        item(key = "throwback") {
                            ThrowbackCard(throwbacks = throwbacks, nowYear = now.year, onOpen = onShowDetail)
                        }
                    }
                    if (fewShows) {
                        item(key = "sample-ticket") {
                            androidx.compose.material3.OutlinedButton(
                                onClick = { showSampleTicket = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("看看票根分享長怎樣（範例）") }
                        }
                    }
                    pageGrouped.forEach { (monthLabel, monthItems) ->
                        stickyHeader(key = "h-$monthLabel") {
                            MonthStickyHeader(monthLabel)
                        }
                        items(monthItems, key = { it.id }) { c ->
                            when (display) {
                                ShowDisplay.MASONRY -> {
                                    val aspect = rememberCoverAspect(c.photoUri ?: c.photoUris.firstOrNull())
                                    val h = (cardWidthDp * aspect).toInt().coerceIn(200, 480)
                                    ConcertCoverCard(concert = c, now = now, heightDp = h, infoLevel = infoLevel, onClick = { onShowDetail(c.id) })
                                }
                                ShowDisplay.LIST -> ShowListRow(concert = c, now = now, onClick = { onShowDetail(c.id) })
                            }
                        }
                    }
                }
                // 時間快捲：長按右側把手上下拖，快速跳到不同月份，拖曳中央顯示目前所在時間。
                TimeScrubber(
                    state = pageListState, groups = pageGrouped,
                    headerStart = if (page == 0 && throwbacks.isNotEmpty()) 1 else 0,
                    // Box 是滿版的，不讓開 header 的話握把最上段會被 TopAppBar + TabRow 蓋住、摸不到。
                    topInset = padding.calculateTopPadding(),
                    bottomInset = padding.calculateBottomPadding()
                )
                }
            }
        }
    }

    if (showQuickAdd) {
        var qArtist by remember { mutableStateOf("") }
        var qName by remember { mutableStateOf("") }
        var qVenue by remember { mutableStateOf("") }
        var qDate by remember { mutableStateOf(now.date) }
        var showDate by remember { mutableStateOf(false) }
        val nameFocus = remember { androidx.compose.ui.focus.FocusRequester() }
        // 開啟即聚焦藝人欄並彈出鍵盤（spec §6.7.2）。
        LaunchedEffect(Unit) { runCatching { nameFocus.requestFocus() } }
        AlertDialog(
            onDismissRequest = { showQuickAdd = false },
            title = { Text("快速新增") },
            text = {
                com.bandary.presentation.ui.components.DialogBody(spacing = 10.dp) {
                    androidx.compose.material3.OutlinedTextField(
                        value = qArtist, onValueChange = { qArtist = it },
                        label = { Text("藝人 / 樂團 *") }, singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().focusRequester(nameFocus)
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = qName, onValueChange = { qName = it },
                        label = { Text("演唱會名稱 *") }, singleLine = true,
                        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = qVenue, onValueChange = { qVenue = it },
                        label = { Text("場地") }, singleLine = true,
                        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
                    )
                    androidx.compose.material3.OutlinedButton(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Outlined.CalendarMonth, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("日期 * ：${qDate.year}/${qDate.monthNumber}/${qDate.dayOfMonth}")
                    }
                    // 通往完整表單（spec §6.7.1）。
                    TextButton(
                        onClick = { showQuickAdd = false; onAddShow() },
                        contentPadding = PaddingValues(horizontal = 0.dp)
                    ) { Text("填完整資料 →", fontWeight = FontWeight.SemiBold) }
                    TextButton(
                        onClick = { showQuickAdd = false; onBulkImport() },
                        contentPadding = PaddingValues(horizontal = 0.dp)
                    ) { Text("一次新增很多場？批量匯入 →", fontWeight = FontWeight.SemiBold) }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (qArtist.isNotBlank() && qName.isNotBlank()) {
                            val savedName = qName.trim()
                            viewModel.quickAdd(qArtist, qName, qVenue, qDate) { newId ->
                                coScope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "已新增《$savedName》",
                                        actionLabel = "編輯",
                                        withDismissAction = true,
                                        duration = androidx.compose.material3.SnackbarDuration.Short
                                    )
                                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) onEditConcert(newId)
                                }
                            }
                            showQuickAdd = false
                        }
                    },
                    enabled = qArtist.isNotBlank() && qName.isNotBlank(),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("儲存", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { showQuickAdd = false }) { Text("取消") } }
        )
        if (showDate) {
            val dpState = androidx.compose.material3.rememberDatePickerState(
                initialSelectedDateMillis = qDate.atStartOfDayIn(TimeZone.of("Asia/Taipei")).toEpochMilliseconds()
            )
            androidx.compose.material3.DatePickerDialog(
                onDismissRequest = { showDate = false },
                confirmButton = {
                    TextButton(onClick = {
                        dpState.selectedDateMillis?.let { ms ->
                            qDate = kotlinx.datetime.Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.of("Asia/Taipei")).date
                        }
                        showDate = false
                    }) { Text("確定") }
                },
                dismissButton = { TextButton(onClick = { showDate = false }) { Text("取消") } }
            ) { androidx.compose.material3.DatePicker(state = dpState) }
        }
    }
}

/**
 * 「去年的今天」回顧：同月同日往年看過的場次，做成封面圖卡片，點一下開詳情。
 * 封面走全 App 同一套「有效封面」規則（使用者封面 > 其他照片 > 內建底圖；選了自訂底色就用漸層），
 * 跟下面清單的封面卡一致。只有一場時滿版一張；多場時橫向滑動。
 */
@Composable
private fun ThrowbackCard(throwbacks: List<Concert>, nowYear: Int, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Outlined.History, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text("#去年的今天", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            if (throwbacks.size > 1) {
                Text("${throwbacks.size} 場", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (throwbacks.size == 1) {
            ThrowbackPoster(throwbacks.first(), nowYear, Modifier.fillMaxWidth().height(190.dp), onOpen)
        } else {
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(throwbacks, key = { it.id }) { c ->
                    ThrowbackPoster(c, nowYear, Modifier.width(280.dp).height(170.dp), onOpen)
                }
            }
        }
    }
}

@Composable
private fun ThrowbackPoster(c: Concert, nowYear: Int, modifier: Modifier, onOpen: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val cover = remember(c.id, c.photoUri, c.photoUris, c.coverColorHex) {
        com.bandary.presentation.ui.components.effectiveCoverUri(context, c)
    }
    val years = nowYear - c.dateTime.year
    val date = "${c.dateTime.year}.${c.dateTime.monthNumber.toString().padStart(2, '0')}.${c.dateTime.dayOfMonth.toString().padStart(2, '0')}"
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(com.bandary.presentation.ui.components.concertCoverBrush(c))
            .clickable { onOpen(c.id) }
    ) {
        if (cover != null) {
            coil.compose.AsyncImage(
                model = cover,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }
        // 下半部壓暗，白字放在任何海報上都讀得到；上半部保留原圖。
        Box(
            Modifier.matchParentSize().background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to androidx.compose.ui.graphics.Color.Transparent,
                    0.35f to androidx.compose.ui.graphics.Color.Transparent,
                    1f to androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.78f)
                )
            )
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(50)) {
                Text(
                    "$years 年前",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            Text(
                c.name.ifBlank { com.bandary.presentation.ui.components.concertArtistsLabel(c) },
                color = androidx.compose.ui.graphics.Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Text(
                listOf(date, c.venue).filter { it.isNotBlank() }.joinToString(" · "),
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 右側捲動條：捲動時淡入一條細長 indicator，顯示目前在整份清單的位置與長度（放手後淡出）。 */
/**
 * 時間快捲把手：右側一條可上下拖的握把，快速跳到不同「年-月」分組；拖曳時螢幕中央顯示目前所在時間。
 * groups＝依月份分組的清單，headerStart＝LazyColumn 中第一個月份標題前的固定 item 數（例如「去年的今天」卡）。
 */
@Composable
private fun BoxScope.TimeScrubber(
    state: LazyListState,
    groups: List<Pair<String, List<Concert>>>,
    headerStart: Int,
    topInset: Dp = 0.dp,      // 讓開 TopAppBar / TabRow，否則握把最上段藏在 header 底下，摸不到
    bottomInset: Dp = 0.dp
) {
    if (groups.size < 3) return  // 月份太少不需要快捲
    val scope = rememberCoroutineScope()
    // 每個月份標題在 LazyColumn 中的 item index。
    val headerIndices = remember(groups, headerStart) {
        var idx = headerStart
        groups.map { (_, items) -> val h = idx; idx += 1 + items.size; h }
    }
    var dragging by remember { mutableStateOf(false) }
    var dragFrac by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    // 目前所在組：找出 index <= 第一個可見 item 的最後一個標題。
    val currentGroup = remember(state.firstVisibleItemIndex, headerIndices) {
        headerIndices.indexOfLast { it <= state.firstVisibleItemIndex }.coerceIn(0, groups.lastIndex)
    }
    val shownGroup = if (dragging)
        (dragFrac * groups.lastIndex).roundToInt().coerceIn(0, groups.lastIndex) else currentGroup
    val thumbFrac = (if (dragging) dragFrac else currentGroup.toFloat() / groups.lastIndex.coerceAtLeast(1))
        .coerceIn(0.02f, 0.98f)

    fun jumpTo(frac: Float) {
        val g = (frac * groups.lastIndex).roundToInt().coerceIn(0, groups.lastIndex)
        scope.launch { state.scrollToItem(headerIndices[g]) }
    }

    // 右側可拖握把（含觸控區）。觸控區加寬到 44dp（Material 觸控下限），比原本 28dp 好抓。
    Box(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .padding(top = topInset, bottom = bottomInset)
            .width(44.dp)
            .pointerInput(headerIndices) {
                detectVerticalDragGestures(
                    onDragStart = { off -> dragging = true; dragFrac = (off.y / size.height).coerceIn(0f, 1f); jumpTo(dragFrac) },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                    onVerticalDrag = { change, _ -> dragFrac = (change.position.y / size.height).coerceIn(0f, 1f); jumpTo(dragFrac) }
                )
            }
    ) {
        // 用權重把握把定位在 thumbFrac 高度，免去像素換算。
        // 握把本身加粗到 10dp、拉長到 56dp，看得到也好拖（原本 6×40 太細）。
        Column(Modifier.fillMaxHeight().width(10.dp).align(Alignment.CenterEnd).padding(vertical = 8.dp, horizontal = 0.dp)) {
            Spacer(Modifier.weight(thumbFrac))
            Box(
                Modifier.width(10.dp).height(56.dp).clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = if (dragging) 1f else 0.6f))
            )
            Spacer(Modifier.weight(1f - thumbFrac))
        }
    }
    // 拖曳時中央顯示目前所在時間。
    if (dragging) {
        androidx.compose.material3.Surface(
            modifier = Modifier.align(Alignment.Center),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 6.dp
        ) {
            Text(
                groups[shownGroup].first,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

private fun Modifier.verticalScrollbar(state: LazyListState, width: Dp = 3.dp): Modifier = composed {
    val barColor = MaterialTheme.colorScheme.onSurface
    val targetAlpha = if (state.isScrollInProgress) 0.4f else 0f
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = if (state.isScrollInProgress) 150 else 600),
        label = "scrollbar-alpha"
    )
    drawWithContent {
        drawContent()
        val info = state.layoutInfo
        val total = info.totalItemsCount
        val visible = info.visibleItemsInfo.size
        if (total > visible && visible > 0 && alpha > 0f) {
            val w = width.toPx()
            val barH = (size.height * visible / total).coerceAtLeast(28f)
            val progress = state.firstVisibleItemIndex.toFloat() / (total - visible).coerceAtLeast(1)
            val barTop = (size.height - barH) * progress.coerceIn(0f, 1f)
            drawRoundRect(
                color = barColor.copy(alpha = alpha),
                topLeft = Offset(size.width - w - 2f, barTop),
                size = Size(w, barH),
                cornerRadius = CornerRadius(w / 2, w / 2)
            )
        }
    }
}

/** 依「年-月」分組（保留原順序），組出 sticky 月份標題用的清單。 */
private fun groupByMonth(list: List<Concert>, historyStyle: Boolean, nowYear: Int): List<Pair<String, List<Concert>>> {
    val map = LinkedHashMap<String, MutableList<Concert>>()
    list.forEach { c ->
        map.getOrPut("${c.dateTime.year}-${c.dateTime.monthNumber}") { mutableListOf() }.add(c)
    }
    return map.entries.map { (k, v) ->
        val (y, m) = k.split("-").map { it.toInt() }
        // 歷史紀錄：一律完整「YYYY 年 M 月」；即將到來：當年只顯示「M 月」，跨年才補年份。
        val label = if (historyStyle) "$y 年 $m 月"
            else if (y == nowYear) "$m 月" else "$y 年 $m 月"
        label to v.toList()
    }
}

/** 捲動時大膽提示月份的 sticky 標題：輕量粗體大字 + 背景色（sticky 時仍清楚）。 */
@Composable
private fun MonthStickyHeader(label: String) {
    Box(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 6.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
        )
    }
}

/** 依封面圖的實際長寬比（高/寬）回傳；載入前預設橫式 0.62（16:10）。無圖也回傳橫式。 */
@Composable
private fun rememberCoverAspect(uri: String?): Float {
    if (uri.isNullOrBlank()) return 0.62f
    val context = androidx.compose.ui.platform.LocalContext.current
    val aspect by androidx.compose.runtime.produceState(0.62f, uri) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use {
                    android.graphics.BitmapFactory.decodeStream(it, null, opts)
                }
                if (opts.outWidth > 0 && opts.outHeight > 0) opts.outHeight.toFloat() / opts.outWidth else 0.62f
            }.getOrDefault(0.62f)
        }
    }
    return aspect
}

/** 清單列：整列以封面圖當底（無圖則用漸層純色底），上覆 scrim 保持文字可讀。 */
@Composable
private fun ShowListRow(concert: Concert, now: LocalDateTime, onClick: () -> Unit) {
    val days = now.date.daysUntil(concert.dateTime.date)
    val context = androidx.compose.ui.platform.LocalContext.current
    val realPoster = concert.photoUri?.takeIf { it.isNotBlank() } ?: concert.photoUris.firstOrNull()
    // 無真正封面且沒選過自訂色時，跟卡片一樣預設套內建底圖（依 id 穩定挑一張）。
    val poster = realPoster ?: remember(concert.id, concert.coverColorHex) {
        if (concert.coverColorHex.isNullOrBlank())
            com.bandary.presentation.ui.components.defaultPosterCoverUri(context, concert) else null
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        val artistsLabel = com.bandary.presentation.ui.components.concertArtistsLabel(concert)
        val showArtistLine = concert.name.isNotBlank() && artistsLabel.isNotBlank() && artistsLabel != concert.name
        Box(Modifier.fillMaxWidth().height(if (showArtistLine) 92.dp else 76.dp)) {
            // 底圖：封面圖優先；沒有圖就用這場的漸層純色底。
            if (poster != null) {
                AsyncImage(model = poster, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(com.bandary.presentation.ui.components.Scrim.start()))
            } else {
                // 無封面：用這場莫蘭迪底色的「偏深」純色當底，白字清楚；
                // 不再疊半透明淺色漸層（原本會像突兀的白色半透明框）。
                val base = Color(android.graphics.Color.parseColor(com.bandary.presentation.ui.components.concertBaseColorHex(concert)))
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.lerp(base, Color.Black, 0.35f)))
            }
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${concert.dateTime.year}.${concert.dateTime.monthNumber.toString().padStart(2, '0')}.${concert.dateTime.dayOfMonth.toString().padStart(2, '0')}",
                        style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f)
                    )
                    Text(
                        concert.name.ifBlank { artistsLabel },
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (showArtistLine) {
                        Text(
                            artistsLabel,
                            style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        buildString {
                            concert.city?.takeIf { it.isNotBlank() }?.let { append(it).append(" · ") }
                            append(concert.venue)
                        },
                        style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                if (concert.dateTime >= now) {
                    Text(
                        if (days <= 0) "今天" else "$days 天",
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White
                    )
                }
            }
        }
    }
}

