package com.bandary.presentation.ui.shows

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.automirrored.outlined.Segment
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Checkbox
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.bandary.data.local.SettingsDataStore
import com.bandary.data.repository.ConcertRepository
import com.bandary.domain.model.Concert
import com.bandary.domain.model.ConcertType
import com.bandary.domain.model.FestivalSlot
import com.bandary.domain.model.SaleTime
import com.bandary.domain.model.PICKUP_MACHINES
import com.bandary.domain.model.pickupProfileFor
import com.bandary.domain.model.ReminderOffset
import com.bandary.domain.model.SALE_REMINDER_OPTIONS
import com.bandary.domain.model.RewatchIntent
import com.bandary.domain.model.TicketPlatform
import com.bandary.domain.model.TicketStatus
import com.bandary.presentation.ui.components.ConcertCoverCard
import com.bandary.presentation.ui.theme.BandaryColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.bandary.data.util.ImageStore
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.daysUntil
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.util.UUID
import javax.inject.Inject

// 台灣縣市清單：新增演唱會與活動編輯共用（internal 供同模組其他頁面取用）。
// 統一相簿：場位圖 / 節目表只是其中某張圖的角色標記，不另外佔配額；實際張數上限依方案分級，
// 見 BillingManager.PhotoQuota——這裡不再用單一常數，改用 AddShowState.photoQuota。
// 舊資料若超過目前方案的上限（例如訂閱到期後降級），編輯時不會被砍掉，只是不能再新增，
// 見下方載入既有場次與新增照片兩處的處理方式差異。
private const val PHOTO_CEILING = com.bandary.data.billing.PhotoQuota.PRO_PLUS  // 載入舊資料的安全上限，不受目前方案影響
private const val MAX_TIMETABLES = 5
private const val PHOTO_ROLE_SEAT = "seat"        // 場位圖：一場只會有一張
private const val PHOTO_ROLE_TIMETABLE = "timetable"  // 節目表：最多 MAX_TIMETABLES 張

internal val TAIWAN_CITIES = listOf(
    "臺北", "新北", "桃園", "新竹", "苗栗", "臺中", "彰化", "南投", "雲林",
    "嘉義", "臺南", "高雄", "屏東", "宜蘭", "花蓮", "臺東", "基隆",
    "澎湖", "金門", "馬祖"
)

// 卡片底色（沒封面時的漸層基底）莫蘭迪色票
private val COVER_COLOR_PRESETS = listOf(
    "#8C7A72", "#7C8A78", "#73818F", "#94856F",
    "#877785", "#7E8268", "#9A8C7E", "#6E7B82"
)

// 自訂搶票提醒的顯示字樣（分鐘 → 天 / 小時 / 分）
private fun saleCustomLabel(mins: Int): String = when {
    mins % 1440 == 0 -> "${mins / 1440} 天前"
    mins % 60 == 0 -> "${mins / 60} 小時前"
    else -> "$mins 分前"
}

data class AddShowState(
    val customPlatforms: List<String> = emptyList(),
    val platformOrder: List<String> = emptyList(),
    val defaultReminders: List<ReminderOffset> = listOf(ReminderOffset.ONE_WEEK, ReminderOffset.ONE_DAY),
    val defaultCalSync: Boolean = false,
    val isPro: Boolean = false,
    val isProPlus: Boolean = false
) {
    /** 目前方案下，這場最多能再新增到幾張照片。見 BillingManager.PhotoQuota。 */
    val photoQuota: Int get() = com.bandary.data.billing.PhotoQuota.forTier(isPro, isProPlus)
}

