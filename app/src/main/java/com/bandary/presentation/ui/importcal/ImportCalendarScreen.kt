package com.bandary.presentation.ui.importcal

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Surface
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bandary.data.local.SettingsDataStore
import com.bandary.data.remote.FirestoreService
import com.bandary.data.remote.GoogleCalendarService
import com.bandary.data.remote.ImportedEvent
import com.bandary.data.repository.ConcertRepository
import com.bandary.data.repository.FreeEventsRepository
import com.bandary.domain.model.Concert
import com.bandary.domain.model.ConcertType
import com.bandary.domain.model.FreeEvent
import com.bandary.presentation.ui.components.EmptyState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import javax.inject.Inject

enum class ImportRange(val label: String, val pastDays: Int, val futureDays: Int) {
    PAST_YEAR_FUTURE("過去一年 + 未來", 365, 365),
    PAST_HALF("過去半年", 183, 7),
    FUTURE_ONLY("只看未來", 0, 365)
}

enum class ImportTarget(val label: String) { CONCERT("我的演唱會"), FREE("活動") }

/**
 * 一筆待匯入的日曆事件。
 *
 * [name]/[artist]/[venue]/[city] 是**匯入前可以改**的值，載入時用 [CalendarTitleParser] 先拆好。
 * 以前直接把 `title` 當藝人名、`location` 當場地寫進去——從別人共編的藝人場次日曆匯進來時，
 * 每一筆的藝人欄都會變成「五月天 @ 台北小巨蛋」這種字串，統計與追蹤藝人跟著全壞。
 */
data class ImportRow(
    val event: ImportedEvent,
    val start: LocalDateTime,
    val multiDay: Boolean,
    val name: String = event.title,
    val artist: String = "",
    val venue: String = "",
    val city: String = ""
) {
    val id: String get() = event.sourceEventId
    /** 還缺藝人或縣市時標出來，提醒使用者匯入前補一下。 */
    val needsAttention: Boolean get() = artist.isBlank() || city.isBlank()
}

