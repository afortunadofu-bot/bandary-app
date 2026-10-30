package com.bandary.presentation.ui.shows

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.outlined.Campaign
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
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import com.bandary.domain.model.occurrenceDates
import com.bandary.domain.model.saleStartAt
import com.bandary.domain.model.typeLabel
import com.bandary.presentation.ui.components.PlatformChip
import com.bandary.presentation.ui.components.TicketStatusChip
import com.bandary.presentation.ui.theme.BandaryColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConcertDetailViewModel @Inject constructor(
    private val repo: ConcertRepository,
    private val songRepo: com.bandary.data.repository.DailySongRepository,
    private val imageSearch: com.bandary.data.remote.ImageSearchService,
    private val freeRepo: com.bandary.data.repository.FreeEventsRepository,
    private val firestore: com.bandary.data.remote.FirestoreService,
    private val settings: com.bandary.data.local.SettingsDataStore,
    private val friendsRepo: com.bandary.data.repository.FriendsRepository
) : ViewModel() {
    private val _concert = MutableStateFlow<Concert?>(null)
    val concert: StateFlow<Concert?> = _concert.asStateFlow()

    /** 把這場活動投稿到「活動」共享清單。管理員直接新增，一般使用者送審。 */
    fun submitToFreeEvents(onMessage: (String) -> Unit) {
        val c = _concert.value ?: return
        if (!firestore.isSignedIn()) { onMessage("請先到「我的」用 Google 登入"); return }
        viewModelScope.launch {
            val admin = runCatching { settings.isAdmin.first() }.getOrDefault(false)
            val event = com.bandary.domain.model.FreeEvent(
                name = c.name,
                artistName = c.artistName,
                venue = c.venue,
                city = c.city,
                dateTime = c.dateTime,
                note = c.notes,
                sourceUrl = c.infoUrl ?: c.ticketUrl
            )
            runCatching { if (admin) freeRepo.add(event) else freeRepo.submit(event) }
                .onSuccess { onMessage(if (admin) "已新增到活動" else "已投稿到活動，待管理員審核") }
                .onFailure { onMessage("投稿失敗：${it.message ?: "請再試一次"}") }
        }
    }

    // Auto-fetched background when the concert has no user photo.
    private val _autoBackground = MutableStateFlow<String?>(null)
    val autoBackground: StateFlow<String?> = _autoBackground.asStateFlow()

    /**
     * 「那陣子我在聽」：明確連到這場的選歌（v21 concertId）＋演出前後一週的每日選歌。
     * 併用日期區間是為了讓**舊資料也有內容**——concertId 是新欄位，先前記的歌都是 null。
     */
    private val _relatedSongs = MutableStateFlow<List<com.bandary.domain.model.DailySong>>(emptyList())
    val relatedSongs: StateFlow<List<com.bandary.domain.model.DailySong>> = _relatedSongs.asStateFlow()
    private var relatedJob: kotlinx.coroutines.Job? = null

    fun load(id: String) {
        viewModelScope.launch {
            val c = repo.getConcertById(id)
            _concert.update { c }
            relatedJob?.cancel()
            if (c != null) {
                val d = c.dateTime.date
                relatedJob = viewModelScope.launch {
                    kotlinx.coroutines.flow.combine(
                        songRepo.getSongsForConcert(id),
                        songRepo.getSongsBetween(
                            d.minus(7, kotlinx.datetime.DateTimeUnit.DAY),
                            d.plus(7, kotlinx.datetime.DateTimeUnit.DAY)
                        )
                    ) { linked, near -> (linked + near).distinctBy { it.id }.sortedWith(compareBy({ it.date }, { it.rank })) }
                        .collect { list -> _relatedSongs.value = list }
                }
            }
            // Only auto-search when the user hasn't set a cover photo.
            if (c != null && c.photoUri.isNullOrBlank() && imageSearch.isConfigured()) {
                val q = c.artistName.ifBlank { c.name }
                // Try artist live photo first, then an empty-stage / venue shot.
                val results = imageSearch.searchImages("$q 演唱會", limit = 3)
                    .ifEmpty { imageSearch.searchImages("${c.venue} 舞台 空景", limit = 3) }
                _autoBackground.update { results.firstOrNull()?.fullUrl }
            }
        }
    }

    /** 「那陣子我在聽」點歌直接編輯；清單是 Flow，存檔後自動更新。 */
    fun updateSong(song: com.bandary.domain.model.DailySong, title: String, artist: String, memo: String) {
        viewModelScope.launch {
            songRepo.saveDailySong(song.copy(songTitle = title, artistName = artist, memo = memo.ifBlank { null }))
        }
    }

    fun deleteSong(id: String) {
        viewModelScope.launch { songRepo.deleteDailySong(id) }
    }

    fun delete(id: String, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.deleteConcert(id)
            runCatching { friendsRepo.publishMyCalendar(quiet = true) }
            runCatching { friendsRepo.publishYearlyLeaderboard() }
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ConcertDetailScreen(
    concertId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onShareTicket: (String) -> Unit,
    onDuplicate: (String) -> Unit,
    onOpenArtist: (String) -> Unit = {},      // 點藝人 → 看這位藝人的其他場
    onOpenCompanion: (String) -> Unit = {},   // 點同行者 → 看一起去過的場次
    viewModel: ConcertDetailViewModel = hiltViewModel()
) {
    androidx.compose.runtime.LaunchedEffect(concertId) { viewModel.load(concertId) }
    val concert by viewModel.concert.collectAsState()
    val autoBackground by viewModel.autoBackground.collectAsState()
    val relatedSongs by viewModel.relatedSongs.collectAsState()
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showTimetableImages by remember { mutableStateOf(false) }
    var zoomImage by remember { mutableStateOf<String?>(null) }
    var galleryImages by remember { mutableStateOf<List<String>>(emptyList()) }  // 左右滑動預覽用
    var galleryStart by remember { mutableStateOf(0) }
    var heroTextHidden by remember { mutableStateOf(false) }  // 大圖只留圖片（隱藏疊字），預設顯示
    var editingSong by remember { mutableStateOf<com.bandary.domain.model.DailySong?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("演唱會詳情", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                },
                // 頂列只留最常用的兩個動作（分享 / 編輯），複製與刪除收進 ⋮ 選單。
                // 四顆並排會把標題擠掉，也不符合「app bar ≤3 動作」的設計準則。
                actions = {
                    var menuOpen by remember { mutableStateOf(false) }
                    IconButton(onClick = { onShareTicket(concertId) }) {
                        Icon(Icons.Outlined.Share, "分享票根")
                    }
                    IconButton(onClick = { onEdit(concertId) }) {
                        Icon(Icons.Outlined.Edit, "編輯")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, "更多")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("複製這場") },
                                leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                                onClick = { menuOpen = false; onDuplicate(concertId) }
                            )
                            DropdownMenuItem(
                                text = { Text("刪除", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { menuOpen = false; showDeleteDialog = true }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        val c = concert
        if (c == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("讀取中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Hero — cover photo, else auto-fetched artist/venue image, else gradient
                item {
                    // 與「我的演唱會」卡片、票根分享同一張封面（有封面用封面，沒有就用依 id 分配到的內建底圖）；
                    // 只有連內建底圖都取不到時，才退回網路自動找圖。
                    val bg = com.bandary.presentation.ui.components.effectiveCoverUri(context, c) ?: autoBackground
                    val heroLight = c.coverGradientLight && bg != null
                    val heroFg = if (heroLight) androidx.compose.ui.graphics.Color(0xFF1E1E1E)
                        else androidx.compose.ui.graphics.Color.White
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 10f)
                            .clip(RoundedCornerShape(16.dp))
                            // 與列表 / 月曆卡片同一套底色邏輯（自訂色或依 id 的莫蘭迪色），確保兩頁一致
                            .background(com.bandary.presentation.ui.components.concertCoverBrush(c))
                    ) {
                        if (bg != null) {
                            // 封面 + 多張照片：直接在詳情頁 hero 上左右滑切換（不必先點開）；仍可點圖開全螢幕檢視。
                            val heroImages = (listOfNotNull(c.photoUri?.takeIf { it.isNotBlank() }) + c.photoUris).distinct().ifEmpty { listOf(bg) }
                            val allImages = (heroImages + listOfNotNull(c.seatMapUri?.takeIf { it.isNotBlank() })).distinct()
                            val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { heroImages.size })
                            androidx.compose.foundation.pager.HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize()
                            ) { page ->
                                AsyncImage(
                                    model = heroImages[page],
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().clickable {
                                        galleryImages = allImages
                                        galleryStart = allImages.indexOf(heroImages[page]).coerceAtLeast(0)
                                    },
                                    contentScale = ContentScale.Crop
                                )
                            }
                            // 多張時右下角顯示頁點指示
                            if (heroImages.size > 1) {
                                Row(
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    repeat(heroImages.size) { i ->
                                        Box(
                                            modifier = Modifier.size(if (i == pagerState.currentPage) 8.dp else 6.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (i == pagerState.currentPage) androidx.compose.ui.graphics.Color.White
                                                    else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f)
                                                )
                                        )
                                    }
                                }
                            }
                        }
                        if (!heroTextHidden) {
                            // bottom scrim（全站統一，見 components/Scrim）：淺色壓白、深色壓黑
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(com.bandary.presentation.ui.components.Scrim.bottom(light = heroLight))
                            )
                            Column(
                                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
                            ) {
                                // 標題優先顯示活動 / 音樂祭名稱（避免被藝人清單蓋掉）；沒有名稱才用藝人。
                                Text(
                                    c.name.ifBlank { com.bandary.presentation.ui.components.concertArtistsLabel(c) },
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = heroFg,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    buildString {
                                        c.city?.takeIf { it.isNotBlank() }?.let { append(it).append("　") }
                                        append(c.venue)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = heroFg.copy(alpha = 0.9f),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (c.photoUri.isNullOrBlank() && autoBackground != null) {
                            Surface(
                                color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                            ) {
                                Text(
                                    "自動配圖",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 9.sp,
                                    color = androidx.compose.ui.graphics.Color.White
                                )
                            }
                        }
                    }
                    // 只看圖 / 顯示資訊 切換（僅在有底圖時）。放在圖片下方而不是疊在圖上：
                    // 疊在左上角會蓋住照片（實機回報）。一定要帶文字，純圖示看起來像載入失敗。
                    if (bg != null) {
                        Surface(
                            onClick = { heroTextHidden = !heroTextHidden },
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    if (heroTextHidden) Icons.Outlined.Info else Icons.Outlined.Photo,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    if (heroTextHidden) "顯示資訊" else "只看圖",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                    }
                }

                // 完整藝人 / 卡司名單（音樂祭多藝人時完整列出，不截斷）；多日活動依日期分組。
                run {
                    val slots = c.festivals.filter { it.isSelected && it.artistName.isNotBlank() }
                    val artists = (listOfNotNull(c.artistName.takeIf { it.isNotBlank() }) + slots.map { it.artistName })
                        .filter { it.isNotBlank() }.distinct()
                    val hasDays = slots.any { it.day.isNotBlank() }
                    if (artists.isNotEmpty()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("藝人 / 卡司 · ${artists.size}　點藝人看他的其他場", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (hasDays) {
                                    // 依日期分組顯示
                                    val byDay = slots.groupBy { it.day }
                                    val orderedDays = byDay.keys.sortedBy { it.ifBlank { "9999" } }
                                    orderedDays.forEach { day ->
                                        val label = runCatching { kotlinx.datetime.LocalDate.parse(day) }.getOrNull()?.let { "${it.monthNumber}/${it.dayOfMonth}" } ?: "未指定日期"
                                        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                        ArtistChipFlow(byDay[day].orEmpty().map { it.artistName }.distinct(), onOpenArtist)
                                    }
                                } else {
                                    // FEAT 的藝人名後面加註記，讓「為什麼他的現場時數沒被算進去」看得出來。
                                    val featOfBy = slots.filter { it.feat }
                                        .associate { it.artistName.trim() to it.featOf.trim() }
                                    ArtistChipFlow(
                                        artists = artists,
                                        onOpenArtist = onOpenArtist,
                                        labelOf = { n ->
                                            featOfBy[n.trim()]?.takeIf { it.isNotBlank() }
                                                ?.let { host -> "$n · feat. $host" } ?: n
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 全部圖片（封面 + 多張 + 場位圖）去重，點任一張都可左右滑動預覽
                run {
                    val allImages = (listOfNotNull(c.photoUri?.takeIf { it.isNotBlank() }) + c.photoUris +
                        listOfNotNull(c.seatMapUri?.takeIf { it.isNotBlank() })).distinct()
                    if (c.photoUris.isNotEmpty()) {
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(c.photoUris) { uri ->
                                    AsyncImage(
                                        model = uri,
                                        contentDescription = null,
                                        modifier = Modifier.size(120.dp).clip(RoundedCornerShape(12.dp))
                                            .clickable { galleryImages = allImages; galleryStart = allImages.indexOf(uri).coerceAtLeast(0) },
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }
                    // 場位圖：只在沒被收進上面的多張照片時才另外顯示（避免重複）
                    val seat = c.seatMapUri?.takeIf { it.isNotBlank() }
                    if (seat != null && seat !in c.photoUris && seat != c.photoUri) {
                        item {
                            Text("場位圖", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            AsyncImage(
                                model = seat,
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    .clickable { galleryImages = allImages; galleryStart = allImages.indexOf(seat).coerceAtLeast(0) },
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }

                // Status chips
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TicketStatusChip(c.ticketStatus)
                        c.ticketPlatform?.takeIf { it.isNotBlank() }?.let { PlatformChip(it) }
                    }
                }

                // Rating
                if (c.rating != null && c.rating!! > 0) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            (1..5).forEach { star ->
                                Icon(
                                    if (star <= c.rating!!) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                    contentDescription = null,
                                    tint = if (star <= c.rating!!) BandaryColors.Yellow else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            c.rewatchIntent?.let {
                                Spacer(Modifier.size(12.dp))
                                Text(
                                    "會想再看：${it.label}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Info card
                item {
                    InfoCard(c)
                }

                // 連結：售票主頁（單一）+ 活動粉專
                run {
                    // 舊資料若含多個 \n 網址，只取第一個。
                    val ticketUrl = c.ticketUrl?.split("\n")?.map { it.trim() }?.firstOrNull { it.isNotBlank() }
                    if (!ticketUrl.isNullOrBlank() || !c.infoUrl.isNullOrBlank()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!ticketUrl.isNullOrBlank()) {
                                    Surface(
                                        onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ticketUrl))) } },
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
                                            Text("售票主頁", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                                if (!c.infoUrl.isNullOrBlank()) {
                                    Surface(
                                        onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(c.infoUrl))) } },
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
                                            Text("活動粉專", color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 節目表：讀取獨立的 timetableUris 欄位（與封面 / 演出照片分開），有上傳才顯示「查看節目表」。
                if (c.timetableUris.isNotEmpty()) {
                    item {
                        Surface(
                            onClick = { showTimetableImages = true },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "查看節目表",
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    "${c.timetableUris.size} 張",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }

                // Notes——「從 Google 日曆匯入」是系統標記，不是使用者備註，不顯示出來。
                val userNote = c.notes?.takeIf {
                    it.isNotBlank() && !it.startsWith(com.bandary.domain.model.CALENDAR_IMPORT_NOTE)
                }
                if (userNote != null) {
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("備忘", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(4.dp))
                                Text(userNote, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                // 同行者
                if (c.companions.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("同行者　點名字看一起去過的場次", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            // 與藝人 chip 同款版面；點下去導到「同行者」篩選頁看所有一起去過的場次。
                            ArtistChipFlow(
                                artists = c.companions,
                                onOpenArtist = { onOpenCompanion(it) },
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                // 歌單
                if (c.setlist.isNotEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                val songCount = c.setlist.count { !it.startsWith(SETLIST_ARTIST_PREFIX) && !it.startsWith(SETLIST_SECTION_PREFIX) }
                                Text("歌單 · $songCount 首", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(2.dp))
                                // 用編輯頁同一套規則解析（沒標頭的舊資料歸給主藝人），每一段都標出是誰的歌單——
                                // 只有一位藝人時也標，不然音樂祭這種多卡司的場次看不出來是誰唱的。
                                val blocks = remember(c.setlist, c.artistName) { parseSetlist(c.setlist, c.artistName.trim()) }
                                blocks.forEach { (artist, lines) ->
                                    if (artist.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(artist, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                    var n = 0
                                    lines.forEach { line ->
                                        if (line.startsWith(SETLIST_SECTION_PREFIX)) {
                                            n = 0
                                            Spacer(Modifier.height(4.dp))
                                            Text("— ${line.removePrefix(SETLIST_SECTION_PREFIX)} —", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
                                        } else {
                                            n++
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text("$n.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(24.dp))
                                                Text(line, style = MaterialTheme.typography.bodyMedium)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 那陣子我在聽：把選歌日記接回這場演出（明確連結 + 前後一週的每日選歌）。
                if (relatedSongs.isNotEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "那陣子我在聽 · ${relatedSongs.size} 首　點歌可編輯",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                relatedSongs.take(12).forEach { s ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { editingSong = s }
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "${s.date.monthNumber}/${s.date.dayOfMonth}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.width(38.dp)
                                        )
                                        Text(
                                            s.songTitle,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        // 從這場歌單記下來的歌標一個小點，和一般的每日選歌區隔。
                                        if (s.concertId == c.id) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                            )
                                        }
                                    }
                                }
                                if (relatedSongs.size > 12) {
                                    Text(
                                        "還有 ${relatedSongs.size - 12} 首",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // 投稿到活動：把這場活動分享到共享的活動清單
                item {
                    OutlinedButton(
                        onClick = { viewModel.submitToFreeEvents { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() } },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Outlined.Campaign, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("投稿此活動到活動")
                    }
                }
            }
        }
    }

    if (showTimetableImages) {
        val c = concert
        val imgs = c?.timetableUris.orEmpty()
        // 用和演唱會照片相同的左右滑 viewer：多張節目表可左右滑切換（先前上下捲的版本讓人以為「只有一張」）。
        if (imgs.isNotEmpty()) {
            com.bandary.presentation.ui.components.SwipeableImageViewer(
                images = imgs,
                onDismiss = { showTimetableImages = false }
            )
        }
    }

    zoomImage?.let { img ->
        com.bandary.presentation.ui.components.ImageZoomDialog(model = img, onDismiss = { zoomImage = null })
    }

    if (galleryImages.isNotEmpty()) {
        com.bandary.presentation.ui.components.SwipeableImageViewer(
            images = galleryImages,
            startIndex = galleryStart,
            onDismiss = { galleryImages = emptyList() }
        )
    }

    editingSong?.let { song ->
        EditRelatedSongDialog(
            song = song,
            onDismiss = { editingSong = null },
            onSave = { title, artist, memo ->
                viewModel.updateSong(song, title, artist, memo)
                editingSong = null
            },
            onDelete = {
                viewModel.deleteSong(song.id)
                editingSong = null
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("刪除這場演唱會？") },
            text = { Text("刪除後無法復原。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.delete(concertId, onBack)
                }) { Text("刪除", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            }
        )
    }
}

/** 「那陣子我在聽」單首編輯：歌名、藝人、備註；刪除要再按一次確認。 */
@Composable
private fun EditRelatedSongDialog(
    song: com.bandary.domain.model.DailySong,
    onDismiss: () -> Unit,
    onSave: (title: String, artist: String, memo: String) -> Unit,
    onDelete: () -> Unit
) {
    var title by remember(song.id) { mutableStateOf(song.songTitle) }
    var artist by remember(song.id) { mutableStateOf(song.artistName) }
    var memo by remember(song.id) { mutableStateOf(song.memo.orEmpty()) }
    var confirmDelete by remember(song.id) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("編輯 ${song.date.monthNumber}/${song.date.dayOfMonth} 的選歌") },
        text = {
            com.bandary.presentation.ui.components.DialogBody(spacing = 8.dp) {
                androidx.compose.material3.OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("歌名") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                androidx.compose.material3.OutlinedTextField(
                    value = artist, onValueChange = { artist = it },
                    label = { Text("藝人") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                androidx.compose.material3.OutlinedTextField(
                    value = memo, onValueChange = { memo = it },
                    label = { Text("備註") }, maxLines = 3, modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = { if (confirmDelete) onDelete() else confirmDelete = true }) {
                    Text(
                        if (confirmDelete) "再按一次刪除這首" else "刪除這首",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title.trim(), artist.trim(), memo.trim()) },
                enabled = title.isNotBlank()
            ) { Text("儲存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/**
 * 藝人 / 卡司 chip 牆。
 *
 * 列距過大的原因不是 spacing，而是 M3 的 `Surface(onClick)` 會套用最小點擊尺寸 48dp——
 * chip 本身只有約 26dp 高，卻各自佔掉 48dp 版面，音樂祭幾十組藝人時整片被撐得很鬆。
 * 這裡把該強制值關掉，改讓 chip 自己長到約 34dp 來維持可點面積：
 * 節距從 48+8＝56dp 降到 34+6＝40dp。
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ArtistChipFlow(
    artists: List<String>,
    onOpenArtist: (String) -> Unit,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    labelOf: (String) -> String = { it }   // 顯示文字可加註記；點擊仍用原始名字
) {
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.material3.LocalMinimumInteractiveComponentSize provides androidx.compose.ui.unit.Dp.Unspecified
    ) {
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            artists.forEach { a ->
                Surface(
                    onClick = { onOpenArtist(a) },
                    shape = RoundedCornerShape(8.dp),
                    color = color
                ) {
                    Text(
                        labelOf(a),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoCard(c: Concert) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val dt = c.dateTime
            fun fmtDate(y: Int, mo: Int, d: Int) = "$y/${mo.toString().padStart(2, '0')}/${d.toString().padStart(2, '0')}"
            val occDates = c.occurrenceDates()
            val dateStr = when {
                // 連續多日：起 – 迄
                c.endDate?.let { it > dt.date } == true && c.extraDates.isEmpty() ->
                    fmtDate(dt.year, dt.monthNumber, dt.dayOfMonth) + " – " +
                        fmtDate(c.endDate!!.year, c.endDate!!.monthNumber, c.endDate!!.dayOfMonth)
                // 非連續多日：列出各日期
                occDates.size > 1 ->
                    occDates.joinToString("、") { "${it.monthNumber}/${it.dayOfMonth}" }
                else -> fmtDate(dt.year, dt.monthNumber, dt.dayOfMonth)
            }
            val timeStr = buildString {
                append(dt.hour.toString().padStart(2, '0')).append(":").append(dt.minute.toString().padStart(2, '0'))
                c.endTime?.let { append(" – ").append(it.hour.toString().padStart(2, '0')).append(":").append(it.minute.toString().padStart(2, '0')) }
            }
            InfoRow("類型", c.typeLabel())
            InfoRow("日期", dateStr)
            InfoRow("時間", timeStr)
            // 多組開賣時間各給一列（有名稱就標名稱），只顯示最早那一組會漏掉後面幾輪。
            c.saleTimes.sortedBy { it.at }.forEach { sale ->
                InfoRow(
                    if (sale.label.isBlank()) "開賣時間" else "開賣・${sale.label}",
                    "${sale.at.monthNumber}/${sale.at.dayOfMonth} " +
                        "${sale.at.hour.toString().padStart(2, '0')}:${sale.at.minute.toString().padStart(2, '0')}"
                )
            }
            InfoRow("地點", buildString {
                c.country?.takeIf { it.isNotBlank() && it != "台灣" }?.let { append(it).append(" · ") }
                c.city?.takeIf { it.isNotBlank() }?.let { append(it).append(" · ") }
                append(c.venue)
                c.hall?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
            })
            c.address?.takeIf { it.isNotBlank() }?.let { InfoRow("地址", it) }
            c.seatInfo?.takeIf { it.isNotBlank() }?.let { InfoRow("座位", it) }
            c.ticketPrice?.let { InfoRow("票價", "NT$${it.toInt()}") }
            c.pickupIdNumber?.takeIf { it.isNotBlank() }?.let { SensitiveInfoRow("取票身份證", it) }
            c.pickupNumber?.takeIf { it.isNotBlank() }?.let { SensitiveInfoRow("取票序號", it) }
            c.pickupTicketNo?.takeIf { it.isNotBlank() }?.let { SensitiveInfoRow("取票號碼", it) }
            c.pickupMachine?.takeIf { it.isNotBlank() }?.let { InfoRow("取票機台", it) }
            PickupHelp(c)
        }
    }
}

/**
 * 「還沒取票」時的取票小抄：步驟、哪個帳號買的、以及當初存的取票截圖。
 *
 * 只在 已購票（還沒取票）且有選購票平台時出現——票已經拿到了還一直佔版面是噪音。
 * 截圖預設收折：裡面常有訂單編號與姓名，不該在別人看得到螢幕時自動攤開。
 */
@Composable
private fun PickupHelp(c: com.bandary.domain.model.Concert) {
    val platform = c.ticketPlatform?.takeIf { it.isNotBlank() } ?: return
    if (c.ticketStatus != com.bandary.domain.model.TicketStatus.BOUGHT) return
    val profile = remember(platform) { com.bandary.domain.model.pickupProfileFor(platform) }
    var open by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(top = 8.dp)) {
        Surface(
            onClick = { open = !open },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    if (profile.eTicketOnly) "這張是電子票券" else "怎麼取票（點開）",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                if (open) {
                    profile.steps.forEachIndexed { i, step ->
                        Text(
                            "${i + 1}. $step",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    profile.note?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    c.pickupShotUri?.takeIf { it.isNotBlank() }?.let { shot ->
                        AsyncImage(
                            model = shot,
                            contentDescription = "取票序號截圖",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * 敏感資訊列（取票身份證 / 序號 / 號碼）：預設打碼、點一下才顯示，避免被旁人偷看或截圖外洩。
 * 遮罩保留頭尾各 1–2 碼，中間以 • 取代。
 */
@Composable
private fun SensitiveInfoRow(label: String, value: String) {
    var revealed by remember(value) { mutableStateOf(false) }
    val masked = remember(value) {
        val v = value.trim()
        when {
            v.length <= 2 -> "•".repeat(v.length.coerceAtLeast(1))
            v.length <= 5 -> v.take(1) + "•".repeat(v.length - 1)
            else -> v.take(2) + "•".repeat((v.length - 4).coerceAtLeast(1)) + v.takeLast(2)
        }
    }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { revealed = !revealed }.padding(vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (revealed) value else masked, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Icon(
                    if (revealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (revealed) "隱藏" else "顯示",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
