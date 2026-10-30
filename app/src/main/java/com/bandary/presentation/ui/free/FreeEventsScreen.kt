package com.bandary.presentation.ui.free

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import com.bandary.presentation.ui.components.isScrollingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.bandary.data.local.SettingsDataStore
import com.bandary.data.remote.FirestoreService
import com.bandary.data.remote.GoogleCalendarService
import com.bandary.data.repository.FreeEventsRepository
import com.bandary.domain.model.FreeEvent
import com.bandary.domain.model.isFromCrawler
import com.bandary.domain.model.SaleTime
import com.bandary.domain.model.display
import com.bandary.domain.model.castLabel
import com.bandary.domain.model.countryOrDefault
import com.bandary.domain.model.priceLabel
import com.bandary.domain.model.occurrenceDates
import com.bandary.presentation.ui.components.EmptyState
import com.bandary.presentation.ui.components.EventImage
import com.bandary.domain.model.images
import com.bandary.presentation.ui.components.SectionHeader
import com.bandary.presentation.ui.components.swipeMonth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

@HiltViewModel
class FreeEventsViewModel @Inject constructor(
    private val repo: FreeEventsRepository,
    private val firestore: FirestoreService,
    private val calendar: GoogleCalendarService,
    private val settings: SettingsDataStore
) : ViewModel() {

    // 共用藝人資料庫。投稿表單用它做選擇而不是純手打——
    // 手打的話同一個團會變成「滅火器」「滅火器 Fire EX.」「Fire EX.」三筆，
    // 之後追蹤、統計、排行全部對不起來。
    private val _artistDirectory =
        kotlinx.coroutines.flow.MutableStateFlow<List<com.bandary.domain.model.ArtistEntry>>(emptyList())
    val artistDirectory: StateFlow<List<com.bandary.domain.model.ArtistEntry>> = _artistDirectory

    private val _artistDirLoading = kotlinx.coroutines.flow.MutableStateFlow(false)
    val artistDirLoading: StateFlow<Boolean> = _artistDirLoading

    /** 只有真的要用到時才抓（開投稿表單那一刻），不要每次進活動頁都拉一次整份名單。 */
    fun loadArtistDirectory() {
        if (_artistDirectory.value.isNotEmpty() || _artistDirLoading.value) return
        viewModelScope.launch {
            _artistDirLoading.value = true
            firestore.fetchArtists().onSuccess { _artistDirectory.value = it }
            _artistDirLoading.value = false
        }
    }

    /** 資料庫裡沒有的團，當場加進去——加完其他人也搜得到。 */
    fun createArtist(name: String, onDone: (String) -> Unit) {
        viewModelScope.launch {
            firestore.addArtist(name)
                .onSuccess { entry ->
                    _artistDirectory.value = (_artistDirectory.value + entry)
                        .distinctBy { it.id.ifBlank { it.name } }
                    onDone(entry.name)
                }
                .onFailure { onDone(name.trim()) }   // 寫不進雲端也不要卡住表單
        }
    }

    val events: StateFlow<List<FreeEvent>> = repo.events
    val pending: StateFlow<List<FreeEvent>> = repo.pending
    val mine: StateFlow<List<FreeEvent>> = repo.mine
    val loading: StateFlow<Boolean> = repo.loading
    val message: StateFlow<String?> = repo.message
    val isAdmin: StateFlow<Boolean> =
        settings.isAdmin.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val interested: StateFlow<Set<String>> =
        settings.interestedEvents.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun toggleInterested(id: String) = viewModelScope.launch { settings.toggleInterestedEvent(id) }

    /** 不想看的活動。隱藏只影響自己這支手機，活動本身不受影響。 */
    val hidden: StateFlow<Set<String>> =
        settings.hiddenEvents.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /**
     * 已經「參加」（加進自己演唱會）的活動 id。參加時複製成一場新的演唱會、沒有記來源 id，
     * 所以用跟重複檢查（findDuplicateConcert）同一套規則比對：名稱相同、同一天。
     */
    val joined: StateFlow<Set<String>> =
        kotlinx.coroutines.flow.combine(repo.events, repo.myConcerts()) { evs, concerts ->
            val keys = concerts.map { it.name.trim().lowercase() to it.dateTime.date }.toSet()
            evs.filter { (it.name.trim().lowercase() to it.dateTime.date) in keys }.map { it.id }.toSet()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun toggleHidden(id: String) = viewModelScope.launch { settings.toggleHiddenEvent(id) }
    fun unhideAll() = viewModelScope.launch { settings.clearHiddenEvents() }

    fun isSignedIn(): Boolean = firestore.isSignedIn()
    fun calendarConnected(): Boolean = calendar.isConnected()

    init { refresh() }

    fun refresh() = viewModelScope.launch { repo.refresh() }

    /** isAdmin=true 直接新增並核准；否則投稿待審核。event 由對話框組好（含要保留的舊圖）；newImages＝這次新選的本機圖。 */
    fun submit(event: FreeEvent, newImages: List<android.net.Uri>, asAdmin: Boolean) {
        viewModelScope.launch {
            if (asAdmin) repo.add(event, imageLocalUris = newImages)
            else repo.submit(event, imageLocalUris = newImages)
        }
    }

    /** 管理員編輯活動內容（保留 id / 狀態 / 投稿者）。 */
    fun update(event: FreeEvent, newImages: List<android.net.Uri>) =
        viewModelScope.launch { repo.update(event, imageLocalUris = newImages) }

    /** 一般使用者提出修改：送出一份待審核的修改提案，不直接動原活動。 */
    fun proposeEdit(event: FreeEvent, newImages: List<android.net.Uri>) =
        viewModelScope.launch { repo.proposeEdit(event, imageLocalUris = newImages) }

    fun delete(id: String) = viewModelScope.launch { repo.delete(id) }
    /** 這個活動上還沒審核的修改提案。所有人都看得到，不是只有管理員。 */
    fun proposalsFor(eventId: String) = repo.proposalsFor(eventId)

    /** 某個活動的修改紀錄。點開才讀——大部分活動沒人改過，一進畫面就撈是白花流量。 */
    suspend fun revisionsOf(eventId: String) = repo.revisions(eventId)

    /** 回溯到某一筆修改之前的內容（僅管理員可用；規則層也擋）。 */
    fun restore(revision: com.bandary.domain.model.EventRevision) =
        viewModelScope.launch { repo.restore(revision) }

    /** 管理員批次刪除勾選的活動。已被其他人「加入」成自己演唱會的複本不受影響。 */
    fun deleteMany(ids: Collection<String>) = viewModelScope.launch { ids.forEach { repo.delete(it) } }
    /** 審核時把投稿併入已公開的相似活動。 */
    fun mergeInto(pendingId: String, targetId: String) = viewModelScope.launch { repo.mergeInto(pendingId, targetId) }
    /** 管理員手動合併勾選的活動。 */
    fun mergeEvents(ids: Collection<String>) = viewModelScope.launch { repo.mergeEvents(ids) }
    fun approve(id: String) = viewModelScope.launch { repo.approve(id) }
    fun reject(id: String, comment: String?) = viewModelScope.launch { repo.reject(id, comment) }

    /** 批次核准 / 駁回：循序處理、只重抓一次。逐筆 launch 會把畫面卡死（見 repo 註解）。 */
    fun approveAll(ids: List<String>) = viewModelScope.launch { repo.approveAll(ids) }
    fun rejectAll(ids: List<String>) = viewModelScope.launch { repo.rejectAll(ids) }
    fun join(event: FreeEvent) = viewModelScope.launch { repo.join(event) }
    /** 參加前先檢查是否重複；重複時回呼 onDuplicate，由 UI 決定要不要仍加入。 */
    fun checkAndJoin(event: FreeEvent, onDuplicate: (FreeEvent) -> Unit) = viewModelScope.launch {
        val dup = repo.findDuplicateConcert(event)
        if (dup != null) onDuplicate(event) else repo.join(event)
    }
    fun clearMessage() = repo.clearMessage()

    fun importFromCalendar() {
        viewModelScope.launch {
            val evs = calendar.importUpcomingEvents()
            if (evs.isEmpty()) { repo.refresh(); return@launch }
            // 重複（名稱 + 日期時間相同）＝覆蓋既有活動（更新名稱/地點/時間、保留藝人/票價等其他欄位），
            // 而不是跳過或再匯入一筆；沒有的才新增。
            val existingList = events.value + pending.value
            var changed = 0
            evs.forEach { ev ->
                val dt = parseIso(ev.startIso) ?: return@forEach
                val dup = existingList.firstOrNull {
                    it.name.trim().equals(ev.title.trim(), ignoreCase = true) && it.dateTime == dt
                }
                if (dup != null) {
                    repo.update(dup.copy(name = ev.title.trim(), venue = ev.location.orEmpty(), dateTime = dt))
                } else {
                    repo.add(FreeEvent(name = ev.title.trim(), dateTime = dt, venue = ev.location.orEmpty()))
                }
                changed++
            }
            if (changed == 0) repo.refresh()
        }
    }

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
fun FreeEventsScreen(
    onImportCalendar: () -> Unit = {},
    /** 追蹤中的藝人。入口放在這一頁，因為需要追蹤的時刻就是看到新場次的時候。 */
    onOpenFollowed: () -> Unit = {},
    viewModel: FreeEventsViewModel = hiltViewModel()
) {
    val events by viewModel.events.collectAsState()
    val pending by viewModel.pending.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val message by viewModel.message.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()
    // 任何登入者都能提出修改，但要經過審核；未登入沒有身分可標記提案者，所以不開放。
    val canPropose = viewModel.isSignedIn() && !isAdmin
    val mine by viewModel.mine.collectAsState()

    val context = LocalContext.current
    var showAdd by remember { mutableStateOf(false) }
    var showReview by remember { mutableStateOf(false) }
    var showMine by remember { mutableStateOf(false) }
    var showManage by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }   // 右上角「更多」選單（我的投稿 / 管理員功能收在這）
    var detailEvent by remember { mutableStateOf<FreeEvent?>(null) }   // 點卡片先預覽詳情
    var editEvent by remember { mutableStateOf<FreeEvent?>(null) }     // 管理員編輯活動內容
    var duplicateEvent by remember { mutableStateOf<FreeEvent?>(null) }  // 複製為新活動（帶內容開一張新的）
    var dupJoinEvent by remember { mutableStateOf<FreeEvent?>(null) }  // 加入時偵測到重複，詢問是否仍加入
    var historyEvent by remember { mutableStateOf<FreeEvent?>(null) }  // 看這場活動的修改紀錄與待審提案
    var viewMode by remember { mutableStateOf(1) } // 0 = 清單, 1 = 月曆（預設月曆）
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val calendarListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scrollScope = androidx.compose.runtime.rememberCoroutineScope()
    // 清單頁的自訂時間區間（開始 / 結束日期，含當日）；預設＝今天起一個月。
    val todayDate = remember { kotlinx.datetime.Clock.System.now().toLocalDateTime(TimeZone.of("Asia/Taipei")).date }
    var rangeStart by remember { mutableStateOf<LocalDate?>(todayDate) }
    var rangeEnd by remember { mutableStateOf<LocalDate?>(todayDate.plus(1, kotlinx.datetime.DateTimeUnit.MONTH)) }
    var showRangePicker by remember { mutableStateOf(false) }
    val rangeLabel = when {
        rangeStart != null && rangeEnd != null -> "${rangeStart!!.monthNumber}/${rangeStart!!.dayOfMonth} – ${rangeEnd!!.monthNumber}/${rangeEnd!!.dayOfMonth}"
        else -> "自訂時間區間"
    }
    val interested by viewModel.interested.collectAsState()
    val joined by viewModel.joined.collectAsState()
    val hidden by viewModel.hidden.collectAsState()
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    // 地區篩選：可複選（想同時看臺北＋新北很正常）。選項只列「資料裡真的有的縣市」，
    // 硬寫 22 個縣市會有一半永遠是 0 筆，選了只換來一片空白。
    var selectedCities by remember { mutableStateOf<Set<String>>(emptySet()) }
    // 票價：可複選多個區間（免費、500 以下、501–1000…），符合任一個就顯示。
    var selectedPrices by remember { mutableStateOf<Set<PriceBucket>>(emptySet()) }
    // 自訂票價上下限（自己輸入）。只填一端也行；跟上面的區間是「或」：符合任一個就顯示。
    var customPriceMin by remember { mutableStateOf("") }
    var customPriceMax by remember { mutableStateOf("") }
    val customMin = customPriceMin.trim().toDoubleOrNull()
    val customMax = customPriceMax.trim().toDoubleOrNull()
    val customPriceText = customPriceLabel(customMin, customMax)
    // 我的：有興趣 / 我參加的 / 我投稿的，可複選，符合任一個就顯示。
    var selectedMine by remember { mutableStateOf<Set<MineFilter>>(emptySet()) }
    // 國家：沒填一律當台灣。只列資料裡真的有的國家，不硬寫一排永遠 0 筆的選項。
    var selectedCountries by remember { mutableStateOf<Set<String>>(emptySet()) }
    // 隱藏的活動預設藏起來；打開「顯示已隱藏」才看得到，方便取消隱藏。
    var showHidden by remember { mutableStateOf(false) }
    // 完整的篩選條件收在面板裡；頁面上只留一行摘要。
    var showFilters by remember { mutableStateOf(false) }

    // 縣市選項：台灣縣市**全部列出**（依北→南固定順序，才找得到），後面再接資料裡的其他城市（海外場）。
    // 筆數只算目前時間區間內的活動。
    val cityOptions = remember(events, rangeStart, rangeEnd, todayDate, viewMode) {
        // 月曆模式會翻到任何月份，這時縣市筆數不該被清單的時間區間綁住。
        val customRange = viewMode == 0 && rangeStart != null && rangeEnd != null
        val counts = events.asSequence()
            .filter {
                if (customRange) it.dateTime.date >= rangeStart!! && it.dateTime.date <= rangeEnd!!
                else it.occurrenceDates().any { d -> d >= todayDate }
            }
            .mapNotNull(::eventCity)
            .groupingBy { it }.eachCount()
        val others = counts.keys.filter { it !in TAIWAN_CITIES }
            .sortedWith(compareByDescending<String> { counts[it] }.thenBy { it })
        (TAIWAN_CITIES + others).map { it to (counts[it] ?: 0) }
    }

    // 國家選項。沒填一律當台灣，所以只要資料全是台灣的，這排就不會出現（不佔版面）。
    val countryOptions = remember(events) {
        // 台灣一定列出來，就算目前資料全是台灣的也一樣，不然國家這排不會出現。
        (events.map { it.countryOrDefault() } + com.bandary.domain.model.DEFAULT_COUNTRY)
            .groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key to it.value }
    }

    val shownEvents = remember(
        events, rangeStart, rangeEnd, searchQuery, todayDate, selectedCities, selectedPrices, customMin, customMax,
        selectedMine, viewMode, selectedCountries, hidden, showHidden, interested, joined, mine
    ) {
        val mineIds = mine.map { it.id }.toSet()
        val q = searchQuery.trim()
        // 時間區間是「清單」專用的條件；月曆自己就是在選日期，套上去會讓別的月份整片空白。
        val customRange = viewMode == 0 && rangeStart != null && rangeEnd != null
        events.asSequence()
            // 沒設自訂區間時，預設只看「今天(含)以後」；搜尋時則不限日期（可搜到過去的活動）。
            .filter {
                when {
                    customRange -> it.dateTime.date >= rangeStart!! && it.dateTime.date <= rangeEnd!!
                    // 月曆會翻到過去的月份，不能只留今天以後的。
                    viewMode == 1 || q.isNotBlank() -> true
                    else -> it.occurrenceDates().any { d -> d >= todayDate }
                }
            }
            .filter { q.isBlank() || listOf(it.name, it.artistName, it.venue, it.city ?: "").any { s -> s.contains(q, ignoreCase = true) } }
            // 比對前先正規化，跟晶片上的選項用同一套寫法（臺北市 / 台北 → 臺北）。
            // 沒填縣市的活動從場地名稱推（見 eventCity），跟選項的筆數同一套規則。
            .filter { selectedCities.isEmpty() || eventCity(it) in selectedCities }
            // 票價區間可複選：符合任一格就留。免費的判定跟卡片上的免費標籤同一套規則。
            .filter { e ->
                val custom = customMin != null || customMax != null
                (selectedPrices.isEmpty() && !custom) ||
                    selectedPrices.any { it.matches(e) } ||
                    (custom && matchesCustomPrice(e, customMin, customMax))
            }
            .filter { e ->
                selectedMine.isEmpty() || selectedMine.any {
                    when (it) {
                        MineFilter.INTERESTED -> e.id in interested
                        MineFilter.JOINED -> e.id in joined
                        MineFilter.SUBMITTED -> e.id in mineIds
                    }
                }
            }
            .filter { selectedCountries.isEmpty() || it.countryOrDefault() in selectedCountries }
            // 隱藏的活動預設不出現；要取消隱藏就打開「顯示已隱藏」
            .filter { showHidden || it.id !in hidden }
            .toList()
    }

    // 目前套用了幾個條件、一句話怎麼說。兩者都只依賴畫面狀態，放在這裡就好。
    val activeFilterCount = listOf(
        viewMode == 0 && rangeStart != null,
        selectedPrices.isNotEmpty() || customPriceText != null, selectedMine.isNotEmpty(), showHidden,
        selectedCountries.isNotEmpty(), selectedCities.isNotEmpty()
    ).count { it }
    val filterSummary = remember(
        viewMode, rangeStart, rangeEnd, selectedPrices, customPriceText,
        selectedMine, showHidden, selectedCountries, selectedCities
    ) {
        buildList {
            if (viewMode == 0 && rangeStart != null) add(rangeLabel)
            PriceBucket.entries.filter { it in selectedPrices }.forEach { add(it.label) }
            customPriceText?.let { add(it) }
            MineFilter.entries.filter { it in selectedMine }.forEach { add(it.label) }
            if (showHidden) add("含已隱藏")
            addAll(selectedCountries.sorted())
            addAll(selectedCities.sorted())
        }.joinToString("・")
    }
    val clearFilters = {
        rangeStart = null; rangeEnd = null
        selectedCities = emptySet(); selectedCountries = emptySet()
        selectedPrices = emptySet(); customPriceMin = ""; customPriceMax = ""
        selectedMine = emptySet(); showHidden = false
    }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                TopAppBar(
                    title = { Text("活動", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1) },
                    actions = {
                        // 搜尋：依名稱 / 藝人 / 場地 / 縣市過濾。
                        IconButton(onClick = { searchActive = !searchActive; if (!searchActive) searchQuery = "" }) {
                            Icon(if (searchActive) Icons.Outlined.SearchOff else Icons.Outlined.Search, contentDescription = "搜尋")
                        }
                        // 篩選：條件全部收在面板裡，頁面上不再擺一整排晶片。
                        BadgedBox(badge = {
                            if (activeFilterCount > 0) Badge { Text("$activeFilterCount") }
                        }) {
                            IconButton(onClick = { showFilters = true }) {
                                Icon(Icons.Outlined.FilterList, contentDescription = "篩選")
                            }
                        }
                        // 從 Google 日曆勾選匯入
                        IconButton(onClick = onImportCalendar) {
                            Icon(Icons.Outlined.EventAvailable, contentDescription = "從日曆匯入")
                        }
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "重新整理")
                        }
                        // 更多：我的投稿 + 管理員功能收在這裡，讓標題不再被擠到換行。
                        val showMenuButton = viewModel.isSignedIn() || isAdmin
                        if (showMenuButton) {
                            // DropdownMenu 一定要跟它的按鈕包在同一個 Box 裡。
                            // 之前是直接丟在 actions 這一列當兄弟節點，彈出選單就會去對齊
                            // 那個「寬度 0 的空位」，結果飄到 ⋮ 左邊一大截（實機回報「方塊還是歪的」）。
                            Box {
                                BadgedBox(badge = { if (isAdmin && pending.isNotEmpty()) Badge { Text("${pending.size}") } }) {
                                    IconButton(onClick = { menuOpen = true }) {
                                        Icon(Icons.Outlined.MoreVert, contentDescription = "更多")
                                    }
                                }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    if (viewModel.isSignedIn()) {
                                        DropdownMenuItem(
                                            text = { Text("我的投稿") },
                                            leadingIcon = { Icon(Icons.Outlined.Inbox, null) },
                                            onClick = { menuOpen = false; showMine = true }
                                        )
                                    }
                                    if (isAdmin) {
                                        DropdownMenuItem(
                                            text = { Text(if (pending.isNotEmpty()) "審核投稿 · ${pending.size}" else "審核投稿") },
                                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.FactCheck, null) },
                                            onClick = { menuOpen = false; showReview = true }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("批次刪除活動") },
                                            leadingIcon = { Icon(Icons.Outlined.DeleteSweep, null) },
                                            onClick = { menuOpen = false; showManage = true }
                                        )
                                        // 「清空活動備註」已移除：備註重新成為正式欄位，
                                        // 一鍵清空會把使用者實際填的入場方式、注意事項全部抹掉。
                                    }
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    SegmentedButton(
                        selected = viewMode == 0,
                        onClick = { viewMode = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text("清單") }
                    SegmentedButton(
                        selected = viewMode == 1,
                        onClick = { viewMode = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text("月曆") }
                }
                // 篩選摘要：頁面上**只留一行**，說明目前篩了什麼。
                // 以前是一整排水平捲動的晶片，在月曆還沒出現前就吃掉一大塊版面，
                // 而且沒篩選時那排也還在（實機回報「很醜」）。
                // 完整的篩選條件收進右上角的篩選面板（FilterSheet）。
                if (activeFilterCount > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showFilters = true }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.FilterList, null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "　" + filterSummary,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { clearFilters() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text("清除", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                // 搜尋列
                if (searchActive) {
                    androidx.compose.material3.OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("搜尋活動 · 藝人 · 場地 · 縣市") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Outlined.Close, "清除") }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        },
        floatingActionButton = {
            // 往下捲時整組讓位，不再蓋住活動列上的按鈕；往上捲或回到頂部再出現（實機回報）。
            // 清單與月曆各有自己的捲動，兩個都要追，依目前分頁挑一個。
            val listScrollingUp = listState.isScrollingUp()
            val calendarScrollingUp = calendarListState.isScrollingUp()
            androidx.compose.animation.AnimatedVisibility(
                visible = if (viewMode == 0) listScrollingUp else calendarScrollingUp,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it / 2 },
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it / 2 }
            ) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (viewMode == 0) {
                    com.bandary.presentation.ui.components.ScrollTopFab(listState, scrollScope)
                }
                ExtendedFloatingActionButton(
                    onClick = {
                        if (viewModel.isSignedIn()) showAdd = true
                        else Toast.makeText(context, "請先到「我的」用 Google 登入", Toast.LENGTH_SHORT).show()
                    },
                    // 捲動清單時收縮成圓形 icon（spec §6.5.3）；回到頂部展開。
                    expanded = viewMode != 0 || listState.firstVisibleItemIndex == 0,
                    icon = { Icon(Icons.Outlined.Add, null) },
                    text = { Text(if (isAdmin) "新增" else "投稿活動", fontWeight = FontWeight.SemiBold) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                )
            }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        // 清單 ↔ 月曆 改用 HorizontalPager，可左右滑切換；分段按鈕與頁面雙向同步。
        // 月曆格子本身的「滑動換月」會 consume 掉手勢（見 swipeMonth），所以兩者不會打架：
        // 手指在格子上＝換月份，在其他地方＝換頁。
        val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = 1, pageCount = { 2 })
        LaunchedEffect(viewMode) { if (pagerState.currentPage != viewMode) pagerState.animateScrollToPage(viewMode) }
        LaunchedEffect(pagerState.currentPage) { viewMode = pagerState.currentPage }
        if (loading && events.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
            }
        } else androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.Top
        ) { page -> when {
            page == 1 -> FreeEventCalendar(
                // 篩選要對月曆也生效：以前這裡傳的是未篩選的原始清單。
                events = shownEvents,
                padding = padding,
                listState = calendarListState,
                isAdmin = isAdmin,
                canPropose = canPropose,
                interested = interested,
                onToggleInterested = { viewModel.toggleInterested(it.id) },
                onOpenDetail = { detailEvent = it },
                onEdit = { editEvent = it },
                onDelete = { viewModel.delete(it.id) }
            )
            events.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = "目前沒有活動",
                    subtitle = if (isAdmin) "點右下角新增，或從 Google 日曆匯入" else "點右下角投稿活動，審核通過後就會出現"
                )
            }
            shownEvents.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                // 講清楚是被哪個條件濾掉的。只說「沒有活動」會讓人以為資料本身是空的，
                // 但真正的原因通常是自己剛剛點的那個地區晶片。
                val active = buildList {
                    if (rangeStart != null) add("時間區間")
                    if (selectedCities.isNotEmpty()) add("地區（${selectedCities.joinToString("、")}）")
                    if (selectedPrices.isNotEmpty() || customPriceText != null) {
                        add("票價（${(selectedPrices.map { it.label } + listOfNotNull(customPriceText)).joinToString("、")}）")
                    }
                    if (selectedMine.isNotEmpty()) add("我的（${selectedMine.joinToString("、") { it.label }}）")
                    if (searchQuery.isNotBlank()) add("搜尋「${searchQuery.trim()}」")
                }
                EmptyState(
                    title = if (active.isEmpty()) "目前沒有活動" else "沒有符合條件的活動",
                    subtitle = if (active.isEmpty()) "之後有新活動會出現在這裡"
                    else "目前的條件：${active.joinToString("、")}。放寬或清除篩選看看。"
                )
            }
            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    // Scaffold 有 FAB 時 bottom 已含 FAB 高度，再加 96dp 會多出一整塊空白。
                    bottom = padding.calculateBottomPadding() + 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // ── 藝人入口 ──
                // 「追蹤中的藝人」原本只在統計頁、而且藏在「藝人」分類後面。
                // 那是回顧的地方；真正需要追蹤的時刻是**在這裡看到新場次**的時候。
                //
                // 「近期最多場次」是從眼前這份活動清單算的，不是跨使用者的人氣榜
                // ——後者要在伺服器端彙整 artist_fans，而且「別人追蹤誰」對
                // 「我現在該追誰」其實沒那麼有用。誰最近有一堆場次才是。
                item {
                    val hotArtists = remember(events) {
                        events.asSequence()
                            .flatMap { e ->
                                (listOf(e.artistName) + e.guestsByArtist.values.flatten()).asSequence()
                            }
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .groupingBy { it }.eachCount()
                            .entries.sortedByDescending { it.value }
                            .take(8)
                            .map { it.key to it.value }
                    }
                    if (hotArtists.isNotEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "近期最多場次",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = onOpenFollowed) {
                                    Text("追蹤中的藝人", fontSize = 13.sp)
                                }
                            }
                            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                            androidx.compose.foundation.layout.FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
                                hotArtists.forEach { (nm, n) ->
                                    // 這一頁沒有「藝人」這個篩選條件，但搜尋本來就比對藝人名。
                                    // 點晶片＝把名字丟進搜尋，再點一次清掉。
                                    // 與其為此新增一套篩選狀態，不如用已經在運作的那個。
                                    FilterPill("$nm $n", selected = searchQuery.trim() == nm) {
                                        searchQuery = if (searchQuery.trim() == nm) "" else nm
                                    }
                                }
                            }
                        }
                    }
                }
                items(shownEvents, key = { it.id }) { e ->
                    FreeEventCard(
                        event = e,
                        isAdmin = isAdmin,
                        canPropose = canPropose,
                        interested = e.id in interested,
                        onToggleInterested = { viewModel.toggleInterested(e.id) },
                        onOpenDetail = { detailEvent = e },
                        onEdit = { editEvent = e },
                        onDelete = { viewModel.delete(e.id) }
                    )
                }
            }
        } }
    }

    // 管理員：清空所有公開活動的備註。不可復原，所以先顯示影響筆數再確認。
    if (showAdd) {
        AddFreeEventDialog(
            isAdmin = isAdmin,
            onDismiss = { showAdd = false },
            onAdd = { event, newImages ->
                viewModel.submit(event, newImages, asAdmin = isAdmin)
                showAdd = false
            }
        )
    }

    // 編輯既有活動：管理員直接改；一般登入者送出修改提案，等管理員審核。
    editEvent?.let { original ->
        AddFreeEventDialog(
            isAdmin = isAdmin,
            initial = original,
            onDuplicate = { built -> editEvent = null; duplicateEvent = built },
            onDismiss = { editEvent = null },
            onAdd = { event, newImages ->
                val merged = event.copy(
                    id = original.id, status = original.status,
                    reviewComment = original.reviewComment,
                    createdBy = original.createdBy, createdAt = original.createdAt
                )
                if (isAdmin) viewModel.update(merged, newImages)
                else viewModel.proposeEdit(merged, newImages)
                editEvent = null
            }
        )
    }

    // 複製為新活動：帶著來源內容開一張「新的」，存成新的一筆（管理員直接新增、一般人送審），不動原活動。
    duplicateEvent?.let { src ->
        AddFreeEventDialog(
            isAdmin = isAdmin,
            initial = src,
            forceNew = true,
            onDismiss = { duplicateEvent = null },
            onAdd = { event, newImages ->
                viewModel.submit(event, newImages, asAdmin = isAdmin)
                duplicateEvent = null
            }
        )
    }

    if (showReview) {
        ReviewPendingDialog(
            pending = pending,
            approved = events,
            onApprove = { viewModel.approve(it) },
            onReject = { id, comment -> viewModel.reject(id, comment) },
            onApproveAll = { viewModel.approveAll(it) },
            onRejectAll = { viewModel.rejectAll(it) },
            onMergeInto = { pendingId, targetId -> viewModel.mergeInto(pendingId, targetId) },
            onDismiss = { showReview = false }
        )
    }

    if (showMine) {
        MySubmissionsDialog(mine = mine, onDelete = { viewModel.delete(it) }, onDismiss = { showMine = false })
    }

    if (showManage) {
        ManageEventsDialog(
            events = events,
            onDeleteSelected = { ids -> viewModel.deleteMany(ids) },
            onMergeSelected = { ids -> viewModel.mergeEvents(ids) },
            onDismiss = { showManage = false }
        )
    }

    detailEvent?.let { ev ->
        FreeEventDetailDialog(
            event = ev,
            interested = ev.id in interested,
            onToggleInterested = { viewModel.toggleInterested(ev.id) },
            isHidden = ev.id in hidden,
            onToggleHidden = { viewModel.toggleHidden(ev.id) },
            onJoin = {
                viewModel.checkAndJoin(ev) { dup -> dupJoinEvent = dup }
                detailEvent = null
            },
            onDuplicate = if (viewModel.isSignedIn()) ({ duplicateEvent = ev; detailEvent = null }) else null,
            // 任何登入的使用者都能改活動內容：管理員直接存檔，其他人送出修改提案待審核。
            onEdit = if (isAdmin || canPropose) ({ editEvent = ev; detailEvent = null }) else null,
            editLabel = if (isAdmin) "編輯活動" else "提出修改（待管理員審核）",
            proposalCount = viewModel.proposalsFor(ev.id).size,
            onHistory = { historyEvent = ev; detailEvent = null },
            onDismiss = { detailEvent = null }
        )
    }

    historyEvent?.let { ev ->
        EventHistoryDialog(
            event = ev,
            proposals = viewModel.proposalsFor(ev.id),
            loadRevisions = { viewModel.revisionsOf(ev.id) },
            canRestore = isAdmin,
            onRestore = { viewModel.restore(it); historyEvent = null },
            onDismiss = { historyEvent = null }
        )
    }

    if (showFilters) {
        EventFilterSheet(
            listMode = viewMode == 0,
            rangeLabel = rangeLabel,
            rangeActive = rangeStart != null,
            onPickRange = { showFilters = false; showRangePicker = true },
            onClearRange = { rangeStart = null; rangeEnd = null },
            selectedPrices = selectedPrices,
            onTogglePrice = { b ->
                selectedPrices = if (b in selectedPrices) selectedPrices - b else selectedPrices + b
            },
            customPriceMin = customPriceMin,
            onCustomPriceMin = { customPriceMin = it },
            customPriceMax = customPriceMax,
            onCustomPriceMax = { customPriceMax = it },
            mineCounts = mapOf(
                MineFilter.INTERESTED to interested.size,
                MineFilter.JOINED to joined.size,
                MineFilter.SUBMITTED to mine.size
            ),
            selectedMine = selectedMine,
            onToggleMine = { m ->
                selectedMine = if (m in selectedMine) selectedMine - m else selectedMine + m
            },
            hiddenCount = hidden.size,
            showHidden = showHidden,
            onShowHidden = { showHidden = it },
            onUnhideAll = { viewModel.unhideAll(); showHidden = false },
            countryOptions = countryOptions,
            selectedCountries = selectedCountries,
            onToggleCountry = { c ->
                selectedCountries = if (c in selectedCountries) selectedCountries - c else selectedCountries + c
            },
            cityOptions = cityOptions,
            selectedCities = selectedCities,
            onToggleCity = { c ->
                selectedCities = if (c in selectedCities) selectedCities - c else selectedCities + c
            },
            matchCount = shownEvents.size,
            onClearAll = { clearFilters() },
            onDismiss = { showFilters = false }
        )
    }

    dupJoinEvent?.let { ev ->
        AlertDialog(
            onDismissRequest = { dupJoinEvent = null },
            title = { Text("你的演唱會已有這場") },
            text = { Text("「${ev.name}」在同一天已存在於你的演唱會。要再加入一份嗎？") },
            confirmButton = {
                TextButton(onClick = { viewModel.join(ev); dupJoinEvent = null }) { Text("仍要加入") }
            },
            dismissButton = { TextButton(onClick = { dupJoinEvent = null }) { Text("不用了") } }
        )
    }

    if (showRangePicker) {
        val rangeState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = rangeStart?.toEpochMillisUtc(),
            initialSelectedEndDateMillis = rangeEnd?.toEpochMillisUtc()
        )
        DatePickerDialog(
            onDismissRequest = { showRangePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val s = rangeState.selectedStartDateMillis
                    val e = rangeState.selectedEndDateMillis
                    if (s != null) {
                        rangeStart = Instant.fromEpochMilliseconds(s).toLocalDateTime(TimeZone.UTC).date
                        rangeEnd = Instant.fromEpochMilliseconds(e ?: s).toLocalDateTime(TimeZone.UTC).date
                    }
                    showRangePicker = false
                }) { Text("套用") }
            },
            dismissButton = { TextButton(onClick = { showRangePicker = false }) { Text("取消") } }
        ) {
            DateRangePicker(state = rangeState, title = { Text("選擇時間區間", modifier = Modifier.padding(16.dp)) })
        }
    }
}