@HiltViewModel
class ImportCalendarViewModel @Inject constructor(
    private val calendar: GoogleCalendarService,
    private val concertRepo: ConcertRepository,
    private val freeRepo: FreeEventsRepository,
    private val firestore: FirestoreService,
    private val settings: SettingsDataStore
) : ViewModel() {

    private val _rows = MutableStateFlow<List<ImportRow>>(emptyList())
    val rows: StateFlow<List<ImportRow>> = _rows.asStateFlow()

    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _range = MutableStateFlow(ImportRange.PAST_YEAR_FUTURE)
    val range: StateFlow<ImportRange> = _range.asStateFlow()

    private val _target = MutableStateFlow(ImportTarget.CONCERT)
    val target: StateFlow<ImportTarget> = _target.asStateFlow()

    // 要從哪一份日曆匯入。別人分享給你的日曆在 Google 是獨立一份，不會併進主日曆，
    // 所以只讀 primary 的話永遠看不到共用日曆的內容。
    private val _calendars = MutableStateFlow<List<GoogleCalendarService.CalendarInfo>>(emptyList())
    val calendars: StateFlow<List<GoogleCalendarService.CalendarInfo>> = _calendars.asStateFlow()

    private val _calendarId = MutableStateFlow("primary")
    val calendarId: StateFlow<String> = _calendarId.asStateFlow()

    fun setCalendar(id: String) {
        if (_calendarId.value == id) return
        _calendarId.value = id
        load()
    }

    /** 讀日曆清單。失敗不擋匯入——退回只讀主日曆就好。 */
    fun loadCalendars() {
        if (_calendars.value.isNotEmpty()) return
        viewModelScope.launch {
            calendar.listCalendars().onSuccess { list -> _calendars.update { list } }
        }
    }

    init { viewModelScope.launch { runCatching { freeRepo.refresh() } } }

    // 目標端現有的活動名稱（小寫去空白），用來偵測重複匯入。
    val existingNames: StateFlow<Set<String>> = kotlinx.coroutines.flow.combine(
        _target, concertRepo.getAllConcerts(), freeRepo.events, freeRepo.pending
    ) { t, concerts, ev, pend ->
        when (t) {
            ImportTarget.CONCERT -> concerts.map { it.name.trim().lowercase() }.toSet()
            ImportTarget.FREE -> (ev + pend).map { it.name.trim().lowercase() }.toSet()
        }
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptySet())

    /** 目前勾選中、名稱已存在於目標端的筆數（重複）。 */
    fun selectedDuplicateCount(): Int {
        val existing = existingNames.value
        return _rows.value.filter { it.id in _selected.value }
            .count { it.name.trim().lowercase() in existing }
    }

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun isConnected(): Boolean = calendar.isConnected()
    fun connectIntent(): android.content.Intent = calendar.signInIntent()

    fun setRange(r: ImportRange) { _range.value = r; load() }
    fun setTarget(t: ImportTarget) { _target.value = t }

    fun load() {
        if (_loading.value) return
        viewModelScope.launch {
            _loading.update { true }
            _error.update { null }
            calendar.importEvents(
                _range.value.pastDays, _range.value.futureDays,
                calendarId = _calendarId.value
            )
                .onSuccess { events ->
                    val rows = events.mapNotNull { e ->
                        val start = parseIso(e.startIso) ?: return@mapNotNull null
                        val end = e.endIso?.let { parseIso(it) }
                        val multiDay = end != null && end.date > start.date
                        val parsed = com.bandary.data.util.CalendarTitleParser
                            .parse(e.title, e.location)
                        ImportRow(
                            event = e, start = start, multiDay = multiDay,
                            name = parsed.name.ifBlank { e.title },
                            artist = parsed.artist,
                            venue = parsed.venue,
                            city = parsed.city
                        )
                    }.sortedBy { it.start }
                    _rows.update { rows }
                    _selected.update { emptySet() }
                    _loaded.update { true }
                    if (rows.isEmpty()) {
                        val name = _calendars.value.firstOrNull { it.id == _calendarId.value }?.name
                        _message.update {
                            if (name != null) "「$name」在這個範圍沒有事件" else "這個範圍沒有日曆事件"
                        }
                    }
                }
                .onFailure { e ->
                    _rows.update { emptyList() }
                    _error.update { e.message ?: "讀取失敗" }
                    _message.update { e.message ?: "讀取失敗" }
                }
            _loading.update { false }
        }
    }

    fun toggle(id: String) {
        _selected.update { if (id in it) it - id else it + id }
    }

    /** 改一筆。只動有傳進來的欄位。 */
    fun updateRow(id: String, name: String? = null, artist: String? = null,
                  venue: String? = null, city: String? = null) {
        _rows.update { list ->
            list.map { r ->
                if (r.id != id) r
                else r.copy(
                    name = name ?: r.name,
                    artist = artist ?: r.artist,
                    venue = venue ?: r.venue,
                    city = city ?: r.city
                )
            }
        }
    }

    /**
     * 把同一組值套到所有勾選的筆數。共編日曆常常整份都是同一個場地或同一位藝人，
     * 一筆一筆改 30 場是不可能的事。留白的欄位不動。
     */
    fun applyToSelected(artist: String, venue: String, city: String): Int {
        val ids = _selected.value
        if (ids.isEmpty()) return 0
        _rows.update { list ->
            list.map { r ->
                if (r.id !in ids) r
                else r.copy(
                    artist = artist.ifBlank { r.artist },
                    venue = venue.ifBlank { r.venue },
                    city = city.ifBlank { r.city }
                )
            }
        }
        return ids.size
    }

    /** 勾選中還缺藝人或縣市的筆數。 */
    fun selectedIncompleteCount(): Int =
        _rows.value.count { it.id in _selected.value && it.needsAttention }

    fun selectAll() { _selected.update { _rows.value.map { r -> r.event.sourceEventId }.toSet() } }
    fun clearSelection() { _selected.update { emptySet() } }

    /** 需先登入才能匯入到活動；回傳 true 代表可繼續。 */
    fun ensureCanImport(): Boolean {
        if (_target.value == ImportTarget.FREE && !firestore.isSignedIn()) {
            _message.update { "投稿活動需先到「我的」用 Google 登入" }
            return false
        }
        return true
    }

    /** 匯入指定 sourceEventId 的活動（供逐筆決定重複與否後呼叫）。 */
    fun importIds(ids: Set<String>, onDone: () -> Unit) {
        if (!ensureCanImport()) return
        val chosen = _rows.value.filter { it.event.sourceEventId in ids }
        if (chosen.isEmpty()) { _message.update { "沒有要匯入的活動" }; onDone(); return }
        doImport(chosen, onDone)
    }

    fun importSelected(skipDuplicates: Boolean = false, onDone: () -> Unit) {
        val chosen0 = _rows.value.filter { it.event.sourceEventId in _selected.value }
        if (chosen0.isEmpty()) { _message.update { "請先勾選要匯入的活動" }; return }
        if (!ensureCanImport()) return
        // 略過重複：名稱已存在於目標端的就不匯入。
        val existing = existingNames.value
        val chosen = if (skipDuplicates) chosen0.filterNot { it.name.trim().lowercase() in existing } else chosen0
        if (chosen.isEmpty()) { _message.update { "所選皆為重複，已全部略過" }; onDone(); return }
        doImport(chosen, onDone)
    }

    private fun doImport(chosen: List<ImportRow>, onDone: () -> Unit) {
        viewModelScope.launch {
            _loading.update { true }
            when (_target.value) {
                ImportTarget.CONCERT -> {
                    chosen.forEach { r ->
                        val end = r.event.endIso?.let { parseIso(it) }
                        concertRepo.saveConcert(
                            Concert(
                                name = r.name.ifBlank { r.event.title },
                                // 藝人沒填就退回活動名——總比把整串標題塞進藝人欄好。
                                artistName = r.artist.ifBlank { r.name.ifBlank { r.event.title } },
                                venue = r.venue.ifBlank { r.event.location.orEmpty() },
                                city = r.city.takeIf { it.isNotBlank() },
                                dateTime = r.start,
                                endDate = if (r.multiDay) end?.date else null,
                                type = ConcertType.CONCERT,
                                notes = buildNote(r)
                            )
                        )
                    }
                    _message.update { "已匯入 ${chosen.size} 場到我的演唱會" }
                }
                ImportTarget.FREE -> {
                    val isAdmin = runCatching { settings.isAdmin.first() }.getOrDefault(false)
                    chosen.forEach { r ->
                        // 多日活動：保留結束日（之前只存起始日，多日會被壓成單日）。
                        val endD = if (r.multiDay) r.event.endIso?.let { parseIso(it) }?.date else null
                        val event = FreeEvent(
                            name = r.name.ifBlank { r.event.title },
                            artistName = r.artist,
                            venue = r.venue.ifBlank { r.event.location.orEmpty() },
                            city = r.city.takeIf { it.isNotBlank() },
                            dateTime = r.start,
                            endDate = endD?.takeIf { it > r.start.date }
                            // 活動不再把「從 Google 日曆匯入」寫進備註：那是來源資訊，不是活動內容，
                            // 而且活動的備註欄已從 UI 移除。多日資訊由 endDate 表示，不必再寫成文字。
                        )
                        if (isAdmin) freeRepo.add(event) else freeRepo.submit(event)
                    }
                    _message.update {
                        if (isAdmin) "已新增 ${chosen.size} 筆活動"
                        else "已投稿 ${chosen.size} 筆活動，待審核"
                    }
                }
            }
            _selected.update { emptySet() }
            _loading.update { false }
            onDone()
        }
    }

    private fun buildNote(r: ImportRow): String? {
        if (!r.multiDay) return com.bandary.domain.model.CALENDAR_IMPORT_NOTE
        val end = r.event.endIso?.let { parseIso(it) }
        return "${com.bandary.domain.model.CALENDAR_IMPORT_NOTE}・多日活動 ${r.start.date} ~ ${end?.date}"
    }

    fun clearMessage() = _message.update { null }

    private fun parseIso(iso: String): LocalDateTime? = runCatching {
        if (iso.contains("T")) {
            LocalDateTime.parse(iso.substringBefore("+").substringBefore("Z").take(19))
        } else {
            val d = LocalDate.parse(iso.take(10))
            LocalDateTime(d.year, d.month, d.dayOfMonth, 19, 0)
        }
    }.getOrNull()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportCalendarScreen(
    onBack: () -> Unit,
    initialTarget: ImportTarget = ImportTarget.CONCERT,
    viewModel: ImportCalendarViewModel = hiltViewModel()
) {
    LaunchedEffect(initialTarget) { viewModel.setTarget(initialTarget) }
    val rows by viewModel.rows.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val message by viewModel.message.collectAsState()
    val range by viewModel.range.collectAsState()
    val target by viewModel.target.collectAsState()
    val loaded by viewModel.loaded.collectAsState()
    val error by viewModel.error.collectAsState()
    val existingNames by viewModel.existingNames.collectAsState()
    val context = LocalContext.current
    // 逐筆詢問重複：待詢問佇列、已同意匯入的重複 id、無重複可直接匯入的 id。
    // 匯入前的編輯：單筆（一支筆）與批次（勾選後一次套用）
    var editingRow by remember { androidx.compose.runtime.mutableStateOf<ImportRow?>(null) }
    var showBatchEdit by remember { androidx.compose.runtime.mutableStateOf(false) }
    var dupQueue by remember { androidx.compose.runtime.mutableStateOf<List<ImportRow>>(emptyList()) }
    var approvedDupIds by remember { androidx.compose.runtime.mutableStateOf<Set<String>>(emptySet()) }
    var nonDupIds by remember { androidx.compose.runtime.mutableStateOf<Set<String>>(emptySet()) }

    val connectLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.load() }

    LaunchedEffect(Unit) { if (viewModel.isConnected()) viewModel.load() }
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("從 Google 日曆匯入", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            if (viewModel.isConnected() && rows.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { if (selected.size == rows.size) viewModel.clearSelection() else viewModel.selectAll() },
                        modifier = Modifier.weight(1f)
                    ) { Text(if (selected.size == rows.size) "全不選" else "全選") }
                    Button(
                        onClick = {
                            val selRows = rows.filter { it.event.sourceEventId in selected }
                            val dups = selRows.filter { it.event.title.trim().lowercase() in existingNames }
                            if (dups.isEmpty()) {
                                viewModel.importSelected(skipDuplicates = false) {}
                            } else {
                                nonDupIds = selRows.map { it.event.sourceEventId }.toSet() - dups.map { it.event.sourceEventId }.toSet()
                                approvedDupIds = emptySet()
                                dupQueue = dups
                            }
                        },
                        enabled = selected.isNotEmpty() && !loading,
                        modifier = Modifier.weight(1.4f)
                    ) { Text("匯入所選 ${selected.size}") }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (!viewModel.isConnected()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Outlined.CalendarMonth, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Text("尚未連結 Google 日曆", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "連結後即可挑選日曆上的活動匯入，含過往活動。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    runCatching { connectLauncher.launch(viewModel.connectIntent()) }
                        .onFailure { Toast.makeText(context, "無法開啟 Google 連結", Toast.LENGTH_SHORT).show() }
                }) { Text("連結 Google 日曆") }
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 範圍 + 目標
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 日曆來源：不只主日曆，別人分享給你的共用日曆也在這裡。
                val calendars by viewModel.calendars.collectAsState()
                val calendarId by viewModel.calendarId.collectAsState()
                androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.loadCalendars() }
                if (calendars.size > 1) {
                    Text("日曆", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(calendars, key = { it.id }) { c ->
                            FilterChip(
                                selected = calendarId == c.id,
                                onClick = { viewModel.setCalendar(c.id) },
                                label = {
                                    Text(
                                        if (c.primary) "我的日曆" else c.name,
                                        fontSize = 12.sp, maxLines = 1
                                    )
                                },
                                leadingIcon = if (!c.owned) ({
                                    Icon(Icons.Outlined.People, null, modifier = Modifier.size(14.dp))
                                }) else null,
                                shape = RoundedCornerShape(8.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = Color.White,
                                    selectedLeadingIconColor = Color.White
                                )
                            )
                        }
                    }
                    Text(
                        "別人分享給你的日曆會標上人形圖示。沒看到想要的？先在 Google 日曆 App 裡訂閱那份日曆。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("時間範圍", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImportRange.entries.forEach { r ->
                        FilterChip(
                            selected = range == r,
                            onClick = { viewModel.setRange(r) },
                            label = { Text(r.label, fontSize = 12.sp) },
                            shape = RoundedCornerShape(8.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
                Text("匯入到", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ImportTarget.entries.forEachIndexed { i, t ->
                        SegmentedButton(
                            selected = target == t,
                            onClick = { viewModel.setTarget(t) },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = ImportTarget.entries.size)
                        ) { Text(t.label) }
                    }
                }
            }

            when {
                loading && rows.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                }
                error != null && rows.isEmpty() -> Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("讀取失敗", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(error ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { viewModel.load() }) { Text("重試") }
                        Button(onClick = {
                            runCatching { connectLauncher.launch(viewModel.connectIntent()) }
                                .onFailure { Toast.makeText(context, "無法開啟 Google 連結", Toast.LENGTH_SHORT).show() }
                        }) { Text("重新連結 Google") }
                    }
                }
                loaded && rows.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(title = "沒有日曆事件", subtitle = "換個時間範圍試試")
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (selected.isNotEmpty()) {
                        item(key = "batch-bar") {
                            // 共編日曆常常整份都是同一個場地或同一位藝人，
                            // 一筆一筆改 30 場沒有人會做完。
                            val incomplete = viewModel.selectedIncompleteCount()
                            Surface(
                                onClick = { showBatchEdit = true },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(18.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "批次設定已勾選的 ${selected.size} 筆",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            if (incomplete > 0) "其中 $incomplete 筆還缺藝人或縣市"
                                            else "藝人、場地、縣市一次套用",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Icon(Icons.Outlined.ChevronRight, null, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                    items(rows, key = { it.event.sourceEventId }) { r ->
                        ImportEventRow(
                            row = r,
                            checked = r.event.sourceEventId in selected,
                            onToggle = { viewModel.toggle(r.event.sourceEventId) },
                            onEdit = { editingRow = r }
                        )
                    }
                }
            }
        }
    }

    // 單筆編輯
    editingRow?.let { row ->
        var name by remember(row.id) { androidx.compose.runtime.mutableStateOf(row.name) }
        var artist by remember(row.id) { androidx.compose.runtime.mutableStateOf(row.artist) }
        var venue by remember(row.id) { androidx.compose.runtime.mutableStateOf(row.venue) }
        var city by remember(row.id) { androidx.compose.runtime.mutableStateOf(row.city) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { editingRow = null },
            title = { Text("編輯這一筆") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "日曆原本寫的是「${row.event.title}」",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ImportField("活動名稱", name) { name = it }
                    ImportField("藝人", artist) { artist = it }
                    ImportField("場地", venue) { venue = it }
                    ImportField("縣市", city) { city = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateRow(row.id, name = name, artist = artist, venue = venue, city = city)
                    editingRow = null
                }) { Text("完成") }
            },
            dismissButton = { TextButton(onClick = { editingRow = null }) { Text("取消") } }
        )
    }

    // 批次編輯：留白的欄位不動
    if (showBatchEdit) {
        var artist by remember { androidx.compose.runtime.mutableStateOf("") }
        var venue by remember { androidx.compose.runtime.mutableStateOf("") }
        var city by remember { androidx.compose.runtime.mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showBatchEdit = false },
            title = { Text("批次設定 ${selected.size} 筆") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "留白的欄位不會動，只有填了的才會套用到勾選的每一筆。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ImportField("藝人", artist) { artist = it }
                    ImportField("場地", venue) { venue = it }
                    ImportField("縣市", city) { city = it }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = artist.isNotBlank() || venue.isNotBlank() || city.isNotBlank(),
                    onClick = {
                        val n = viewModel.applyToSelected(artist, venue, city)
                        showBatchEdit = false
                        Toast.makeText(context, "已套用到 $n 筆", Toast.LENGTH_SHORT).show()
                    }
                ) { Text("套用") }
            },
            dismissButton = { TextButton(onClick = { showBatchEdit = false }) { Text("取消") } }
        )
    }

    // 重複匯入：逐筆詢問。每一筆名稱重複的活動，分別問要不要匯入。
    val current = dupQueue.firstOrNull()
    if (current != null) {
        // 處理一筆決定；佇列清空後，把「無重複」+「同意的重複」一起匯入。
        val advance: (Boolean) -> Unit = { approve ->
            val nextApproved = if (approve) approvedDupIds + current.event.sourceEventId else approvedDupIds
            val nextQueue = dupQueue.drop(1)
            approvedDupIds = nextApproved
            dupQueue = nextQueue
            if (nextQueue.isEmpty()) {
                val toImport = nonDupIds + nextApproved
                if (toImport.isEmpty()) Toast.makeText(context, "沒有要匯入的活動", Toast.LENGTH_SHORT).show()
                else viewModel.importIds(toImport) {}
            }
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { /* 需明確選擇，不可點外面關閉 */ },
            title = { Text("名稱重複・剩 ${dupQueue.size} 筆") },
            text = {
                Text(
                    "「${current.event.title}」在「${target.label}」已經有了。要匯入這一筆嗎？",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Row {
                    // 一次處理完剩下的全部。250 筆的日曆裡有 176 筆重複，
                    // 逐筆點「略過這筆」要點 176 次——實機回報最痛的就是這個。
                    if (dupQueue.size > 1) {
                        TextButton(onClick = {
                            // 全部略過：重複的都不要，只匯入沒重複的那些。
                            dupQueue = emptyList()
                            if (nonDupIds.isEmpty()) {
                                Toast.makeText(context, "沒有要匯入的活動", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.importIds(nonDupIds + approvedDupIds) {}
                            }
                        }) { Text("全部略過") }
                        TextButton(onClick = {
                            // 全部匯入：剩下的重複全部收，之後可以在活動頁合併或刪除。
                            val rest = dupQueue.mapNotNull { it.event.sourceEventId }.toSet()
                            dupQueue = emptyList()
                            viewModel.importIds(nonDupIds + approvedDupIds + rest) {}
                        }) { Text("全部匯入") }
                    }
                    TextButton(onClick = { advance(true) }) { Text("匯入這筆") }
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        // 取消整批：不匯入任何東西
                        dupQueue = emptyList(); approvedDupIds = emptySet(); nonDupIds = emptySet()
                        Toast.makeText(context, "已取消匯入", Toast.LENGTH_SHORT).show()
                    }) { Text("取消") }
                    TextButton(onClick = { advance(false) }) { Text("略過這筆") }
                }
            }
        )
    }
}

@Composable
private fun ImportField(label: String, value: String, onChange: (String) -> Unit) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ImportEventRow(
    row: ImportRow,
    checked: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onToggle() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (checked) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    row.name.ifBlank { row.event.title },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        val s = row.start
                        append("${s.year}/${s.monthNumber}/${s.dayOfMonth}")
                        if (!row.event.allDay) append(" ${s.hour.toString().padStart(2, '0')}:${s.minute.toString().padStart(2, '0')}")
                        if (row.multiDay) append("  ·  多日")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                // 拆出來的藝人 / 場地 / 縣市。缺的用「？」標出來，匯入前一眼看得到要補什麼。
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Outlined.LocationOn, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        listOf(
                            row.artist.ifBlank { "藝人？" },
                            row.venue.ifBlank { row.event.location.orEmpty() }.ifBlank { "場地？" },
                            row.city.ifBlank { "縣市？" }
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (row.needsAttention) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Outlined.Edit, "編輯這一筆",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