@HiltViewModel
class AddShowViewModel @Inject constructor(
    private val repo: ConcertRepository,
    private val settings: SettingsDataStore,
    private val calendar: com.bandary.data.remote.GoogleCalendarService,
    private val imageSearch: com.bandary.data.remote.ImageSearchService,
    private val spotify: com.bandary.data.remote.SpotifyService,
    private val ocr: com.bandary.data.remote.TimetableOcrService,
    private val friendsRepo: com.bandary.data.repository.FriendsRepository,
    private val freeEventsRepo: com.bandary.data.repository.FreeEventsRepository,
    private val firestoreService: com.bandary.data.remote.FirestoreService,
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context
) : ViewModel() {

    // 圖片文字辨識（OCR）：辨識到的每一行文字，供使用者點選填入名稱 / 歌手 / 場地。
    private val _ocrLines = MutableStateFlow<List<String>?>(null)
    val ocrLines: StateFlow<List<String>?> = _ocrLines.asStateFlow()
    private val _ocrRunning = MutableStateFlow(false)
    val ocrRunning: StateFlow<Boolean> = _ocrRunning.asStateFlow()

    // 票根解讀結果。OCR 的逐行清單留著（海報掃卡司還是要用），
    // 這裡多一層把「像票根」的圖直接讀成欄位——掃完就能整批套用，不用一行一行點。
    private val _ocrStub = MutableStateFlow<com.bandary.data.util.TicketStubParser.Stub?>(null)
    val ocrStub: StateFlow<com.bandary.data.util.TicketStubParser.Stub?> = _ocrStub.asStateFlow()

    /**
     * 解析「分享」進來的一段文字（訂票確認信之類）。
     *
     * 走的是與票根辨識**完全相同**的解析器與審閱流程：
     * OCR 的輸出本來就是「一行一行的文字」，而信件內文也是——
     * 兩邊唯一的差別只是文字從哪裡來。為此另寫一套解析只會多一份要維護的規則。
     *
     * 解不出任何欄位就讓 stub 留 null，審閱對話框會退回逐行挑選，
     * 使用者仍然可以自己點需要的那幾行填進表單。
     */
    fun parseSharedText(text: String) {
        val ordered = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        _ocrStub.value = com.bandary.data.util.TicketStubParser.parse(ordered)
            .takeIf { it.filledCount > 0 }
        _ocrLines.value = ordered.distinct()
        _ocrRunning.value = false
    }

    fun runOcr(uri: String) {
        viewModelScope.launch {
            _ocrRunning.value = true
            _ocrLines.value = null
            _ocrStub.value = null
            val res = ocr.recognize(uri)
            // 解讀用「原始順序」的行，不能用 distinct 後的——票根是標籤在上、值在下的版面，
            // 去重會把重複的標籤行抽掉，值就對不回它的標籤了。
            val ordered = res.spots.map { it.text.trim() }.filter { it.isNotBlank() }
            _ocrStub.value = com.bandary.data.util.TicketStubParser.parse(ordered)
                .takeIf { it.filledCount > 0 }
            _ocrLines.value = ordered.distinct()
            _ocrRunning.value = false
        }
    }

    fun clearOcr() { _ocrLines.value = null; _ocrRunning.value = false; _ocrStub.value = null }

    /** 各售票平台的帳號備忘（只存本機）。選到平台時直接帶出來，不用自己回想。 */
    val platformAccounts: StateFlow<Map<String, String>> =
        settings.platformAccounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun setPlatformAccount(platform: String, account: String) =
        viewModelScope.launch { settings.setPlatformAccount(platform, account) }

    val state: StateFlow<AddShowState> = combine(
        combine(
            settings.customPlatforms,
            settings.platformOrder,
            settings.defaultReminders,
            settings.notifyCalSync
        ) { customs, order, defReminders, calSync ->
            AddShowState(customs.toList(), order, defReminders, calSync)
        },
        settings.isPro,
        settings.isProPlus
    ) { base, isPro, isProPlus -> base.copy(isPro = isPro, isProPlus = isProPlus) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AddShowState())

    /** 用過的同行者暱稱，供輸入時自動建議（同藝人建議一樣，常一起去的排前面）。 */
    val knownCompanions: StateFlow<List<String>> = repo.getAllConcerts()
        .map { list ->
            list.flatMap { it.companions }.map { it.trim() }.filter { it.isNotBlank() }
                .groupingBy { it }.eachCount()
                .entries.sortedByDescending { it.value }.map { it.key }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 看過的所有藝人名稱（含音樂祭卡司），供輸入藝人時做自動建議。
     * 同名以出現次數排序，常看的排前面。
     */
    /**
     * 共用藝人資料庫。只找得到「自己看過的」時，第一次記某個團永遠沒有建議可選，
     * 而且同一位藝人每個人各自打各自的寫法，之後比對不起來。
     * 讀失敗不影響輸入——建議清單只是少了後半段。
     */
    private val _artistDirectory = kotlinx.coroutines.flow.MutableStateFlow<List<String>>(emptyList())

    init {
        viewModelScope.launch {
            firestoreService.fetchArtists().onSuccess { list ->
                _artistDirectory.value = list.map { it.name }
            }
        }
    }

    val knownArtists: StateFlow<List<String>> = combine(
        repo.getAllConcerts(), _artistDirectory
    ) { list, directory ->
        val mine = list.flatMap { c ->
            listOfNotNull(c.artistName.takeIf { it.isNotBlank() }) +
                c.festivals.filter { it.artistName.isNotBlank() }.map { it.artistName }
        }.map { it.trim() }.filter { it.isNotBlank() }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key }
        // 自己看過的排前面（最常看的在最上面），後面接資料庫裡其他人加過的
        val seen = mine.map { com.bandary.domain.model.artistFollowKey(it) }.toSet()
        mine + directory.filter { com.bandary.domain.model.artistFollowKey(it) !in seen }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 你去過的場館，常去的排前面，連同上次填過的地址與縣市。
     *
     * 只從**自己的紀錄**來：免費活動（爬蟲來的）只有場館名與縣市、沒有地址欄，
     * 補不出地址。與其混進來讓人選到一個沒地址的同名場館，不如只給查得到地址的。
     */
    val knownVenues: StateFlow<List<com.bandary.domain.model.VenueHint>> =
        repo.getAllConcerts()
            .map { list ->
                com.bandary.domain.model.buildVenueHints(
                    list.map { Triple(it.venue, it.address, it.city) }
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 你記錄過的所有歌名（供歌單輸入自動建議）。同名以出現次數排序，常聽的排前面。
     * 排除 `▶ 藝人` / `◆ 段落` 這兩種結構標記列，它們不是歌。
     */
    val knownSongs: StateFlow<List<String>> = repo.getAllConcerts()
        .map { list ->
            list.flatMap { it.setlist }
                .map { it.trim() }
                .filter {
                    it.isNotBlank() &&
                        !it.startsWith(SETLIST_ARTIST_PREFIX) &&
                        !it.startsWith(SETLIST_SECTION_PREFIX)
                }
                .groupingBy { it }.eachCount()
                .entries.sortedByDescending { it.value }.map { it.key }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Google 日曆是否已連結（決定要不要顯示逐場同步開關）。 */
    fun isCalendarConnected(): Boolean = calendar.isConnected()

    /**
     * 一張候選圖。[thumb] 給格子顯示、[full] 是要存下來的那張；
     * 兩者可能是 http 網址，也可能是活動資料庫裡的 Base64 字串（`EventImage` 兩種都吃）。
     * [source] 要顯示給使用者看——「這是海報」跟「這只是藝人的照片」是完全不同的東西，
     * 不講清楚就會有人以為 App 找錯圖。
     */
    data class PosterHit(val thumb: String, val full: String, val source: String)

    private val _posterHits = MutableStateFlow<List<PosterHit>>(emptyList())
    val posterHits: StateFlow<List<PosterHit>> = _posterHits.asStateFlow()

    private val _posterSearching = MutableStateFlow(false)
    val posterSearching: StateFlow<Boolean> = _posterSearching.asStateFlow()

    /** 這次是在哪一層找到的，找不到時是 null。畫面用它說明結果的性質。 */
    private val _posterTier = MutableStateFlow<String?>(null)
    val posterTier: StateFlow<String?> = _posterTier.asStateFlow()

    /**
     * 自動找圖，由準到寬**逐層往下退**，第一層有結果就停：
     *
     *  1. **活動資料庫**——別人投稿同一場時上傳的海報。這層最準（就是這場的圖），
     *     而且是本機已快取的資料，不花任何搜尋額度。
     *  2. **場次名稱 + 藝人**的海報搜尋。
     *  3. **只用藝人名字**的海報搜尋——巡演名稱常常跟網路上的寫法對不起來。
     *  4. **Spotify 的藝人照片**。連海報都沒有的小型場次至少給一張人物照，
     *     總比一格灰底好；但標籤會寫明這是藝人照片而不是海報。
     *
     * 為什麼要「第一層有結果就停」而不是全部跑完合併：Google Custom Search 免費額度是
     * 一天 100 次、而且是**全部使用者共用同一把金鑰**。一次點擊跑四個查詢，
     * 二十幾個人就把一天份用完了。
     *
     * 第 4 層為什麼用 Spotify 而不是繼續用圖片搜尋：
     *   - 不花那 100 次額度，而且 Spotify 沒有這種每日上限。
     *   - 拿到的是官方藝人照，不是「網路上搜到的某張圖」——授權狀況乾淨得多。
     *   - **Google Custom Search JSON API 已經不收新客戶，2027-01-01 停止服務。**
     *     能少靠它一層就少一層。
     */
    fun findPoster(artist: String, showName: String) {
        val who = artist.trim()
        val title = showName.trim()
        // 兩個欄位都空白時，之前是直接 return——畫面上什麼都不會變，
        // 使用者只看到一個說「找不到圖」的視窗，完全不知道是因為沒東西可搜。
        if (who.isBlank() && title.isBlank()) {
            _posterHits.value = emptyList()
            _posterTier.value = "還沒有東西可以搜尋。先填上藝人或場次名稱，再按一次。"
            _posterSearching.value = false
            return
        }
        viewModelScope.launch {
            _posterSearching.value = true
            _posterHits.value = emptyList()
            _posterTier.value = null

            // ── 第 1 層：活動資料庫（免費、最準）──────────────────────
            val fromDb = if (who.isBlank()) emptyList() else {
                val key = com.bandary.domain.model.artistFollowKey(who)
                freeEventsRepo.events.value
                    .filter { e ->
                        e.imageUris.isNotEmpty() &&
                            (com.bandary.domain.model.artistFollowKey(e.artistName) == key ||
                                e.name.contains(who, ignoreCase = true))
                    }
                    .flatMap { e -> e.imageUris.map { PosterHit(it, it, "活動資料庫 · " + e.name) } }
                    .take(12)
            }
            if (fromDb.isNotEmpty()) {
                _posterHits.value = fromDb
                _posterTier.value = "從活動資料庫找到——別人投稿這場時上傳的圖。"
                _posterSearching.value = false
                return@launch
            }

            // ── 第 2～3 層：海報搜尋 ────────────────────────────────
            if (!imageSearch.isConfigured()) {
                // 圖片搜尋走 Cloud Function 代理，代理只給登入者用——
                // 不然它就變成一個公開的免費圖片搜尋 API。
                _posterTier.value = "要登入才能上網找海報。先登入，或直接從相簿選圖。"
                _posterSearching.value = false
                return@launch
            }
            // 場次名稱跟藝人名字一樣時不用多跑一次，白花額度。
            val titleQuery = if (title.isNotBlank() && !title.equals(who, ignoreCase = true))
                (who + " " + title).trim() else null
            val steps = listOfNotNull(
                titleQuery?.let { it to "海報搜尋 · 場次名稱" },
                who.takeIf { it.isNotBlank() }?.let { it to "海報搜尋 · 藝人" }
            )
            // 搜尋失敗（金鑰、額度、沒部署）跟「真的沒這張海報」要分開記。
            // 失敗就**不要再搜下一句**：會失敗的原因都是整支服務層級的，
            // 第二句一定也失敗，白等一次 20 秒的 timeout。
            var searchFailure: String? = null
            for ((q, label) in steps) {
                val hits = imageSearch.searchPosters(q, limit = 12)
                if (hits.isNotEmpty()) {
                    _posterHits.value = hits.map { PosterHit(it.thumbnailUrl, it.fullUrl, label) }
                    _posterTier.value = "用「" + q + "」找到的海報。"
                    _posterSearching.value = false
                    return@launch
                }
                searchFailure = imageSearch.lastError.value
                if (searchFailure != null) break
            }
            // 最後一層：退回 Spotify 的官方藝人照。
            if (who.isNotBlank()) {
                val artists = spotify.searchArtists(who, limit = 12)
                    .mapNotNull { a -> a.imageUrl?.let { PosterHit(it, it, "Spotify · " + a.name) } }
                if (artists.isNotEmpty()) {
                    _posterHits.value = artists
                    _posterTier.value = "找不到這場的海報，以下是「" + who + "」的藝人照片。"
                    _posterSearching.value = false
                    return@launch
                }
            }
            // 搜尋壞掉的話，說壞掉的原因。
            //
            // 原本這裡一律印「搜過『某某』都沒有結果」——金鑰沒設、額度用完、
            // 函式沒部署，全都講這句。那是假話，而且它正好長得像功能有在運作，
            // 所以實機回報只能停在「還沒好」，沒人知道要去修哪裡。
            // 伺服器已經把原因寫在錯誤訊息裡了（見 CallableFailure），印出來就好。
            val failure = searchFailure ?: spotify.lastError.value
            if (failure != null) {
                _posterTier.value = "海報搜尋失敗：" + failure
                _posterSearching.value = false
                return@launch
            }
            // 真的只是沒有結果。把實際用過的關鍵字列出來，才判斷得出是關鍵字不好。
            val tried = (steps.map { it.first } + listOfNotNull(who.takeIf { it.isNotBlank() }))
                .distinct()
                .joinToString("、") { "「" + it + "」" }
            _posterTier.value = "搜過 " + tried + " 都沒有結果。"
            _posterSearching.value = false
        }
    }

    fun clearPosters() {
        _posterHits.value = emptyList()
        _posterTier.value = null
    }

    /**
     * 把選中的那張存成本機檔，回傳 `file://` URI；失敗回 null。
     *
     * 一定要落地：原本這個對話框是直接把遠端網址塞進封面欄位，
     * 結果那張圖既不在相簿裡、備份也帶不走，對方撤圖就變破圖。
     */
    suspend fun savePosterLocally(hit: PosterHit): String? = withContext(Dispatchers.IO) {
        if (hit.full.startsWith("http", ignoreCase = true)) {
            val (bytes, ext) = imageSearch.download(hit.full) ?: return@withContext null
            ImageStore.persistBytes(appContext, bytes, ext)
        } else {
            // 活動資料庫的圖是 Base64。
            val bytes = runCatching {
                android.util.Base64.decode(hit.full, android.util.Base64.DEFAULT)
            }.getOrNull() ?: return@withContext null
            ImageStore.persistBytes(appContext, bytes, "jpg")
        }
    }

    suspend fun loadConcert(id: String): Concert? = repo.getConcertById(id)

    // 編輯頁自動草稿（spec §8）。
    suspend fun loadDraft(key: String): Concert? = repo.loadDraft(key)
    fun saveDraft(key: String, c: Concert) { viewModelScope.launch { repo.saveDraft(key, c) } }
    fun clearDraft(key: String) { viewModelScope.launch { repo.clearDraft(key) } }

    fun saveConcert(concert: Concert, onSuccess: () -> Unit, onError: (Throwable) -> Unit = {}) {
        viewModelScope.launch {
            // 存檔前先記住這筆原本存的照片，存成功後拿去跟新內容比對、清掉真的被移除的檔案
            // ——不能在存檔失敗時清，也不能用「使用者按了移除照片」那一刻就清，
            // 因為存檔前使用者還隨時可能按返回放棄這次編輯（見下方 cleanupRemovedPhotos 的說明）。
            val before = runCatching { repo.getConcertById(concert.id) }.getOrNull()
            // 任何例外都要回報，讓畫面重置「儲存中」狀態，不然按鈕會一直卡住無法再按。
            runCatching { repo.saveConcert(concert) }
                .onSuccess {
                    cleanupRemovedPhotos(before, concert)
                    // 新增（不是編輯）的演出自動送去「活動」頁審核；只帶公開資訊，靜默、失敗不影響存檔。
                    if (before == null) launch { runCatching { freeEventsRepo.submitFromConcert(concert) } }
                    // 新增/改動一場就順便重發共享日曆，好友不用等你自己開「好友」分頁才看得到最新的——
                    // 沿用既有的 quiet 發布邏輯（未登入或發布失敗都靜默略過，不影響這次存檔的成功流程）。
                    launch { runCatching { friendsRepo.publishMyCalendar(quiet = true) } }
                    launch { runCatching { friendsRepo.publishYearlyLeaderboard() } }
                    onSuccess()
                }
                .onFailure { onError(it) }
        }
    }

    /**
     * 刪掉「原本這場有、存檔後這場沒有」的本機照片檔案——涵蓋移除照片、拿掉場位圖/節目表標記
     * 後被擠出統一相簿等情況。只在存檔成功之後才清，這樣使用者編輯到一半按返回放棄整次編輯時，
     * 原本存檔的照片不會被誤刪（因為根本沒進到這裡）。
     */
    private fun cleanupRemovedPhotos(before: Concert?, after: Concert) {
        if (before == null) return
        fun localUris(c: Concert): Set<String> =
            (listOfNotNull(c.photoUri, c.seatMapUri) + c.photoUris + c.timetableUris + c.dayCovers)
                .filter { it.isNotBlank() && com.bandary.data.util.ImageStore.isLocal(it) }.toSet()
        val removed = localUris(before) - localUris(after)
        removed.forEach { com.bandary.data.util.ImageStore.delete(appContext, it) }
    }

    fun savePlatformOrder(order: List<String>) = viewModelScope.launch {
        settings.setPlatformOrder(order)
    }

    fun addCustomPlatform(name: String) = viewModelScope.launch {
        settings.addCustomPlatform(name)
    }

    fun removeCustomPlatform(name: String) = viewModelScope.launch {
        settings.removeCustomPlatform(name)
    }
}

/** 讀剪貼簿目前的圖片（使用者在相簿 App 複製一張圖後回來貼上）；沒有圖就回 null。 */
private fun clipboardImageUri(context: android.content.Context): Uri? {
    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        ?: return null
    val clip = cm.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    val item = clip.getItemAt(0)
    val uri = item.uri ?: return null
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { true }
    }.getOrNull()?.let { uri }
}

/**
 * 清單式表單的無邊框輸入框：不畫外框線，但給一個明顯的淺底色方塊，讓「這裡可以輸入」一眼看得出來
 * （之前全透明太不明顯，使用者回饋看不出哪裡可輸入）。聚焦時底色加深 + 主題色外框。
 */
@Composable
// 極簡無框欄位：不畫外框、不填底色，欄位的「邊界」由 FormSection 底部那條細分隔線表達。
// 聚焦時只用極淡的重點色底（alpha 0.06）當回饋——足以看出游標在哪，又不會讓整頁變成一格一格的色塊。
private fun minimalFieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color.Transparent,
    unfocusedBorderColor = Color.Transparent,
    disabledBorderColor = Color.Transparent,
    errorBorderColor = MaterialTheme.colorScheme.error,
    focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent
)

// debounce（草稿自動儲存用）掛著 @FlowPreview。明確 opt-in 而不是讓它一直發警告：
// 用途單純（打字停 800ms 才寫草稿），真的哪天 API 變了，編譯會直接告訴我們。
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class, kotlinx.coroutines.FlowPreview::class)
@Composable
fun AddShowScreen(
    onBack: () -> Unit,
    existingConcertId: String? = null,
    duplicateFromId: String? = null,
    initialDate: String? = null,
    onOpenTicketShare: (String) -> Unit = {},
    onDuplicate: (String) -> Unit = {},
    onOpenPro: () -> Unit = {},
    autoScanPoster: Boolean = false,   // 進來就自動開海報辨識（第一次記錄的快速入口）
    viewModel: AddShowViewModel = hiltViewModel()
) {
    val tz = remember { TimeZone.of("Asia/Taipei") }
    val nowDateTime = remember { Clock.System.now().toLocalDateTime(tz) }
    val vmState by viewModel.state.collectAsState()
    val photoQuota = vmState.photoQuota  // 目前方案能再新增到幾張照片（免費 3 / Pro 10 / Pro+ 30）
    var showPhotoQuotaDialog by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    // 三個 Tab 各自的捲動狀態：Hero 是否收合由「目前這頁捲到哪」推導出來（見下方 heroCollapsed）。
    // 刻意不用 nestedScroll 連動——這頁是 HorizontalPager + LazyColumn，過去接手勢攔截多次把垂直捲動吃掉；
    // 只「讀」捲動位置不攔截任何手勢，就不會有這個風險。
    val pageListStates = listOf(
        androidx.compose.foundation.lazy.rememberLazyListState(),
        androidx.compose.foundation.lazy.rememberLazyListState(),
        androidx.compose.foundation.lazy.rememberLazyListState()
    )
    val formScope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var artists by remember { mutableStateOf(listOf("")) }   // 歌手清單（第一個＝主秀），全類型皆可多個
    // 快速模式：欄位對齊網頁版；預設開（編輯既有場次時預設完整模式）。切到「完整編輯」才是三分頁。
    var quickMode by remember { mutableStateOf(existingConcertId == null) }
    var quickArtist by remember { mutableStateOf("") }  // 快速模式的藝人單一輸入（顯示原文，同步拆進 artists）
    var showPasteArtists by remember { mutableStateOf(false) }  // 貼上卡司名單一鍵拆分
    var artistDayByName by remember { mutableStateOf<Map<String, String>>(emptyMap()) }  // 多日：藝人 → 演出日期(yyyy-MM-dd)
    // FEAT：主要藝人 → 他找來客串的人（同一行輸入）。客串者會另外存成一筆 feat = true 的 slot，
    // 算進藝人統計，但不併入現場時數。
    var featByArtist by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    // 已看過的藝人：輸入時做自動建議用。
    val knownArtists by viewModel.knownArtists.collectAsState()
    val knownVenues by viewModel.knownVenues.collectAsState()
    val knownSongs by viewModel.knownSongs.collectAsState()
    val knownCompanions by viewModel.knownCompanions.collectAsState()
    var venue by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("台灣") }
    // 國內 / 海外：空白、台灣、臺灣都算國內（與統計頁的 isOverseas 同一套判定）。
    // 海外場次給台灣縣市清單是沒有意義的，所以縣市那一欄的呈現要跟著這個走。
    val isOverseas = country.trim().let { it.isNotBlank() && it != "台灣" && it != "臺灣" }
    var selectedCity by remember { mutableStateOf<String?>(null) }
    var customCityInput by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ConcertType.CONCERT) }
    var customType by remember { mutableStateOf("") }   // 選「自訂」時的類型名稱
    var selectedDate by remember {
        mutableStateOf(initialDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: nowDateTime.date)
    }
    var selectedTime by remember { mutableStateOf(LocalTime(19, 30)) }
    var endTime by remember { mutableStateOf<LocalTime?>(null) }
    var endDate by remember { mutableStateOf<LocalDate?>(null) }   // 舊資料的連續結束日（保留相容，UI 不再新增）
    var extraDates by remember { mutableStateOf<List<LocalDate>>(emptyList()) }  // 其他演出日期（逐日）
    var showExtraDatePicker by remember { mutableStateOf(false) }
    var syncToCalendar by remember { mutableStateOf(false) }       // 逐場：是否同步到 Google 日曆
    var ticketStatus by remember { mutableStateOf(TicketStatus.BOUGHT) }  // 預設已購票（最常見）
    var platform by remember { mutableStateOf<String?>(null) }
    var customPlatformInput by remember { mutableStateOf("") }
    var ticketPrice by remember { mutableStateOf("") }
    var ticketUrl by remember { mutableStateOf("") }  // 售票主頁：單一網址
    var infoUrl by remember { mutableStateOf("") }
    var pickupNumber by remember { mutableStateOf("") }
    var pickupIdNumber by remember { mutableStateOf("") }
    var pickupTicketNo by remember { mutableStateOf("") }
    var pickupMachine by remember { mutableStateOf("") }
    // 取票序號截圖。存 file:// （ImageStore 複製一份），不然換 App 重啟就讀不到了。
    var pickupShotUri by remember { mutableStateOf<String?>(null) }
    // 正在編輯合作藝人的那一首歌：(藝人, 在該藝人歌單中的索引)。
    var featEditTarget by remember { mutableStateOf<Pair<String, Int>?>(null) }
    // 這個平台的取票規則：決定預選哪個機台、要不要身分證、序號欄叫什麼。
    // 放在畫面本體而不是 LazyColumn 裡——LazyListScope 不是 @Composable，remember 不能寫在那。
    val pickupProfile = remember(platform) { pickupProfileFor(platform) }
    // 電子票券平台（KKTIX / KLOOK）預設把取票欄位收起來——那些場次根本不用取票。
    // 但個別場次可能另有安排，所以留一顆「還是要填」自己展開。
    var pickupExpanded by remember(platform) { mutableStateOf(!pickupProfile.eTicketOnly) }
    var seatInfo by remember { mutableStateOf("") }
    var setlistByArtist by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }  // 歌單：每位藝人各自一份
    var setlistExpanded by remember { mutableStateOf(false) }  // 歌單編輯區預設折疊，避免歌多時把表單撐長
    var companions by remember { mutableStateOf<List<String>>(emptyList()) }  // 同行者暱稱
    var companionInput by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var rating by remember { mutableStateOf(0) }
    var rewatchIntent by remember { mutableStateOf<RewatchIntent?>(null) }
    var photoUri by remember { mutableStateOf<String?>(null) }
    // 統一相簿：這場的所有上傳圖都在同一條列（最多 MAX_PHOTOS 張），
    // 場位圖 / 節目表不再各開一個上傳口，改成在這條列上為某張圖標角色（photoRoles）。
    var photoUris by remember { mutableStateOf<List<String>>(emptyList()) }
    // 海報提示被關掉就不再出現（同一次新增內）。問一次是幫忙，問第二次是煩。
    var posterPromptDismissed by remember { mutableStateOf(false) }
    var photoRoles by remember { mutableStateOf<Map<String, String>>(emptyMap()) }  // uri → PHOTO_ROLE_*
    var photoSheetUri by remember { mutableStateOf<String?>(null) }  // 正在設定的那張圖
    var dayCovers by remember { mutableStateOf<List<String>>(emptyList()) }  // 多日各日封面
    var dayCoverPickFor by remember { mutableStateOf<Int?>(null) }  // 正在為第幾天挑封面（從已上傳圖片選）
    // 多選移除：以前只能一張一張點進「照片設定」再移除，清一場的廢圖要按十幾次。
    var photoSelectMode by remember { mutableStateOf(false) }
    var photoSelection by remember { mutableStateOf<Set<String>>(emptySet()) }
    // 本機讀不到的照片：相簿原圖被刪、或舊版直接存了只有當次授權的 content:// 連結。
    // 這種欄位畫面上是破圖、照片張數照算、備份也傳不上去，先標出來讓使用者一次清掉。
    var brokenPhotos by remember { mutableStateOf<Set<String>>(emptySet()) }
    var zoomImage by remember { mutableStateOf<String?>(null) }
    var coverColorHex by remember { mutableStateOf<String?>(null) }
    var coverGradientLight by remember { mutableStateOf(false) }
    var showCoverColorPicker by remember { mutableStateOf(false) }
    // 每次相簿內容變動就重新檢查一次哪些讀不到。內建封面走 assets，contentResolver 讀不到是正常的，排除。
    androidx.compose.runtime.LaunchedEffect(photoUris) {
        val uris = photoUris
        brokenPhotos = withContext(Dispatchers.IO) {
            uris.filter { u ->
                !com.bandary.presentation.ui.share.PosterBackgrounds.isCoverUri(u) &&
                    !runCatching {
                        context.contentResolver.openInputStream(android.net.Uri.parse(u))?.use { true } ?: false
                    }.getOrDefault(false)
            }.toSet()
        }
    }
    /** 一次移除數張：連同角色標記、封面與各日封面的引用一起清掉，避免留下指向已刪除圖片的欄位。 */
    fun removePhotos(targets: Set<String>) {
        if (targets.isEmpty()) return
        photoUris = photoUris - targets
        photoRoles = photoRoles - targets
        if (photoUri in targets) photoUri = null
        dayCovers = dayCovers.map { if (it in targets) "" else it }
        photoSelection = photoSelection - targets
        brokenPhotos = brokenPhotos - targets
    }
    // 舊資料裡帶有台上時間 / 舞台的 slot。音樂祭時程表功能已移除（沒有任何入口進得去），
    // 但已經存在的資料不主動刪，編輯時原封帶回去存檔，免得使用者的紀錄被默默清掉。
    var keptFestivals by remember { mutableStateOf<List<FestivalSlot>>(emptyList()) }
    // 多組開賣時間（會員預購 / 一般售票…）。日期與時間選擇器共用，靠 saleEditIndex 指定改哪一組。
    var saleTimes by remember { mutableStateOf<List<SaleTime>>(emptyList()) }
    var saleEditIndex by remember { mutableStateOf(-1) }
    var saleReminderMinutes by remember { mutableStateOf(60) }  // 搶票提醒：開賣前幾分鐘（所有組共用）
    var showSaleCustomDialog by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showSalePicker by remember { mutableStateOf(false) }
    var showSaleTimePicker by remember { mutableStateOf(false) }
    var showPosterSearch by remember { mutableStateOf(false) }
    var showVenueViews by remember { mutableStateOf(false) }
    val posterHits by viewModel.posterHits.collectAsState()
    val posterSearching by viewModel.posterSearching.collectAsState()
    val posterTier by viewModel.posterTier.collectAsState()
    val ocrLines by viewModel.ocrLines.collectAsState()
    val ocrRunning by viewModel.ocrRunning.collectAsState()
    val ocrStub by viewModel.ocrStub.collectAsState()
    var showOcr by remember { mutableStateOf(false) }
    // 非 null 時代表這次 OCR 是為了掃歌單（值＝要把辨識到的歌加進哪位藝人），而不是掃海報找藝人/場地。
    var ocrForSetlistArtist by remember { mutableStateOf<String?>(null) }
    // 非 null 時代表正在為這位藝人「批次貼上歌單」（一次貼一整份、每行一首）。
    var pasteSetlistArtist by remember { mutableStateOf<String?>(null) }
    var moveSetlistFrom by remember { mutableStateOf<String?>(null) }  // 正在把哪位藝人的歌單搬到別人名下
    var selectedReminders by remember {
        // 智慧預設（spec §4.3）：1 天前 + 2 小時前。使用者若在設定自訂預設，稍後的 effect 會覆寫。
        mutableStateOf(setOf(ReminderOffset.ONE_DAY, ReminderOffset.TWO_HOURS))
    }
    // 多個自訂提醒：每個是一組絕對日期時間；儲存時各自換算成「演出前幾分鐘」。
    var customReminders by remember { mutableStateOf<List<LocalDateTime>>(emptyList()) }
    var customReminderDate by remember { mutableStateOf<LocalDate?>(null) }  // 新增中的那一筆：日期
    var customReminderTime by remember { mutableStateOf(LocalTime(12, 0)) }  // 新增中的那一筆：時間
    var showCustomRemDate by remember { mutableStateOf(false) }
    var showCustomRemTime by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showCityPicker by remember { mutableStateOf(false) }
    var orderedPlatforms by remember { mutableStateOf<List<String>>(emptyList()) }

    // 唯一的上傳口：一次可選多張，全部進統一相簿（含封面、場位圖、節目表）。
    // 只新增到「目前方案的張數上限」為止；超過的部分不新增，也不會動到既有已上傳的照片
    // ——降級/訂閱到期後既有照片不會被砍，只是不能再加，見 showPhotoQuotaDialog。
    val photoPickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(photoQuota)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            formScope.launch {
                // 系統相簿選取器沒有「跳到某一天」的功能（要做得自己寫相簿瀏覽器，
                // 需要全相簿權限）。所以從另一端解決：選完之後照拍攝時間排好，
                // 並且把「不是那天拍的」標出來——找照片還是得自己捲，
                // 但挑錯了會被抓出來，那才是真正會造成損失的部分。
                val withTime = withContext(Dispatchers.IO) {
                    uris.map { u -> u to ImageStore.capturedAt(context, u) }
                }
                // 讀不到時間的排在最後：它們沒有排序依據，硬排進中間只會打亂其他張。
                val ordered = withTime.sortedWith(
                    compareBy(nullsLast()) { it.second }
                ).map { it.first }
                val showMillis = runCatching {
                    selectedDate.atTime(selectedTime).toInstant(TimeZone.currentSystemDefault())
                        .toEpochMilliseconds()
                }.getOrNull()
                val offDay = if (showMillis == null) 0 else withTime.count { (_, t) ->
                    // ±1 天以外就算對不上：演出跨夜很常見（散場都半夜了），
                    // 抓太緊會把真的那天的照片也標成錯的。
                    t != null && kotlin.math.abs(t - showMillis) > 36L * 60 * 60 * 1000
                }
                val locals = withContext(Dispatchers.IO) {
                    ordered.map { u -> ImageStore.persist(context, u) ?: u.toString() }
                }
                if (offDay > 0) {
                    android.widget.Toast.makeText(
                        context,
                        "有 " + offDay + " 張不是這天拍的，確認一下是不是挑錯了",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
                val remaining = (photoQuota - photoUris.size).coerceAtLeast(0)
                val toAdd = locals.take(remaining)
                if (toAdd.isNotEmpty()) {
                    // 還沒有封面時，實際加進相簿的第一張自動當封面底圖。
                    if (photoUri.isNullOrBlank()) photoUri = toAdd.first()
                    photoUris = (photoUris + toAdd).distinct()
                }
                if (toAdd.size < locals.size) showPhotoQuotaDialog = true
            }
        }
    }

    // 從別的 App 分享進來的文字（Gmail 的訂票確認信…）：一進表單就解析並開審閱視窗。
    // take() 會清空，所以轉螢幕或返回重進不會再套用一次。
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.bandary.data.util.SharedTicketText.take()?.let { shared ->
            viewModel.parseSharedText(shared)
            showOcr = true
        }
    }

    // 圖片文字辨識（OCR）：選一張海報 / 卡司圖 → **先裁切** → 辨識文字 → 點選填入。
    // 裁到只剩歌單／卡司那一塊，雜訊直接歸零，這比任何前處理都有效。
    val startOcrPick = com.bandary.presentation.ui.components.rememberPickAndCrop { uri ->
        showOcr = true
        viewModel.runOcr(uri.toString())
    }

    // 取票序號截圖：裁掉桌面與手指再存。選完立刻複製進 App 私有目錄，拿到 file:// 才存得住。
    val startPickupShotPick = com.bandary.presentation.ui.components.rememberPickAndCrop { uri ->
        pickupShotUri = com.bandary.data.util.ImageStore.persist(context, uri) ?: pickupShotUri
    }

    // 從空白狀態的「掃海報」進來：自動打開圖片辨識，不用再點一次。
    LaunchedEffect(Unit) {
        if (autoScanPoster && existingConcertId == null && duplicateFromId == null) {
            ocrForSetlistArtist = null
            startOcrPick()
        }
    }

    // 新演唱會：套用「設定」的預設入場提醒（編輯 / 複製時用該場自己的）。
    LaunchedEffect(vmState.defaultReminders) {
        if (existingConcertId == null && duplicateFromId == null) {
            selectedReminders = vmState.defaultReminders.toSet()
        }
    }

    // 新演唱會：同步 Google 日曆的開關預設帶入「設定」的全域值（可逐場調整）。
    LaunchedEffect(vmState.defaultCalSync) {
        if (existingConcertId == null && duplicateFromId == null) {
            syncToCalendar = vmState.defaultCalSync
        }
    }

    // Build platform list: built-in + custom, applying saved order if present.
    LaunchedEffect(vmState.customPlatforms, vmState.platformOrder) {
        val builtIn = TicketPlatform.entries.map { it.label }
        val all = (builtIn + vmState.customPlatforms).distinct()
        val ordered = if (vmState.platformOrder.isNotEmpty()) {
            val sortMap = vmState.platformOrder.withIndex().associate { (i, v) -> v to i }
            all.sortedBy { sortMap[it] ?: Int.MAX_VALUE }
        } else all
        orderedPlatforms = ordered
    }

    val draftKey = existingConcertId ?: "new"
    // 這次的內容是從草稿還原來的嗎——決定要不要掛那條「已還原」提示。
    var restoredFromDraft by remember { mutableStateOf(false) }
    // 有未存完的草稿就優先載入草稿（自動暫存的還原）；複製流程不套草稿。
    LaunchedEffect(existingConcertId, duplicateFromId) {
        // 編輯既有場次：直接套草稿（那就是這一筆沒存完的內容，沒有歧義）。
        //
        // 新增：**以前整個不還原**，理由是「避免下次新增看到上次沒存完的殘留」。
        // 那個顧慮是對的，但代價太大——使用者回報的流程是：
        //   輸入資料 → 加超過三張照片 → 點升級加張數 → 看方案 → 返回 → **全部不見**。
        // 去看一下方案就把剛打的東西清光，而畫面底下還寫著「變更會即時暫存」。
        // 導航去 Pro 頁時這個畫面的 composition 會被釋放，純 remember 的欄位就沒了。
        //
        // 改成：撿起來但不自動填，問一句要不要繼續。
        // 既不會靜悄悄弄丟資料，也不會下次新增時莫名其妙看到上次的殘留。
        val draft = if (duplicateFromId == null) {
            viewModel.loadDraft(draftKey)
                // 新增模式：空白草稿不算（只點開過沒打字的那種），
                // 不然每次新增都會掛一條「已還原」卻什麼都沒還原。
                ?.takeIf {
                    existingConcertId != null ||
                        it.name.isNotBlank() || it.artistName.isNotBlank() || it.venue.isNotBlank()
                }
        } else null
        restoredFromDraft = draft != null && existingConcertId == null
        val sourceId = existingConcertId ?: duplicateFromId
        val existing = draft ?: sourceId?.let { viewModel.loadConcert(it) }
        val fromDuplicate = draft == null && duplicateFromId != null
        if (existing != null) {
                name = existing.name
                venue = existing.venue
                address = existing.address ?: ""
                country = existing.country ?: "台灣"
                selectedCity = existing.city
                selectedType = existing.type
                customType = existing.customType ?: ""
                coverColorHex = existing.coverColorHex
                coverGradientLight = existing.coverGradientLight
                // 有台上時間/舞台的 slot 是舊版時程表留下的，原樣保留；沒有時間/舞台的當作一般歌手
                keptFestivals = existing.festivals.filter { it.startTime.isNotBlank() || it.stage.isNotBlank() }
                val performerSlots = existing.festivals
                    .filter { it.startTime.isBlank() && it.stage.isBlank() }
                // 客串者不佔一列，回填到他所屬主要藝人那一行的 feat 欄。
                artists = (listOfNotNull(existing.artistName.takeIf { it.isNotBlank() }) +
                    performerSlots.filterNot { it.feat }.map { it.artistName })
                    .filter { it.isNotBlank() }.distinct().ifEmpty { listOf("") }
                // 還原藝人 → 演出日期的對應
                artistDayByName = performerSlots.filter { it.artistName.isNotBlank() && it.day.isNotBlank() }
                    .associate { it.artistName.trim() to it.day }
                // 同一位主要藝人的多位客串，回填成「A、B」一個欄位。
                featByArtist = performerSlots
                    .filter { it.feat && it.artistName.isNotBlank() && it.featOf.isNotBlank() }
                    .groupBy { it.featOf.trim() }
                    .mapValues { (_, slots) -> slots.joinToString("、") { it.artistName.trim() } }
                // 日期一律還原（複製時也維持原場日期，使用者要求不要跑掉）。
                selectedDate = existing.dateTime.date
                selectedTime = existing.dateTime.time
                if (!fromDuplicate) {
                    ticketStatus = existing.ticketStatus
                    photoUri = existing.photoUri
                    dayCovers = existing.dayCovers
                    // 舊資料的場位圖 / 節目表併回統一相簿，並還原成角色標記。
                    val seat = existing.seatMapUri?.takeIf { it.isNotBlank() }
                    val tables = existing.timetableUris.filter { it.isNotBlank() }
                    // 用安全上限（不是目前方案的額度）載入：訂閱到期降級後重開舊場次，
                    // 既有照片不該被無聲砍掉——新增才受目前方案的張數限制，見 photoPickLauncher。
                    // 封面不在相簿裡時（例如從活動「我要參加」帶進來的舊資料）補回相簿最前面，
                    // 否則這裡看不到它，一換封面它就被丟掉。內建底圖不是照片，不補。
                    val builtinCovers = com.bandary.presentation.ui.share.PosterBackgrounds.availableIndices(context)
                        .mapNotNull { com.bandary.presentation.ui.share.PosterBackgrounds.coverUri(context, it) }.toSet()
                    photoUris = com.bandary.domain.model.albumIncludingCover(
                        existing.photoUri,
                        (existing.photoUris + listOfNotNull(seat) + tables).filter { it.isNotBlank() }.distinct()
                    ) { it in builtinCovers }.take(PHOTO_CEILING)
                    photoRoles = buildMap {
                        seat?.let { put(it, PHOTO_ROLE_SEAT) }
                        tables.forEach { put(it, PHOTO_ROLE_TIMETABLE) }
                    }
                } else {
                    // Duplicate: clear ticket info（票務狀態回預設已購票），日期已在上方保留。
                    ticketStatus = TicketStatus.BOUGHT
                    photoUri = null
                }
                platform = existing.ticketPlatform
                ticketPrice = existing.ticketPrice?.toInt()?.toString() ?: ""
                // 舊資料若有多個 \n 分隔網址，取第一個（收斂為單一售票網址）。
                ticketUrl = existing.ticketUrl?.split("\n")?.map { it.trim() }?.firstOrNull { it.isNotBlank() } ?: ""
                infoUrl = existing.infoUrl ?: ""
                pickupNumber = existing.pickupNumber ?: ""
                pickupIdNumber = existing.pickupIdNumber ?: ""
                pickupTicketNo = existing.pickupTicketNo ?: ""
                pickupMachine = existing.pickupMachine ?: ""
                pickupShotUri = existing.pickupShotUri
                seatInfo = existing.seatInfo ?: ""
                setlistByArtist = parseSetlist(existing.setlist, existing.artistName.ifBlank { "" })
                companions = existing.companions
                notes = existing.notes ?: ""
                selectedReminders = existing.reminders.toSet()
                // 自訂提醒（分鐘前）→ 還原成絕對日期時間清單（含相容用的舊單一欄位）。
                run {
                    val allMins = (existing.extraReminderMinutes + listOfNotNull(existing.customReminderMinutes))
                        .filter { it > 0 }.distinct()
                    customReminders = allMins.map { mins ->
                        existing.dateTime.toInstant(tz).minus(mins, DateTimeUnit.MINUTE, tz).toLocalDateTime(tz)
                    }.sortedBy { it }
                }
                // 逐場同步開關：編輯時沿用該場設定；複製時預設關閉（避免重複建立日曆事件）。
                syncToCalendar = if (!fromDuplicate) existing.syncToCalendar else false
                if (!fromDuplicate) {
                    endTime = existing.endTime
                    // 舊資料若用「連續結束日」記多日，載入時攤平成一天一筆的 extraDates
                    // （UI 已經沒有結束日期了，不攤平的話那幾天會憑空消失）。
                    val spanDays = existing.endDate?.takeIf { it > existing.dateTime.date }?.let { end ->
                        generateSequence(existing.dateTime.date.plus(1, DateTimeUnit.DAY)) { d ->
                            d.plus(1, DateTimeUnit.DAY).takeIf { it <= end }
                        }.takeWhile { it <= end }.toList()
                    }.orEmpty()
                    endDate = null
                    extraDates = (existing.extraDates + spanDays).distinct().sorted()
                    rating = existing.rating ?: 0
                    rewatchIntent = existing.rewatchIntent
                    saleTimes = existing.saleTimes
                    existing.saleReminderMinutes?.let { saleReminderMinutes = it }
                }
            }
    }

    // 各場發生日（主日期 + 一天一天加的 extraDates），供「藝人分日 / 各日封面」用。
    val occurrences = remember(selectedDate, extraDates) {
        (listOf(selectedDate) + extraDates).distinct().sorted()
    }
    // 不設任何必填：不管內容如何都可以按儲存。
    val isValid = true
    val titleText = when {
        existingConcertId != null -> "編輯演唱會"
        duplicateFromId != null -> "複製為新演唱會"
        else -> "新增演唱會"
    }

    // 產生要儲存的 Concert；id 一開始就定住（新場次用穩定 UUID），
    // 之後不管自動草稿或最後按「儲存」都更新同一場，不會重複建立。
    // 提早定 id（而非首次呼叫才生成）是因為 Hero / 自動草稿會在 composition 期間呼叫 buildConcert，
    // 不能在讀取時寫 state。
    val workingId = remember { existingConcertId ?: UUID.randomUUID().toString() }
    fun buildConcert(): Concert {
        val id = workingId
        return Concert(
            id = id,
            name = name.trim(),
            artistName = (artists.firstOrNull { it.isNotBlank() } ?: "").trim(),
            venue = venue.trim(),
            hall = null,
            address = address.ifBlank { null },
            city = selectedCity,
            country = country.ifBlank { null },
            dateTime = LocalDateTime(selectedDate, selectedTime),
            endTime = endTime,
            // 多日一律用 extraDates 一天一筆表示，不再寫入連續結束日。
            endDate = null,
            extraDates = extraDates.filter { it != selectedDate }.distinct().sorted(),
            type = selectedType,
            customType = if (selectedType == ConcertType.OTHER) customType.trim().ifBlank { null } else null,
            ticketStatus = ticketStatus,
            ticketPlatform = platform,
            ticketUrl = ticketUrl.trim().ifBlank { null },
            infoUrl = infoUrl.ifBlank { null },
            pickupNumber = pickupNumber.ifBlank { null },
            pickupIdNumber = pickupIdNumber.ifBlank { null },
            pickupTicketNo = pickupTicketNo.ifBlank { null },
            pickupMachine = pickupMachine.ifBlank { null },
            pickupShotUri = pickupShotUri,
            ticketPrice = ticketPrice.toDoubleOrNull(),
            seatInfo = seatInfo.ifBlank { null },
            setlist = encodeSetlist(setlistByArtist, artists.filter { it.isNotBlank() }.map { it.trim() }),
            companions = companions.map { it.trim() }.filter { it.isNotBlank() },
            notes = notes.ifBlank { null },
            rating = rating.takeIf { it > 0 },
            rewatchIntent = rewatchIntent,
            photoUri = photoUri,
            // 統一相簿拆回三個既有欄位：有角色標記的不重複算進「更多照片」。
            photoUris = photoUris.filter { photoRoles[it] == null },
            dayCovers = dayCovers,
            seatMapUri = photoUris.firstOrNull { photoRoles[it] == PHOTO_ROLE_SEAT },
            timetableUris = photoUris.filter { photoRoles[it] == PHOTO_ROLE_TIMETABLE }.take(MAX_TIMETABLES),
            coverColorHex = coverColorHex,
            coverGradientLight = coverGradientLight,
            festivals = run {
                // 全部歌手都存成 slot（isSelected → 列入統計）；day 記錄該藝人演出日期（多日用）
                val artistSlots = artists.filter { it.isNotBlank() }
                    .map { FestivalSlot(startTime = "", endTime = "", stage = "", artistName = it.trim(), isSelected = true, day = artistDayByName[it.trim()] ?: "") }
                // 客串者也存成 slot（算進藝人統計），標 feat 讓現場時數排除，featOf 記住是誰找來的。
                // 一位主要藝人可以有多位客串：輸入時用、／，／／ 分隔，這裡拆成一位一筆。
                val guestSlots = artists.filter { it.isNotBlank() }.flatMap { host ->
                    val h = host.trim()
                    featByArtist[h].orEmpty().split('、', ',', '，', '/', '／')
                        .map { it.trim() }.filter { it.isNotBlank() }.distinct()
                        .map { guest ->
                            FestivalSlot(
                                startTime = "", endTime = "", stage = "", artistName = guest,
                                isSelected = true, day = artistDayByName[h] ?: "", feat = true, featOf = h
                            )
                        }
                }
                val allArtistSlots = artistSlots + guestSlots
                if (selectedType == ConcertType.FESTIVAL) keptFestivals + allArtistSlots else allArtistSlots
            },
            // 依時間排序後再存：提醒的 index、詳情頁的顯示順序都直接吃這個順序。
            saleTimes = saleTimes.sortedBy { it.at },
            saleReminderMinutes = if (saleTimes.isNotEmpty()) saleReminderMinutes else null,
            syncToCalendar = syncToCalendar,
            reminders = selectedReminders.toList(),
            // 新資料只用清單（把舊單一欄位清成 null，避免重複排程）；每筆換算成演出前幾分鐘。
            customReminderMinutes = null,
            extraReminderMinutes = run {
                val show = LocalDateTime(selectedDate, selectedTime).toInstant(tz)
                customReminders.mapNotNull { at ->
                    ((show - at.toInstant(tz)).inWholeMinutes).toInt().takeIf { it > 0 }
                }.distinct().sorted()
            }
        )
    }
    // 暫存：跟「儲存」走同一條路徑，但存完留在原畫面（不 onBack），給備註等長文字欄位隨時能存檔的手感。
    var draftSaving by remember { mutableStateOf(false) }
    var draftSavedMsg by remember { mutableStateOf<String?>(null) }
    val doDraftSave: () -> Unit = {
        if (!draftSaving) {
            draftSaving = true
            viewModel.saveConcert(
                buildConcert(),
                onSuccess = { draftSaving = false; draftSavedMsg = "已暫存" },
                onError = {
                    draftSaving = false
                    android.widget.Toast.makeText(context, "暫存失敗：${it.message ?: "請再試一次"}", android.widget.Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
    // 底部「儲存」唯一提交點（spec §8）：成功即清掉草稿並返回。
    val doSave: () -> Unit = {
        if (isValid && !isSaving) {
            isSaving = true
            viewModel.saveConcert(
                buildConcert(),
                onSuccess = { viewModel.clearDraft(draftKey); onBack() },
                onError = {
                    isSaving = false
                    android.widget.Toast.makeText(context, "儲存失敗：${it.message ?: "請再試一次"}", android.widget.Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
    var showEditMenu by remember { mutableStateOf(false) }
    // Hero 收合：由「目前這頁是否已往下捲」推導，捲回頂端自動展開。
    // 只讀 LazyListState、不攔手勢，所以不會發生過去那種「手勢吃掉垂直捲動」的問題。
    // 三 Tab（演出 / 票務 / 心得）+ Pager。
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { 3 })
    val activeListState = pageListStates[pagerState.currentPage.coerceIn(0, 2)]
    // 門檻用 24px 而不是 0，避免手指輕微抖動就在展開 / 收合之間來回跳。
    val heroCollapsed by remember(activeListState) {
        androidx.compose.runtime.derivedStateOf {
            activeListState.firstVisibleItemIndex > 0 || activeListState.firstVisibleItemScrollOffset > 24
        }
    }
    val tabScope = rememberCoroutineScope()

    // 自動草稿（spec §8）：載入穩定後設基準線；欄位變更 debounce 800ms 寫入草稿，用於崩潰 / 離開後還原。
    var draftBaseline by remember { mutableStateOf<Concert?>(null) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(400)   // 等載入 / 草稿還原把欄位填好，再定基準線
        draftBaseline = runCatching { buildConcert() }.getOrNull()
        androidx.compose.runtime.snapshotFlow { runCatching { buildConcert() }.getOrNull() }
            .debounce(800)
            .collect { c -> if (c != null && c != draftBaseline) viewModel.saveDraft(draftKey, c) }
    }
    // 返回時若有未提交變更 → 捨棄 / 繼續編輯（spec §8）。
    var showDiscardDialog by remember { mutableStateOf(false) }
    val onBackRequest: () -> Unit = {
        val cur = runCatching { buildConcert() }.getOrNull()
        if (draftBaseline != null && cur != null && cur != draftBaseline) showDiscardDialog = true else onBack()
    }
    androidx.activity.compose.BackHandler { onBackRequest() }

    // 快速模式：把 artists 併回單一輸入框顯示（切進快速模式、或掃描/載入把 artists 換掉後都對得上）。
    // 刻意不以 artists 當 key，避免打字當下被 join 回去、游標亂跳。
    LaunchedEffect(quickMode, showOcr) {
        if (quickMode && !showOcr) quickArtist = artists.filter { it.isNotBlank() }.joinToString(" ｜ ")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titleText, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackRequest) {
                        Icon(Icons.Outlined.Close, contentDescription = "關閉")
                    }
                },
                actions = {
                    if (existingConcertId != null) {
                        IconButton(onClick = { onDuplicate(existingConcertId) }) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = "複製為新演唱會")
                        }
                        // 匯出票根是「動作」不是欄位（spec §5.3）：移到 ⋮ 選單。
                        Box {
                            IconButton(onClick = { showEditMenu = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "更多")
                            }
                            androidx.compose.material3.DropdownMenu(
                                expanded = showEditMenu,
                                onDismissRequest = { showEditMenu = false }
                            ) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("匯出票根") },
                                    onClick = { showEditMenu = false; onOpenTicketShare(existingConcertId) }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            // 底部唯一提交點（spec §1、§8）：儲存 fillMaxWidth，未變更 / 儲存中則 disabled。
            androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                Column(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    androidx.compose.material3.Button(
                        onClick = doSave,
                        enabled = isValid && !isSaving,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isSaving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else Text("儲存", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "變更會即時暫存",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        // ── 票根 Hero + 固定 Tab + Pager（spec §1）。Hero 取自目前編輯中的內容（buildConcert）。──
        // 為保護捲動手感（本專案手勢吃捲動已踩雷多次），先用「靜態 Hero」不做收合動畫（spec §2 允許）。
        val heroConcert = buildConcert()
        val heroDate = heroConcert.dateTime.date
        val heroToday = nowDateTime.date
        val heroCountdown = if (heroDate < heroToday) null else heroToday.daysUntil(heroDate)
        val heroCover = com.bandary.presentation.ui.components.effectiveCoverUri(context, heroConcert)
        Column(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            com.bandary.presentation.ui.shows.edit.TicketHero(
                countdownDays = heroCountdown,
                coverUri = heroCover,
                title = heroConcert.name,
                // 「日期 時間 · 場地」。壓縮列只取 " · " 之前那段（日期時間），場地不顯示。
                dateVenue = "%04d/%02d/%02d %02d:%02d".format(
                    heroDate.year, heroDate.monthNumber, heroDate.dayOfMonth,
                    heroConcert.dateTime.hour, heroConcert.dateTime.minute
                ) + (heroConcert.venue.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                seat = heroConcert.seatInfo,
                price = heroConcert.ticketPrice?.takeIf { it > 0 }?.let { "NT$${it.toInt()}" },
                statusLabel = heroConcert.ticketStatus.label,
                onCoverClick = { tabScope.launch { pagerState.animateScrollToPage(0) } },
                collapsed = heroCollapsed,
                // 收合狀態只有一個來源（捲動位置），所以點收合列＝捲回頂端，它就會自己展開。
                onToggleCollapse = { formScope.launch { activeListState.animateScrollToItem(0) } },
                modifier = Modifier.padding(top = 8.dp).animateContentSize(tween(220))
            )
            // 模式切換：快速新增（欄位對齊網頁版）／完整編輯（演出・票務・心得三分頁）。
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                com.bandary.presentation.ui.shows.edit.SegmentedRow(
                    options = listOf(true, false),
                    selected = quickMode,
                    labelOf = { if (it) "快速新增" else "完整編輯" },
                    onSelect = { quickMode = it }
                )
            }
            if (quickMode) {
                // ── 快速新增：與網頁版同一組核心欄位，單頁捲動 ──
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Spacer(Modifier.height(4.dp))
                    androidx.compose.material3.FilledTonalButton(
                        onClick = { startOcrPick() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("📷 掃描票券自動填寫", fontWeight = FontWeight.SemiBold)
                    }
                    FormSection("演出名稱 *", wide = true) {
                        OutlinedTextField(value = name, onValueChange = { name = it },
                            placeholder = { Text("名稱") }, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp), singleLine = true, colors = minimalFieldColors())
                    }
                    FormSection("歌手 / 樂團", wide = true) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            OutlinedTextField(
                                value = quickArtist,
                                onValueChange = { v ->
                                    quickArtist = v
                                    // 認明確分隔符與「 / 」（官方卡司最常見的寫法，整份貼上就會自動拆）。
                                    // 單一空格仍不拆：邊打邊用空格拆會在團名還沒打完時就切成好幾位。
                                    artists = com.bandary.domain.model.splitArtistNamesPasted(v)
                                        .ifEmpty { listOf("") }
                                },
                                placeholder = { Text("整份卡司可直接貼上（｜ 、 / 換行都會自動拆）") },
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                                // 使用者回報：貼一整份卡司進來只看得到一行，看不到自己貼了什麼。
                                // 這個欄位本來就是設計成「整份卡司直接貼上」的，
                                // 單行等於把它最主要的用途做成看不見。
                                //
                                // 上限 5 行：再多就把下面的日期時間推出畫面，
                                // 而且那時候「快速新增」也已經不是對的工具了——
                                // 卡司長到五行的場次，使用者真正需要的是完整編輯那邊
                                // 一位一列、可以各自加嘉賓的介面。所以超過就引導過去，
                                // 而不是讓這個欄位一直長高。
                                singleLine = false, minLines = 1, maxLines = 5,
                                colors = minimalFieldColors())
                            if (artists.count { it.isNotBlank() } > 5) {
                                Surface(
                                    onClick = { quickMode = false },
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "已經有 ${artists.count { it.isNotBlank() }} 位藝人——" +
                                                "切到「完整編輯」可以一位一列、各自加嘉賓",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            Icons.AutoMirrored.Outlined.KeyboardArrowRight, null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                            // 只有「用空格拆真的會多出幾位」時才出現，平常不佔位置。
                            val bySpace = com.bandary.domain.model.splitArtistNames(quickArtist)
                            val explicit = com.bandary.domain.model.splitArtistNamesPasted(quickArtist)
                            if (bySpace.size > explicit.size) {
                                TextButton(onClick = {
                                    artists = bySpace.ifEmpty { listOf("") }
                                    quickArtist = bySpace.joinToString("｜")
                                }) { Text("依空格拆成 ${bySpace.size} 位藝人", fontSize = 13.sp) }
                            }
                        }
                    }
                    FormSection("日期 & 時間", wide = true) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(onClick = { showDatePicker = true },
                                modifier = Modifier.weight(1.3f).height(56.dp), shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                                Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${selectedDate.year}/${selectedDate.month.value.toString().padStart(2, '0')}/${selectedDate.dayOfMonth.toString().padStart(2, '0')}",
                                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                    Icon(Icons.Outlined.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                }
                            }
                            Surface(onClick = { showTimePicker = true },
                                modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                                Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${selectedTime.hour.toString().padStart(2, '0')}:${selectedTime.minute.toString().padStart(2, '0')}",
                                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                    FormSection("場地", wide = true) {
                        // 快速新增也要有建議（使用者要求：「不管是不是完整編輯都要有」）。
                        // 快速新增沒有地址欄，所以這裡只帶入場館名與縣市。
                        var qVenueOpen by remember { mutableStateOf(false) }
                        val qVenueHits = remember(venue, knownVenues) {
                            val q = venue.trim()
                            if (q.isBlank()) emptyList()
                            else com.bandary.domain.model.NameSuggest
                                .suggest(q, knownVenues.map { it.name }, limit = 5)
                                .mapNotNull { n -> knownVenues.firstOrNull { it.name == n } }
                        }
                        Box {
                            OutlinedTextField(value = venue,
                                onValueChange = { venue = it; qVenueOpen = true },
                                placeholder = { Text("場地名稱") }, modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp), singleLine = true, colors = minimalFieldColors())
                            DropdownMenu(
                                expanded = qVenueOpen && qVenueHits.isNotEmpty(),
                                onDismissRequest = { qVenueOpen = false },
                                properties = androidx.compose.ui.window.PopupProperties(focusable = false)
                            ) {
                                qVenueHits.forEach { hint ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(hint.name)
                                                if (hint.hintLine.isNotBlank()) {
                                                    Text(hint.hintLine,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        },
                                        onClick = {
                                            venue = hint.name
                                            hint.address?.takeIf { it.isNotBlank() }?.let { address = it }
                                            hint.city?.takeIf { it.isNotBlank() }?.let { selectedCity = it }
                                            qVenueOpen = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    FormSection("縣市 & 票價", wide = true) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(onClick = { showCityPicker = true },
                                modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)) {
                                Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(selectedCity ?: "選擇縣市", style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        color = if (selectedCity == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            OutlinedTextField(value = ticketPrice, onValueChange = { ticketPrice = it.filter { c -> c.isDigit() } },
                                placeholder = { Text("NT$ 票價") }, modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp), singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = minimalFieldColors())
                        }
                    }
                    FormSection("售票平台", wide = true) {
                        OutlinedTextField(value = platform ?: "", onValueChange = { platform = it.ifBlank { null } },
                            placeholder = { Text("例：拓元、KKTIX、寬宏…") }, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp), singleLine = true, colors = minimalFieldColors())
                    }
                    FormSection("購票狀況", wide = true) {
                        com.bandary.presentation.ui.shows.edit.SegmentedRow(
                            options = TicketStatus.selectable, selected = ticketStatus,
                            labelOf = { it.label }, onSelect = { ticketStatus = it })
                    }
                    FormSection("備忘錄", wide = true) {
                        OutlinedTextField(value = notes, onValueChange = { notes = it },
                            placeholder = { Text("輸入備註…") }, modifier = Modifier.fillMaxWidth().height(110.dp),
                            shape = RoundedCornerShape(12.dp), colors = minimalFieldColors())
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("要卡司時間表、取票資訊、歌單、照片、提醒…？切到上方「完整編輯」。",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(padding.calculateBottomPadding() + 24.dp))
                }
            } else {
            androidx.compose.material3.TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                listOf("演出", "票務", "心得").forEachIndexed { i, label ->
                    androidx.compose.material3.Tab(
                        selected = pagerState.currentPage == i,
                        onClick = { tabScope.launch { pagerState.animateScrollToPage(i) } },
                        text = { Text(label, fontWeight = FontWeight.SemiBold, maxLines = 1) }
                    )
                }
            }
            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) { page ->
                LazyColumn(
                    state = pageListStates[page],
                    modifier = Modifier.fillMaxSize().imePadding(),
                    // 底部 padding 含「儲存」列高度，最後幾項才能捲到儲存列之上；imePadding 處理鍵盤。
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp + padding.calculateBottomPadding()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    when (page) {
                    // ── Tab 1：演出 ──
                    0 -> {

            // 掃描票券主入口（spec §4.4）：演出 tab 第一屏第一個元素，辨識後帶入欄位。
            item {
                androidx.compose.material3.FilledTonalButton(
                    onClick = { startOcrPick() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.AutoAwesome, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("📷 掃描票券自動填寫", fontWeight = FontWeight.SemiBold)
                }
            }

            item { FormGroupHeader("基本資料", first = true) }
            item {
                FormSection("演出名稱 *") {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("名稱") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = minimalFieldColors()
                    )
                }
            }
            // ── 海報提示 ──
            // 使用者要的是「輸入藝人或演唱會名字就自動搜尋跳出圖片問你是不是」。
            //
            // **刻意不自動搜。** 圖片搜尋走的是 Google Custom Search 的免費額度：
            // 一天 100 次，而且是**所有使用者共用的一份**（見 ImageSearchService 的說明）。
            // 邊打字邊搜的話，十個人各新增一場就把全 App 的額度用完，
            // 後面的人按「找海報」只會拿到錯誤——那比沒有這個功能更糟。
            //
            // 所以改成「主動問、你按才搜」：填了名稱或藝人、而且還沒有任何圖片時，
            // 這裡冒出一句。該有的效果（不必自己找到那顆按鈕）有了，額度也還在。
            item {
                val posterQuery = name.trim().ifBlank { artists.firstOrNull()?.trim().orEmpty() }
                if (!posterPromptDismissed && photoUris.isEmpty() && posterQuery.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.ImageSearch, null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "要幫你找「$posterQuery」的海報嗎？",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "到網路上找幾張圖給你挑",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = {
                                showPosterSearch = true
                                viewModel.findPoster(artists.firstOrNull().orEmpty(), name)
                            }) { Text("找海報", fontSize = 13.sp) }
                            IconButton(
                                onClick = { posterPromptDismissed = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Close, "不用了",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item {
                FormSection("歌手 / 樂團", wide = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // 只留「嘉賓不佔時數」這句——那是看不出來、又會影響統計的規則；
                        // 拖曳排序、多位用、分隔這些操作提示已由 UI 本身表達（握把圖示、輸入框 placeholder）。
                        Text(
                            "嘉賓算進看過的藝人，但不佔這場的現場時數",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val density = androidx.compose.ui.platform.LocalDensity.current
                        var dragFrom by remember { mutableStateOf(-1) }
                        var dragDelta by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
                        artists.forEachIndexed { idx, a ->
                            val isDragging = dragFrom == idx
                            val multiDay = occurrences.size > 1 && a.isNotBlank()
                            Row(
                                modifier = Modifier
                                    // 拖曳中：抬起（zIndex 疊上其他列）、跟著手指移動、加底色，做出「體驗感」。
                                    .zIndex(if (isDragging) 1f else 0f)
                                    .graphicsLayer { translationY = if (isDragging) dragDelta else 0f }
                                    .then(
                                        if (isDragging) Modifier.background(
                                            MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)
                                        ) else Modifier
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // 長按握把拖曳排序：拖過半列高度就即時交換順序（邊拖邊排，不再是放開才跳一次）。
                                Icon(
                                    Icons.Outlined.DragHandle, "長按拖曳排序",
                                    modifier = Modifier.size(24.dp)
                                        .pointerInput(artists.size) {
                                            val rowPx = with(density) { 60.dp.toPx() }
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = { dragFrom = idx; dragDelta = 0f },
                                                onDrag = { change, amt ->
                                                    change.consume()
                                                    dragDelta += amt.y
                                                    while (dragDelta > rowPx / 2 && dragFrom < artists.lastIndex) {
                                                        artists = artists.toMutableList().also { it.add(dragFrom, it.removeAt(dragFrom + 1)) }
                                                        dragFrom += 1
                                                        dragDelta -= rowPx
                                                    }
                                                    while (dragDelta < -rowPx / 2 && dragFrom > 0) {
                                                        artists = artists.toMutableList().also { it.add(dragFrom, it.removeAt(dragFrom - 1)) }
                                                        dragFrom -= 1
                                                        dragDelta += rowPx
                                                    }
                                                },
                                                onDragEnd = { dragFrom = -1; dragDelta = 0f },
                                                onDragCancel = { dragFrom = -1; dragDelta = 0f }
                                            )
                                        },
                                    tint = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                // 藝人輸入 + 已看過的藝人自動建議（邊打邊比對，點一下就填入）。
                                var suggestOpen by remember { mutableStateOf(false) }
                                val suggestions = remember(a, knownArtists) {
                                    val q = a.trim()
                                    // 比對改用 NameSuggest（字序無關）：原本的 contains 照順序比，
                                    // 打「青少年公館」比不到「公館青少年GGteen」（實機回報）。
                                    // 已經填在這張表單上的也排除，不要建議重複的。
                                    if (q.isBlank()) emptyList()
                                    else com.bandary.domain.model.NameSuggest.suggest(
                                        q, knownArtists, exclude = artists.map { n -> n.trim() }
                                    )
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    OutlinedTextField(
                                        value = a,
                                        onValueChange = { v ->
                                            // 貼上一整份卡司（含 ｜ 、 ／ ; 換行 或「 / 」）時直接展開成多列，
                                            // 不用自己一位一位剪貼。單一空格仍不拆（團名本身常有空格）。
                                            val parts = com.bandary.domain.model.splitArtistNamesPasted(v)
                                            artists = if (parts.size > 1) {
                                                val list = artists.toMutableList()
                                                list.removeAt(idx)
                                                val existing = list.map { it.trim() }.filter { it.isNotBlank() }.toSet()
                                                list.addAll(idx, parts.filter { it !in existing })
                                                list.ifEmpty { listOf("") }
                                            } else {
                                                artists.toMutableList().also { it[idx] = v }
                                            }
                                            suggestOpen = true
                                        },
                                        placeholder = { Text(if (idx == 0) "主要藝人" else "藝人名稱・可整份貼上") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true,
                                        colors = minimalFieldColors()
                                    )
                                    DropdownMenu(
                                        expanded = suggestOpen && suggestions.isNotEmpty(),
                                        onDismissRequest = { suggestOpen = false },
                                        // 不搶走輸入焦點，才能邊打字邊看建議。
                                        properties = androidx.compose.ui.window.PopupProperties(focusable = false)
                                    ) {
                                        suggestions.forEach { s ->
                                            DropdownMenuItem(
                                                text = { Text(s) },
                                                onClick = {
                                                    artists = artists.toMutableList().also { it[idx] = s }
                                                    suggestOpen = false
                                                }
                                            )
                                        }
                                    }
                                }
                                // FEAT：這位主要藝人「找了誰來客串」，直接在同一行右邊填。
                                // 沒填過就只顯示一顆小 +feat 鈕，不佔版面；填了就展開成輸入欄。
                                val featGuest = featByArtist[a.trim()].orEmpty()
                                var featOpen by remember(a.trim()) { mutableStateOf(featGuest.isNotBlank()) }
                                if (featOpen) {
                                    OutlinedTextField(
                                        value = featGuest,
                                        onValueChange = { v ->
                                            val key = a.trim()
                                            featByArtist = if (key.isBlank()) featByArtist
                                            else if (v.isBlank()) featByArtist - key
                                            else featByArtist + (key to v)
                                        },
                                        // 多位客串用、或 , 分隔，存檔時會拆成一位一筆。
                                        placeholder = { Text("嘉賓名字・多位用、分隔", fontSize = 11.sp) },
                                        modifier = Modifier.weight(0.8f),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true,
                                        colors = minimalFieldColors()
                                    )
                                } else {
                                    androidx.compose.material3.Surface(
                                        onClick = { featOpen = true },
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            "＋嘉賓",
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                // 多日活動：這位藝人哪天演出——改成 row 內緊湊按鈕，不再獨佔一整列
                                if (multiDay) {
                                    var dayMenu by remember { mutableStateOf(false) }
                                    val cur = artistDayByName[a.trim()].orEmpty()
                                    val curLabel = occurrences.firstOrNull { it.toString() == cur }?.let { "${it.monthNumber}/${it.dayOfMonth}" } ?: "全日"
                                    Box {
                                        androidx.compose.material3.TextButton(onClick = { dayMenu = true }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                                            Icon(Icons.Outlined.CalendarMonth, null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.size(2.dp))
                                            Text(curLabel, fontSize = 11.sp)
                                        }
                                        DropdownMenu(expanded = dayMenu, onDismissRequest = { dayMenu = false }) {
                                            DropdownMenuItem(text = { Text("全部日期") }, onClick = { artistDayByName = artistDayByName - a.trim(); dayMenu = false })
                                            occurrences.forEach { d ->
                                                DropdownMenuItem(text = { Text("${d.monthNumber}/${d.dayOfMonth}") }, onClick = { artistDayByName = artistDayByName + (a.trim() to d.toString()); dayMenu = false })
                                            }
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = { artists = artists.toMutableList().also { it.removeAt(idx) }.ifEmpty { listOf("") } },
                                    enabled = artists.size > 1, modifier = Modifier.size(36.dp)
                                ) { Icon(Icons.Outlined.Close, "移除", modifier = Modifier.size(20.dp)) }
                            }
                        }
                        // 新增歌手 + 貼上名單同一行（依使用者偏好，省版面）。掃描圖片已上移為第一屏主入口。
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { artists = artists + "" },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("新增歌手", fontSize = 13.sp, maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { showPasteArtists = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Outlined.ContentPaste, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("貼上名單", fontSize = 13.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }

            item {
                FormSection("演出類型") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // 下拉選單取代長長一排 chip；不放「免費活動」——免費以票價 0 表示（票價欄留空即免費）。
                        val typeOptions = ConcertType.entries.filter { it != ConcertType.FREE }
                        DropdownField(
                            current = selectedType,
                            options = typeOptions,
                            labelOf = { it.label },
                            onSelect = { selectedType = it }
                        )
                        // 選「自訂」時，讓使用者輸入自己的類型名稱（例如 音樂劇 / Livehouse / 音樂祭）
                        if (selectedType == ConcertType.OTHER) {
                            OutlinedTextField(
                                value = customType,
                                onValueChange = { customType = it },
                                singleLine = true,
                                label = { Text("自訂類型名稱") },
                                placeholder = { Text("例如：音樂劇、Livehouse、音樂祭") },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
            item {
                FormSection("日期 & 時間", wide = true) {
                    // 日期獨佔一行：三個欄位擠一行時「2026-08-15」加行事曆圖示會被截掉（跑版）。
                    // 第二行才放開始 / 結束時間，兩欄各半剛好夠 "19:00"。
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        "日期",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "${selectedDate.year}/${selectedDate.month.value.toString().padStart(2, '0')}/${selectedDate.dayOfMonth.toString().padStart(2, '0')}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                                Icon(
                                    Icons.Outlined.CalendarMonth,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(
                                onClick = { showTimePicker = true },
                                modifier = Modifier.weight(1f).height(56.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        "開始",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "${selectedTime.hour.toString().padStart(2, '0')}:${selectedTime.minute.toString().padStart(2, '0')}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                            }
                            // 結束時間：單日演出也要能填。沒填的話統計的現場時數只能用類型預設值推估。
                            Surface(
                                onClick = { showEndTimePicker = true },
                                modifier = Modifier.weight(1f).height(56.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        "結束",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        endTime?.let { "${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}" } ?: "未填",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        color = if (endTime == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item { FormGroupHeader("時間與地點") }
            // 「多日演出」與「其他日期」原本是兩個各自獨立的區塊，讀起來像在講同一件事。
            // 整併成單一「加開的日期」：**連續**跨天用結束日期，**不連續**的另外加日期，
            // 兩者都會併進場次的所有演出日期，只是輸入方式不同，這裡把差別直接寫在畫面上。
            item {
                FormSection("加開的日期", wide = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // 一律一天一天加；區塊標題「加開的日期」已足夠說明，不再加說明句。
                        // 一橫排：已加入的日期 chips 與「＋日期」按鈕同列；會一併顯示於月曆。
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            extraDates.sorted().forEach { d ->
                                InputChip(
                                    selected = false,
                                    onClick = { extraDates = extraDates - d },
                                    label = { Text("${d.monthNumber}/${d.dayOfMonth}", fontSize = 12.sp) },
                                    trailingIcon = { Icon(Icons.Outlined.Close, "移除", modifier = Modifier.size(14.dp)) }
                                )
                            }
                            androidx.compose.material3.AssistChip(
                                onClick = { showExtraDatePicker = true },
                                label = { Text("＋日期", fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Outlined.Add, null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                    }
                }
            }

            item {
                // 地點：國家 + 縣市同一列，接著場地、地址（使用者要求的順序）。
                FormSection("地點", wide = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            // 實機回報：「填寫地址欄顯示『國家』是什麼意思？填美國或澳大利亞，
                            // 對應的選擇縣市要手動嗎？」——會這樣問是因為畫面什麼都沒講：
                            // 只有一個 placeholder「國家」，看不出它可不可以留空、留空等於什麼。
                            // 實際上**留空就是台灣**（統計一律把 null / 空白算成台灣），
                            // 所以用 label 固定顯示欄名，placeholder 直接寫出預設值。
                            OutlinedTextField(
                                value = country,
                                onValueChange = { country = it },
                                label = { Text("國家") },
                                placeholder = { Text("留空＝台灣") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                colors = minimalFieldColors()
                            )
                            Surface(
                                onClick = { showCityPicker = true },
                                modifier = Modifier.weight(1f).height(56.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        // 海外場次按「選擇縣市」進去只看到台灣縣市，會以為不支援。
                                        // 文字跟著國家走，進去之前就知道該打字還是該挑。
                                        selectedCity ?: if (isOverseas) "輸入城市" else "選擇縣市",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        color = if (selectedCity == null)
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                        // 場館自動補完。比對走 NameSuggest（與藝人同一套：字序無關、算字元次數），
                        // 因為打場館的習慣一樣——記得「小巨蛋」，不記得全名是「台北小巨蛋」還是
                        // 「臺北市立小巨蛋」。選中會一併帶入上次填過的地址與縣市：
                        // 同一個場館的地址不會變，讓人每次重打一次是沒有道理的。
                        var venueSuggestOpen by remember { mutableStateOf(false) }
                        val venueHits = remember(venue, knownVenues) {
                            val q = venue.trim()
                            if (q.isBlank()) emptyList()
                            else {
                                val names = com.bandary.domain.model.NameSuggest.suggest(
                                    q, knownVenues.map { it.name }, limit = 5
                                )
                                names.mapNotNull { n -> knownVenues.firstOrNull { it.name == n } }
                            }
                        }
                        Box {
                            OutlinedTextField(
                                value = venue,
                                onValueChange = { venue = it; venueSuggestOpen = true },
                                placeholder = { Text("場地名稱") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                colors = minimalFieldColors()
                            )
                            DropdownMenu(
                                expanded = venueSuggestOpen && venueHits.isNotEmpty(),
                                onDismissRequest = { venueSuggestOpen = false },
                                properties = androidx.compose.ui.window.PopupProperties(focusable = false)
                            ) {
                                venueHits.forEach { hint ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(hint.name)
                                                if (hint.hintLine.isNotBlank()) {
                                                    Text(
                                                        hint.hintLine,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            venue = hint.name
                                            // 空白不要覆蓋掉使用者已經打好的東西。
                                            hint.address?.takeIf { it.isNotBlank() }?.let { address = it }
                                            hint.city?.takeIf { it.isNotBlank() }?.let { selectedCity = it }
                                            venueSuggestOpen = false
                                        }
                                    )
                                }
                            }
                        }
                        // 座位視野：填了場地才有意義（查詢是用場館名稱比對的）。
                        // 放在場地欄位正下方，而不是另開一個頁籤——
                        // 會想看視野的那一刻，就是正在填場地、挑位子的那一刻。
                        if (venue.isNotBlank()) {
                            TextButton(
                                onClick = { showVenueViews = true },
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Visibility, null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("看這個場館的座位視野", fontSize = 13.sp)
                            }
                        }
                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            placeholder = { Text("地址") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            colors = minimalFieldColors()
                        )
                        // 地址填了才給地圖按鈕。沒地址時用場館名查也行，但查出來的
                        // 可能是同名的別間店——寧可不給，不要給一個錯的位置。
                        if (address.isNotBlank()) {
                            val mapCtx = androidx.compose.ui.platform.LocalContext.current
                            TextButton(
                                onClick = {
                                    val q = android.net.Uri.encode(address.trim())
                                    runCatching {
                                        mapCtx.startActivity(
                                            android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                android.net.Uri.parse("geo:0,0?q=$q")
                                            )
                                        )
                                    }.onFailure {
                                        // 沒裝地圖 App 就開網頁版，不要讓按鈕按了沒反應。
                                        runCatching {
                                            mapCtx.startActivity(
                                                android.content.Intent(
                                                    android.content.Intent.ACTION_VIEW,
                                                    android.net.Uri.parse(
                                                        "https://www.google.com/maps/search/?api=1&query=$q"
                                                    )
                                                )
                                            )
                                        }
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Place, null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("在地圖開啟", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            if (restoredFromDraft) {
                item {
                    // 自動還原如果沒說出來，下次新增看到上次的殘留會以為是 bug。
                    // 說一句 + 給一個「清空重來」，兩種情境就都有出路了。
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "已還原上次沒存完的內容",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                viewModel.clearDraft(draftKey)
                                restoredFromDraft = false
                                onBack()
                            }) { Text("清空重來", fontSize = 13.sp) }
                        }
                    }
                }
            }

            item { FormGroupHeader("其他") }
            item {
                FormSection("活動粉專") {
                    OutlinedTextField(
                        value = infoUrl,
                        onValueChange = { infoUrl = it },
                        placeholder = { Text("連結") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = minimalFieldColors()
                    )
                }
            }

            item {
                FormSection("演出前提醒", wide = false) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        var reminderMenuOpen by remember { mutableStateOf(false) }
                        var showCustomReminder by remember { mutableStateOf(false) }
                        // 一橫排的下拉：摘要目前已選的提醒；點開勾選各項預設提醒，或自訂時間。
                        val summary = run {
                            val presets = ReminderOffset.entries.filter { it in selectedReminders }.map { it.label }
                            val custom = if (customReminders.isNotEmpty()) listOf("自訂 ${customReminders.size}") else emptyList()
                            (presets + custom).takeIf { it.isNotEmpty() }?.joinToString("、") ?: "未設定提醒"
                        }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Surface(
                                onClick = { reminderMenuOpen = true },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Outlined.Alarm, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text(summary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    Text("▾", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            DropdownMenu(expanded = reminderMenuOpen, onDismissRequest = { reminderMenuOpen = false }) {
                                ReminderOffset.entries.forEach { r ->
                                    val on = r in selectedReminders
                                    DropdownMenuItem(
                                        text = { Text((if (on) "✓  " else "　  ") + r.label, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal) },
                                        onClick = { selectedReminders = if (on) selectedReminders - r else selectedReminders + r }
                                    )
                                }
                                androidx.compose.material3.HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("自訂時間…") },
                                    onClick = { showCustomReminder = true; reminderMenuOpen = false }
                                )
                            }
                        }
                        // 已加入的自訂時間提醒（可移除）
                        if (customReminders.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                customReminders.sortedBy { it }.forEach { at ->
                                    InputChip(
                                        selected = false,
                                        onClick = { customReminders = customReminders - at },
                                        label = { Text("${at.monthNumber}/${at.dayOfMonth} ${at.hour.toString().padStart(2, '0')}:${at.minute.toString().padStart(2, '0')}", fontSize = 12.sp) },
                                        trailingIcon = { Icon(Icons.Outlined.Close, "移除", modifier = Modifier.size(14.dp)) }
                                    )
                                }
                            }
                        }
                        // 自訂時間新增列（點「自訂時間…」才展開，平時不佔空間）
                        if (showCustomReminder) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = { showCustomRemDate = true },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(customReminderDate?.let { "${it.year}/${it.monthNumber}/${it.dayOfMonth}" } ?: "選日期", fontSize = 13.sp)
                                }
                                OutlinedButton(
                                    onClick = { showCustomRemTime = true },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("${customReminderTime.hour.toString().padStart(2, '0')}:${customReminderTime.minute.toString().padStart(2, '0')}", fontSize = 13.sp)
                                }
                                androidx.compose.material3.FilledTonalButton(
                                    onClick = {
                                        val d = customReminderDate ?: return@FilledTonalButton
                                        val at = LocalDateTime(d, customReminderTime)
                                        if (at !in customReminders) customReminders = customReminders + at
                                        customReminderDate = null
                                    },
                                    enabled = customReminderDate != null,
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Outlined.Add, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.size(4.dp))
                                    Text("加入", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 逐場自訂：同步到 Google 日曆。僅在已連結、且為「新增」時有意義
            // （編輯不會重新推送，避免重複建立事件）。
            if (viewModel.isCalendarConnected() && existingConcertId == null) {
                item {
                    FormSection("同步到 Google 日曆", wide = true) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "儲存時建立一則 Google 日曆行程",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Switch(
                                    checked = syncToCalendar,
                                    onCheckedChange = { syncToCalendar = it }
                                )
                            }
                        }
                    }
                }
            }

            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // 大張即時預覽已移除：上方票根 Hero 現在就是用封面當底圖，換圖立刻在那裡看到，
                        // 不必在表單裡再放一次同一張圖佔掉近 200dp。編輯入口（內建封面 / 更多照片）保留。
                        // 內建封面（縮小的縮圖，48dp）——拿掉「自訂圖片 / 移除」兩顆按鈕；自訂封面請用下方「更多照片」上傳後設為封面。
                        val posterBgIdxs = com.bandary.presentation.ui.share.PosterBackgrounds.availableIndices(context)
                        Text("內建封面", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(posterBgIdxs) { idx ->
                                val uri = com.bandary.presentation.ui.share.PosterBackgrounds.coverUri(context, idx) ?: return@items
                                val sel = photoUri == uri
                                Box(
                                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                                        .border(BorderStroke(if (sel) 3.dp else 1.dp,
                                            if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline), RoundedCornerShape(8.dp))
                                        .clickable { photoUri = if (sel) null else uri; coverGradientLight = false }
                                ) {
                                    AsyncImage(model = uri, contentDescription = null,
                                        contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                                }
                            }
                        }

                        // 統一相簿：這場的所有照片都在這裡，點一張圖打開設定（封面 / 場位圖 / 節目表 / 順序 / 移除）。
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("照片", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.weight(1f))
                            if (photoUris.isNotEmpty()) {
                                TextButton(
                                    onClick = {
                                        photoSelectMode = !photoSelectMode
                                        if (!photoSelectMode) photoSelection = emptySet()
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text(if (photoSelectMode) "取消選取" else "選取",
                                        style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Text("${photoUris.size} / $photoQuota", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        // 讀不到的照片：一鍵全部移除。留著只會一直佔張數、也備份不上去。
                        if (brokenPhotos.isNotEmpty()) {
                            Surface(
                                onClick = { removePhotos(brokenPhotos) },
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Outlined.BrokenImage, null, modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onErrorContainer)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("有 ${brokenPhotos.size} 張照片已經讀不到",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onErrorContainer)
                                        Text("原圖被刪或授權失效；留著會佔張數也備份不上去",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer)
                                    }
                                    Text("全部移除", style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                        }
                        if (photoSelectMode) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = {
                                        photoSelection = if (photoSelection.size == photoUris.size) emptySet()
                                        else photoUris.toSet()
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text(if (photoSelection.size == photoUris.size) "取消全選" else "全選",
                                        style = MaterialTheme.typography.labelSmall)
                                }
                                Spacer(Modifier.weight(1f))
                                TextButton(
                                    onClick = {
                                        removePhotos(photoSelection)
                                        photoSelectMode = false
                                    },
                                    enabled = photoSelection.isNotEmpty(),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text("移除選取的 ${photoSelection.size} 張",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (photoSelection.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                                        else MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(photoUris, key = { uri -> uri }) { uri ->
                                val role = photoRoles[uri]
                                val badge = when {
                                    photoUri == uri -> "封面"
                                    role == PHOTO_ROLE_SEAT -> "場位圖"
                                    role == PHOTO_ROLE_TIMETABLE -> "節目表"
                                    else -> null
                                }
                                val picked = uri in photoSelection
                                val broken = uri in brokenPhotos
                                Box(modifier = Modifier.size(88.dp)) {
                                    AsyncImage(model = uri, contentDescription = null,
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp))
                                            .clickable {
                                                if (photoSelectMode) {
                                                    photoSelection = if (picked) photoSelection - uri else photoSelection + uri
                                                } else photoSheetUri = uri
                                            }, contentScale = ContentScale.Crop)
                                    if (photoSelectMode) {
                                        Box(
                                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    if (picked) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                                    else Color.Black.copy(alpha = 0.15f)
                                                )
                                        )
                                        Icon(
                                            if (picked) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                            contentDescription = if (picked) "已選取" else "未選取",
                                            tint = Color.White,
                                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp)
                                        )
                                    }
                                    if (broken) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.error,
                                            shape = RoundedCornerShape(bottomEnd = 6.dp),
                                            modifier = Modifier.align(Alignment.TopStart)
                                        ) {
                                            Text("讀不到", color = MaterialTheme.colorScheme.onError,
                                                fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                        }
                                    }
                                    if (badge != null) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(topEnd = 6.dp),
                                            modifier = Modifier.align(Alignment.BottomStart)
                                        ) {
                                            Text(badge, color = MaterialTheme.colorScheme.onPrimary,
                                                fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                        }
                                    }
                                }
                            }
                            if (photoUris.size < photoQuota) {
                                item {
                                    Surface(
                                        onClick = { photoPickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                        modifier = Modifier.size(88.dp), shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Outlined.AddPhotoAlternate, "新增照片", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                                // 找海報：原本這個功能整組都寫好了，卻沒有任何地方打得開
                                // （`showPosterSearch` 從來沒有被設成 true），等於沒做。
                                item {
                                    Surface(
                                        onClick = {
                                            showPosterSearch = true
                                            viewModel.findPoster(artists.firstOrNull().orEmpty(), name)
                                        },
                                        modifier = Modifier.size(88.dp), shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxSize().padding(6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(Icons.Outlined.ImageSearch, "找海報", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                                            Spacer(Modifier.height(2.dp))
                                            Text("找海報", style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                                item {
                                    Surface(
                                        onClick = {
                                            val uri = clipboardImageUri(context)
                                            if (uri != null) {
                                                formScope.launch {
                                                    val local = withContext(Dispatchers.IO) { ImageStore.persist(context, uri) }
                                                    if (local != null && photoUris.size < photoQuota) {
                                                        if (photoUri.isNullOrBlank()) photoUri = local
                                                        photoUris = (photoUris + local).distinct()
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(88.dp), shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Outlined.ContentPaste, "貼上剪貼簿圖片", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            } else if (!vmState.isProPlus) {
                                // 已達目前方案上限：用一張可點的卡片導去 Pro 頁，而不是靜默不給加——
                                // 這是最自然的加購時機（使用者剛好想再加照片）。
                                item {
                                    Surface(
                                        onClick = { showPhotoQuotaDialog = true },
                                        modifier = Modifier.size(88.dp), shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxSize().padding(6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(Icons.Outlined.Lock, "已達上限", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                            Text("升級加張數", style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                                        }
                                    }
                                }
                            }
                        }
                        Text(
                            if (photoSelectMode) "點照片勾選，可一次移除多張。"
                            else "點照片可設為封面、場位圖或節目表；要一次刪多張請按「選取」。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                    }
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                }
            }

            // 各日封面（多日活動）：緊接在圖片區塊之後，從上方「更多照片」已上傳的圖片挑選，不再重新上傳。
            if (occurrences.size > 1) {
                item {
                    CollapsibleSection("各日封面", "多日活動可為每天設不同封面") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            occurrences.forEachIndexed { idx, d ->
                                val cover = dayCovers.getOrNull(idx)?.takeIf { it.isNotBlank() }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp))) {
                                        if (cover != null) {
                                            AsyncImage(model = cover, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                        } else {
                                            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
                                        }
                                    }
                                    Text("${d.monthNumber}/${d.dayOfMonth}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    TextButton(onClick = { dayCoverPickFor = idx }) {
                                        Text(if (cover == null) "選擇" else "更換", fontSize = 13.sp)
                                    }
                                    if (cover != null) {
                                        IconButton(onClick = {
                                            val list = dayCovers.toMutableList(); if (idx < list.size) list[idx] = ""; dayCovers = list
                                        }, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.Close, "清除", modifier = Modifier.size(18.dp)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
                    } // ── Tab 1：演出 結束 ──

                    // ── Tab 2：票務 ──
                    1 -> {

            item { FormGroupHeader("購票", first = true) }
            item {
                FormSection("購票狀態", wide = true) {
                    // 四態 SegmentedRow（spec §4.3）：無 / 已購票 / 已取票 / 已看過。
                    com.bandary.presentation.ui.shows.edit.SegmentedRow(
                        options = TicketStatus.selectable,
                        selected = ticketStatus,
                        labelOf = { it.label },
                        onSelect = { ticketStatus = it }
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        FormLabel("票價 NT$")
                        Spacer(Modifier.height(2.dp))
                        OutlinedTextField(
                            value = ticketPrice,
                            onValueChange = { ticketPrice = it.filter { c -> c.isDigit() } },
                            placeholder = { Text("0") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            colors = minimalFieldColors()
                        )
                        FormUnderline()
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        FormLabel("座位 / 區域")
                        Spacer(Modifier.height(2.dp))
                        OutlinedTextField(
                            value = seatInfo,
                            onValueChange = { seatInfo = it },
                            placeholder = { Text("B區 12排") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            colors = minimalFieldColors()
                        )
                        FormUnderline()
                    }
                }
            }

            // 購票平台：下拉選單 + 自訂輸入（排序在 設定 → 自訂購票平台）
            item {
                FormSection("購票平台", wide = true) {
                    var platformExpanded by remember { mutableStateOf(false) }
                    // 只有選「自訂新平台」才展開手動輸入框，平常不佔版面。
                    var customMode by remember { mutableStateOf(false) }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExposedDropdownMenuBox(
                            expanded = platformExpanded,
                            onExpandedChange = { platformExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = platform ?: "",
                                onValueChange = {},
                                readOnly = true,
                                placeholder = { Text("選擇購票平台") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = platformExpanded) },
                                modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = minimalFieldColors()
                            )
                            ExposedDropdownMenu(
                                expanded = platformExpanded,
                                onDismissRequest = { platformExpanded = false }
                            ) {
                                orderedPlatforms.forEach { p ->
                                    val isCustom = p in vmState.customPlatforms
                                    DropdownMenuItem(
                                        text = { Text(p) },
                                        onClick = { platform = p; customMode = false; platformExpanded = false },
                                        // 自訂平台可個別移除（內建平台不行）。
                                        trailingIcon = if (isCustom) {
                                            {
                                                IconButton(onClick = {
                                                    viewModel.removeCustomPlatform(p)
                                                    if (platform == p) platform = null
                                                }, modifier = Modifier.size(28.dp)) {
                                                    Icon(Icons.Outlined.Close, "移除自訂平台", modifier = Modifier.size(16.dp),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        } else null
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("＋ 自訂新平台", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary) },
                                    onClick = { customMode = true; platformExpanded = false }
                                )
                                if (platform != null) {
                                    DropdownMenuItem(
                                        text = { Text("清除選擇", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                        onClick = { platform = null; platformExpanded = false }
                                    )
                                }
                            }
                        }
                        // 手動輸入新平台：只在選了「自訂新平台」後才出現。
                        if (customMode) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = customPlatformInput,
                                    onValueChange = { customPlatformInput = it },
                                    placeholder = { Text("輸入新平台名稱") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true,
                                    colors = minimalFieldColors()
                                )
                                TextButton(
                                    onClick = {
                                        val n = customPlatformInput.trim()
                                        if (n.isNotBlank()) {
                                            viewModel.addCustomPlatform(n)
                                            platform = n
                                            customPlatformInput = ""
                                            customMode = false
                                        }
                                    },
                                    enabled = customPlatformInput.isNotBlank()
                                ) { Text("加入") }
                            }
                        }
                    }
                }
            }

            item {
                FormSection("售票主頁") {
                    OutlinedTextField(
                        value = ticketUrl,
                        onValueChange = { ticketUrl = it },
                        placeholder = { Text("購票連結") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = minimalFieldColors()
                    )
                }
            }

            // 還沒選購票平台前，取票相關欄位（機台 / 身份證 / 序號 / 號碼）自動隱藏。
            if (platform != null) {
            item { FormGroupHeader("取票資訊") }

            // 取票步驟：把「去哪台機器、按哪個選項、要帶什麼」直接寫在表單裡。
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            if (pickupProfile.eTicketOnly) "這個平台是電子票券" else "怎麼取票",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        pickupProfile.steps.forEachIndexed { i, step ->
                            Text(
                                "${i + 1}. $step",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        pickupProfile.note?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // 帳號備忘：搶票前想不起來「這家是用哪個帳號買的」最痛。
                        // 只存本機、不進雲端備份。
                        val accounts by viewModel.platformAccounts.collectAsState()
                        var accountDraft by remember(platform, accounts) {
                            mutableStateOf(accounts[platform].orEmpty())
                        }
                        OutlinedTextField(
                            value = accountDraft,
                            onValueChange = {
                                accountDraft = it
                                viewModel.setPlatformAccount(platform!!, it)
                            },
                            label = { Text("$platform 用哪個帳號", fontSize = 12.sp) },
                            placeholder = { Text("例：Google 主帳號 / 老婆的 FB", fontSize = 12.sp) },
                            supportingText = {
                                Text(
                                    "只存在這支手機，不會上傳雲端。下次選這個平台會自動帶出來。",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth(),
                            colors = minimalFieldColors()
                        )
                        if (pickupProfile.eTicketOnly && !pickupExpanded) {
                            TextButton(
                                onClick = { pickupExpanded = true },
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) { Text("這場還是要超商取票", fontSize = 12.sp) }
                        }
                    }
                }
            }

            if (pickupExpanded) {
            // 取票序號截圖：在超商機台前翻訂單信最慌，存一張截圖最快。
            item {
                FormSection("取票序號截圖", wide = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val shot = pickupShotUri
                        if (shot != null) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                AsyncImage(
                                    model = shot,
                                    contentDescription = "取票序號截圖",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 220.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        startPickupShotPick()
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) { Text("換一張", fontSize = 13.sp) }
                                TextButton(onClick = { pickupShotUri = null }) {
                                    Text("移除", fontSize = 13.sp)
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    startPickupShotPick()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("＋ 上傳訂單信 / 取票畫面截圖") }
                        }
                        Text(
                            "存一張截圖，到超商時不用再翻信箱。截圖會跟著演唱會一起備份。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                FormSection("取票機台", wide = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // 這個平台常用的機台排前面（第一個就是預選），其餘仍可選——
                        // 規則會變，這裡只給建議，不擋住任何組合。
                        val machineOptions = remember(pickupProfile) {
                            (pickupProfile.machines + PICKUP_MACHINES).distinct()
                        }
                        // FlowRow：機台多時自動換行，右緣不再被切掉（spec §4.4——原本用 Row 會擠出畫面）。
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            machineOptions.forEach { m ->
                                FilterChip(
                                    selected = pickupMachine == m,
                                    onClick = { pickupMachine = if (pickupMachine == m) "" else m },
                                    label = { Text(m, fontSize = 13.sp) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                        OutlinedTextField(
                            value = if (pickupMachine in PICKUP_MACHINES) "" else pickupMachine,
                            onValueChange = { pickupMachine = it },
                            placeholder = { Text("自訂機台", fontSize = 13.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            colors = minimalFieldColors()
                        )
                    }
                }
            }

            // 身分證只有真的要用的平台才出現（拓元 / ibon）。
            // 已經填過的一律照舊顯示，否則舊資料會憑空消失。
            if (pickupProfile.needsIdNumber || pickupIdNumber.isNotBlank()) {
            item {
                FormSection("取票身份證") {
                    // 名稱不動（spec §3）；語意改由輔助說明 + 鍵盤 + 驗證承擔（spec §6）。
                    val idError = pickupIdNumber.isNotBlank() &&
                        !Regex("^[A-Za-z][0-9]{9}$").matches(pickupIdNumber.trim())
                    OutlinedTextField(
                        value = pickupIdNumber,
                        onValueChange = { pickupIdNumber = it },
                        placeholder = { Text("例：A123456789") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        isError = idError,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            capitalization = KeyboardCapitalization.Characters
                        ),
                        supportingText = {
                            if (idError) Text("格式應為 1 英文 + 9 數字")
                        },
                        colors = minimalFieldColors()
                    )
                }
            }

            }
            item {
                FormSection(pickupProfile.serialLabel) {
                    // 這裡通常是英數字代碼；曾有人把人名（黃馨瑩）填進來——偵測到 CJK 給「非阻斷」提醒，不擋儲存（spec §6）。
                    val serialCjk = containsCjk(pickupNumber)
                    OutlinedTextField(
                        value = pickupNumber,
                        onValueChange = { pickupNumber = it },
                        placeholder = { Text(pickupProfile.serialHint) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        supportingText = {
                            if (serialCjk) Text("這裡通常是英數字代碼，確定是這個嗎？", color = BandaryColors.Orange)
                        },
                        colors = minimalFieldColors()
                    )
                }
            }

            // 第二組號碼：只有這個平台真的有才出現（KKTIX 就沒有）。
            pickupProfile.ticketNoLabel?.let { ticketNoLabel ->
                item {
                    FormSection(ticketNoLabel) {
                        OutlinedTextField(
                            value = pickupTicketNo,
                            onValueChange = { pickupTicketNo = it },
                            placeholder = { Text("例：0912345678 / 訂單號") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            // 訂單編號常含英文字母，鎖成數字鍵盤會打不出來。
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                            colors = minimalFieldColors()
                        )
                    }
                }
            }
            } // end pickupExpanded
            } // end 取票欄位（依購票平台顯示）

            item { FormGroupHeader("開賣提醒") }
            item {
                FormSection("開賣時間", wide = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (saleTimes.isEmpty()) {
                            Text(
                                "可以加好幾組（會員預購、一般售票、加場…），每一組都會各自提醒。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        saleTimes.forEachIndexed { index, sale ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedTextField(
                                    value = sale.label,
                                    onValueChange = { v ->
                                        saleTimes = saleTimes.mapIndexed { i, s ->
                                            if (i == index) s.copy(label = v) else s
                                        }
                                    },
                                    label = { Text("這一輪的名稱（可留空）") },
                                    placeholder = { Text("會員預購 / 一般售票") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        onClick = { saleEditIndex = index; showSalePicker = true },
                                        modifier = Modifier.weight(1f).height(56.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "${sale.at.year}/${sale.at.monthNumber}/${sale.at.dayOfMonth}　" +
                                                    "${sale.at.hour.toString().padStart(2, '0')}:" +
                                                    sale.at.minute.toString().padStart(2, '0'),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                    TextButton(onClick = {
                                        saleTimes = saleTimes.filterIndexed { i, _ -> i != index }
                                    }) { Text("移除") }
                                }
                            }
                        }
                        TextButton(onClick = {
                            // 先記住新的位置再塞，避免之後靠 size 反推時多算一格。
                            val newIndex = saleTimes.size
                            saleTimes = saleTimes + SaleTime("", LocalDateTime(selectedDate, LocalTime(12, 0)))
                            saleEditIndex = newIndex
                            showSalePicker = true
                        }) { Text("＋ 新增開賣時間") }
                    }
                }
            }

            // 開賣提醒：只有設了開賣時間才顯示（提前幾分鐘是所有組共用的一個值）
            if (saleTimes.isNotEmpty()) {
                item {
                    FormSection("開賣提醒", wide = true) {
                        val isCustomSale = saleReminderMinutes >= 0 && SALE_REMINDER_OPTIONS.none { it.first == saleReminderMinutes }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                // 不提醒：可以一個都不選
                                FilterChip(
                                    selected = saleReminderMinutes < 0,
                                    onClick = { saleReminderMinutes = -1 },
                                    label = { Text("不提醒", fontSize = 13.sp) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                            items(SALE_REMINDER_OPTIONS) { (mins, label) ->
                                FilterChip(
                                    selected = saleReminderMinutes == mins,
                                    onClick = { saleReminderMinutes = mins },
                                    label = { Text(label, fontSize = 13.sp) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                            item {
                                FilterChip(
                                    selected = isCustomSale,
                                    onClick = { showSaleCustomDialog = true },
                                    label = { Text(if (isCustomSale) saleCustomLabel(saleReminderMinutes) else "自訂", fontSize = 13.sp) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }
            }


            // 座位圖 / 節目表的區塊已移除：兩者都只是「演出」分頁那條相簿裡某張圖的角色標記，
            // 在這裡再放一次唯讀預覽，只是把同一張圖顯示兩遍、把票務分頁拉長。

                    } // ── Tab 2：票務 結束 ──

                    // ── Tab 3：心得 ──
                    2 -> {

            // 演出前提示（spec §6.8.3 調整）：演出還沒發生時給一張小提示卡，鼓勵演出後回來補齊——
            // 但欄位仍可編輯，讓使用者能先預填心得。
            if (heroDate > heroToday) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("🎵", fontSize = 22.sp)
                            Text("演出後回來補齊更完整",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
            }

            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 折疊式標題：歌單可能很長，預設收合，點一下才展開編輯。
                    val totalSongs = setlistByArtist.values.sumOf { it.size }
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { setlistExpanded = !setlistExpanded }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("歌單" + if (totalSongs > 0) "・$totalSongs 首" else "", style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        Icon(if (setlistExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            if (setlistExpanded) "收合" else "展開", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (setlistExpanded) Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        // 每位（非空白）藝人一個區塊；沒有藝人時用單一「」區塊。
                        // 先照卡司順序（歌單順序＝藝人順序），再補上「歌單有、但卡司已改名/移除」的藝人，
                        // 否則那些歌在編輯器裡看不到卻仍存在資料裡，順序也會被擠到最後。
                        val performers = (
                            artists.map { it.trim() }.filter { it.isNotBlank() } +
                                setlistByArtist.keys.filter { it.isNotBlank() }
                            ).distinct().ifEmpty { listOf("") }
                        performers.forEach { artistName ->
                            val songs = setlistByArtist[artistName] ?: emptyList()
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (artistName.isNotBlank()) {
                                    Text(artistName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                }
                                var songNo = 0
                                songs.forEachIndexed { idx, song ->
                                    val isSection = song.startsWith(SETLIST_SECTION_PREFIX)
                                    if (isSection) songNo = 0 else songNo++
                                    val displayNo = songNo
                                    // 一首歌 = 一個 Column：上面一列是「編號 + 輸入框 + 動作」，
                                    // feat. 另起一行掛在下面。之前把 feat. 放進列內的 Column，
                                    // 那個 Column 變兩倍高，而該列是垂直置中，編號與箭頭就被推到
                                    // 中間、跟輸入框對不齊（實機回報「歌單輸入的很跑版」）。
                                    Column {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (isSection) {
                                            Icon(Icons.AutoMirrored.Outlined.Segment, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.width(22.dp).size(18.dp))
                                            OutlinedTextField(
                                                value = song.removePrefix(SETLIST_SECTION_PREFIX),
                                                onValueChange = { v -> setlistByArtist = setlistByArtist + (artistName to songs.toMutableList().also { it[idx] = SETLIST_SECTION_PREFIX + v }) },
                                                placeholder = { Text("段落，例如 安可 / Encore") },
                                                modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), singleLine = true,
                                                textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary),
                                                colors = minimalFieldColors()
                                            )
                                        } else {
                                            Text("$displayNo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(22.dp))
                                            // 歌名輸入 + 已記錄過的歌自動建議（打前幾個字就跳出，點一下填入）。
                                            var songSuggestOpen by remember { mutableStateOf(false) }
                                            val songSuggestions = remember(song, knownSongs) {
                                                val q = song.trim()
                                                if (q.isBlank()) emptyList()
                                                else knownSongs
                                                    .filter { it.contains(q, ignoreCase = true) && !it.equals(q, ignoreCase = true) }
                                                    .take(6)
                                            }
                                            // 歌名與合作藝人存在同一列字串（見 SongFeat），
                                            // 但輸入框只給歌名——讓使用者在同一格裡打 "feat." 會很容易打壞格式。
                                            val parsedFeat = remember(song) { com.bandary.domain.model.SongFeat.parse(song) }
                                            val songTitle = parsedFeat.first
                                            val songFeats = parsedFeat.second
                                            Box(modifier = Modifier.weight(1f)) {
                                                OutlinedTextField(
                                                    value = songTitle,
                                                    onValueChange = { v ->
                                                        val merged = com.bandary.domain.model.SongFeat.format(v, songFeats)
                                                        setlistByArtist = setlistByArtist + (artistName to songs.toMutableList().also { it[idx] = merged })
                                                        songSuggestOpen = true
                                                    },
                                                    placeholder = { Text("歌名") },
                                                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true,
                                                    colors = minimalFieldColors()
                                                )
                                                DropdownMenu(
                                                    expanded = songSuggestOpen && songSuggestions.isNotEmpty(),
                                                    onDismissRequest = { songSuggestOpen = false },
                                                    // 不搶輸入焦點，才能邊打字邊看建議。
                                                    properties = androidx.compose.ui.window.PopupProperties(focusable = false)
                                                ) {
                                                    songSuggestions.forEach { s ->
                                                        DropdownMenuItem(
                                                            text = { Text(s) },
                                                            onClick = {
                                                                val merged = com.bandary.domain.model.SongFeat.format(s, songFeats)
                                                                setlistByArtist = setlistByArtist + (artistName to songs.toMutableList().also { it[idx] = merged })
                                                                songSuggestOpen = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        IconButton(onClick = { if (idx > 0) setlistByArtist = setlistByArtist + (artistName to songs.toMutableList().also { it.add(idx - 1, it.removeAt(idx)) }) },
                                            enabled = idx > 0, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.KeyboardArrowUp, "上移", modifier = Modifier.size(20.dp)) }
                                        IconButton(onClick = { if (idx < songs.lastIndex) setlistByArtist = setlistByArtist + (artistName to songs.toMutableList().also { it.add(idx + 1, it.removeAt(idx)) }) },
                                            enabled = idx < songs.lastIndex, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.KeyboardArrowDown, "下移", modifier = Modifier.size(20.dp)) }
                                        IconButton(onClick = { setlistByArtist = setlistByArtist + (artistName to songs.toMutableList().also { it.removeAt(idx) }) },
                                            modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.Close, "移除", modifier = Modifier.size(20.dp)) }
                                    }
                                    // 合作藝人：只有歌名填了才出現——空白列上掛一顆 feat. 是噪音。
                                    // 縮排對齊輸入框（編號寬 22 + 間距 4），看起來才像附屬於這首歌。
                                    if (!isSection) {
                                        val (_, featsOfSong) = com.bandary.domain.model.SongFeat.parse(song)
                                        if (com.bandary.domain.model.SongFeat.titleOf(song).isNotBlank()) {
                                            TextButton(
                                                onClick = { featEditTarget = artistName to idx },
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                                modifier = Modifier
                                                    .padding(start = 26.dp)
                                                    .heightIn(min = 24.dp)
                                            ) {
                                                Text(
                                                    if (featsOfSong.isEmpty()) "＋ feat."
                                                    else "feat. " + featsOfSong.joinToString("、"),
                                                    fontSize = 11.sp,
                                                    color = if (featsOfSong.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                                                    else MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                    }
                                }
                                // 只留「新增歌曲」為主要動作；貼上 / 段落 / 掃歌單都是偶爾才用，收進 ⋮。
                                // 原本四顆並排在窄螢幕會擠成一團，主次也分不出來。
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { setlistByArtist = setlistByArtist + (artistName to songs + "") },
                                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.size(4.dp))
                                        // 藝人名已在上方當區塊標題，這裡不重複塞進按鈕——長團名會把按鈕撐成 3 行（跑版）。
                                        Text("新增歌曲", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    var setlistMenuOpen by remember(artistName) { mutableStateOf(false) }
                                    Box {
                                        IconButton(onClick = { setlistMenuOpen = true }) {
                                            Icon(Icons.Outlined.MoreVert, "更多歌單操作",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        DropdownMenu(expanded = setlistMenuOpen, onDismissRequest = { setlistMenuOpen = false }) {
                                            // 批次貼上：一次貼一整份歌單（每行一首），不用一首一首按新增。
                                            DropdownMenuItem(
                                                text = { Text("貼上整份歌單") },
                                                leadingIcon = { Icon(Icons.Outlined.ContentPaste, null) },
                                                onClick = { setlistMenuOpen = false; pasteSetlistArtist = artistName }
                                            )
                                            // 圖片辨識：拍歌單 / 電光看板照片。
                                            DropdownMenuItem(
                                                text = { Text("掃描歌單照片") },
                                                leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null) },
                                                onClick = {
                                                    setlistMenuOpen = false
                                                    ocrForSetlistArtist = artistName
                                                    startOcrPick()
                                                }
                                            )
                                            // 新增段落分隔（例如：安可）
                                            DropdownMenuItem(
                                                text = { Text("加入段落（如 安可）") },
                                                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Segment, null) },
                                                onClick = {
                                                    setlistMenuOpen = false
                                                    setlistByArtist = setlistByArtist + (artistName to songs + (SETLIST_SECTION_PREFIX + ""))
                                                }
                                            )
                                            // 藝人 key 錯時用：整份（預設全選）或挑幾首搬到另一位藝人名下。
                                            // 用勾選取代跨區拖曳——拖曳在長歌單裡要邊拖邊捲，一次也只能搬一首。
                                            if (performers.size > 1 && songs.any { it.isNotBlank() }) {
                                                DropdownMenuItem(
                                                    text = { Text("移到其他藝人…") },
                                                    leadingIcon = { Icon(Icons.Outlined.SwapHoriz, null) },
                                                    onClick = { setlistMenuOpen = false; moveSetlistFrom = artistName }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                }
            }

            item { FormGroupHeader("評分與心得", first = true) }
            item {
                // 五顆星與「評分」同一行（省垂直空間）；星鈕縮小以塞進右側欄。
                FormSection("評分", wide = false) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        (1..5).forEach { star ->
                            IconButton(onClick = { rating = if (rating == star) 0 else star }, modifier = Modifier.size(34.dp)) {
                                Icon(
                                    if (star <= rating) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                    contentDescription = "$star 星",
                                    modifier = Modifier.size(24.dp),
                                    tint = if (star <= rating) BandaryColors.Yellow
                                    else MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }

            item {
                // 「會想再看」與選項同一行。
                FormSection("會想再看", wide = true) {
                    // SegmentedRow（與購票狀態同款，spec §6.8.4）。
                    com.bandary.presentation.ui.shows.edit.SegmentedRow(
                        options = RewatchIntent.entries.toList(),
                        selected = rewatchIntent,
                        labelOf = { it.label },
                        onSelect = { rewatchIntent = if (rewatchIntent == it) null else it }
                    )
                }
            }

            item { FormGroupHeader("現場紀錄") }
            item {
                FormSection("同行者", wide = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (companions.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                companions.forEach { name ->
                                    InputChip(
                                        selected = false,
                                        onClick = { companions = companions - name },
                                        label = { Text(name) },
                                        trailingIcon = { Icon(Icons.Outlined.Close, "移除", modifier = Modifier.size(16.dp)) }
                                    )
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // 同行者暱稱也做自動建議：常一起去的人不用每次重打。
                            var compSuggestOpen by remember { mutableStateOf(false) }
                            val compSuggestions = remember(companionInput, knownCompanions, companions) {
                                val q = companionInput.trim()
                                if (q.isBlank()) emptyList()
                                else knownCompanions
                                    .filter { it.contains(q, ignoreCase = true) && !it.equals(q, ignoreCase = true) }
                                    .filterNot { it in companions }
                                    .take(6)
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = companionInput,
                                    onValueChange = { companionInput = it; compSuggestOpen = true },
                                    placeholder = { Text("同行者暱稱") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true,
                                    colors = minimalFieldColors()
                                )
                                DropdownMenu(
                                    expanded = compSuggestOpen && compSuggestions.isNotEmpty(),
                                    onDismissRequest = { compSuggestOpen = false },
                                    properties = androidx.compose.ui.window.PopupProperties(focusable = false)
                                ) {
                                    compSuggestions.forEach { s ->
                                        DropdownMenuItem(
                                            text = { Text(s) },
                                            onClick = {
                                                if (s !in companions) companions = companions + s
                                                companionInput = ""; compSuggestOpen = false
                                            }
                                        )
                                    }
                                }
                            }
                            // ＋ 按鈕（與新增歌手一致，spec §6.8.4）。
                            OutlinedButton(
                                onClick = {
                                    val n = companionInput.trim()
                                    if (n.isNotBlank() && n !in companions) { companions = companions + n; companionInput = "" }
                                },
                                enabled = companionInput.isNotBlank(),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(4.dp)); Text("加入", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            item {
                FormSection("備忘錄", wide = true) {
                    var notesTall by remember { mutableStateOf(false) }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // 備忘錄是大塊多行編輯區：給明顯的淺底方框，一眼看得出這是可輸入的地方。
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it; draftSavedMsg = null },
                            placeholder = { Text("輸入備註…") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (notesTall) 320.dp else 120.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                        )
                        // 「暫存」按鈕已移除，改為自動草稿（spec §8）；儲存改由底部唯一按鈕提交。
                        androidx.compose.material3.TextButton(
                            onClick = { notesTall = !notesTall },
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(if (notesTall) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text(if (notesTall) "收合" else "展開更大的編輯區", fontSize = 12.sp)
                        }
                    }
                }
            }

                    } // ── Tab 3：心得 結束 ──
                    } // when(page)
                }     // LazyColumn（單一 Pager 分頁）
            }         // HorizontalPager
            }         // else（完整編輯模式）
        }             // Column（Hero + 模式切換 + 內容）
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("捨棄變更？") },
            text = { Text("你有尚未儲存的變更，要捨棄嗎？") },
            confirmButton = {
                TextButton(onClick = { showDiscardDialog = false; viewModel.clearDraft(draftKey); onBack() }) { Text("捨棄") }
            },
            dismissButton = { TextButton(onClick = { showDiscardDialog = false }) { Text("繼續編輯") } }
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDayMillis()
        )
        var showMonthJump by remember { mutableStateOf(false) }
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { ms ->
                        selectedDate = Instant.fromEpochMilliseconds(ms)
                            .toLocalDateTime(tz).date
                    }
                    showDatePicker = false
                }) { Text("確定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            Column {
                TextButton(onClick = { showMonthJump = true }, modifier = Modifier.align(Alignment.End)) {
                    Text("跳到月份")
                }
                DatePicker(state = datePickerState)
            }
        }
        if (showMonthJump) {
            val cur = Instant.fromEpochMilliseconds(datePickerState.displayedMonthMillis).toLocalDateTime(tz).date
            com.bandary.presentation.ui.components.MonthYearPickerDialog(
                initialYear = cur.year,
                initialMonth = cur.monthNumber,
                onPick = { y, m ->
                    datePickerState.displayedMonthMillis = LocalDate(y, m, 1).atStartOfDayMillis()
                    showMonthJump = false
                },
                onDismiss = { showMonthJump = false }
            )
        }
    }

    // 照片設定：把原本散在縮圖上的小按鈕（封面 / 刪除 / 排序）和票務分頁的兩個上傳口，
    // 全部收進這一個對話框——縮圖本身就乾淨了。
    photoSheetUri?.let { uri ->
        val idx = photoUris.indexOf(uri)
        val role = photoRoles[uri]
        val timetableCount = photoUris.count { photoRoles[it] == PHOTO_ROLE_TIMETABLE }
        fun setRole(next: String?) {
            photoRoles = when {
                next == null -> photoRoles - uri
                // 場位圖一場只留一張：指定新的就把舊的降回一般照片。
                next == PHOTO_ROLE_SEAT ->
                    photoRoles.filterValues { it != PHOTO_ROLE_SEAT } + (uri to next)
                else -> photoRoles + (uri to next)
            }
        }
        AlertDialog(
            onDismissRequest = { photoSheetUri = null },
            title = { Text("照片設定") },
            text = {
                com.bandary.presentation.ui.components.DialogBody(spacing = 12.dp) {
                    AsyncImage(
                        model = uri, contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(8.dp))
                            .clickable { photoSheetUri = null; zoomImage = uri },
                        contentScale = ContentScale.Crop
                    )
                    Text("用途", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = photoUri == uri,
                            onClick = { photoUri = if (photoUri == uri) null else uri; coverGradientLight = false },
                            label = { Text("封面") }
                        )
                        FilterChip(
                            selected = role == PHOTO_ROLE_SEAT,
                            onClick = { setRole(if (role == PHOTO_ROLE_SEAT) null else PHOTO_ROLE_SEAT) },
                            label = { Text("場位圖") }
                        )
                        FilterChip(
                            // 已經有 MAX_TIMETABLES 張節目表時就不能再標，除非這張本來就是。
                            selected = role == PHOTO_ROLE_TIMETABLE,
                            enabled = role == PHOTO_ROLE_TIMETABLE || timetableCount < MAX_TIMETABLES,
                            onClick = { setRole(if (role == PHOTO_ROLE_TIMETABLE) null else PHOTO_ROLE_TIMETABLE) },
                            label = { Text("節目表") }
                        )
                    }
                    Text("封面可以和場位圖或節目表同時成立；沒選用途就是一般演出照片。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("排序", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        IconButton(
                            onClick = { if (idx > 0) photoUris = photoUris.toMutableList().also { it.add(idx - 1, it.removeAt(idx)) } },
                            enabled = idx > 0
                        ) { Icon(Icons.Outlined.ChevronLeft, "往前") }
                        Text("${idx + 1} / ${photoUris.size}", style = MaterialTheme.typography.bodySmall)
                        IconButton(
                            onClick = { if (idx in 0 until photoUris.lastIndex) photoUris = photoUris.toMutableList().also { it.add(idx + 1, it.removeAt(idx)) } },
                            enabled = idx in 0 until photoUris.lastIndex
                        ) { Icon(Icons.Outlined.ChevronRight, "往後") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { photoSheetUri = null }) { Text("完成") } },
            dismissButton = {
                TextButton(onClick = {
                    photoUris = photoUris - uri
                    photoRoles = photoRoles - uri
                    if (photoUri == uri) photoUri = null
                    // 各日封面若指到這張，一併清掉，避免留下已刪除的圖。
                    dayCovers = dayCovers.map { if (it == uri) "" else it }
                    photoSheetUri = null
                }) { Text("移除照片", color = MaterialTheme.colorScheme.error) }
            }
        )
    }

    zoomImage?.let { img ->
        com.bandary.presentation.ui.components.ImageZoomDialog(model = img, onDismiss = { zoomImage = null })
    }

    if (showPhotoQuotaDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoQuotaDialog = false },
            title = { Text("這場的照片已達上限") },
            text = {
                Text(
                    if (vmState.isPro) "Pro 方案每場最多 ${com.bandary.data.billing.PhotoQuota.PRO} 張，升級 Pro+ 可以到 ${com.bandary.data.billing.PhotoQuota.PRO_PLUS} 張。"
                    else "免費方案每場最多 ${com.bandary.data.billing.PhotoQuota.FREE} 張，解鎖 Pro 可以到 ${com.bandary.data.billing.PhotoQuota.PRO} 張、Pro+ 到 ${com.bandary.data.billing.PhotoQuota.PRO_PLUS} 張。"
                )
            },
            confirmButton = {
                TextButton(onClick = { showPhotoQuotaDialog = false; onOpenPro() }) { Text("看方案") }
            },
            dismissButton = {
                TextButton(onClick = { showPhotoQuotaDialog = false }) { Text("先這樣就好") }
            }
        )
    }

    // 批次貼上歌單：一次貼一整份（每行一首），自動去掉「1.」「01」「- 」這類序號前綴。
    // 歌單搬家：把某位藝人的歌（預設全選＝整份換過去）搬到另一位藝人名下。
    moveSetlistFrom?.let { from ->
        val items = setlistByArtist[from].orEmpty()
        val targets = (artists.map { it.trim() }.filter { it.isNotBlank() } + setlistByArtist.keys.filter { it.isNotBlank() })
            .distinct().filter { it != from }
        var picked by remember(from) { mutableStateOf(items.indices.filter { items[it].isNotBlank() }.toSet()) }
        var moveTo by remember(from) { mutableStateOf(targets.firstOrNull()) }
        AlertDialog(
            onDismissRequest = { moveSetlistFrom = null },
            title = { Text("移到其他藝人") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "把「${from.ifBlank { "未指定藝人" }}」的歌搬過去。預設全選，就是整份歌單換給另一位藝人。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("搬到", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        targets.forEach { t ->
                            FilterChip(
                                selected = moveTo == t,
                                onClick = { moveTo = t },
                                label = { Text(t, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                    }
                    Text("要搬的歌（${picked.size}）", style = MaterialTheme.typography.labelMedium)
                    Column(modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                        items.forEachIndexed { i, line ->
                            if (line.isNotBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable { picked = if (i in picked) picked - i else picked + i }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(checked = i in picked, onCheckedChange = null)
                                    Text(
                                        if (line.startsWith(SETLIST_SECTION_PREFIX)) "— ${line.removePrefix(SETLIST_SECTION_PREFIX)} —" else line,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = moveTo != null && picked.isNotEmpty(),
                    onClick = {
                        moveTo?.let { t -> setlistByArtist = moveSetlistItems(setlistByArtist, from, picked, t) }
                        moveSetlistFrom = null
                    }
                ) { Text("搬過去") }
            },
            dismissButton = { TextButton(onClick = { moveSetlistFrom = null }) { Text("取消") } }
        )
    }

    pasteSetlistArtist?.let { target ->
        PasteSetlistDialog(
            artistName = target,
            onDismiss = { pasteSetlistArtist = null },
            onConfirm = { pasted ->
                val existing = setlistByArtist[target].orEmpty()
                setlistByArtist = setlistByArtist + (target to (existing.filter { it.isNotBlank() } + pasted))
                pasteSetlistArtist = null
            }
        )
    }

    // 合作藝人編輯：一首歌可以有多位 feat.，用「、」分隔一次打完。
    featEditTarget?.let { (artist, idx) ->
        val line = setlistByArtist[artist]?.getOrNull(idx).orEmpty()
        val parsed = com.bandary.domain.model.SongFeat.parse(line)
        var draft by remember(artist, idx) {
            mutableStateOf(parsed.second.joinToString("、"))
        }
        AlertDialog(
            onDismissRequest = { featEditTarget = null },
            title = { Text("「${parsed.first}」的合作藝人") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = { Text("例：老王樂隊、原子邦妮") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = minimalFieldColors()
                    )
                    Text(
                        "多位用「、」分隔。合作藝人會記在歌名後面（愛你 feat. 老王），" +
                            "統計歌曲時仍然算同一首。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val feats = draft.split("、", ",", "，", "/", "&")
                        .map { it.trim() }.filter { it.isNotBlank() }
                    val songs = setlistByArtist[artist].orEmpty().toMutableList()
                    if (idx in songs.indices) {
                        songs[idx] = com.bandary.domain.model.SongFeat.format(parsed.first, feats)
                        setlistByArtist = setlistByArtist + (artist to songs)
                    }
                    featEditTarget = null
                }) { Text("儲存") }
            },
            dismissButton = { TextButton(onClick = { featEditTarget = null }) { Text("取消") } }
        )
    }

    if (showOcr) {
        val setlistTarget = ocrForSetlistArtist
        OcrPickerDialog(
            running = ocrRunning,
            lines = ocrLines,
            stub = ocrStub,
            setlistMode = setlistTarget != null,
            onAddArtist = { line ->
                // **整串卡司的源頭就在這裡。**
                // 掃海報時 OCR 回來的一行常常是「周湯豪 / 盧廣仲 / 玖壹壹 / …」整排卡司，
                // 原本直接把這一行當成「一位藝人」加進去；它又是第一位，
                // 於是 buildConcert 的 artistName（= artists.first()）就變成那一整串。
                // 後台看到的「一位藝人叫做三十個團名」就是從這裡長出來的。
                //
                // 改成先拆：拆得開就每位各一列，拆不開（單一團名）行為不變。
                val base = artists.filter { it.isNotBlank() }
                val picked = com.bandary.domain.model.splitArtistNamesPasted(line)
                    .ifEmpty { listOf(line.trim()) }
                    .filter { it.isNotBlank() && it !in base }
                if (picked.isNotEmpty()) artists = base + picked
            },
            onAddSong = { line ->
                if (setlistTarget != null) {
                    val songs = (setlistByArtist[setlistTarget] ?: emptyList()).filter { it.isNotBlank() }
                    if (line !in songs) setlistByArtist = setlistByArtist + (setlistTarget to songs + line)
                }
            },
            onSetName = { name = it },
            onSetVenue = { venue = it },
            onApplyStub = { s ->
                // 只填空白欄位。使用者已經打好的東西不該被一張掃描結果蓋掉——
                // 要換掉自己改就好，反過來（蓋掉了要救回來）沒得救。
                if (name.isBlank()) s.showName?.let { name = it }
                if (venue.isBlank()) s.venue?.let { venue = it }
                if (selectedCity == null) s.city?.takeIf { it in TAIWAN_CITIES }?.let { selectedCity = it }
                s.date?.let { selectedDate = it }
                s.time?.let { selectedTime = it }
                if (seatInfo.isBlank()) s.seat?.let { seatInfo = it }
                if (ticketPrice.isBlank()) s.price?.let { ticketPrice = it.toInt().toString() }
                if (platform == null) s.platform?.let { platform = it }
                if (pickupMachine.isBlank()) s.machine?.let { pickupMachine = it }
                if (pickupNumber.isBlank()) s.pickupNumber?.let { pickupNumber = it }
                if (pickupTicketNo.isBlank()) s.pickupTicketNo?.let { pickupTicketNo = it }
                // 掃到票根代表票已經買了，狀態還停在「無」就往前推一格。
                if (s.pickupNumber != null && ticketStatus == TicketStatus.NONE) {
                    ticketStatus = TicketStatus.BOUGHT
                }
            },
            onDismiss = { showOcr = false; ocrForSetlistArtist = null; viewModel.clearOcr() }
        )
    }

    if (showPasteArtists) {
        PasteArtistsDialog(
            onConfirm = { pasted ->
                val parsed = splitArtistList(pasted)
                if (parsed.isNotEmpty()) {
                    val existing = artists.filter { it.isNotBlank() }
                    val merged = (existing + parsed).distinct()
                    artists = merged.ifEmpty { listOf("") }
                }
                showPasteArtists = false
            },
            onDismiss = { showPasteArtists = false }
        )
    }

    // 各日封面：從已上傳的圖片（封面 + 更多照片）挑一張，不重新上傳。
    dayCoverPickFor?.let { dayIdx ->
        val available = (listOfNotNull(photoUri?.takeIf { it.isNotBlank() }) + photoUris.filter { it.isNotBlank() }).distinct()
        AlertDialog(
            onDismissRequest = { dayCoverPickFor = null },
            title = { Text("選擇當日封面") },
            text = {
                if (available.isEmpty()) {
                    Text("還沒有可選的圖片。請先在上方「更多照片」上傳圖片，再回來挑選各日封面。",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        available.forEach { uri ->
                            val selected = dayCovers.getOrNull(dayIdx) == uri
                            Box(
                                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp))
                                    .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)) else Modifier)
                                    .clickable {
                                        val list = dayCovers.toMutableList()
                                        while (list.size <= dayIdx) list.add("")
                                        list[dayIdx] = uri
                                        dayCovers = list
                                        dayCoverPickFor = null
                                    }
                            ) {
                                AsyncImage(model = uri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dayCoverPickFor = null }) { Text("關閉") } }
        )
    }

    if (showSaleCustomDialog) {
        var minsText by remember { mutableStateOf(saleReminderMinutes.toString()) }
        AlertDialog(
            onDismissRequest = { showSaleCustomDialog = false },
            title = { Text("自訂開賣提醒") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("開賣前幾分鐘提醒？", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = minsText,
                        onValueChange = { s -> minsText = s.filter { it.isDigit() }.take(5) },
                        suffix = { Text("分鐘") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = minimalFieldColors()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    minsText.toIntOrNull()?.takeIf { it > 0 }?.let { saleReminderMinutes = it }
                    showSaleCustomDialog = false
                }) { Text("確定") }
            },
            dismissButton = { TextButton(onClick = { showSaleCustomDialog = false }) { Text("取消") } }
        )
    }

    if (showExtraDatePicker) {
        val extraPickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDayMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showExtraDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    extraPickerState.selectedDateMillis?.let { ms ->
                        val d = Instant.fromEpochMilliseconds(ms).toLocalDateTime(tz).date
                        if (d != selectedDate) extraDates = (extraDates + d).distinct().sorted()
                    }
                    showExtraDatePicker = false
                }) { Text("加入") }
            },
            dismissButton = {
                TextButton(onClick = { showExtraDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = extraPickerState)
        }
    }



    if (showSalePicker) {
        val editing = saleTimes.getOrNull(saleEditIndex)
        val salePickerState = rememberDatePickerState(
            initialSelectedDateMillis = (editing?.at?.date ?: selectedDate).atStartOfDayMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showSalePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    salePickerState.selectedDateMillis?.let { ms ->
                        val d = Instant.fromEpochMilliseconds(ms).toLocalDateTime(tz).date
                        saleTimes = saleTimes.mapIndexed { i, s ->
                            if (i == saleEditIndex) s.copy(at = LocalDateTime(d, s.at.time)) else s
                        }
                    }
                    showSalePicker = false
                    // 選完日期直接接時間選擇器，少一次點擊（開賣時間幾乎都要指定到分）。
                    showSaleTimePicker = true
                }) { Text("確定") }
            },
            dismissButton = {
                TextButton(onClick = { showSalePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = salePickerState)
        }
    }

    if (showVenueViews) {
        com.bandary.presentation.ui.venue.VenueViewsDialog(
            venue = venue,
            onDismiss = { showVenueViews = false }
        )
    }

    if (showPosterSearch) {
        // 選中的那張要先下載 / 解碼成本機檔才算數，所以會有一小段等待。
        var savingPoster by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = {
                if (!savingPoster) { showPosterSearch = false; viewModel.clearPosters() }
            },
            title = { Text("找海報") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 先說明這批圖是哪裡來的。「這是海報」跟「這只是藝人的照片」
                    // 是完全不同的東西，不寫出來就會有人以為 App 找錯圖。
                    posterTier?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    when {
                        posterSearching || savingPoster -> Row(
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Spacer(Modifier.weight(1f))
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            Text(
                                if (savingPoster) "下載中…" else "搜尋中…",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.weight(1f))
                        }
                        // 上面那行 posterTier 已經說明狀況了，這裡只在它沒話說時補一句。
                        // 兩段都印出來的話，畫面上會是兩句意思一樣的話（實機回報）。
                        posterHits.isEmpty() && posterTier == null -> Text(
                            "可以把藝人或場次名稱填得完整一點再試一次；或直接從相簿選圖。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        posterHits.isEmpty() -> Text(
                            "也可以直接從相簿選圖。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        else -> LazyColumn(
                            modifier = Modifier.heightIn(max = 340.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(posterHits.chunked(2)) { rowItems ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    rowItems.forEach { hit ->
                                        Column(modifier = Modifier.weight(1f)) {
                                            // EventImage 兩種來源都吃：網路圖走 Coil，
                                            // 活動資料庫的 Base64 走背景執行緒解碼。
                                            com.bandary.presentation.ui.components.EventImage(
                                                data = hit.thumb,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(110.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable(enabled = !savingPoster) {
                                                        savingPoster = true
                                                        formScope.launch {
                                                            val local = viewModel.savePosterLocally(hit)
                                                            savingPoster = false
                                                            if (local == null) {
                                                                android.widget.Toast.makeText(
                                                                    context, "這張抓不下來，換一張試試", android.widget.Toast.LENGTH_SHORT
                                                                ).show()
                                                                return@launch
                                                            }
                                                            // 找到的圖跟自己拍的照片走同一條路：
                                                            // 進相簿、受張數上限、能備份。
                                                            if (photoUris.size < photoQuota) {
                                                                photoUris = (photoUris + local).distinct()
                                                            }
                                                            if (photoUri.isNullOrBlank()) photoUri = local
                                                            showPosterSearch = false
                                                            viewModel.clearPosters()
                                                        }
                                                    },
                                                contentScale = ContentScale.Crop
                                            )
                                            Text(
                                                hit.source,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    // 找到的圖多半是別人的作品。寫出來，不要讓人以為可以隨便拿去用。
                    // 一張圖都沒有的時候不用講版權——那時候這段只是讓視窗更長而已。
                    if (posterHits.isNotEmpty()) {
                        Text(
                            "圖片版權屬於原作者。存下來當自己的紀錄沒問題，對外公開使用前請自行確認。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !posterSearching && !savingPoster,
                    onClick = { viewModel.findPoster(artists.firstOrNull().orEmpty(), name) }
                ) { Text("重新搜尋") }
            },
            dismissButton = {
                TextButton(
                    enabled = !savingPoster,
                    onClick = { showPosterSearch = false; viewModel.clearPosters() }
                ) { Text("關閉") }
            }
        )
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = selectedTime.hour,
            initialMinute = selectedTime.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("選擇時間") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    selectedTime = LocalTime(timePickerState.hour, timePickerState.minute)
                    showTimePicker = false
                }) { Text("確定") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("取消") }
            }
        )
    }

    if (showEndTimePicker) {
        val endPickerState = rememberTimePickerState(
            initialHour = endTime?.hour ?: 22,
            initialMinute = endTime?.minute ?: 0,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showEndTimePicker = false },
            title = { Text("選擇結束時間") },
            text = { TimePicker(state = endPickerState) },
            confirmButton = {
                TextButton(onClick = {
                    endTime = LocalTime(endPickerState.hour, endPickerState.minute)
                    showEndTimePicker = false
                }) { Text("確定") }
            },
            dismissButton = {
                TextButton(onClick = { showEndTimePicker = false }) { Text("取消") }
            }
        )
    }

    if (showSaleTimePicker) {
        val editing = saleTimes.getOrNull(saleEditIndex)
        val salePickerState = rememberTimePickerState(
            initialHour = editing?.at?.hour ?: 12,
            initialMinute = editing?.at?.minute ?: 0,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showSaleTimePicker = false },
            title = { Text("選擇開賣時間") },
            text = { TimePicker(state = salePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    val t = LocalTime(salePickerState.hour, salePickerState.minute)
                    saleTimes = saleTimes.mapIndexed { i, s ->
                        if (i == saleEditIndex) s.copy(at = LocalDateTime(s.at.date, t)) else s
                    }
                    showSaleTimePicker = false
                }) { Text("確定") }
            },
            dismissButton = {
                TextButton(onClick = { showSaleTimePicker = false }) { Text("取消") }
            }
        )
    }

    if (showCustomRemDate) {
        val st = rememberDatePickerState(
            initialSelectedDateMillis = (customReminderDate ?: selectedDate).atStartOfDayMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showCustomRemDate = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { ms ->
                        customReminderDate = Instant.fromEpochMilliseconds(ms).toLocalDateTime(tz).date
                    }
                    showCustomRemDate = false
                }) { Text("確定") }
            },
            dismissButton = { TextButton(onClick = { showCustomRemDate = false }) { Text("取消") } }
        ) { DatePicker(state = st) }
    }

    if (showCustomRemTime) {
        val st = rememberTimePickerState(
            initialHour = customReminderTime.hour, initialMinute = customReminderTime.minute, is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showCustomRemTime = false },
            title = { Text("選擇提醒時間") },
            text = { TimePicker(state = st) },
            confirmButton = {
                TextButton(onClick = { customReminderTime = LocalTime(st.hour, st.minute); showCustomRemTime = false }) { Text("確定") }
            },
            dismissButton = { TextButton(onClick = { showCustomRemTime = false }) { Text("取消") } }
        )
    }

    if (showCoverColorPicker) {
        com.bandary.presentation.ui.components.ColorPickerDialog(
            initialHex = coverColorHex ?: COVER_COLOR_PRESETS.first(),
            onPick = { hex -> coverColorHex = hex; showCoverColorPicker = false },
            onDismiss = { showCoverColorPicker = false }
        )
    }

    if (showCityPicker) {
        // 縣市選項多，改用半彈窗（點列表就選，不必再按確定），比對話框更適合長清單。
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { showCityPicker = false }) {
            Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
                Text(
                    if (isOverseas) "輸入城市" else "選擇縣市",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                )
                if (isOverseas) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "「$country」不是台灣，所以下面的台灣縣市清單不適用——直接打字輸入城市名稱。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customCityInput,
                        onValueChange = { customCityInput = it },
                        placeholder = { Text(if (isOverseas) "例：洛杉磯、墨爾本" else "自訂輸入縣市 / 地區") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = minimalFieldColors()
                    )
                    TextButton(
                        onClick = {
                            val c = customCityInput.trim()
                            if (c.isNotBlank()) { selectedCity = c; customCityInput = ""; showCityPicker = false }
                        },
                        enabled = customCityInput.isNotBlank()
                    ) { Text("使用") }
                }
                Spacer(Modifier.height(4.dp))
                // 海外不列台灣縣市。列出來不只是沒用，而是會讓人以為「一定要從這裡挑」，
                // 然後找不到自己的城市就卡住——那正是這次回報的來源。
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(if (isOverseas) emptyList() else TAIWAN_CITIES) { city ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedCity = city
                                    showCityPicker = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp)
                        ) {
                            Text(city, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = { selectedCity = null; showCityPicker = false },
                        modifier = Modifier.weight(1f)
                    ) { Text("清除") }
                    TextButton(
                        onClick = { showCityPicker = false },
                        modifier = Modifier.weight(1f)
                    ) { Text("關閉") }
                }
            }
        }
    }
}

/** 偵測字串是否含中日韓漢字（取票序號填人名的非阻斷提醒用，spec §6）。 */
private fun containsCjk(s: String) = s.any { it.code in 0x4E00..0x9FFF }

/** 釘住標頭的不透明底：sticky 時內容才不會透出到標頭後面。 */
@Composable
private fun StickyGroupBg(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 2.dp)
    ) { content() }
}

/** 表單三大區塊的標頭：粗體大標，點一下整區展開 / 收合。 */
@Composable
private fun GroupHeader(title: String, expanded: Boolean, onToggle: () -> Unit, summary: String? = null) {
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                // 收合時列出區塊內可填的欄位，讓使用者一眼看出有哪些東西可填。
                if (!expanded && summary != null) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                null, tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** 單選下拉：一橫排顯示目前選項 + ▾，點開列出所有選項。多選項欄位共用，取代往下長長一排 chip。 */
@Composable
private fun <T> DropdownField(
    current: T,
    options: List<T>,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(labelOf(current), style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text("▾", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { opt ->
                val on = opt == current
                DropdownMenuItem(
                    text = { Text((if (on) "✓  " else "　  ") + labelOf(opt), fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal) },
                    onClick = { onSelect(opt); open = false }
                )
            }
        }
    }
}

/**
 * 清單式表單列：標題靠左（固定寬、粗體），輸入區靠右（填滿剩餘寬），底部一條細分隔線。
 * [wide] 給內容天生較高/較寬的區塊（圖片、藝人清單、歌單…）用——標題改放上方一行，內容佔滿整寬，
 * 不硬塞進右側窄欄。
 */
/**
 * 全表單統一的文字欄位（Phase 1C）：M3 OutlinedTextField 浮動標籤（label 內建、必填加 *），
 * 取代散落的「左側外部標籤 / 純 placeholder」樣式。圓角/邊框/focus 走既有 theme token。
 */
@Composable
private fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    required: Boolean = false,
    placeholder: String? = null,
    singleLine: Boolean = true,
    isError: Boolean = false,
    supporting: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(if (required) "$label *" else label) },
        placeholder = placeholder?.let { p -> { Text(p) } },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        singleLine = singleLine,
        isError = isError,
        keyboardOptions = keyboardOptions,
        supportingText = supporting?.let { s -> { Text(s, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        colors = minimalFieldColors()
    )
}

/** 表單分組標題（讓使用者一眼掃出各區有哪些欄位可填）。 */
@Composable
// 分組標題：不用重點色（整頁只留一個重點色給「聚焦中的欄位」），改用小字灰 + 字距，
// 分組靠「上方大留白」表達，而不是靠顏色或框線。
// 分組標題改成「有底色的色帶」：純靠留白分組時，捲到中間會分不清楚換區了沒。
// 色帶用 surfaceVariant 的淡色（不是重點色），仍然只有一個重點色留給聚焦中的欄位。
// [first] = 該 Tab 的第一個分組：上方緊接著 TabRow，不需要和前一組拉開距離的留白。
private fun FormGroupHeader(title: String, first: Boolean = false) {
    // 與網頁版的區塊標題同一套：一條較粗的上緣線 + 拉開字距的小標。
    // 先前用實心色塊是為了「看得出分界」，但整份表單塞滿色塊反而顯得雜；
    // 粗線同樣有明確的分界感，而且留白一多，捲動時更好定位。
    Column(
        modifier = Modifier
            .padding(top = if (first) 8.dp else 28.dp, bottom = 10.dp)
            .fillMaxWidth()
    ) {
        androidx.compose.material3.HorizontalDivider(
            thickness = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            letterSpacing = 1.6.sp,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

// 欄位容器：小字灰標籤在上、內容在下、底部一條細線當「欄位邊界」。
// 這條線就是整個表單唯一的分隔手段——沒有外框、沒有底色、沒有卡片。
@Composable
private fun FormSection(title: String, wide: Boolean = false, content: @Composable () -> Unit) {
    if (wide) {
        // 欄位之間留白拉大：網頁版之所以「好填」，多半來自呼吸空間而不是欄位變少。
        Column(modifier = Modifier.fillMaxWidth()) {
            FormLabel(title, Modifier.padding(top = 18.dp, bottom = 4.dp))
            content()
            Spacer(Modifier.height(10.dp))
            FormUnderline()
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FormLabel(title, Modifier.width(96.dp).padding(end = 8.dp))
                Box(modifier = Modifier.weight(1f)) { content() }
            }
            FormUnderline()
        }
    }
}

@Composable
private fun FormLabel(text: String, modifier: Modifier = Modifier) {
    // 欄位標籤：小字 + 明顯字距。字距拉開才像「標籤」，
    // 靠縮小字級只會變得看不清楚——這點在網頁版驗證過後拉回 App。
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

@Composable
private fun FormUnderline() {
    androidx.compose.material3.HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

/** 可折疊區塊：標題列點一下展開 / 收合，預設收合，避免表單一路往下拉不完。 */
@Composable
private fun CollapsibleSection(title: String, subtitle: String? = null, initiallyExpanded: Boolean = false, content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            onClick = { expanded = !expanded },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    if (subtitle != null && !expanded) {
                        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(
                    if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    null, tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (expanded) content()
    }
}

private fun LocalDate.atStartOfDayMillis(): Long {
    val instant = LocalDateTime(this, LocalTime(0, 0)).toInstant(TimeZone.UTC)
    return instant.toEpochMilliseconds()
}

// 歌單編碼（encodeSetlist / parseSetlist / moveSetlistItems 與兩個前綴常數）在 SetlistCodec.kt。

/**
 * 把貼上的一整份歌單文字拆成一首一首。每行一首，並去掉常見的序號前綴
 * （「1.」「1、」「01」「- 」「• 」「(1)」等），避免整份貼進來還要一行行手動清。
 */
fun parsePastedSetlist(raw: String): List<String> =
    raw.split('\n')
        .map { line ->
            line.trim()
                .replace(Regex("""^[(（\[【]?\s*\d{1,3}\s*[)）\]】.、．:：-]?\s*"""), "")
                .replace(Regex("""^[-–—•·*▪◦]\s*"""), "")
                .trim()
        }
        .filter { it.isNotBlank() }

/** 批次貼上歌單的對話框：貼一整份文字 → 預覽會拆成幾首 → 確認後一次加入。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasteSetlistDialog(
    artistName: String,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    var text by remember { mutableStateOf("") }
    val parsed = remember(text) { parsePastedSetlist(text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (artistName.isBlank()) "貼上歌單" else "貼上 $artistName 的歌單") },
        text = {
            com.bandary.presentation.ui.components.DialogBody(spacing = 8.dp) {
                Text(
                    "一行一首，直接整份貼進來即可。開頭的「1.」「- 」等序號會自動去掉。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 280.dp),
                    placeholder = { Text("1. 山海\n2. 大風吹\n3. 爛泥") },
                    shape = RoundedCornerShape(12.dp)
                )
                Text(
                    if (parsed.isEmpty()) "尚未偵測到歌曲" else "會加入 ${parsed.size} 首",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(parsed) }, enabled = parsed.isNotEmpty()) {
                Text("加入 ${parsed.size} 首")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/**
 * 把一整份卡司名單拆成多位藝人。分隔符：全形／半形直線 ｜ |、頓號 、、全形斜線 ／、分號 ; ；、換行。
 * 刻意不拆半形斜線 "/"，以免把日期（7/18）或團名（AC/DC）切開。會去掉「7/18：」這種日期前綴、
 * 純日期或空白 token，並去除重複。
 */
// 卡司字串拆成一位一位：活動編輯頁、追蹤藝人比對都走同一套規則，
// 實作放在 domain（splitArtistNames），這裡只是既有呼叫端的別名。
internal fun splitArtistList(text: String): List<String> =
    com.bandary.domain.model.splitArtistNames(text)

@Composable
private fun PasteArtistsDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val preview = remember(text) { splitArtistList(text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("貼上卡司名單") },
        text = {
            com.bandary.presentation.ui.components.DialogBody(spacing = 8.dp) {
                Text(
                    "把整份卡司貼進來，會自動拆成一位一位。可用 ｜ 、 ／ 「 / 」 分號、換行或兩個以上空格分隔；團名裡的單一空格會保留（如 Sabrina 胡恂舞），AC/DC 這種沒空白的斜線不會被切開，日期前綴如「7/18：」會自動去掉。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 240.dp),
                    placeholder = { Text("例：康士坦的變化球KST｜名譽伝説｜溫室雜草…") }
                )
                if (preview.isNotEmpty()) {
                    Text("將新增 ${preview.size} 位：${preview.take(6).joinToString("、")}${if (preview.size > 6) "…" else ""}",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = preview.isNotEmpty()) {
                Text("新增 ${preview.size} 位")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/**
 * 圖片文字辨識結果選取視窗：辨識中顯示轉圈；辨識完列出每一行文字，
 * 點文字＝加入為歌手，另可將該行「設為名稱 / 場地」。
 */
@Composable
private fun OcrPickerDialog(
    running: Boolean,
    lines: List<String>?,
    stub: com.bandary.data.util.TicketStubParser.Stub? = null,
    setlistMode: Boolean = false,
    onAddArtist: (String) -> Unit,
    onAddSong: (String) -> Unit = {},
    onSetName: (String) -> Unit,
    onSetVenue: (String) -> Unit,
    onApplyStub: (com.bandary.data.util.TicketStubParser.Stub) -> Unit = {},
    onDismiss: () -> Unit
) {
    // 解讀到的欄位預設全部勾選——票根上的資訊本來就是要填進表單的，
    // 讓使用者「取消不要的」比「一個一個勾」快得多。
    val stubFields = remember(stub) { if (setlistMode) emptyList() else stubFieldsOf(stub) }
    var picked by remember(stub) { mutableStateOf(stubFields.map { it.key }.toSet()) }
    // 解讀出來的值可以直接在這裡改。OCR 認錯字是常態（實機：場地讀成「新北工商更覽中」、
    // 座位讀成「2啡-1」、票價讀成 2006），套用之後再回表單一欄一欄找出來改，
    // 比在這張卡上順手改掉慢太多。
    var edits by remember(stub) { mutableStateOf(stubFields.associate { it.key to it.raw }) }
    // 原文清單預設收合——沒有票根欄位時它就是主角，有欄位時它是備查。
    var rawOpen by remember(stub) { mutableStateOf(stubFields.isEmpty()) }
    // 改壞的（日期打成「九月」、票價打成空白）不算進套用項數，也不讓它靜悄悄被丟掉。
    val invalidKeys = remember(edits, picked) {
        picked.filterNot { stubValueValid(it, edits[it].orEmpty()) }.toSet()
    }
    val applyCount = (picked - invalidKeys).size

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    setlistMode -> "掃歌單"
                    stubFields.isNotEmpty() -> "票根解讀"
                    else -> "圖片文字辨識"
                }
            )
        },
        text = {
            when {
                running -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("辨識中…", style = MaterialTheme.typography.bodyMedium)
                }
                lines.isNullOrEmpty() -> Text(
                    "沒有辨識到文字。請換一張文字較清楚、對比明顯的圖片再試。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // ── 票根解讀卡：讀得出欄位時才出現 ──────────────────────
                    if (stubFields.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "讀到 " + stubFields.size + " 個欄位",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        onClick = {
                                            picked = if (picked.size == stubFields.size) emptySet()
                                            else stubFields.map { it.key }.toSet()
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Text(if (picked.size == stubFields.size) "全不選" else "全選", fontSize = 12.sp)
                                    }
                                }
                                // 欄位多的票根（ibon 會讀到 9～11 項）整張卡會比對話框還高，
                                // 所以只讓欄位區捲動，標題與說明固定在上下。
                                Column(
                                    modifier = Modifier
                                        .heightIn(max = if (rawOpen) 210.dp else 400.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    stubFields.forEach { f ->
                                        val bad = f.key in invalidKeys
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Checkbox(
                                                checked = f.key in picked,
                                                onCheckedChange = {
                                                    picked = if (f.key in picked) picked - f.key else picked + f.key
                                                }
                                            )
                                            Text(
                                                f.label,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.width(56.dp)
                                            )
                                            // 值本身就是輸入框：點下去直接改，不用先按編輯再改。
                                            // 外面包一層底色是為了讓人看得出「這格可以打字」——
                                            // 純文字看起來就只是結果，沒人會去點它。
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (bad) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                                else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                BasicTextField(
                                                    value = edits[f.key].orEmpty(),
                                                    onValueChange = { v ->
                                                        edits = edits + (f.key to v)
                                                        // 動手改過就當作要套用；不然改完還得回頭勾。
                                                        if (v.isNotBlank()) picked = picked + f.key
                                                    },
                                                    singleLine = true,
                                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                                        color = if (bad) MaterialTheme.colorScheme.error
                                                        else MaterialTheme.colorScheme.onSurface
                                                    ),
                                                    keyboardOptions = if (f.key == "price")
                                                        KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                                                    else KeyboardOptions.Default,
                                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 8.dp, vertical = 7.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                if (invalidKeys.isNotEmpty()) {
                                    Text(
                                        "有 " + invalidKeys.size + " 欄還讀不懂（日期要像 2026-09-19、時間 19:30、票價只填數字），改好才會套用。",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                Text(
                                    "可以直接改；只會填進空白欄位，已經填過的不會被蓋掉。",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    // 原文區：有票根欄位時改成可收合的一列。
                    // 之前是兩塊固定高度的東西搶同一個對話框高度，解讀卡吃完之後
                    // 原文只剩兩行的縫，裡面的字根本看不到（實機回報「區域太小」）。
                    if (stubFields.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { rawOpen = !rawOpen }
                                .padding(vertical = 6.dp)
                        ) {
                            Text(
                                "辨識原文 · " + lines.size + " 行",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                if (rawOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                if (rawOpen) "收合原文" else "展開原文",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            if (setlistMode) "點一行文字＝加入這位藝人的歌單。"
                            else "點一行文字＝加入為歌手；也可將該行設為名稱或場地。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (rawOpen) LazyColumn(
                        modifier = Modifier.heightIn(max = if (stubFields.isEmpty()) 360.dp else 260.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(lines) { line ->
                            Surface(
                                onClick = { if (setlistMode) onAddSong(line) else onAddArtist(line) },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(line, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    if (!setlistMode) {
                                        TextButton(onClick = { onSetName(line) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                            Text("名稱", fontSize = 12.sp)
                                        }
                                        TextButton(onClick = { onSetVenue(line) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                            Text("場地", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (stubFields.isNotEmpty() && stub != null) {
                TextButton(
                    enabled = applyCount > 0,
                    onClick = { onApplyStub(editedStub(stub, edits, picked - invalidKeys)); onDismiss() }
                ) { Text("套用 " + applyCount + " 項") }
            } else {
                TextButton(onClick = onDismiss) { Text("完成") }
            }
        },
        dismissButton = {
            if (stubFields.isNotEmpty()) TextButton(onClick = onDismiss) { Text("不要套用") }
        }
    )
}

/**
 * 解讀卡上的一列。key 同時當勾選狀態與編輯內容的識別碼。
 *
 * [raw] 是丟進輸入框的字，跟給人看的 [display] 分開：票價顯示「NT$2000」好讀，
 * 但要改的時候前面那個 NT$ 只會礙事。
 */
private data class StubField(val key: String, val label: String, val display: String, val raw: String)

private fun stubFieldsOf(s: com.bandary.data.util.TicketStubParser.Stub?): List<StubField> {
    if (s == null) return emptyList()
    fun f(key: String, label: String, value: String, raw: String = value) =
        StubField(key, label, value, raw)
    return listOfNotNull(
        s.showName?.let { f("name", "名稱", it) },
        s.venue?.let { f("venue", "場地", it) },
        s.city?.let { f("city", "縣市", it) },
        s.date?.let { f("date", "日期", it.toString()) },
        s.time?.let { f("time", "時間", it.toString()) },
        s.seat?.let { f("seat", "座位", it) },
        s.price?.let { f("price", "票價", "NT$" + it.toInt(), it.toInt().toString()) },
        s.platform?.let { f("platform", "平台", it) },
        s.machine?.let { f("machine", "取票機台", it) },
        s.pickupNumber?.let { f("serial", "取票序號", it) },
        s.pickupTicketNo?.let { f("orderNo", "取票號碼", it) }
    )
}

// ── 編輯後的值要變回型別才填得進表單 ──────────────────────────────
// 日期、時間、票價在 Stub 裡是 LocalDate / LocalTime / Double，不是字串。
// 使用者在卡上改完之後要能轉得回來，轉不回來就不要默默吃掉——標紅、不算進套用項數。

/** 日期：接受 2026-09-19 / 2026/9/19 / 20260919 這幾種寫法。 */
private fun parseStubDate(v: String): LocalDate? {
    val t = v.trim().replace('/', '-').replace('.', '-')
    runCatching { return LocalDate.parse(t) }
    val digits = t.filter { it.isDigit() }
    if (digits.length == 8) {
        runCatching {
            return LocalDate.parse(digits.substring(0, 4) + "-" + digits.substring(4, 6) + "-" + digits.substring(6, 8))
        }
    }
    // 2026-9-7 這種沒補零的寫法手打很常見。
    val parts = t.split("-").map { it.trim() }
    if (parts.size == 3) {
        runCatching {
            return LocalDate.parse(
                parts[0].padStart(4, '0') + "-" + parts[1].padStart(2, '0') + "-" + parts[2].padStart(2, '0')
            )
        }
    }
    return null
}

/** 時間：接受 19:30 / 19：30（全形冒號）/ 1930。 */
private fun parseStubTime(v: String): LocalTime? {
    val t = v.trim().replace('：', ':')
    runCatching { return LocalTime.parse(t) }
    val parts = t.split(":")
    if (parts.size == 2) {
        runCatching { return LocalTime.parse(parts[0].padStart(2, '0') + ":" + parts[1].padStart(2, '0')) }
    }
    val digits = t.filter { it.isDigit() }
    if (digits.length == 4) {
        runCatching { return LocalTime.parse(digits.substring(0, 2) + ":" + digits.substring(2, 4)) }
    }
    return null
}

/** 票價：把 NT$、逗號、元這些裝飾去掉再轉數字。 */
private fun parseStubPrice(v: String): Double? =
    v.filter { it.isDigit() || it == '.' }.toDoubleOrNull()?.takeIf { it > 0 }

/** 這一欄改成現在這個樣子，還填得進表單嗎。 */
private fun stubValueValid(key: String, value: String): Boolean {
    if (value.isBlank()) return false
    return when (key) {
        "date" -> parseStubDate(value) != null
        "time" -> parseStubTime(value) != null
        "price" -> parseStubPrice(value) != null
        else -> true
    }
}

/**
 * 把卡片上編輯後的值組回 Stub。沒勾的欄位一律歸 null——
 * 套用端的規則是「非 null 才填」，歸 null 就等於跳過這一欄。
 */
private fun editedStub(
    s: com.bandary.data.util.TicketStubParser.Stub,
    edits: Map<String, String>,
    picked: Set<String>
): com.bandary.data.util.TicketStubParser.Stub {
    fun t(key: String): String? = edits[key]?.trim()?.takeIf { it.isNotBlank() && key in picked }
    return s.copy(
        showName = t("name"),
        venue = t("venue"),
        city = t("city"),
        date = t("date")?.let { parseStubDate(it) },
        time = t("time")?.let { parseStubTime(it) },
        seat = t("seat"),
        price = t("price")?.let { parseStubPrice(it) },
        platform = t("platform"),
        machine = t("machine"),
        pickupNumber = t("serial"),
        pickupTicketNo = t("orderNo")
    )
}