// ── 我的投稿（狀態 + 管理員意見 + 刪除）───────────────────────────
@Composable
private fun MySubmissionsDialog(mine: List<FreeEvent>, onDelete: (String) -> Unit, onDismiss: () -> Unit) {
    var confirmClearAll by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("我的投稿 · ${mine.size}") },
        text = {
            if (mine.isEmpty()) {
                Text("你還沒有投稿過活動", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(mine, key = { it.id }) { e ->
                        val statusColor = when (e.status) {
                            com.bandary.domain.model.FreeEventStatus.APPROVED -> MaterialTheme.colorScheme.primary
                            com.bandary.domain.model.FreeEventStatus.REJECTED -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Text(e.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    Text(com.bandary.domain.model.FreeEventStatus.label(e.status),
                                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = statusColor)
                                    IconButton(onClick = { onDelete(e.id) }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Outlined.Delete, "刪除投稿", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                    }
                                }
                                Text(
                                    "${e.dateTime.year}/${e.dateTime.monthNumber}/${e.dateTime.dayOfMonth} " +
                                        "${e.dateTime.hour.toString().padStart(2, '0')}:${e.dateTime.minute.toString().padStart(2, '0')}" +
                                        (e.endDate?.takeIf { it > e.dateTime.date }?.let { " – ${it.monthNumber}/${it.dayOfMonth}" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (e.status == com.bandary.domain.model.FreeEventStatus.REJECTED && !e.reviewComment.isNullOrBlank()) {
                                    Text("管理員意見：${e.reviewComment}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("關閉") } },
        dismissButton = {
            if (mine.isNotEmpty()) {
                TextButton(onClick = { confirmClearAll = true }) {
                    Text("全部刪除", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    )
    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("刪除全部投稿") },
            text = { Text("將刪除你投稿的 ${mine.size} 筆活動。已有其他人加入自己月曆的不受影響。") },
            confirmButton = {
                TextButton(onClick = {
                    mine.forEach { onDelete(it.id) }
                    confirmClearAll = false
                }) { Text("全部刪除", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            },
            dismissButton = { TextButton(onClick = { confirmClearAll = false }) { Text("取消") } }
        )
    }
}

// ── 管理員：批次勾選刪除活動 ─────────────────────────────────
@Composable
private fun ManageEventsDialog(
    events: List<FreeEvent>,
    onDeleteSelected: (Set<String>) -> Unit,
    onMergeSelected: (Set<String>) -> Unit = {},
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmMerge by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("管理活動 · 已選 ${selected.size}") },
        text = {
            if (events.isEmpty()) {
                Text("目前沒有已公開的活動", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "勾選要刪除或合併的活動。刪除只影響公開清單；已被使用者「加入」成自己演唱會的複本不受影響。",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // 同一場被投稿／匯入成好幾筆時用：勾兩筆以上才出現。
                    if (selected.size >= 2) {
                        TextButton(
                            onClick = { confirmMerge = true },
                            contentPadding = PaddingValues(horizontal = 0.dp)
                        ) { Text("合併所選 ${selected.size} 筆為同一場") }
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.heightIn(max = 360.dp)) {
                        items(events, key = { it.id }) { e ->
                            val checked = e.id in selected
                            Surface(
                                onClick = { selected = if (checked) selected - e.id else selected + e.id },
                                shape = RoundedCornerShape(10.dp),
                                color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    androidx.compose.material3.Checkbox(checked = checked, onCheckedChange = { selected = if (it) selected + e.id else selected - e.id })
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(e.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            "${e.dateTime.year}/${e.dateTime.monthNumber}/${e.dateTime.dayOfMonth}",
                                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (selected.isNotEmpty()) confirmDelete = true },
                enabled = selected.isNotEmpty()
            ) { Text("刪除所選", color = if (selected.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("關閉") } }
    )
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("刪除所選活動") },
            text = { Text("將從公開清單刪除 ${selected.size} 筆活動，無法復原。已加入自己演唱會的複本不受影響。") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSelected(selected)
                    confirmDelete = false
                    onDismiss()
                }) { Text("刪除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
        )
    }
    if (confirmMerge) {
        val primary = remember(selected, events) {
            com.bandary.domain.model.pickMergePrimary(events.filter { it.id in selected })
        }
        AlertDialog(
            onDismissRequest = { confirmMerge = false },
            title = { Text("合併所選活動") },
            text = {
                Text(
                    "會以資料最完整的「${primary?.name.orEmpty()}」為主，其他 ${selected.size - 1} 筆缺的欄位、" +
                        "藝人、開賣時間與圖片（最多 5 張）補進去後刪除，無法復原。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onMergeSelected(selected)
                    confirmMerge = false
                    onDismiss()
                }) { Text("合併") }
            },
            dismissButton = { TextButton(onClick = { confirmMerge = false }) { Text("取消") } }
        )
    }
}

// 活動詳情預覽：點卡片先看到更多細節，再決定要不要參加。
@Composable
private fun FreeEventDetailDialog(
    event: FreeEvent,
    interested: Boolean,
    onToggleInterested: () -> Unit,
    isHidden: Boolean = false,
    onToggleHidden: (() -> Unit)? = null,
    onJoin: () -> Unit,
    onDuplicate: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    editLabel: String = "提出修改",
    /** 這場還沒審核的修改提案數。>0 時按鈕要講出來，不然沒人知道已經有人提過了。 */
    proposalCount: Int = 0,
    onHistory: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val images = event.images
    var zoomIndex by remember { mutableStateOf<Int?>(null) }

    // 滿版檢視：活動圖常常是含時間表與卡司的長海報，裁成 180dp 縮圖等於看不到內容。
    // 多張時要能左右滑——以前這裡只吃單張，進到滿版就再也看不到第二張。
    zoomIndex?.let { start ->
        com.bandary.presentation.ui.components.SwipeableEventImageViewer(
            images = images,
            startIndex = start,
            onDismiss = { zoomIndex = null }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(event.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = {
            // 欄位間距壓到 6dp：這個對話框欄位多，8dp 累積起來會多出一大段空白。
            com.bandary.presentation.ui.components.DialogBody(spacing = 6.dp) {
                if (images.isNotEmpty()) {
                    // 張數說明放在圖「上面」：放在圖下面的話，第一張夠高就要捲動才看得到，
                    // 使用者會以為活動只有一張圖。
                    Text(
                        if (images.size > 1) "共 ${images.size} 張 · 左右滑看其他張，點圖看滿版" else "點圖看滿版",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (images.size == 1) {
                        // 單張：依原比例完整顯示（不裁切），點一下開滿版。
                        EventImage(
                            data = images.first(),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { zoomIndex = 0 }
                        )
                    } else {
                        // 多張：全部排成可左右滑的一列，每張都一樣大——以前是「第一張大圖 +
                        // 其餘 72dp 縮圖」，縮圖那排用 LazyRow 放在 verticalScroll 的 Column 裡，
                        // 父層高度無上限時量不出 viewport，第二張之後整排不顯示，看起來就只有一張。
                        // 這裡改用一般的 Row + horizontalScroll，量測行為單純、不會整排消失。
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            images.forEachIndexed { i, img ->
                                Box {
                                    EventImage(
                                        data = img,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.width(220.dp).heightIn(max = 300.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { zoomIndex = i }
                                    )
                                    androidx.compose.material3.Surface(
                                        color = Color.Black.copy(alpha = 0.55f),
                                        shape = RoundedCornerShape(50),
                                        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                                    ) {
                                        Text(
                                            "${i + 1}/${images.size}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Text(
                    "${event.dateTime.year}/${event.dateTime.monthNumber}/${event.dateTime.dayOfMonth} " +
                        "${event.dateTime.hour.toString().padStart(2, '0')}:${event.dateTime.minute.toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold
                )
                if (event.artistName.isNotBlank()) Text(
                    "藝人：" + event.castLabel(),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (event.venue.isNotBlank() || !event.city.isNullOrBlank()) {
                    Text(buildString {
                        event.city?.takeIf { it.isNotBlank() }?.let { append(it).append("　") }
                        append(event.venue)
                    }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // 票價：有區間就給區間（「NT$400–500」），其次才是自由輸入的說明與單一金額。
                run {
                    val p = event.ticketPrice
                    val label = event.priceLabel()
                        ?: if (p == null || p == 0.0) "免費" else "付費"
                    Text("票價：$label", style = MaterialTheme.typography.bodyMedium)
                }
                if (!event.note.isNullOrBlank()) {
                    Text(
                        event.note!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!event.sourceUrl.isNullOrBlank()) {
                    TextButton(onClick = {
                        runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(event.sourceUrl))) }
                    }, contentPadding = PaddingValues(0.dp)) { Text("活動連結  →") }
                }
                OutlinedButton(onClick = onToggleInterested, modifier = Modifier.fillMaxWidth()) {
                    Icon(if (interested) Icons.Filled.Star else Icons.Outlined.StarBorder, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(if (interested) "已標示有興趣要去" else "有興趣要去")
                }
                // 不想看這場：只藏自己這支手機的畫面，活動本身不受影響。
                onToggleHidden?.let { toggle ->
                    TextButton(onClick = toggle, contentPadding = PaddingValues(0.dp)) {
                        Icon(
                            if (isHidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                            null, modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            if (isHidden) "取消隱藏" else "不想看這場",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                // 活動內容有錯（時間改了、卡司加人）時，在詳情就能改，不必回到卡片的選單找。
                if (onEdit != null) {
                    OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(editLabel)
                    }
                }
                // 修改紀錄與待審提案。有人提過修改時把數字寫在按鈕上——
                // 不然使用者會重複提一模一樣的修改，管理員也得一筆筆讀重複的東西。
                if (onHistory != null) {
                    OutlinedButton(onClick = onHistory, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.History, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(
                            if (proposalCount > 0) "有 $proposalCount 個人提議修改" else "修改紀錄",
                            fontWeight = if (proposalCount > 0) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
                // 複製為新活動：用這場的內容開一張新的（例如同系列、換個日期再投稿一場）。
                if (onDuplicate != null) {
                    OutlinedButton(onClick = onDuplicate, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("複製為新活動")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onJoin) { Text("我要參加", fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("關閉") } }
    )
}

// 編輯風：無卡片、大留白、極細亮色線區隔，大標題（襯線）+ 極小資訊。
@Composable
private fun FreeEventCard(
    event: FreeEvent,
    isAdmin: Boolean,
    canPropose: Boolean = false,   // 已登入的一般使用者：可提出修改（送審）
    interested: Boolean = false,
    onToggleInterested: () -> Unit = {},
    onOpenDetail: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().clickable { onOpenDetail() }) {
        // 頂部極細亮線
        androidx.compose.material3.HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Column(modifier = Modifier.padding(vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 極小的日期標（單線 sans）
                Text(
                    "${event.dateTime.year}.${event.dateTime.monthNumber.toString().padStart(2, '0')}.${event.dateTime.dayOfMonth.toString().padStart(2, '0')}　" +
                        "${event.dateTime.hour.toString().padStart(2, '0')}:${event.dateTime.minute.toString().padStart(2, '0')}" +
                        (event.endDate?.takeIf { it > event.dateTime.date }?.let { "　– ${it.monthNumber.toString().padStart(2, '0')}.${it.dayOfMonth.toString().padStart(2, '0')}" } ?: ""),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                // 有興趣要去：明顯的填色 / 外框膠囊
                Surface(
                    onClick = onToggleInterested,
                    shape = RoundedCornerShape(50),
                    color = if (interested) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    border = if (interested) null else BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            if (interested) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            null, modifier = Modifier.size(15.dp),
                            tint = if (interested) Color.White else MaterialTheme.colorScheme.primary
                        )
                        Text(
                            if (interested) "有興趣要去" else "有興趣",
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            color = if (interested) Color.White else MaterialTheme.colorScheme.primary
                        )
                    }
                }
                // 編輯 / 刪除收進 ⋮（spec §6.5.2），不再佔卡片底部一整排。
                // 任何登入者都能提出修改（送審），刪除仍限管理員。
                if (isAdmin || canPropose) {
                    var menuOpen by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.MoreVert, "更多", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        androidx.compose.material3.DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(if (isAdmin) "編輯" else "提出修改") },
                                onClick = { menuOpen = false; onEdit() }
                            )
                            if (isAdmin) {
                                androidx.compose.material3.DropdownMenuItem(text = { Text("刪除") }, onClick = { menuOpen = false; onDelete() })
                            }
                        }
                    }
                }
            }
            // 標題（titleMedium，不用 display/襯線大字，spec §6.5.2）
            Text(
                event.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            // 藝人名單（多位以 / 分隔）
            if (event.artistName.isNotBlank()) {
                Text(
                    event.castLabel(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
            event.images.firstOrNull()?.let { cover ->
                EventImage(
                    data = cover,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(6.dp))
                )
            }
            if (event.venue.isNotBlank() || !event.city.isNullOrBlank()) {
                Text(
                    buildString {
                        event.city?.takeIf { it.isNotBlank() }?.let { append(it).append("　") }
                        append(event.venue)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            // 票價只分免費 / 付費：null 或 0＝免費；>0＝付費並顯示金額；<0＝付費但金額未定。
            val p = event.ticketPrice ?: 0.0
            val isFree = p == 0.0
            Text(
                // 有區間就給區間，其次才是自由輸入的說明（「早鳥 400／現場 500」）與數字。
                if (isFree) "免費" else (event.priceLabel() ?: "付費"),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (isFree) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            // 「查看詳情 →」已移除——整張卡可點進詳情（spec §6.5.2）。
        }
    }
}

// ── 月曆形式 ──────────────────────────────────────────────────
@Composable
private fun FreeEventCalendar(
    events: List<FreeEvent>,
    padding: PaddingValues,
    // 由外層傳入，外層的浮動按鈕才能依月曆頁的捲動方向讓位。
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    isAdmin: Boolean,
    canPropose: Boolean = false,
    interested: Set<String> = emptySet(),
    onToggleInterested: (FreeEvent) -> Unit = {},
    onOpenDetail: (FreeEvent) -> Unit,
    onEdit: (FreeEvent) -> Unit = {},
    onDelete: (FreeEvent) -> Unit
) {
    val tz = remember { TimeZone.of("Asia/Taipei") }
    val today = remember { kotlinx.datetime.Clock.System.now().toLocalDateTime(tz).date }
    var year by remember { mutableStateOf(today.year) }
    var month by remember { mutableStateOf(today.month) }
    var selected by remember { mutableStateOf(today) }
    var showMonthPicker by remember { mutableStateOf(false) }
    // 切月時同步把選取日移到新月份（今天在該月就選今天，否則選 1 號），下方清單才會跟著更新。
    fun syncSelected() { selected = if (year == today.year && month == today.month) today else LocalDate(year, month, 1) }
    fun prevMonth() { if (month == Month.JANUARY) { year -= 1; month = Month.DECEMBER } else month = Month.entries[month.ordinal - 1]; syncSelected() }
    fun nextMonth() { if (month == Month.DECEMBER) { year += 1; month = Month.JANUARY } else month = Month.entries[month.ordinal + 1]; syncSelected() }

    if (showMonthPicker) {
        com.bandary.presentation.ui.components.MonthYearPickerDialog(
            initialYear = year, initialMonth = month.value,
            onPick = { y, m -> year = y; month = Month.entries[m - 1]; syncSelected(); showMonthPicker = false },
            onDismiss = { showMonthPicker = false }
        )
    }

    // 多日活動：在其涵蓋的每一天都出現（不只開始日）。
    val eventsByDate = remember(events, year, month) {
        val map = mutableMapOf<LocalDate, MutableList<FreeEvent>>()
        events.forEach { e ->
            e.occurrenceDates()
                .filter { it.year == year && it.month == month }
                .forEach { d -> map.getOrPut(d) { mutableListOf() }.add(e) }
        }
        map.mapValues { entry -> entry.value.sortedBy { it.dateTime } }
    }
    val dayEvents = remember(events, selected) {
        events.filter { selected in it.occurrenceDates() }.sortedBy { it.dateTime }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 4.dp,
            // Scaffold 有 FAB 時 bottom 已含 FAB 高度，再加 96dp 會多出一整塊空白。
                    bottom = padding.calculateBottomPadding() + 16.dp
        )
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { prevMonth() }) { Icon(Icons.Outlined.ChevronLeft, "上個月") }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("$year 年 ${month.value} 月", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { showMonthPicker = true })
                    Surface(onClick = { year = today.year; month = today.month; selected = today },
                        shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
                        Text("今天", modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
                    }
                }
                IconButton(onClick = { nextMonth() }) { Icon(Icons.Outlined.ChevronRight, "下個月") }
            }
        }
        item {
            FreeEventGrid(
                year = year, month = month, today = today,
                eventsByDate = eventsByDate, selectedDate = selected,
                interested = interested,
                onSelect = { selected = it },
                modifier = Modifier.padding(horizontal = 8.dp)
                    .swipeMonth(onPrev = { prevMonth() }, onNext = { nextMonth() })
            )
        }
        item {
            SectionHeader("${month.value} 月 ${selected.dayOfMonth} 日", modifier = Modifier.padding(top = 12.dp))
        }
        if (dayEvents.isEmpty()) {
            item {
                Text(
                    "這天沒有活動",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(dayEvents, key = { it.id }) { e ->
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                    FreeEventCard(
                        event = e, isAdmin = isAdmin,
                        canPropose = canPropose,
                        interested = e.id in interested,
                        onToggleInterested = { onToggleInterested(e) },
                        onOpenDetail = { onOpenDetail(e) },
                        onEdit = { onEdit(e) },
                        onDelete = { onDelete(e) }
                    )
                }
            }
        }
    }
}

/**
 * 跨日活動色條的顏色：色相由活動 id 的雜湊決定，所以同一個活動在任何一週、任何一個月
 * 都是同一條顏色；不同活動之間也不容易撞色。
 *
 * 只用單一主題色 + 透明度的話，同一格裡兩三條線幾乎分不出是不同活動，所以改成不同色相。
 * 「要去」用飽和實色、「有興趣」降飽和加亮，兩者仍能區分，而且不是只靠透明度。
 */
private fun spanColor(id: String, going: Boolean): Color {
    val hue = ((id.hashCode() % 360) + 360) % 360
    return if (going) Color.hsl(hue.toFloat(), 0.62f, 0.45f)
    else Color.hsl(hue.toFloat(), 0.34f, 0.68f)
}

@Composable
private fun FreeEventGrid(
    year: Int,
    month: Month,
    today: LocalDate,
    eventsByDate: Map<LocalDate, List<FreeEvent>>,
    selectedDate: LocalDate,
    interested: Set<String>,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val isLeap = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
    val daysInMonth = month.length(isLeap)
    val firstDayOfWeek = LocalDate(year, month, 1).dayOfWeek.ordinal  // Mon=0
    val sundayStartOffset = (firstDayOfWeek + 1) % 7

    Column(modifier = modifier.padding(horizontal = 2.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("日", "一", "二", "三", "四", "五", "六").forEach { d ->
                Text(
                    d,
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
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
            // 這一週每一格對應的日期（落在本月外為 null）。
            val rowDates = (0 until 7).map { c ->
                val d = row * 7 + c - sundayStartOffset + 1
                if (d in 1..daysInMonth) LocalDate(year, month, d) else null
            }
            // 跨日活動 → 一條貫穿多格的連續色條（spec §6.5.1），不再每天各塞一個點。
            val spans = rowDates.filterNotNull()
                .flatMap { eventsByDate[it].orEmpty() }
                .distinctBy { it.id }
                .filter { it.occurrenceDates().size > 1 }
                .mapNotNull { e ->
                    val days = e.occurrenceDates().toSet()
                    val cols = rowDates.mapIndexedNotNull { i, d -> if (d != null && d in days) i else null }
                    if (cols.isEmpty()) null else Triple(e, cols.min(), cols.max())
                }
                .take(3)
            // 格子高度從 48dp 放大到 78dp：要放得下活動名稱。
            //
            // 原本是「日期 + 最多三個圓點」。圓點只回答「這天有沒有事」，
            // 回答不了「有什麼事」——而使用者翻月曆要找的就是後者，
            // 結果每一天都得點進去看才知道，等於月曆只是個有顏色的日期表。
            // 實機回報指名別的 App「比較清楚」，差別正是格子裡有沒有名字。
            //
            // 代價是整個月曆高了約 180dp（六週 × 30dp）。值得：
            // 多捲一點就能看到的東西，比少捲一點但每天都要點進去好。
            Box(modifier = Modifier.fillMaxWidth().height(78.dp)) {
            Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                for (col in 0 until 7) {
                    val dayNum = row * 7 + col - sundayStartOffset + 1
                    if (dayNum in 1..daysInMonth) {
                        val date = LocalDate(year, month, dayNum)
                        val isToday = date == today
                        val isSelected = date == selectedDate
                        val dayList = eventsByDate[date].orEmpty()
                        Surface(
                            onClick = { onSelect(date) },
                            modifier = Modifier.weight(1f).fillMaxHeight().padding(1.5.dp),
                            shape = RoundedCornerShape(6.dp),
                            color = when {
                                isSelected -> MaterialTheme.colorScheme.primaryContainer
                                isToday -> MaterialTheme.colorScheme.surfaceVariant
                                else -> MaterialTheme.colorScheme.surface
                            },
                            border = if (isToday) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp, vertical = 2.dp),
                                // 名稱要靠左才讀得順；日期自己置中（見下方的 fillMaxWidth）。
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    "$dayNum",
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(2.dp))
                                // 名稱標籤：每格最多兩條，超過的用「+N」收尾。
                                // 跨日活動不在這裡列——它們用下方那條貫穿多格的色條表示，
                                // 在每一天重複印同一個名字只是把格子塞滿。
                                //
                                // 一格大約 50dp 寬，放得下 3～4 個中文字。看起來很少，
                                // 但「認出自己已經知道的那場」只需要前兩個字；
                                // 真正要讀完整名稱的人本來就會點進去。
                                //
                                // **與參考的那個 App 不同的地方**：這裡的顏色不是隨機分配，
                                // 而是沿用 Bandary 既有的語意——有興趣的用主色實心色條、
                                // 其餘用淡色。翻月曆最常問的是「我要去的那場在哪天」，
                                // 顏色拿來回答那個問題，比拿來區分不同活動有用。
                                val dayItems = dayList.filter { it.occurrenceDates().size <= 1 }
                                dayItems.take(2).forEach { e ->
                                    val going = e.id in interested
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 1.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(2.dp).height(10.dp)
                                                .clip(RoundedCornerShape(1.dp))
                                                .background(
                                                    if (going) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.outlineVariant
                                                )
                                        )
                                        Spacer(Modifier.width(2.dp))
                                        Text(
                                            e.name,
                                            fontSize = 8.sp,
                                            lineHeight = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = if (going) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (going) MaterialTheme.colorScheme.onSurface
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (dayItems.size > 2) {
                                    Text(
                                        "+${dayItems.size - 2}",
                                        fontSize = 8.sp,
                                        lineHeight = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.align(Alignment.Start).padding(start = 4.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
            // 跨日活動的連續色條：疊在這一週格子的底部，跨格相連成一條（spec §6.5.1）。
            if (spans.isNotEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(bottom = 3.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    spans.forEach { (e, startCol, endCol) ->
                        val going = e.id in interested
                        // 每個活動一個固定色：色相由活動 id 決定，所以同一個活動跨週、跨月都是同一條顏色，
                        // 相鄰的不同活動也不會撞色。「要去」用飽和實色，只是有興趣的降飽和度區隔。
                        val barColor = spanColor(e.id, going)
                        Row(modifier = Modifier.fillMaxWidth().height(4.dp)) {
                            for (c in 0 until 7) {
                                Box(
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                        // 只在真正的起訖格內縮一點，中間格貼齊 → 視覺上連成一條。
                                        .padding(
                                            start = if (c == startCol) 4.dp else 0.dp,
                                            end = if (c == endCol) 4.dp else 0.dp
                                        )
                                        .background(if (c in startCol..endCol) barColor else Color.Transparent)
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

/** 縮圖右上角的移除鈕（活動圖多張時每張各一個）。 */
@Composable
private fun BoxScope.RemoveBadge(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(2.dp)
            .size(22.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Outlined.Close, "移除", tint = Color.White, modifier = Modifier.size(15.dp))
    }
}

/**
 * 從海報／截圖的 OCR 結果猜活動欄位：名稱抓「面積最大的一塊文字」（海報標題通常字最大、
 * 佔的方塊面積也最大），日期／時間／票價用印刷品常見格式抓。這是輔助填表，不求全對——
 * 猜錯或猜不到的欄位使用者自己在表單上調整即可，所以規則寧可保守、抓不到就留空。
 */
private object PosterParse {
    data class Guess(
        val name: String?,
        val date: kotlinx.datetime.LocalDate?,
        val time: LocalTime?,
        val price: Int?
    )

    private val dateWithYear = Regex("""(20\d{2})[./年](\d{1,2})[./月](\d{1,2})""")
    private val dateNoYear = Regex("""(?<!\d)(\d{1,2})[./月](\d{1,2})日?(?!\d)""")
    private val timePattern = Regex("""(\d{1,2})[:：](\d{2})""")
    private val pricePattern = Regex("""(?:NT\$|NTD|＄|\$|新台幣)\s*(\d{2,5})|(\d{2,5})\s*元""")

    fun guess(spots: List<com.bandary.data.remote.TextHotspot>, today: kotlinx.datetime.LocalDate): Guess {
        val lines = spots.map { it.text }

        var date: kotlinx.datetime.LocalDate? = null
        for (line in lines) {
            val m = dateWithYear.find(line) ?: continue
            val (y, mo, d) = m.destructured
            date = runCatching { kotlinx.datetime.LocalDate(y.toInt(), mo.toInt(), d.toInt()) }.getOrNull()
            if (date != null) break
        }
        if (date == null) {
            for (line in lines) {
                val m = dateNoYear.find(line) ?: continue
                val mo = m.groupValues[1].toIntOrNull(); val d = m.groupValues[2].toIntOrNull()
                if (mo !in 1..12 || d !in 1..31) continue
                // 海報通常沒印年份：先當今年，若已經過了就當明年（活動投稿只收未來場次）。
                var cand = runCatching { kotlinx.datetime.LocalDate(today.year, mo!!, d!!) }.getOrNull()
                if (cand != null && cand < today) {
                    cand = runCatching { kotlinx.datetime.LocalDate(today.year + 1, mo!!, d!!) }.getOrNull()
                }
                if (cand != null) { date = cand; break }
            }
        }

        // 時間：含 START／開演／入場 字樣那行優先（海報常見 "OPEN 18:30 START 19:30"，
        // 我們要的是演出開始，不是開放入場），抓不到才退回第一個 HH:MM。
        var time: LocalTime? = null
        val prioritized = lines.filter {
            it.contains("START", true) || it.contains("開演") || it.contains("入場")
        } + lines
        for (line in prioritized) {
            val m = timePattern.find(line) ?: continue
            val h = m.groupValues[1].toIntOrNull(); val mi = m.groupValues[2].toIntOrNull()
            if (h in 0..23 && mi in 0..59) { time = LocalTime(h!!, mi!!); break }
        }

        var price: Int? = null
        for (line in lines) {
            val m = pricePattern.find(line) ?: continue
            price = (m.groupValues[1].ifBlank { m.groupValues[2] }).toIntOrNull()
            if (price != null) break
        }

        val name = spots
            .filter { it.text.trim().length in 2..40 }
            .maxByOrNull { (it.right - it.left).toLong() * (it.bottom - it.top).toLong() }
            ?.text?.trim()

        return Guess(name, date, time, price)
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun AddFreeEventDialog(
    isAdmin: Boolean,
    initial: FreeEvent? = null,   // 帶入＝編輯模式（管理員）
    forceNew: Boolean = false,    // 帶入 initial 只為預填，但仍存成新的一筆（複製為新活動）
    onDuplicate: ((FreeEvent) -> Unit)? = null,  // 「複製為新活動」：把目前表單內容送出去開一張新的
    onDismiss: () -> Unit,
    onAdd: (event: FreeEvent, newImages: List<android.net.Uri>) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    // 藝人名單（可多位）：一位一列，每位可各自加嘉賓 feat.（與演唱會表單一致）。
    // 初始從既有 artistName 以 / 拆開；儲存時再以 " / " 合併回單一欄位。
    val artists = remember {
        androidx.compose.runtime.mutableStateListOf<String>().apply {
            addAll(initial?.artistName?.split("/", "、", "／")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList())
            if (isEmpty()) add("")
        }
    }
    // 嘉賓 feat.（客串）：主藝人 → 該藝人的嘉賓（用「、」串成一個字串方便編輯）。
    var showArtistPicker by remember { mutableStateOf(false) }
    var featByArtist by remember {
        mutableStateOf(initial?.guestsByArtist.orEmpty().mapValues { (_, v) -> v.joinToString("、") })
    }
    var venue by remember { mutableStateOf(initial?.venue ?: "") }
    var city by remember { mutableStateOf(initial?.city ?: "") }
    // 國家：留空＝台灣。只有收海外場（日韓、Billboard Live 這種）才需要填。
    var country by remember { mutableStateOf(initial?.country ?: "") }
    // 備註欄位已從 UI 移除（資料模型保留）；儲存一律寫 null，舊資料經編輯即清空。
    var cityMenu by remember { mutableStateOf(false) }
    // 票價只分免費 / 付費：選付費才出現金額欄。
    var paid by remember { mutableStateOf((initial?.ticketPrice ?: 0.0) > 0) }
    var sourceUrl by remember { mutableStateOf(initial?.sourceUrl ?: "") }
    // 複製（forceNew）不帶原圖，與網頁一致。
    // 圖片改成「一份有順序的清單」，舊圖與這次新選的圖混在一起排。
    // 第一張就是封面（存檔時 imageUri 取 imageUris.first()），所以順序是有意義的、要能調。
    var newImages by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    // 清單元素：既有圖存 Base64 字串，新圖存 __NEW_n__ 佔位符（n＝在 newImages 裡的索引）。
    // 存檔時由 FreeEventsRepository.save() 把佔位符換成編碼後的圖，順序照這份清單。
    var imageOrder by remember {
        mutableStateOf(if (initial != null && !forceNew) initial.images else emptyList())
    }
    val today = remember { kotlinx.datetime.Clock.System.now().toLocalDateTime(TimeZone.of("Asia/Taipei")).date }
    var date by remember { mutableStateOf(initial?.dateTime?.date ?: today) }
    var time by remember { mutableStateOf(initial?.dateTime?.time ?: LocalTime(19, 0)) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    // 活動可含付費 + 跨日：結束日 / 結束時間 / 票價 / 售票連結。
    var endDate by remember { mutableStateOf(initial?.endDate) }
    var endTime by remember { mutableStateOf(initial?.endTime) }
    var showEndDate by remember { mutableStateOf(false) }
    var showEndTime by remember { mutableStateOf(false) }
    // 票價文字：優先沿用 priceText（自由輸入），沒有才退回舊的數字欄位。
    var ticketPriceText by remember {
        mutableStateOf(
            initial?.priceText?.takeIf { it.isNotBlank() }
                ?: initial?.ticketPrice?.takeIf { it > 0 }?.toInt()?.toString().orEmpty()
        )
    }
    var ticketUrl by remember { mutableStateOf(initial?.ticketUrl ?: "") }
    // 多組開賣（搶票）時間，格式與演唱會同一套；選擇器共用，靠 saleEditIndex 指定改哪一組。
    var saleTimes by remember { mutableStateOf(initial?.saleTimes.orEmpty()) }
    var saleEditIndex by remember { mutableStateOf(-1) }
    var showSaleDate by remember { mutableStateOf(false) }
    var showSaleTime by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf(initial?.note ?: "") }

    // 一次可選多張；連同已保留的圖不超過 5 張，超過就只取前面幾張。
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(5)
    ) { uris ->
        if (uris.isNotEmpty()) {
            val room = (5 - imageOrder.size).coerceAtLeast(0)
            val added = uris.take(room)
            // newImages 與 imageOrder 裡的佔位符要同步：佔位符存的是「在 newImages 裡的索引」，
            // 所以新圖一律往 newImages 尾端加，索引才不會位移到別張圖上。
            val base = newImages.size
            newImages = newImages + added
            imageOrder = imageOrder + added.indices.map {
                com.bandary.data.repository.FreeEventsRepository.newImagePlaceholder(base + it)
            }
        }
    }

    // 掃卡司圖：選圖 → OCR → 挑要加入的名字。辨識結果先用同一套分隔規則拆過，
    // 海報上「A｜B｜C」擠在一行時才不會變成一個超長的假名字。
    val ocrScope = rememberCoroutineScope()
    val ocrCtx = androidx.compose.ui.platform.LocalContext.current
    val ocrService = remember { com.bandary.data.remote.TimetableOcrService(ocrCtx) }
    var castLines by remember { mutableStateOf<List<String>?>(null) }
    var castRunning by remember { mutableStateOf(false) }
    // 先裁切再辨識：卡司圖常常整張海報只有一小塊是名單。
    // 共用藝人資料庫的選擇器。跟活動頁同一個 ViewModel 實例，
    // 所以名單抓一次就好，不會因為開了對話框又重抓。
    val pickerVm: FreeEventsViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val artistDirectory by pickerVm.artistDirectory.collectAsState()
    val artistDirLoading by pickerVm.artistDirLoading.collectAsState()
    androidx.compose.runtime.LaunchedEffect(Unit) { pickerVm.loadArtistDirectory() }

    if (showArtistPicker) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showArtistPicker = false },
            title = { Text("從藝人資料庫選") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "大家共用同一份名單。挑現成的，別人追蹤這個團時才對得起來；" +
                            "名單裡沒有的可以直接新增。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    com.bandary.presentation.ui.components.ArtistPicker(
                        directory = artistDirectory,
                        loading = artistDirLoading,
                        label = "搜尋藝人",
                        placeholder = "打幾個字，沒有的也可以直接加",
                        alreadyPicked = artists.map { it.trim() }.filter { it.isNotBlank() }.toSet(),
                        onPick = { picked ->
                            // 填進第一個還空著的欄位；沒有空的就加一列。
                            val slot = artists.indexOfFirst { it.isBlank() }
                            if (slot >= 0) artists[slot] = picked else artists.add(picked)
                            showArtistPicker = false
                        },
                        onCreate = if (pickerVm.isSignedIn()) { typed ->
                            pickerVm.createArtist(typed) { created ->
                                val slot = artists.indexOfFirst { it.isBlank() }
                                if (slot >= 0) artists[slot] = created else artists.add(created)
                            }
                            showArtistPicker = false
                        } else null
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showArtistPicker = false }) { Text("關閉") }
            }
        )
    }

    val castPicker = com.bandary.presentation.ui.components.rememberPickAndCrop { uri ->
        run {
            castRunning = true
            castLines = emptyList()
            ocrScope.launch {
                val res = runCatching { ocrService.recognize(uri.toString()) }.getOrNull()
                castLines = res?.spots.orEmpty()
                    .flatMap { com.bandary.domain.model.splitArtistNames(it.text) }
                    .map { it.trim() }
                    .filter { it.isNotBlank() && it.length <= 40 }
                    .distinct()
                castRunning = false
            }
        }
    }

    // 掃海報截圖：選圖（IG／Threads 貼文截圖也可以） → OCR → 猜名稱/日期/時間/票價直接
    // 填回表單欄位，使用者看一眼調整再送出。不碰任何平台的伺服器，純粹處理使用者已經
    // 截好的圖，所以沒有 IG／Threads 的 ToS 或反爬蟲問題——這是輔助填表，不是爬蟲。
    var posterScanning by remember { mutableStateOf(false) }
    // 先裁切再辨識：IG／Threads 的截圖上下常有一堆介面元素，裁掉命中率差很多。
    val posterPicker = com.bandary.presentation.ui.components.rememberPickAndCrop { uri ->
        run {
            posterScanning = true
            ocrScope.launch {
                val res = runCatching { ocrService.recognize(uri.toString()) }.getOrNull()
                if (res == null || res.spots.isEmpty()) {
                    Toast.makeText(ocrCtx, "沒有辨識到文字，圖片太模糊或字太小時容易失敗", Toast.LENGTH_SHORT).show()
                } else {
                    val g = PosterParse.guess(res.spots, today)
                    val filled = mutableListOf<String>()
                    if (g.name != null && name.isBlank()) { name = g.name; filled += "名稱" }
                    if (g.date != null) { date = g.date; filled += "日期" }
                    if (g.time != null) { time = g.time; filled += "時間" }
                    if (g.price != null) { paid = true; ticketPriceText = g.price.toString(); filled += "票價" }
                    Toast.makeText(
                        ocrCtx,
                        if (filled.isEmpty()) "辨識到文字，但沒抓到看得懂的日期/時間/票價，你可以對照截圖自己填"
                        else "已幫你填入：${filled.joinToString("、")}，記得檢查一下對不對",
                        Toast.LENGTH_LONG
                    ).show()
                }
                posterScanning = false
            }
        }
    }

    // 掃描結果挑選：點一個名字就加入，已加入的不再出現。
    if (castRunning || castLines != null) {
        AlertDialog(
            onDismissRequest = { if (!castRunning) castLines = null },
            title = { Text("掃卡司圖") },
            text = {
                val remaining = castLines.orEmpty().filter { l -> artists.none { it.equals(l, true) } }
                when {
                    castRunning -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(20.dp), strokeWidth = 2.dp
                        )
                        Text("辨識中…", style = MaterialTheme.typography.bodyMedium)
                    }
                    remaining.isEmpty() -> Text(
                        "沒有辨識到可用的文字。圖片太模糊或字太小時容易失敗，可以改試裁切過的卡司區塊。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("點名字加入，可連續點多個。", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            remaining.forEach { line ->
                                androidx.compose.material3.SuggestionChip(
                                    onClick = { if (artists.none { it.equals(line, true) }) artists.add(line) },
                                    label = { Text(line, fontSize = 12.sp) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { castLines = null }, enabled = !castRunning) { Text("完成") }
            }
        )
    }

    // editing = 真的在改既有活動；forceNew（複製）雖然帶了 initial，但要當成新的一筆。
    val editing = initial != null && !forceNew
    fun buildEvent(): FreeEvent {
        val artist = artists.map { it.trim() }.filter { it.isNotBlank() }.joinToString(" / ")
        return FreeEvent(
            name = name.trim(),
            artistName = artist,
            guestsByArtist = artists.map { it.trim() }.filter { it.isNotBlank() }.associateWith { host ->
                featByArtist[host].orEmpty().split('、', ',', '，', '/', '／', '｜', '|', ';', '；')
                    .map { it.trim() }.filter { it.isNotBlank() }.distinct()
            }.filterValues { it.isNotEmpty() },
            venue = venue.trim(),
            city = city.trim().ifBlank { null },
            dateTime = LocalDateTime(date, time),
            endDate = endDate?.takeIf { it > date },
            endTime = endTime,
            // 免費＝null；付費但沒填金額＝以 -1 表示「要錢但金額未知」，顯示端只看有沒有 > 0。
            country = country.trim().ifBlank { null },
            ticketPrice = if (!paid) null else firstNumberIn(ticketPriceText)?.takeIf { it > 0 } ?: -1.0,
            priceText = if (paid) ticketPriceText.trim().ifBlank { null } else null,
            // 票價區間直接從使用者打的字裡抓數字，不另外開兩個欄位——
            // 大家本來就會寫成「早鳥 400／現場 500」，多要兩格反而沒人填。
            minPrice = if (paid) allNumbersIn(ticketPriceText).minOrNull() else null,
            maxPrice = if (paid) allNumbersIn(ticketPriceText).maxOrNull() else null,
            ticketUrl = ticketUrl.trim().ifBlank { null },
            saleTimes = saleTimes.sortedBy { it.at },
            note = note.trim().ifBlank { null },
            sourceUrl = sourceUrl.trim().ifBlank { null },
            // 有順序的完整清單（既有圖 + __NEW_n__ 佔位符），佔位符由 repository 換成編碼後的圖。
            imageUris = imageOrder,
            // 封面在 repository 存檔時會依最終的 imageUris.first() 重設，
            // 這裡只在「第一張本來就是既有圖」時先給值；是新圖就留 null 等 repository 填。
            imageUri = imageOrder.firstOrNull()
                ?.takeUnless { it.startsWith(com.bandary.data.repository.FreeEventsRepository.NEW_IMAGE_PREFIX) }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    forceNew -> "複製為新活動"
                    editing && !isAdmin -> "提出修改"
                    editing -> "編輯活動"
                    isAdmin -> "新增活動"
                    else -> "投稿活動"
                }
            )
        },
        text = {
            com.bandary.presentation.ui.components.DialogBody(spacing = 8.dp) {
                if (!isAdmin) {
                    Text(
                        if (editing)
                            "修改會送出審核，通過後才會套用到這個活動。原活動在這之前維持原樣。"
                        else "投稿後需經管理員審核，通過後才會公開顯示。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // 從 IG／Threads 貼文截圖（或任何海報圖）自動抓名稱/日期/時間/票價填表——
                // 我們不去碰 IG／Threads 本身（那邊沒有公開 API 也擋爬蟲），只處理使用者
                // 已經截好的圖，所以是「輔助填表」而不是「爬蟲」。放在表單最上面，跟新增
                // 演唱會的「掃描票券自動填寫」是同一個位置邏輯：最先看到、最先用。
                OutlinedButton(
                    onClick = { posterPicker() },
                    enabled = !posterScanning,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (posterScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(6.dp)); Text("辨識中…", fontSize = 13.sp)
                    } else {
                        Icon(Icons.Outlined.Image, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp)); Text("掃描海報／截圖自動填寫", fontSize = 13.sp)
                    }
                }
                Text(
                    "IG、Threads 的貼文截圖也可以：只是幫忙辨識圖上的文字填欄位，不是自動爬蟲，抓錯記得手動改。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("活動名稱 *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                // 藝人 / 樂團：一位一列，每位可各自加「＋嘉賓 feat.」（與演唱會表單一致）。
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("藝人 / 樂團", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    artists.forEachIndexed { idx, a ->
                        val key = a.trim()
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedTextField(
                                    value = a,
                                    onValueChange = { v -> artists[idx] = v },
                                    placeholder = { Text(if (idx == 0) "主要藝人" else "藝人 / 樂團") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        if (key.isNotBlank()) featByArtist = featByArtist - key
                                        artists.removeAt(idx); if (artists.isEmpty()) artists.add("")
                                    },
                                    enabled = artists.size > 1
                                ) { Icon(Icons.Outlined.Close, "移除", modifier = Modifier.size(20.dp)) }
                            }
                            // 這位藝人的嘉賓 feat.（可留空；多位用、分隔）。
                            var featOpen by remember(key) { mutableStateOf(featByArtist[key].orEmpty().isNotBlank()) }
                            if (featOpen) {
                                OutlinedTextField(
                                    value = featByArtist[key].orEmpty(),
                                    onValueChange = { v ->
                                        featByArtist = if (key.isBlank()) featByArtist
                                            else if (v.isBlank()) featByArtist - key else featByArtist + (key to v)
                                    },
                                    placeholder = { Text("嘉賓，多位用「、」分隔（例：宇宙人、傷心欲絕）", fontSize = 12.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp)
                                )
                            } else {
                                TextButton(onClick = { featOpen = true }, contentPadding = PaddingValues(horizontal = 12.dp)) {
                                    Icon(Icons.Outlined.Add, null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.size(2.dp)); Text("嘉賓 feat.", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { artists.add("") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.size(4.dp)); Text("手動輸入", fontSize = 13.sp)
                        }
                        // 從共用資料庫挑，而不是每個人各自手打。同一個團被打成
                        // 「滅火器」「滅火器 Fire EX.」「Fire EX.」三種寫法之後，
                        // 追蹤、統計、排行就全部對不起來了。
                        OutlinedButton(onClick = { showArtistPicker = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Icon(Icons.Outlined.Search, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.size(4.dp)); Text("找資料庫", fontSize = 13.sp)
                        }
                        OutlinedButton(onClick = { castPicker() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Icon(Icons.Outlined.AddPhotoAlternate, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.size(4.dp)); Text("掃圖", fontSize = 13.sp)
                        }
                    }
                }
                                OutlinedTextField(value = venue, onValueChange = { venue = it }, label = { Text("地點") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                // 縣市改用選單（與新增演唱會一致），避免自由輸入造成「台北 / 臺北」這種同義不同字。
                Box {
                    // 可手打也可選：海外場、非行政區的地名（如「宜蘭冬山」）都不在清單裡，
                    // 只給選單就填不進去。打字時同步過濾選單建議。
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it; cityMenu = it.isNotBlank() },
                        label = { Text("縣市・可直接輸入") }, singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { cityMenu = !cityMenu }) {
                                Icon(Icons.Outlined.ArrowDropDown, "選擇縣市")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    // 22 個縣市用預設高度的 DropdownMenuItem（各 48dp）會拉出將近整個螢幕高。
                    // 限制選單總高 + 把每一列壓到 36dp，一次看得到七八個又不會蓋掉整頁。
                    // 打字時只列出符合的建議；沒有符合的就不擋著畫面。
                    val citySuggestions = com.bandary.presentation.ui.shows.TAIWAN_CITIES
                        .filter { city.isBlank() || it.contains(city.trim(), ignoreCase = true) }
                    DropdownMenu(
                        expanded = cityMenu && citySuggestions.isNotEmpty(),
                        onDismissRequest = { cityMenu = false },
                        // 焦點留在輸入框，否則一打字選單就把鍵盤輸入搶走。
                        properties = androidx.compose.ui.window.PopupProperties(focusable = false),
                        modifier = Modifier.heightIn(max = 280.dp)
                    ) {
                        citySuggestions.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c, fontSize = 14.sp) },
                                onClick = { city = c; cityMenu = false },
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                modifier = Modifier.height(36.dp)
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = country,
                    onValueChange = { country = it },
                    label = { Text("國家") },
                    placeholder = { Text("留空＝台灣") },
                    supportingText = {
                        Text(
                            "只有海外場需要填，例如「日本」「韓國」。填了之後活動頁就能用國家篩選。",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                // 日期 / 時間：各自加小標題，避免只有兩顆按鈕看不出是什麼、也不再擠成一團。
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        Text("日期", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text(
                                "${date.year}/${date.monthNumber.toString().padStart(2, '0')}/${date.dayOfMonth.toString().padStart(2, '0')}",
                                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        Text("時間", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = { showTime = true }, modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text(
                                "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}",
                                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                // 結束日期 / 時間（可跨日；留空＝當天）
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        Text("結束日期", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = { showEndDate = true }, modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text(endDate?.let { "${it.year}/${it.monthNumber.toString().padStart(2, '0')}/${it.dayOfMonth.toString().padStart(2, '0')}" } ?: "同一天",
                                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        Text("結束時間", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = { showEndTime = true }, modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text(endTime?.let { "${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}" } ?: "—",
                                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (endDate != null || endTime != null) {
                    TextButton(onClick = { endDate = null; endTime = null }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text("清除結束日期 / 時間", fontSize = 12.sp)
                    }
                }
                // 票價只有免費 / 付費兩種；選付費才需要填金額。
                Text("票價", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !paid,
                        onClick = { paid = false; ticketPriceText = "" },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text("免費") }
                    SegmentedButton(
                        selected = paid,
                        onClick = { paid = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text("付費") }
                }
                if (paid) {
                    // 票價改成可自由輸入：現實票價常常不是一個數字（「早鳥 400／現場 500」「$300 起」），
                    // 只准數字會逼使用者硬選一個、把資訊弄丟。開頭的數字會另外抓出來給統計用。
                    OutlinedTextField(
                        value = ticketPriceText,
                        onValueChange = { ticketPriceText = it },
                        label = { Text("票價・可留空") },
                        placeholder = { Text("例：500，或 早鳥 400／現場 500") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = ticketUrl, onValueChange = { ticketUrl = it },
                    label = { Text("售票 / 報名連結") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                // 多組開賣時間（會員預購 / 一般售票…），與演唱會表單同一套。
                Text(
                    "開賣時間",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (saleTimes.isEmpty()) {
                    Text(
                        "可以加好幾組（會員預購、一般售票…），免費活動留空就好。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                saleTimes.forEachIndexed { index, sale ->
                    OutlinedTextField(
                        value = sale.label,
                        onValueChange = { v ->
                            saleTimes = saleTimes.mapIndexed { i, s -> if (i == index) s.copy(label = v) else s }
                        },
                        label = { Text("這一輪的名稱（可留空）") },
                        placeholder = { Text("會員預購 / 一般售票") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = { saleEditIndex = index; showSaleDate = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "${sale.at.year}/${sale.at.monthNumber}/${sale.at.dayOfMonth}　" +
                                    "${sale.at.hour.toString().padStart(2, '0')}:" +
                                    sale.at.minute.toString().padStart(2, '0')
                            )
                        }
                        TextButton(onClick = {
                            saleTimes = saleTimes.filterIndexed { i, _ -> i != index }
                        }) { Text("移除") }
                    }
                }
                TextButton(onClick = {
                    val newIndex = saleTimes.size
                    saleTimes = saleTimes + SaleTime("", LocalDateTime(date, LocalTime(12, 0)))
                    saleEditIndex = newIndex
                    showSaleDate = true
                }) { Text("＋ 新增開賣時間") }
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("備註") },
                    placeholder = { Text("入場方式、注意事項…") },
                    minLines = 2, maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sourceUrl, onValueChange = { sourceUrl = it },
                    label = { Text("來源網址・活動頁或粉專") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                // 活動圖：最多 5 張，新舊圖排在同一份清單裡，可調順序。第一張是封面。
                val total = imageOrder.size
                Text("活動圖（$total / 5）· 第一張是封面", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (total > 0) {
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(imageOrder, key = { _, u -> u }) { idx, u ->
                            val newIdx = com.bandary.data.repository.FreeEventsRepository.newImageIndex(u)
                                .takeIf { u.startsWith(com.bandary.data.repository.FreeEventsRepository.NEW_IMAGE_PREFIX) }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(96.dp)) {
                                    if (newIdx != null) {
                                        AsyncImage(
                                            model = newImages.getOrNull(newIdx), contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.matchParentSize().clip(RoundedCornerShape(10.dp))
                                        )
                                    } else {
                                        EventImage(
                                            data = u, contentScale = ContentScale.Crop,
                                            modifier = Modifier.matchParentSize().clip(RoundedCornerShape(10.dp))
                                        )
                                    }
                                    RemoveBadge { imageOrder = imageOrder.filterIndexed { i, _ -> i != idx } }
                                    // 封面標記：不用讓人自己數是不是第一張。
                                    if (idx == 0) {
                                        Text(
                                            "封面",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(4.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.primary,
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                // 左右移：對話框裡用拖曳排序很容易跟捲動打架，用兩顆箭頭最不會出錯。
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            imageOrder = imageOrder.toMutableList()
                                                .apply { add(idx - 1, removeAt(idx)) }
                                        },
                                        enabled = idx > 0,
                                        modifier = Modifier.size(28.dp)
                                    ) { Icon(Icons.Outlined.ChevronLeft, "往前移", modifier = Modifier.size(18.dp)) }
                                    IconButton(
                                        onClick = {
                                            imageOrder = imageOrder.toMutableList()
                                                .apply { add(idx + 1, removeAt(idx)) }
                                        },
                                        enabled = idx < imageOrder.lastIndex,
                                        modifier = Modifier.size(28.dp)
                                    ) { Icon(Icons.Outlined.ChevronRight, "往後移", modifier = Modifier.size(18.dp)) }
                                }
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = total < 5,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Image, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(if (total == 0) "上傳活動圖（可多張）" else "再加圖片")
                }
                // 複製為新活動：把目前表單內容原封不動開一張新的（存成新的一筆，不動原活動）。
                if (onDuplicate != null && editing) {
                    TextButton(
                        onClick = { if (name.isNotBlank()) onDuplicate(buildEvent()) },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("複製為新活動", fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onAdd(buildEvent(), newImages) },
                enabled = name.isNotBlank()
            ) { Text(if (editing) "儲存" else if (isAdmin) "新增" else "送出投稿") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )

    if (showEndDate) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = (endDate ?: date).toEpochMillisUtc())
        DatePickerDialog(
            onDismissRequest = { showEndDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { ms ->
                        endDate = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.UTC).date
                    }
                    showEndDate = false
                }) { Text("確定") }
            },
            dismissButton = { TextButton(onClick = { showEndDate = false }) { Text("取消") } }
        ) { DatePicker(state = dpState) }
    }

    if (showEndTime) {
        val tpState = rememberTimePickerState(initialHour = endTime?.hour ?: 22, initialMinute = endTime?.minute ?: 0, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showEndTime = false },
            confirmButton = {
                TextButton(onClick = { endTime = LocalTime(tpState.hour, tpState.minute); showEndTime = false }) { Text("確定") }
            },
            dismissButton = { TextButton(onClick = { showEndTime = false }) { Text("取消") } },
            text = { TimePicker(state = tpState) }
        )
    }

    if (showDate) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = date.toEpochMillisUtc()
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { ms ->
                        date = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.UTC).date
                    }
                    showDate = false
                }) { Text("確定") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("取消") } }
        ) { DatePicker(state = dpState) }
    }

    if (showTime) {
        val tpState = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = { time = LocalTime(tpState.hour, tpState.minute); showTime = false }) { Text("確定") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("取消") } },
            text = { TimePicker(state = tpState) }
        )
    }

    // 開賣時間：選完日期直接接時間，少一次點擊。改的是 saleTimes[saleEditIndex]。
    if (showSaleDate) {
        val editing = saleTimes.getOrNull(saleEditIndex)
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = (editing?.at?.date ?: date).toEpochMillisUtc()
        )
        DatePickerDialog(
            onDismissRequest = { showSaleDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { ms ->
                        val d = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.UTC).date
                        saleTimes = saleTimes.mapIndexed { i, s ->
                            if (i == saleEditIndex) s.copy(at = LocalDateTime(d, s.at.time)) else s
                        }
                    }
                    showSaleDate = false
                    showSaleTime = true
                }) { Text("確定") }
            },
            dismissButton = { TextButton(onClick = { showSaleDate = false }) { Text("取消") } }
        ) { DatePicker(state = dpState) }
    }

    if (showSaleTime) {
        val editing = saleTimes.getOrNull(saleEditIndex)
        val tpState = rememberTimePickerState(
            initialHour = editing?.at?.hour ?: 12,
            initialMinute = editing?.at?.minute ?: 0,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showSaleTime = false },
            confirmButton = {
                TextButton(onClick = {
                    val t = LocalTime(tpState.hour, tpState.minute)
                    saleTimes = saleTimes.mapIndexed { i, s ->
                        if (i == saleEditIndex) s.copy(at = LocalDateTime(s.at.date, t)) else s
                    }
                    showSaleTime = false
                }) { Text("確定") }
            },
            dismissButton = { TextButton(onClick = { showSaleTime = false }) { Text("取消") } },
            text = { TimePicker(state = tpState) }
        )
    }
}

/**
 * 從票價文字裡抓出第一組數字，供統計與「免費 / 付費」判斷使用。
 * 「早鳥 400／現場 500」→ 400；「$1,800」→ 1800；沒有數字 → null。
 */
private fun firstNumberIn(text: String): Double? =
    Regex("""\d[\d,]*""").find(text)?.value?.replace(",", "")?.toDoubleOrNull()

/**
 * 票價字串裡的所有金額。「早鳥 400／現場 500」→ [400, 500]，用來推出票價區間。
 * 過濾掉太小的數字（「2 日票」的 2、「Day1」的 1），那些是場次編號不是錢。
 */
private fun allNumbersIn(text: String): List<Double> =
    Regex("""\d[\d,]*""").findAll(text)
        .mapNotNull { it.value.replace(",", "").toDoubleOrNull() }
        .filter { it >= 50 }
        .toList()

/** 比對修改提案與原活動，列出「欄位：舊 → 新」。只列真的變了的欄位。 */
private fun freeEventDiff(original: FreeEvent, proposal: FreeEvent): List<String> {
    fun show(v: Any?) = v?.toString()?.takeIf { it.isNotBlank() } ?: "（空）"
    val fields = listOf<Triple<String, Any?, Any?>>(
        Triple("名稱", original.name, proposal.name),
        Triple("藝人", original.artistName, proposal.artistName),
        Triple("嘉賓", original.guestsByArtist.values.flatten().joinToString("、"), proposal.guestsByArtist.values.flatten().joinToString("、")),
        Triple("地點", original.venue, proposal.venue),
        Triple("縣市", original.city, proposal.city),
        Triple("日期時間", original.dateTime, proposal.dateTime),
        Triple("結束日", original.endDate, proposal.endDate),
        Triple("結束時間", original.endTime, proposal.endTime),
        Triple("票價", original.priceText ?: original.ticketPrice, proposal.priceText ?: proposal.ticketPrice),
        Triple("備註", original.note, proposal.note),
        Triple("售票連結", original.ticketUrl, proposal.ticketUrl),
        // 開賣時間是清單，先排序攤平成字串再比，否則順序不同會被當成有改。
        Triple(
            "開賣時間",
            original.saleTimes.sortedBy { it.at }.joinToString("・") { it.display() },
            proposal.saleTimes.sortedBy { it.at }.joinToString("・") { it.display() }
        ),
        Triple("來源網址", original.sourceUrl, proposal.sourceUrl)
    )
    val out = fields.filter { (_, a, b) -> a != b }.map { (k, a, b) -> "$k：${show(a)} → ${show(b)}" }
    // 圖片是 Base64 長字串，不適合印出來比對，只說換了沒。
    return if (original.images != proposal.images)
        out + "活動圖：${original.images.size} → ${proposal.images.size} 張" else out
}

// ── 管理員審核投稿 ─────────────────────────────────────────────
@Composable
private fun ReviewPendingDialog(
    pending: List<FreeEvent>,
    approved: List<FreeEvent> = emptyList(),   // 用來對照修改提案改了哪些欄位
    onApprove: (String) -> Unit,
    onReject: (String, String?) -> Unit,
    onApproveAll: (List<String>) -> Unit = {},
    onRejectAll: (List<String>) -> Unit = {},
    onMergeInto: (pendingId: String, targetId: String) -> Unit = { _, _ -> },
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    // 依來源分開審（使用者要求）。兩者要審的東西完全不同：
    // 使用者投稿是一筆一筆手打的，要看「這是真的活動嗎」，而且背後有人在等回覆；
    // 爬蟲是整批進來的，要看「這個來源這次抓得對不對」，一次處理幾十筆。
    // 混在一起的話，爬蟲那幾十筆會把使用者那三筆洗掉——而等回覆的是那三筆的人。
    //
    // 依演出日期由近到遠，跟網頁版審核區一致：越快開演的越該先看。
    val byUser = remember(pending) {
        pending.filter { !it.isFromCrawler() }.sortedBy { it.dateTime }
    }
    val byCrawler = remember(pending) {
        pending.filter { it.isFromCrawler() }.sortedBy { it.dateTime }
    }
    // 預設停在「使用者投稿」——那一邊有人在等。只有在它空了而爬蟲有東西時才跳過去。
    var sourceTab by remember(pending) {
        mutableStateOf(if (byUser.isEmpty() && byCrawler.isNotEmpty()) 1 else 0)
    }
    val sorted = if (sourceTab == 0) byUser else byCrawler
    // 批次核准／駁回：跟網頁版同一套（勾選 → 全選 → 核准已選／駁回已選）。逐筆呼叫既有的
    // onApprove/onReject 就好——這兩個本來就各自 launch 成獨立協程，不用額外做進度序列。
    var selected by remember(sorted) { mutableStateOf<Set<String>>(emptySet()) }
    // 目前展開細節的那一筆。一次只展開一筆——同時攤開好幾張又變回一面牆。
    var expandedId by remember(sorted) { mutableStateOf<String?>(null) }
    val allSelected = sorted.isNotEmpty() && selected.size == sorted.size
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("審核投稿 · ${pending.size}") },
        text = {
            if (pending.isEmpty()) {
                Text("目前沒有待審核的投稿", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(Modifier.fillMaxWidth()) {
                    // 來源分頁。數字直接寫在標籤上：要先知道哪一邊有東西才知道該點哪邊。
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                    ) {
                        listOf("使用者投稿 ${byUser.size}", "爬蟲 ${byCrawler.size}")
                            .forEachIndexed { i, label ->
                                SegmentedButton(
                                    selected = sourceTab == i,
                                    onClick = { sourceTab = i },
                                    shape = SegmentedButtonDefaults.itemShape(index = i, count = 2)
                                ) { Text(label, fontSize = 13.sp) }
                            }
                    }
                    if (sorted.isEmpty()) {
                        Text(
                            if (sourceTab == 0) "沒有使用者投稿等審核" else "沒有爬蟲抓到的新活動等審核",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    // 批次工具列：全選 + 核准已選 / 駁回已選。選了才顯示動作按鈕，避免誤觸。
                    // Checkbox 預設帶 48dp 的最小觸控範圍，直接排進 Row 會把整列往右推出一塊空白
                    // （就是「跑版」的來源）。用 sizeIn 收掉那圈留白，再自己補間距。
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = allSelected,
                            onCheckedChange = { checked -> selected = if (checked) sorted.map { it.id }.toSet() else emptySet() },
                            modifier = Modifier.sizeIn(minWidth = 24.dp, minHeight = 24.dp)
                        )
                        Text(
                            if (selected.isEmpty()) "全選" else "已選 ${selected.size} 筆",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                        )
                        if (selected.isNotEmpty()) {
                            // 交給 ViewModel 循序處理。原本在這裡 forEach 逐筆 launch，
                            // 24 筆＝24 次全量重抓，畫面直接卡住（實機回報「按不下去」）。
                            TextButton(
                                onClick = {
                                    onApproveAll(selected.toList().sortedBy { id -> sorted.indexOfFirst { it.id == id } })
                                    selected = emptySet()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) { Text("核准已選", fontSize = 13.sp) }
                            TextButton(
                                onClick = {
                                    onRejectAll(selected.toList())
                                    selected = emptySet()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) { Text("駁回已選", fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }
                        }
                    }
                // 清單要給明確的高度上限。AlertDialog 的 text slot 是 wrapContentHeight，
                // 裡面塞沒有界限的 LazyColumn 會量不出高度，內容就被擠成一團。
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp)
                ) {
                    items(sorted, key = { it.id }) { e ->
                        // 預設只給一行摘要，點一下才展開細節與動作。
                        // 以前每一筆都把封面圖、完整日期、連結、投稿者、駁回意見欄、
                        // 核准／駁回按鈕全部攤開，一筆就三百多 dp——24 筆待審等於一面牆
                        // （實機回報「投稿審核那裏很醜」）。批次核准才是常態，
                        // 逐筆細看是例外，版面要照這個比例配。
                        val expanded = e.id == expandedId
                        val original = e.editOfId?.let { id -> approved.firstOrNull { it.id == id } }
                        val similar = remember(e, approved) {
                            if (e.editOfId == null) com.bandary.domain.model.findSimilarEvent(e, approved) else null
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                // ── 摘要列：勾選 + 日期 + 名稱 + 地點 ──────────────
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    androidx.compose.material3.Checkbox(
                                        checked = e.id in selected,
                                        onCheckedChange = { checked ->
                                            selected = if (checked) selected + e.id else selected - e.id
                                        },
                                        modifier = Modifier.sizeIn(minWidth = 24.dp, minHeight = 24.dp)
                                    )
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = 8.dp)
                                            .clickable { expandedId = if (expanded) null else e.id }
                                    ) {
                                        Text(
                                            e.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = if (expanded) 3 else 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        Text(
                                            buildString {
                                                append("${e.dateTime.monthNumber}/${e.dateTime.dayOfMonth}")
                                                e.city?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                                                e.venue.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                    // 需要注意的事就算收合也要看得到，不然批次核准會直接蓋過去。
                                    if (e.editOfId != null) {
                                        Text(
                                            "修改",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.padding(end = 6.dp)
                                        )
                                    }
                                    if (similar != null || (e.editOfId != null && original == null)) {
                                        Icon(
                                            Icons.Outlined.Warning, "需要注意",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    Icon(
                                        if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                        null,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clickable { expandedId = if (expanded) null else e.id },
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (expanded) {
                                    Column(
                                        Modifier.padding(top = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        e.images.firstOrNull()?.let { cover ->
                                            EventImage(
                                                data = cover,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(8.dp))
                                            )
                                        }
                                        Text(
                                            "${e.dateTime.year}/${e.dateTime.monthNumber}/${e.dateTime.dayOfMonth} " +
                                                "${e.dateTime.hour.toString().padStart(2, '0')}:${e.dateTime.minute.toString().padStart(2, '0')}",
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary
                                        )
                                        // 修改提案：列出到底改了哪些欄位——只給一份新資料，
                                        // 管理員無從判斷該不該核准。
                                        if (e.editOfId != null) {
                                            if (original == null) {
                                                Text(
                                                    "原活動已不存在，核准會失敗，建議直接駁回。",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            } else {
                                                val diffs = freeEventDiff(original, e)
                                                Text(
                                                    if (diffs.isEmpty()) "內容與原活動相同"
                                                    else diffs.joinToString("\n"),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        // 新投稿：同一天已有名稱相似的公開活動就標出來，
                                        // 管理員可以直接併進去，不用核准後再手動刪一筆重複的。
                                        similar?.let { s ->
                                            Text(
                                                "可能與已公開的「${s.name}」（${s.dateTime.monthNumber}/${s.dateTime.dayOfMonth}）重複",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                            TextButton(
                                                onClick = { onMergeInto(e.id, s.id) },
                                                contentPadding = PaddingValues(horizontal = 0.dp)
                                            ) { Text("併入現有活動（補上缺的欄位與圖片，刪掉這筆投稿）", fontSize = 12.sp) }
                                        }
                                        if (!e.sourceUrl.isNullOrBlank()) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.clickable {
                                                    runCatching {
                                                        context.startActivity(
                                                            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(e.sourceUrl))
                                                        )
                                                    }
                                                }
                                            ) {
                                                Icon(Icons.Outlined.Link, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                                                Text("活動連結", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                        e.createdBy?.takeIf { it.isNotBlank() }?.let {
                                            Text("投稿者：$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        var comment by remember(e.id) { mutableStateOf("") }
                                        // 用 placeholder 而不是 label：卡片內的欄位高度本來就緊，
                                        // 浮動 label 在這個高度會壓到外框上，看起來就是「跑版」。
                                        OutlinedTextField(
                                            value = comment, onValueChange = { comment = it },
                                            placeholder = { Text("駁回意見（選填）", style = MaterialTheme.typography.bodySmall) },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            textStyle = MaterialTheme.typography.bodySmall,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                                            Button(
                                                onClick = { onApprove(e.id) },
                                                shape = RoundedCornerShape(10.dp),
                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                            ) { Text("核准", fontSize = 13.sp) }
                                            OutlinedButton(
                                                onClick = { onReject(e.id, comment.ifBlank { null }) },
                                                shape = RoundedCornerShape(10.dp),
                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                                            ) { Text("駁回", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("關閉") } }
    )
}

private fun LocalDate.toEpochMillisUtc(): Long =
    LocalDateTime(this, LocalTime(0, 0))
        .toInstant(TimeZone.UTC)
        .toEpochMilliseconds()

/** 活動頁篩選晶片的共用配色。同一排裡每顆各寫一次，改一次要改七個地方。 */
@Composable
private fun eventChipColors() = androidx.compose.material3.FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = Color.White,
    selectedLeadingIconColor = Color.White
)

/**
 * 縣市正規化。實機截圖上同時出現「臺北市 2」與「臺北 1」——
 * 那是同一個地方，被當成兩個選項，點哪個都只看得到一半的活動。
 *
 * 統一成不含「市／縣」的「臺」字寫法：台北市 / 台北 / 臺北市 → 臺北。
 */
internal fun normalizeCity(raw: String): String {
    val t = raw.trim().replace("台", "臺")
    return when {
        t.endsWith("市") || t.endsWith("縣") -> t.dropLast(1)
        else -> t
    }.ifBlank { raw.trim() }
}

// ── 修改紀錄與待審提案 ────────────────────────────────────────────
/**
 * 一個對話框同時給兩件事：
 *   • 還沒審核的修改提案——所有人都看得到「有人想把場地改成什麼」，
 *     不然同一筆錯誤資料會被不同人重複提案。
 *   • 已經發生過的修改——誰在什麼時候改了哪些欄位，管理員可以回溯。
 *
 * 兩者顯示的是同一種東西（欄位的 before → after），所以共用同一個列。
 */
@Composable
private fun EventHistoryDialog(
    event: FreeEvent,
    proposals: List<FreeEvent>,
    loadRevisions: suspend () -> List<com.bandary.domain.model.EventRevision>,
    canRestore: Boolean,
    onRestore: (com.bandary.domain.model.EventRevision) -> Unit,
    onDismiss: () -> Unit
) {
    var revisions by remember(event.id) {
        mutableStateOf<List<com.bandary.domain.model.EventRevision>?>(null)
    }
    var confirmRestore by remember {
        mutableStateOf<com.bandary.domain.model.EventRevision?>(null)
    }
    LaunchedEffect(event.id) { revisions = loadRevisions() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改紀錄", fontWeight = FontWeight.Bold) },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ── 待審核的提案 ────────────────────────────────
                if (proposals.isNotEmpty()) {
                    item {
                        Text(
                            "還在等審核的修改（${proposals.size}）",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    items(proposals, key = { "p-" + it.id }) { p ->
                        val changes = remember(p.id) {
                            com.bandary.domain.model.EventDiff.diff(event, p)
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    (p.createdBy ?: "某位使用者") + " 提議：",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (changes.isEmpty()) {
                                    Text("（內容與現況相同）", style = MaterialTheme.typography.bodySmall)
                                } else {
                                    changes.forEach { ChangeRow(it) }
                                }
                            }
                        }
                    }
                    item { HorizontalDivider() }
                }

                // ── 已發生的修改 ────────────────────────────────
                val revs = revisions
                if (revs == null) {
                    item {
                        Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        }
                    }
                } else if (revs.isEmpty()) {
                    item {
                        Text(
                            if (proposals.isEmpty()) "這場活動還沒有人改過。"
                            else "這場活動還沒有已核准的修改。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(revs, key = { "r-" + it.id }) { rev ->
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                rev.note + " · " + rev.changedBy,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                formatRevisionTime(rev.changedAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            rev.changes.forEach { ChangeRow(it) }
                            if (canRestore) {
                                TextButton(
                                    onClick = { confirmRestore = rev },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                ) { Text("回溯到這次修改之前", fontSize = 12.sp) }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("關閉") } }
    )

    confirmRestore?.let { rev ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text("回溯這場活動？") },
            text = {
                Text(
                    "會把「" + com.bandary.domain.model.EventDiff.summarize(rev.changes) +
                        "」那次修改之前的內容套回去。\n\n" +
                        "圖片不會被還原（修改紀錄裡不存圖片），維持現在這幾張。\n" +
                        "回溯本身也會留一筆紀錄。"
                )
            },
            confirmButton = {
                TextButton(onClick = { onRestore(rev); confirmRestore = null }) { Text("回溯") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("取消") } }
        )
    }
}

/** 一個欄位的 before → after。刪除線的舊值 + 新值，一行看完。 */
@Composable
private fun ChangeRow(change: com.bandary.domain.model.FieldChange) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            change.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                change.beforeText(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
            )
            Text(
                change.afterText(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/** 修改時間。同年不寫年份——修改紀錄多半是最近的事，寫全日期反而難掃。 */
private fun formatRevisionTime(millis: Long): String {
    if (millis <= 0L) return ""
    val dt = Instant.fromEpochMilliseconds(millis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val hm = dt.hour.toString().padStart(2, '0') + ":" + dt.minute.toString().padStart(2, '0')
    return if (dt.year == now.year) "${dt.monthNumber}/${dt.dayOfMonth} $hm"
    else "${dt.year}/${dt.monthNumber}/${dt.dayOfMonth} $hm"
}

// ── 活動篩選面板 ──────────────────────────────────────────────────
/**
 * 所有篩選條件都在這裡。
 *
 * 為什麼收進面板：以前條件是一整排水平捲動的晶片直接貼在頁面上，
 * 在月曆還沒出現前就吃掉一大塊版面，而且沒有套用任何條件時那排也還在
 * （實機回報「很醜」）。收進來之後頁面只剩一行摘要，
 * 也才有空間把條件寫完整——「只看付費」這種第二層條件原本根本放不下。
 *
 * 底部固定一條「看 N 筆」：改條件時就看得到會剩幾筆，不必關掉面板才知道篩太兇。
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun EventFilterSheet(
    listMode: Boolean,
    rangeLabel: String,
    rangeActive: Boolean,
    onPickRange: () -> Unit,
    onClearRange: () -> Unit,
    selectedPrices: Set<PriceBucket>,
    onTogglePrice: (PriceBucket) -> Unit,
    customPriceMin: String,
    onCustomPriceMin: (String) -> Unit,
    customPriceMax: String,
    onCustomPriceMax: (String) -> Unit,
    mineCounts: Map<MineFilter, Int>,
    selectedMine: Set<MineFilter>,
    onToggleMine: (MineFilter) -> Unit,
    hiddenCount: Int,
    showHidden: Boolean,
    onShowHidden: (Boolean) -> Unit,
    onUnhideAll: () -> Unit,
    countryOptions: List<Pair<String, Int>>,
    selectedCountries: Set<String>,
    onToggleCountry: (String) -> Unit,
    cityOptions: List<Pair<String, Int>>,
    selectedCities: Set<String>,
    onToggleCity: (String) -> Unit,
    matchCount: Int,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "篩選",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onClearAll) { Text("全部清除", fontSize = 13.sp) }
            }

            run {
                val scroll = rememberScrollState()
                // 自訂金額預設收起來。它原本是兩個永遠攤在那裡的大輸入框，
                // 在這個全是晶片的版面上視覺份量最重、卻是最少用的條件
                // ——上面六個區間已經涵蓋絕大多數情況（實機回報「很醜」）。
                var priceCustomOpen by remember {
                    mutableStateOf(customPriceMin.isNotBlank() || customPriceMax.isNotBlank())
                }
                // 縣市預設只列「真的有活動」的。實機截圖上 20 個縣市裡只有 7 個有資料，
                // 其餘 13 個照樣佔滿整整三行——找得到的東西被找不到的東西淹掉。
                var showAllCities by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier
                        .heightIn(max = 440.dp)
                        .verticalScroll(scroll)
                ) {
                    // 時間區間只給清單：月曆本身就是在選日期，套上去會讓別的月份整片空白。
                    if (listMode) {
                        FilterSectionLabel("時間區間", if (rangeActive) 1 else 0)
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            // 縱距 0：晶片已經各自保留 48dp 的觸控高度（見 FilterPill），
                            // 48 本身就是間距，再加 6dp 只會讓面板變高。
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            androidx.compose.material3.FilterChip(
                                selected = rangeActive,
                                onClick = onPickRange,
                                label = { Text(rangeLabel, fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(Icons.Outlined.CalendarMonth, null, modifier = Modifier.size(16.dp))
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = eventChipColors()
                            )
                            // 「不限」本來是一顆文字按鈕，跟旁邊的晶片不同型、也不像可選項。
                            // 兩個都做成晶片，才看得出它們是同一組二選一。
                            FilterPill("不限", selected = !rangeActive, onClick = onClearRange)
                        }
                    }

                    FilterSectionLabel(
                        "票價",
                        selectedPrices.size +
                            (if (customPriceMin.isNotBlank() || customPriceMax.isNotBlank()) 1 else 0)
                    )
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        // 縱距 0：晶片已各自保留 48dp 觸控高度（見 FilterPill）。
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        PriceBucket.entries.forEach { b ->
                            FilterPill(b.label, b in selectedPrices) { onTogglePrice(b) }
                        }
                    }
                    TextButton(
                        onClick = {
                            priceCustomOpen = !priceCustomOpen
                            // 收起來時把值清掉，否則條件還在作用但使用者看不到它
                            // ——那是最糟的一種：篩不到東西而且找不到原因。
                            if (!priceCustomOpen) { onCustomPriceMin(""); onCustomPriceMax("") }
                        },
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Icon(
                            if (priceCustomOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            null, modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (priceCustomOpen) "收起自訂金額" else "自訂金額範圍", fontSize = 13.sp)
                    }
                    if (priceCustomOpen) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // 只收數字：貼上「NT$1,200」這種也會被清成 1200。
                            val digits = { v: String -> v.filter { it.isDigit() }.take(7) }
                            OutlinedTextField(
                                value = customPriceMin,
                                onValueChange = { onCustomPriceMin(digits(it)) },
                                label = { Text("最低", fontSize = 12.sp) },
                                placeholder = { Text("不限", fontSize = 12.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Text("–", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedTextField(
                                value = customPriceMax,
                                onValueChange = { onCustomPriceMax(digits(it)) },
                                label = { Text("最高", fontSize = 12.sp) },
                                placeholder = { Text("不限", fontSize = 12.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // 我的：每一類都列出來（附筆數），可複選。0 筆的也看得到，只是按不下去——
                    // 知道「有這個條件」比整排不見好。
                    FilterSectionLabel("我的", selectedMine.size + (if (showHidden) 1 else 0))
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        // 縱距 0：晶片已各自保留 48dp 觸控高度（見 FilterPill）。
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        MineFilter.entries.forEach { m ->
                            val n = mineCounts[m] ?: 0
                            FilterPill(
                                "${m.label} $n",
                                selected = m in selectedMine,
                                enabled = n > 0 || m in selectedMine,
                                onClick = { onToggleMine(m) }
                            )
                        }
                        if (hiddenCount > 0) {
                            FilterPill("已隱藏 $hiddenCount", showHidden) { onShowHidden(!showHidden) }
                        }
                    }
                    if (hiddenCount > 0) {
                        TextButton(onClick = onUnhideAll, contentPadding = PaddingValues(horizontal = 4.dp)) {
                            Text("全部取消隱藏", fontSize = 13.sp)
                        }
                    }

                    // 國家：一定列出（至少有台灣），可複選。
                    if (countryOptions.isNotEmpty()) {
                        FilterSectionLabel("國家", selectedCountries.size)
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            // 縱距 0：晶片已經各自保留 48dp 的觸控高度（見 FilterPill），
                            // 48 本身就是間距，再加 6dp 只會讓面板變高。
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            countryOptions.forEach { (country, n) ->
                                FilterPill("$country $n", country in selectedCountries) {
                                    onToggleCountry(country)
                                }
                            }
                        }
                    }

                    if (cityOptions.isNotEmpty()) {
                        FilterSectionLabel("縣市", selectedCities.size)
                        // 有活動的排前面；沒活動的收起來（已選的一定留著，
                        // 否則使用者選過的條件會在清單變動時憑空消失）。
                        val withData = cityOptions.filter { it.second > 0 || it.first in selectedCities }
                        val empty = cityOptions.filterNot { it.second > 0 || it.first in selectedCities }
                        val shown = if (showAllCities) withData + empty else withData
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            // 縱距 0：晶片已經各自保留 48dp 的觸控高度（見 FilterPill），
                            // 48 本身就是間距，再加 6dp 只會讓面板變高。
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            shown.forEach { (city, n) ->
                                FilterPill(
                                    if (n > 0) "$city $n" else city,
                                    city in selectedCities
                                ) { onToggleCity(city) }
                            }
                        }
                        if (empty.isNotEmpty()) {
                            TextButton(
                                onClick = { showAllCities = !showAllCities },
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Icon(
                                    if (showAllCities) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                    null, modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (showAllCities) "收起沒有活動的縣市"
                                    else "另外 ${empty.size} 個縣市目前沒有活動",
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                // 即時筆數：改條件時就知道會剩幾筆，不必關掉面板才發現篩太兇。
                Text(if (matchCount > 0) "看 $matchCount 筆活動" else "沒有符合的活動")
            }
        }
    }
}

/**
 * 段落標題。這一段有設條件時，標題上主色並在後面點一個小圓點。
 *
 * 為什麼需要：面板要捲好幾屏，捲到下面時完全看不出上面有沒有設條件
 * ——使用者只好一路捲回去確認（實機回報「不直觀」）。
 *
 * 為什麼是圓點而不是「已選 2」：**幾項不重要，有沒有才重要**。
 * 捲動時要回答的問題是「上面還有沒有東西沒清掉」，不是「上面選了幾個」；
 * 而且晶片本身已經顯示選中狀態，數字只是把同一件事再講一次。
 * 一個點讀起來比一串字快，也不會把標題撐長。
 */
@Composable
private fun FilterSectionLabel(text: String, activeCount: Int = 0) {
    val on = activeCount > 0
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (on) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface
        )
        if (on) {
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        androidx.compose.foundation.shape.CircleShape
                    )
            )
        }
    }
}

/**
 * 篩選用的晶片。選中時加一個勾。
 *
 * 原本只靠底色區分選中與否，在這種一整片晶片的版面上辨識度很差
 * ——尤其莫蘭迪色系本來就低對比。多一個勾是最便宜的辨識度。
 */
@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    // onClick 放最後：呼叫端大量用 trailing lambda，排在 enabled 前面的話
    // 那個 lambda 會被綁到 enabled 去（Boolean），而且錯誤訊息完全看不出原因。
    onClick: () -> Unit
) {
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        // 「不好按」的實際機制：晶片的視覺高度是 32dp（Material3 的 FilterChipTokens），
        // 而系統會自動把可點範圍擴張到 48dp。晶片換行成多排、縱向只隔 6dp 的時候，
        // 上下兩排擴張後的觸控區**重疊 10dp**——按到靠邊的地方，跳的是隔壁排那一顆。
        //
        // minimumInteractiveComponentSize() 不會把晶片畫大（視覺仍是 32dp），
        // 它只在版面上保留 48dp 並把晶片置中，讓相鄰的觸控區不再互搶。
        // Checkbox / IconButton / RadioButton 內建就有這一層，Chip 沒有。
        //
        // 配合呼叫端把 FlowRow 的 verticalArrangement 改成 0：
        // 48dp 本身已經是間距，再加 6dp 只是讓面板變高而已。
        modifier = Modifier.minimumInteractiveComponentSize(),
        label = { Text(label, fontSize = 13.sp) },
        leadingIcon = if (selected) {
            { Icon(Icons.Outlined.Check, null, modifier = Modifier.size(15.dp)) }
        } else null,
        shape = RoundedCornerShape(10.dp),
        colors = eventChipColors()
    )
}
