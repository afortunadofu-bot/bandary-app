package com.bandary.presentation.ui.leaderboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandary.presentation.ui.components.ScrollTopFab
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bandary.data.local.SettingsDataStore
import com.bandary.data.remote.FirestoreService
import com.bandary.data.remote.LeaderboardEntry
import com.bandary.data.repository.FriendsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

/**
 * 全站年度排行榜。
 *
 * 公開的東西分三層，每一層都能單獨關掉：
 *   1. 名次      ── 暱稱 + 總場次（原本就有）
 *   2. 常聽藝人  ── 前 5 位藝人的名字。這是聚合結果，不是逐場清單。
 *   3. 重疊比對  ── 上傳的是雜湊，別人無法列舉你看了什麼；但你能算出自己的雜湊，
 *                  所以「你們重疊什麼」算得出來。也就是你只會看到跟自己重疊的部分。
 *
 * 逐場清單（哪一天、哪個場館）一律不上傳。想讓特定的人看到完整行程，那是「好友」的共享日曆在做的事。
 */
@HiltViewModel
class LeaderboardViewModel @Inject constructor(
    private val firestore: FirestoreService,
    private val friendsRepo: FriendsRepository,
    private val settings: SettingsDataStore
) : ViewModel() {

    private val thisYear = Clock.System.now().toLocalDateTime(TimeZone.of("Asia/Taipei")).year

    private val _year = MutableStateFlow(thisYear)
    val year: StateFlow<Int> = _year.asStateFlow()

    private val _years = MutableStateFlow(listOf(thisYear))
    val years: StateFlow<List<Int>> = _years.asStateFlow()

    private val _entries = MutableStateFlow<List<LeaderboardEntry>>(emptyList())
    val entries: StateFlow<List<LeaderboardEntry>> = _entries.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** 我在每位藝人粉絲榜的名次。全站榜人少時，這份即使只有我一個人也講得出東西。 */
    private val _artistRanks = MutableStateFlow<List<FriendsRepository.ArtistRank>>(emptyList())
    val artistRanks: StateFlow<List<FriendsRepository.ArtistRank>> = _artistRanks.asStateFlow()

    /** 點開某個人時算出的重疊。null＝還沒算 / 對方沒開比對。 */
    private val _overlap = MutableStateFlow<Map<String, FriendsRepository.Overlap?>>(emptyMap())
    val overlap: StateFlow<Map<String, FriendsRepository.Overlap?>> = _overlap.asStateFlow()

    val optedOut: StateFlow<Boolean> = settings.leaderboardOptOut
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val shareArtists: StateFlow<Boolean> = settings.lbShareArtists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val shareOverlap: StateFlow<Boolean> = settings.lbShareOverlap
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun isSignedIn(): Boolean = firestore.isSignedIn()
    fun myUid(): String? = firestore.currentUid()

    fun selectYear(y: Int) {
        if (_year.value == y) return
        _year.value = y
        _overlap.value = emptyMap()   // 換年份，之前算的重疊就不適用了
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            // 先確保自己這筆是最新的（例如剛改了公開設定），再讀整份排行榜。
            runCatching { friendsRepo.publishYearlyLeaderboard() }
            _years.value = firestore.leaderboardYears()
            _entries.value = firestore.yearlyLeaderboard(_year.value)
            _loading.value = false
            // 藝人榜要一位一次讀取，比較慢——等主清單顯示出來之後再補上，不擋畫面。
            _artistRanks.value = runCatching { friendsRepo.myArtistRanks(_year.value) }.getOrDefault(emptyList())
        }
    }

    /** 點開某個人時才算重疊——一次把所有人都算會對每個人都讀一次子文件，太重。 */
    fun loadOverlap(uid: String) {
        if (_overlap.value.containsKey(uid)) return
        viewModelScope.launch {
            val o = runCatching { friendsRepo.overlapWith(_year.value, uid) }.getOrNull()
            _overlap.value = _overlap.value + (uid to o)
        }
    }

    fun setOptOut(value: Boolean) = viewModelScope.launch {
        settings.setLeaderboardOptOut(value); friendsRepo.publishYearlyLeaderboard(); refresh()
    }
    fun setShareArtists(value: Boolean) = viewModelScope.launch {
        settings.setLbShareArtists(value); friendsRepo.publishYearlyLeaderboard(); refresh()
    }
    fun setShareOverlap(value: Boolean) = viewModelScope.launch {
        settings.setLbShareOverlap(value); friendsRepo.publishYearlyLeaderboard(); refresh()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    onBack: () -> Unit,
    onOpenArtist: (String) -> Unit = {},
    viewModel: LeaderboardViewModel = hiltViewModel()
) {
    val year by viewModel.year.collectAsState()
    val years by viewModel.years.collectAsState()
    val entries by viewModel.entries.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val optedOut by viewModel.optedOut.collectAsState()
    val shareArtists by viewModel.shareArtists.collectAsState()
    val shareOverlap by viewModel.shareOverlap.collectAsState()
    val overlaps by viewModel.overlap.collectAsState()
    val artistRanks by viewModel.artistRanks.collectAsState()
    val myUid = viewModel.myUid()
    var expandedUid by remember { mutableStateOf<String?>(null) }
    var showPrivacy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scrollScope = androidx.compose.runtime.rememberCoroutineScope()

    Scaffold(
        // 清單可能很長，捲下去之後要回得來。行為與其他頁共用同一個元件。
        floatingActionButton = { ScrollTopFab(listState, scrollScope) },
        topBar = {
            TopAppBar(
                title = { Text("年度排行榜", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回") }
                },
                actions = {
                    androidx.compose.material3.TextButton(onClick = { showPrivacy = !showPrivacy }) {
                        Text(if (showPrivacy) "收起" else "公開設定", fontSize = 13.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                // 這頁的 Scaffold 有 FAB，bottom 已含 FAB 高度；只補呼吸空間。
                bottom = padding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 時間區段：有資料的年份才列得出來。
            if (years.size > 1) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(years) { y ->
                            FilterChip(
                                selected = y == year,
                                onClick = { viewModel.selectYear(y) },
                                label = { Text("$y 年", fontSize = 13.sp) },
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

            if (showPrivacy) {
                item {
                    PrivacyCard(
                        optedOut = optedOut, shareArtists = shareArtists, shareOverlap = shareOverlap,
                        onOptOut = viewModel::setOptOut,
                        onShareArtists = viewModel::setShareArtists,
                        onShareOverlap = viewModel::setShareOverlap
                    )
                }
            } else {
                item {
                    Text(
                        // 使用者還少的時候總榜本來就只有一兩筆，講清楚才不會以為是壞掉。
                        if (entries.size <= 1)
                            "目前上榜的人還不多。點下面「你在每位藝人的粉絲排名」也看得到自己的數字。"
                        else
                            "點任一個人看看你們重疊了什麼。逐場清單不會公開——只會顯示你自己也有的部分。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            when {
                !viewModel.isSignedIn() -> item {
                    Text(
                        "登入後才能看到排行榜、也才會被排進去。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
                loading -> item {
                    Text("讀取中…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                entries.isEmpty() -> item {
                    Text(
                        "$year 年還沒有人上榜。記一場看完的演出就會是第一名。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> itemsIndexed(entries, key = { _, e -> e.uid }) { i, e ->
                    LeaderboardRow(
                        rank = i + 1,
                        entry = e,
                        isMe = e.uid == myUid,
                        expanded = expandedUid == e.uid,
                        overlap = overlaps[e.uid],
                        overlapLoaded = overlaps.containsKey(e.uid),
                        onClick = {
                            expandedUid = if (expandedUid == e.uid) null else e.uid
                            if (expandedUid == e.uid && e.uid != myUid) viewModel.loadOverlap(e.uid)
                        },
                        onOpenArtist = onOpenArtist
                    )
                }
            }

            // 個別藝人的粉絲榜。全站總榜在使用者少的時候只有一兩筆，看不出什麼；
            // 這一段即使只有自己一個人也講得出「你看了這位幾場、在粉絲裡排第幾」。
            if (artistRanks.isNotEmpty()) {
                item {
                    Text(
                        "你在每位藝人的粉絲排名",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 20.dp, bottom = 2.dp)
                    )
                }
                items(artistRanks, key = { it.artist }) { r ->
                    ArtistRankRow(rank = r, onClick = { onOpenArtist(r.artist) })
                }
            }
        }
    }
}

/** 我在某位藝人粉絲榜的名次。點進去看該藝人的完整粉絲排行與我和他的現場紀錄。 */
@Composable
private fun ArtistRankRow(rank: FriendsRepository.ArtistRank, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    rank.artist, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    "你看了 ${rank.myCount} 場",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "第 ${rank.rank} 名",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    // 只有自己一個粉絲時講「第 1 名／共 1 位」很空虛，直接講清楚現況。
                    if (rank.totalFans <= 1) "目前只有你記錄過"
                    else "共 ${rank.totalFans} 位粉絲",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PrivacyCard(
    optedOut: Boolean, shareArtists: Boolean, shareOverlap: Boolean,
    onOptOut: (Boolean) -> Unit, onShareArtists: (Boolean) -> Unit, onShareOverlap: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ToggleRow(
                title = "參加排行榜",
                desc = "公開你的暱稱與總場次。關掉就整筆從排行榜移除，你自己還是看得到別人的名次。",
                checked = !optedOut, onChange = { onOptOut(!it) }
            )
            ToggleRow(
                title = "公開常聽藝人",
                desc = "顯示你今年前 5 位最常看的藝人名字。這是聚合結果，不含哪一天、哪個場館。",
                checked = shareArtists, onChange = onShareArtists, enabled = !optedOut
            )
            ToggleRow(
                title = "允許比對重疊",
                desc = "讓別人算得出「你們重疊了哪些藝人、幾場」。上傳的是不可逆推的雜湊，" +
                    "對方無法列舉你看過什麼，只看得到你們的交集。",
                checked = shareOverlap, onChange = onShareOverlap, enabled = !optedOut
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String, desc: String, checked: Boolean,
    onChange: (Boolean) -> Unit, enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked && enabled, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun LeaderboardRow(
    rank: Int,
    entry: LeaderboardEntry,
    isMe: Boolean,
    expanded: Boolean,
    overlap: FriendsRepository.Overlap?,
    overlapLoaded: Boolean,
    onClick: () -> Unit,
    onOpenArtist: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(if (isMe) 2.dp else 0.dp)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (rank <= 3) {
                    Icon(
                        Icons.Outlined.EmojiEvents, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        "$rank", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(20.dp)
                    )
                }
                Text(
                    (if (isMe) "你・" else "") + entry.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 12.dp)
                )
                Text(
                    "${entry.count} 場", style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary
                )
            }
            // 常聽藝人：排行榜有名字才有人味，只有數字看不出這個人是誰。
            if (entry.topArtists.isNotEmpty()) {
                Text(
                    entry.topArtists.joinToString("・"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 32.dp, top = 4.dp)
                )
            }
            if (expanded && !isMe) {
                Column(Modifier.padding(start = 32.dp, top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    when {
                        !overlapLoaded -> Text(
                            "比對中…", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        overlap == null -> Text(
                            "對方沒有開放比對重疊。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        overlap.sharedArtists.isEmpty() && overlap.sharedShows == 0 -> Text(
                            "今年你們還沒有重疊的藝人或場次。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        else -> {
                            if (overlap.sharedShows > 0) {
                                Text(
                                    "同場 ${overlap.sharedShows} 場",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (overlap.sharedArtists.isNotEmpty()) {
                                Text(
                                    "都看過 ${overlap.sharedArtists.size} 組藝人",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(overlap.sharedArtists.take(12)) { a ->
                                        androidx.compose.material3.AssistChip(
                                            onClick = { onOpenArtist(a) },
                                            label = { Text(a, fontSize = 12.sp) }
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
}
