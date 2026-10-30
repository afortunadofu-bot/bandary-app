package com.bandary.presentation.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bandary.data.local.SettingsDataStore
import com.bandary.data.repository.ConcertRepository
import com.bandary.data.repository.FreeEventsRepository
import com.bandary.domain.model.Concert
import com.bandary.domain.model.FreeEvent
import com.bandary.domain.model.saleStartAt
import com.bandary.domain.model.coverForDate
import com.bandary.domain.model.occurrenceDates
import com.bandary.presentation.ui.components.ConcertCoverCard
import com.bandary.presentation.ui.components.EmptyState
import com.bandary.presentation.ui.components.SectionHeader
import com.bandary.presentation.ui.components.swipeMonth
import androidx.compose.foundation.clickable
import com.bandary.presentation.ui.theme.BandaryColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

data class YearMonth(val year: Int, val month: Month)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repo: ConcertRepository,
    private val freeRepo: FreeEventsRepository,
    private val settings: SettingsDataStore
) : ViewModel() {

    private val today = Clock.System.now()
        .toLocalDateTime(TimeZone.of("Asia/Taipei")).date

    init { viewModelScope.launch { freeRepo.refresh() } }

    private val _selectedDate = MutableStateFlow(today)
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _currentMonth = MutableStateFlow(YearMonth(today.year, today.month))
    val currentMonth: StateFlow<YearMonth> = _currentMonth.asStateFlow()

    // 使用者標示「有興趣要去」的免費活動（已核准 ∩ 有興趣集合）。
    private val interestedFree: StateFlow<List<FreeEvent>> =
        combine(freeRepo.events, settings.interestedEvents) { events, ids ->
            events.filter { it.id in ids }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 當月有興趣免費活動的日期（月曆標色用）——多日活動標整段區間的每一天。
    val interestedDatesForMonth: StateFlow<Set<LocalDate>> =
        combine(_currentMonth, interestedFree) { ym, list ->
            list.flatMap { it.occurrenceDates() }
                .filter { it.year == ym.year && it.month == ym.month }.toSet()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // 選取日的有興趣免費活動（下方清單用）——多日活動在區間內任一天都會列出。
    val interestedForSelected: StateFlow<List<FreeEvent>> =
        combine(_selectedDate, interestedFree) { date, list ->
            list.filter { date in it.occurrenceDates() }.sortedBy { it.dateTime }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 一鍵把有興趣的免費活動加入「我的演唱會」。 */
    fun joinFreeEvent(event: FreeEvent, onDone: (String) -> Unit) = viewModelScope.launch {
        freeRepo.join(event)
        onDone("已加入「${event.name}」到我的演唱會")
    }

    val concertsForMonth: StateFlow<List<Concert>> = _currentMonth
        .flatMapLatest { ym ->
            val isLeap = (ym.year % 4 == 0 && ym.year % 100 != 0) || ym.year % 400 == 0
            val days = ym.month.length(isLeap)
            val start = LocalDateTime(ym.year, ym.month, 1, 0, 0)
            val end = LocalDateTime(ym.year, ym.month, days, 23, 59)
            repo.getConcertsBetween(start, end)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 全部演唱會（用來標記發生日 / 開賣日 / 顯示當日）。
    private val allConcerts: StateFlow<List<Concert>> =
        repo.getAllConcerts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val concertsForSelected: StateFlow<List<Concert>> =
        combine(_selectedDate, allConcerts) { date, list ->
            // 多日活動（連續或非連續）：任一發生日都算當日活動。
            list.filter { c -> date in c.occurrenceDates() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 當月每一天的演出「數量」（含多日 / 非連續），月曆用點點數量呈現。
    val concertCountsForMonth: StateFlow<Map<LocalDate, Int>> =
        combine(_currentMonth, allConcerts) { ym, list ->
            list.flatMap { it.occurrenceDates() }
                .filter { it.year == ym.year && it.month == ym.month }
                .groupingBy { it }.eachCount()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /**
     * 當月每一天的演出**名稱**（月曆格子裡顯示）。
     *
     * 與 [concertCountsForMonth] 並存而不是取代它：數量仍然要用來決定底色
     *（有沒有演出）與「+N」，名稱只是多給的資訊。兩個都從同一份 allConcerts 來，
     * 不會對不起來。
     *
     * 名稱優先用場次名，空白才退回藝人——「告五人」比「（未命名）」有用得多。
     */
    val concertNamesForMonth: StateFlow<Map<LocalDate, List<String>>> =
        combine(_currentMonth, allConcerts) { ym, list ->
            val byDate = mutableMapOf<LocalDate, MutableList<String>>()
            list.forEach { c ->
                val label = c.name.trim().ifBlank { c.artistName.trim() }
                if (label.isBlank()) return@forEach
                c.occurrenceDates()
                    .filter { it.year == ym.year && it.month == ym.month }
                    .forEach { d -> byDate.getOrPut(d) { mutableListOf() }.add(label) }
            }
            byDate
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // 當月的開賣日（在月曆上標記）。
    val saleDatesForMonth: StateFlow<Set<LocalDate>> =
        combine(_currentMonth, allConcerts) { ym, list ->
            list.mapNotNull { it.saleStartAt }
                .filter { it.year == ym.year && it.month == ym.month }
                .map { it.date }.toSet()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // 選取日當天要開賣的演唱會。
    val salesForSelected: StateFlow<List<Concert>> =
        combine(_selectedDate, allConcerts) { date, list ->
            list.filter { it.saleStartAt?.date == date }.sortedBy { it.saleStartAt }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 即將開賣（跨月，未來排序取前幾筆）— 讓要搶票的場次一眼看到。
    val upcomingSales: StateFlow<List<Concert>> = allConcerts.map { list ->
        val now = Clock.System.now().toLocalDateTime(TimeZone.of("Asia/Taipei"))
        list.filter { it.saleStartAt != null && it.saleStartAt!! >= now }
            .sortedBy { it.saleStartAt }
            .take(5)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 一場都還沒記過。預設 true，避免資料還在載入時先閃一下範例。
     * 給行事曆的空白日用：完全沒資料的人看到的只有「這天沒有演出」，不知道記了會長怎樣。
     */
    val hasAnyConcert: StateFlow<Boolean> =
        allConcerts.map { it.isNotEmpty() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun select(date: LocalDate) { _selectedDate.value = date }

    fun nextMonth() {
        val ym = _currentMonth.value
        val next = if (ym.month == Month.DECEMBER) YearMonth(ym.year + 1, Month.JANUARY)
        else YearMonth(ym.year, Month.entries[ym.month.ordinal + 1])
        _currentMonth.value = next
        syncSelectedToMonth(next)
    }

    fun prevMonth() {
        val ym = _currentMonth.value
        val prev = if (ym.month == Month.JANUARY) YearMonth(ym.year - 1, Month.DECEMBER)
        else YearMonth(ym.year, Month.entries[ym.month.ordinal - 1])
        _currentMonth.value = prev
        syncSelectedToMonth(prev)
    }

    /** 切月時把選取日移到新月份（該月是本月就選今天，否則選 1 號），下方清單才會跟著更新。 */
    private fun syncSelectedToMonth(ym: YearMonth) {
        val today = Clock.System.now().toLocalDateTime(TimeZone.of("Asia/Taipei")).date
        _selectedDate.value = if (ym.year == today.year && ym.month == today.month) today
        else LocalDate(ym.year, ym.month, 1)
    }

    /** 一鍵回到今天：切回本月並選取今天。 */
    fun goToToday() {
        val t = Clock.System.now().toLocalDateTime(TimeZone.of("Asia/Taipei")).date
        _currentMonth.value = YearMonth(t.year, t.month)
        _selectedDate.value = t
    }

    fun setMonth(year: Int, month: Month) { _currentMonth.value = YearMonth(year, month) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onShowDetail: (String) -> Unit,
    onAddConcert: (LocalDate) -> Unit = {},
    embedded: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val selected by viewModel.selectedDate.collectAsState()
    val currentMonth by viewModel.currentMonth.collectAsState()
    val concerts by viewModel.concertsForMonth.collectAsState()
    val concertCounts by viewModel.concertCountsForMonth.collectAsState()
    val concertNames by viewModel.concertNamesForMonth.collectAsState()
    val todayConcerts by viewModel.concertsForSelected.collectAsState()
    val saleDates by viewModel.saleDatesForMonth.collectAsState()
    val todaySales by viewModel.salesForSelected.collectAsState()
    val interestedDates by viewModel.interestedDatesForMonth.collectAsState()
    val interestedToday by viewModel.interestedForSelected.collectAsState()
    val hasAnyConcert by viewModel.hasAnyConcert.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val now = remember { Clock.System.now().toLocalDateTime(TimeZone.of("Asia/Taipei")) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scrollScope = androidx.compose.runtime.rememberCoroutineScope()
    var showMonthPicker by remember { mutableStateOf(false) }

    val content: @Composable (PaddingValues) -> Unit = { pad ->
      Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = listState, contentPadding = pad) {
            item {
                // Month header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.prevMonth() }) {
                        Icon(Icons.Outlined.ChevronLeft, "上個月")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 點月份 = 一次選 1–12 月
                        Text(
                            "${currentMonth.year} 年 ${currentMonth.month.value} 月",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { showMonthPicker = true }
                        )
                        Spacer(Modifier.size(8.dp))
                        // 一鍵回到今天
                        Surface(
                            onClick = { viewModel.goToToday() },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text("今天", modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    IconButton(onClick = { viewModel.nextMonth() }) {
                        Icon(Icons.Outlined.ChevronRight, "下個月")
                    }
                }
            }

            item {
                // 左右滑動換月份
                CalendarGrid(
                    yearMonth = currentMonth,
                    concertCounts = concertCounts,
                    concertNames = concertNames,
                    saleDates = saleDates,
                    interestedDates = interestedDates,
                    selectedDate = selected,
                    onSelect = viewModel::select,
                    modifier = Modifier.padding(horizontal = 8.dp)
                        .swipeMonth(onPrev = { viewModel.prevMonth() }, onNext = { viewModel.nextMonth() })
                )
            }

            // 圖例：演出（青底 + 主色點）/ 開賣（橘點）
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(12.dp).background(MaterialTheme.colorScheme.primaryContainer, androidx.compose.foundation.shape.CircleShape).border(2.dp, MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape))
                        Text("今天", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(12.dp).background(MaterialTheme.colorScheme.tertiaryContainer, androidx.compose.foundation.shape.CircleShape))
                        Text("有演出", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(12.dp).background(com.bandary.presentation.ui.theme.BandaryColors.Orange, androidx.compose.foundation.shape.CircleShape))
                        Text("開賣", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(12.dp).background(com.bandary.presentation.ui.theme.BandaryColors.Purple, androidx.compose.foundation.shape.CircleShape))
                        Text("有興趣的活動", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, start = 16.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${currentMonth.month.value} 月 ${selected.dayOfMonth} 日",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
                    )
                    androidx.compose.material3.TextButton(onClick = { onAddConcert(selected) }) {
                        Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
                        Text("新增這天的演出", fontSize = 13.sp)
                    }
                }
            }

            if (todayConcerts.isEmpty()) {
                item {
                    Text(
                        "這天沒有演出",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = if (hasAnyConcert) 24.dp else 12.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // 一場都還沒記過：直接把卡片畫出來，比一句「這天沒有演出」清楚得多。
                if (!hasAnyConcert) {
                    item {
                        com.bandary.presentation.ui.components.SampleBanner(
                            "記下來之後，行事曆上會長這樣（以下為示意資料）",
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                    items(
                        com.bandary.presentation.ui.components.sampleConcerts(now.date),
                        key = { "cal-sample-${it.id}" }
                    ) { s ->
                        // 半透明：實機回報「第一眼看不出來是範例」。
                        com.bandary.presentation.ui.components.SampleContent(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            ConcertCoverCard(concert = s, now = now, onClick = { onAddConcert(selected) })
                        }
                    }
                }
            } else {
                items(todayConcerts, key = { it.id }) { c ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        // 多日活動顯示「當天」的封面
                        ConcertCoverCard(concert = c, now = now, overridePoster = c.coverForDate(selected), onClick = { onShowDetail(c.id) })
                    }
                }
            }

            // 本日開賣（搶票日期 / 時間 / 平台）
            if (todaySales.isNotEmpty()) {
                item {
                    SectionHeader("本日開賣", modifier = Modifier.padding(top = 12.dp))
                }
                items(todaySales, key = { "sale_${it.id}" }) { c ->
                    val sale = c.saleStartAt
                    androidx.compose.material3.Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 5.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        onClick = { onShowDetail(c.id) },
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Outlined.ConfirmationNumber, null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    c.name.ifBlank { c.artistName },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                // 藝人有誰
                                val artists = (listOfNotNull(c.artistName.takeIf { it.isNotBlank() }) +
                                    c.festivals.filter { it.isSelected }.map { it.artistName })
                                    .filter { it.isNotBlank() }.distinct()
                                if (artists.isNotEmpty()) {
                                    Text(
                                        artists.joinToString(" / "),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    buildString {
                                        if (sale != null) append("開賣 ${sale.hour.toString().padStart(2, '0')}:${sale.minute.toString().padStart(2, '0')}")
                                        c.ticketPlatform?.takeIf { it.isNotBlank() }?.let { append("　·　$it") }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }
            }

            // 有興趣的免費活動（在我的月曆一併顯示，可一鍵加入我的演唱會）
            if (interestedToday.isNotEmpty()) {
                item { SectionHeader("有興趣的活動", modifier = Modifier.padding(top = 12.dp)) }
                items(interestedToday, key = { "free_${it.id}" }) { ev ->
                    androidx.compose.material3.Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = BandaryColors.PurpleBg)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Outlined.EventAvailable, null, tint = BandaryColors.Purple, modifier = Modifier.size(20.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("活動", style = MaterialTheme.typography.labelSmall, color = BandaryColors.Purple, fontWeight = FontWeight.SemiBold)
                                Text(ev.name.ifBlank { ev.artistName }, style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    buildString {
                                        append("${ev.dateTime.hour.toString().padStart(2, '0')}:${ev.dateTime.minute.toString().padStart(2, '0')}")
                                        if (ev.venue.isNotBlank()) append("　·　${ev.venue}")
                                    },
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FilledTonalButton(
                                onClick = { viewModel.joinFreeEvent(ev) { msg -> android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show() } },
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Icon(Icons.Outlined.Add, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(2.dp))
                                Text("加入", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
        com.bandary.presentation.ui.components.ScrollTopFab(
            listState, scrollScope,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
      }
    }

    if (embedded) {
        content(contentPadding)
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text("演出月曆", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            content(
                PaddingValues(
                    top = padding.calculateTopPadding() + 4.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp
                )
            )
        }
    }

    if (showMonthPicker) {
        com.bandary.presentation.ui.components.MonthYearPickerDialog(
            initialYear = currentMonth.year,
            initialMonth = currentMonth.month.value,
            onPick = { y, m -> viewModel.setMonth(y, Month.entries[m - 1]); showMonthPicker = false },
            onDismiss = { showMonthPicker = false }
        )
    }
}

// ── 日曆中心：演出月曆 + 選歌日曆 合併（頂部切換）──────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarHubScreen(
    onShowDetail: (String) -> Unit,
    onOpenFriends: () -> Unit = {},
    onAddConcert: (LocalDate) -> Unit = {},
    onImportCalendar: () -> Unit = {}
) {
    var mode by remember { mutableStateOf(0) } // 0 = 演出, 1 = 選歌
    // 選歌頁「加一首」註冊進來的動作；有這個才能在 FAB 一鍵開啟，不用滑過月曆才點得到。
    var songAddAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    // 選歌輸入面板打開時，要把左右滑換頁與 FAB 都收起來——面板是畫在頁面上的覆蓋層，
    // 不擋掉的話打字打到一半側滑就換頁了。
    var songSheetOpen by remember { mutableStateOf(false) }
    Scaffold(
        floatingActionButton = {
            if (mode == 1 && !songSheetOpen) {
                androidx.compose.material3.FloatingActionButton(onClick = { songAddAction?.invoke() }) {
                    Icon(Icons.Outlined.Add, "加一首")
                }
            }
        },
        topBar = {
            Column(
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                TopAppBar(
                    title = { Text("行事曆", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                    actions = {
                        // 只有「演出」月曆才顯示：從 Google 日曆挑選匯入活動
                        if (mode == 0) {
                            IconButton(onClick = onImportCalendar) {
                                Icon(Icons.Outlined.EventAvailable, contentDescription = "從 Google 日曆匯入")
                            }
                        }
                        IconButton(onClick = onOpenFriends) {
                            Icon(Icons.Outlined.People, contentDescription = "好友的行事曆")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    SegmentedButton(
                        selected = mode == 0,
                        onClick = { mode = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text("演出") }
                    SegmentedButton(
                        selected = mode == 1,
                        onClick = { mode = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text("選歌") }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        // FAB 是浮在內容之上的，Scaffold 的 calculateBottomPadding() 不會替它讓位，
        // 所以捲到底時最後一張卡會被 FAB 蓋住。選歌頁有 FAB，就多留一顆 FAB 的高度。
        // 選歌模式（mode == 1）才掛 Scaffold 的 FAB，而那時 calculateBottomPadding()
        // 已經把 FAB 高度算進去了——再補 88dp 是第二次，捲到底就是一大塊空白。
        // 兩種模式都只留一點呼吸空間。
        val fabClearance = 16.dp
        val pad = PaddingValues(
            top = padding.calculateTopPadding() + 4.dp,
            bottom = padding.calculateBottomPadding() + fabClearance
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceContainer,
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        ) {
            // 演出 ↔ 選歌 改用 HorizontalPager，可左右滑切換；分段按鈕與頁面雙向同步。
            // 兩頁裡的月曆格子都會 consume 掉自己的「滑動換月」手勢（見 swipeMonth），
            // 所以手指在格子上＝換月份，在其他地方＝換頁，不會互搶。
            val modePager = androidx.compose.foundation.pager.rememberPagerState(pageCount = { 2 })
            LaunchedEffect(mode) { if (modePager.currentPage != mode) modePager.animateScrollToPage(mode) }
            LaunchedEffect(modePager.currentPage) { mode = modePager.currentPage }
            androidx.compose.foundation.pager.HorizontalPager(
                state = modePager,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !songSheetOpen,
                verticalAlignment = Alignment.Top
            ) { page ->
                if (page == 0) {
                    CalendarScreen(onShowDetail = onShowDetail, onAddConcert = onAddConcert, embedded = true, contentPadding = pad)
                } else {
                    com.bandary.presentation.ui.home.HomeScreen(
                        embedded = true,
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp,
                            top = padding.calculateTopPadding() + 4.dp,
                            bottom = padding.calculateBottomPadding() + fabClearance
                        ),
                        onRegisterAdd = { songAddAction = it },
                        onSheetOpenChange = { songSheetOpen = it }
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarGrid(
    yearMonth: YearMonth,
    concertCounts: Map<LocalDate, Int>,
    concertNames: Map<LocalDate, List<String>> = emptyMap(),
    saleDates: Set<LocalDate> = emptySet(),
    interestedDates: Set<LocalDate> = emptySet(),
    selectedDate: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = Clock.System.now().toLocalDateTime(TimeZone.of("Asia/Taipei")).date
    val isLeap = (yearMonth.year % 4 == 0 && yearMonth.year % 100 != 0) || yearMonth.year % 400 == 0
    val daysInMonth = yearMonth.month.length(isLeap)
    val firstDayOfWeek = LocalDate(yearMonth.year, yearMonth.month, 1).dayOfWeek.ordinal  // Mon=0
    // convert so Sunday starts: Mon=1, Tue=2, ... Sun=0
    val sundayStartOffset = (firstDayOfWeek + 1) % 7

    Column(modifier = modifier.padding(horizontal = 4.dp)) {
        // Day of week headers
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("日", "一", "二", "三", "四", "五", "六").forEach { d ->
                Text(
                    d,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 6.dp),
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
                        val isToday = date == today
                        val isSelected = date == selectedDate
                        val concertCount = concertCounts[date] ?: 0
                        val hasConcert = concertCount > 0
                        val hasSale = date in saleDates
                        val hasInterested = date in interestedDates

                        // 底色優先序：選取 > 有演出 > 開賣 > 有興趣免費活動。
                        // 「今天」改用外框圈起來（不搶底色），這樣今天就算有演出也看得出是演出色，
                        // 且今天與有演出的日期不會撞色。演出=青底、開賣=橘底、有興趣=紫底。
                        // 格子從圓形改成圓角方格：圓裡放不下名稱。
                        //
                        // **底色的狀態編碼原封不動**（選取 > 有演出 > 開賣 > 有興趣 > 今天），
                        // 包含「開賣日整格橘底」——那是使用者特別要求過的，
                        // 加名稱不該把它換掉。名稱是多給的資訊，不是取代原本的訊號。
                        Surface(
                            onClick = { onSelect(date) },
                            modifier = Modifier
                                .weight(1f)
                                .height(74.dp)
                                .padding(2.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = when {
                                isSelected -> MaterialTheme.colorScheme.primary
                                hasConcert -> MaterialTheme.colorScheme.tertiaryContainer
                                hasSale -> BandaryColors.OrangeBg
                                hasInterested -> BandaryColors.PurpleBg
                                isToday -> MaterialTheme.colorScheme.primaryContainer  // 今天無活動時填淺主色底，更明顯
                                else -> MaterialTheme.colorScheme.surface
                            },
                            // 今天一律加粗主色外框（不論有無活動），與底色雙重標示更明顯。
                            border = if (isToday && !isSelected)
                                androidx.compose.foundation.BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary)
                            else null
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp, vertical = 3.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                    Text(
                                        "$dayNum",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isToday || isSelected || hasConcert || hasSale || hasInterested) FontWeight.Bold
                                        else FontWeight.Normal,
                                        color = when {
                                            isSelected -> androidx.compose.ui.graphics.Color.White
                                            hasConcert -> MaterialTheme.colorScheme.onTertiaryContainer
                                            hasSale -> BandaryColors.Orange
                                            hasInterested -> BandaryColors.Purple
                                            isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    // 開賣 / 有興趣仍然用點表示——那兩件事名稱說不出來
                                    // （「這天開賣」不是一場演出，沒有名字可以印）。
                                    // 演出的點則拿掉了：名稱本身就說明了有幾場，再印點是講兩遍。
                                    if (hasSale || hasInterested) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                            if (hasSale) Box(
                                                modifier = Modifier.size(5.dp).background(
                                                    color = if (isSelected) androidx.compose.ui.graphics.Color.White
                                                    else BandaryColors.Orange,
                                                    shape = CircleShape
                                                )
                                            )
                                            if (hasInterested) Box(
                                                modifier = Modifier.size(5.dp).background(
                                                    color = if (isSelected) androidx.compose.ui.graphics.Color.White
                                                    else BandaryColors.Purple,
                                                    shape = CircleShape
                                                )
                                            )
                                        }
                                    }
                                    // 演出名稱：每格最多兩個，超過用「+N」收尾。
                                    // 與活動月曆同一套做法——圓點只回答「這天有沒有事」，
                                    // 回答不了「有什麼事」，而翻月曆要找的就是後者。
                                    val names = concertNames[date].orEmpty()
                                    names.take(2).forEach { nm ->
                                        Text(
                                            nm,
                                            fontSize = 8.sp,
                                            lineHeight = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth(),
                                            color = if (isSelected) androidx.compose.ui.graphics.Color.White
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    if (names.size > 2) {
                                        Text(
                                            "+${names.size - 2}",
                                            fontSize = 8.sp,
                                            lineHeight = 10.sp,
                                            color = if (isSelected) androidx.compose.ui.graphics.Color.White
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
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
