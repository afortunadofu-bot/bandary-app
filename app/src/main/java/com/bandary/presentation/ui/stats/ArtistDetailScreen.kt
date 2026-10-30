package com.bandary.presentation.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.datetime.toLocalDateTime
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bandary.presentation.ui.components.ScrollTopFab
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bandary.data.repository.ConcertRepository
import com.bandary.domain.model.Concert
import com.bandary.presentation.ui.components.TicketStatusChip
import com.bandary.presentation.ui.theme.BandaryColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArtistDetailState(
    val artistName: String = "",
    val shows: List<Concert> = emptyList(),
    val songs: List<com.bandary.domain.model.DailySong> = emptyList(),
    val totalSpent: Double = 0.0,
    val avgRating: Double = 0.0
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    concertRepo: ConcertRepository,
    dailySongRepo: com.bandary.data.repository.DailySongRepository,
    private val firestore: com.bandary.data.remote.FirestoreService,
    private val settings: com.bandary.data.local.SettingsDataStore,
    private val freeEventsRepo: com.bandary.data.repository.FreeEventsRepository
) : ViewModel() {

    private val _name = MutableStateFlow("")
    fun setName(name: String) { _name.value = name }

    /** 這位藝人是否在追蹤名單裡（比對用正規化鍵，避免大小寫 / 空白造成漏判）。 */
    val isFollowing: StateFlow<Boolean> =
        combine(_name, settings.followedArtists) { name, followed ->
            name.isNotBlank() && followed.any {
                com.bandary.domain.model.artistFollowKey(it) == com.bandary.domain.model.artistFollowKey(name)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun toggleFollow() {
        val name = _name.value
        if (name.isBlank()) return
        viewModelScope.launch {
            settings.toggleFollowArtist(name)
            // 追蹤清單與網頁版共用同一份（users/{uid}.followedArtists），改完要推上去。
            runCatching { firestore.publishFollowedArtists(settings.followedArtists.first()) }
        }
    }

    /** 活動裡這位藝人未來的場次（追蹤的價值來源：他有新活動時看得到）。 */
    val upcomingEvents: StateFlow<List<com.bandary.domain.model.FreeEvent>> =
        combine(_name, freeEventsRepo.events) { name, events ->
            if (name.isBlank()) return@combine emptyList()
            val today = kotlinx.datetime.Clock.System.now()
                .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
            events.filter { e ->
                e.dateTime.date >= today &&
                    com.bandary.domain.model.matchFollowedArtist(e.artistName, setOf(name)) != null
            }.sortedBy { it.dateTime }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // 進來就抓一次活動；已在載入中會自行略過。
        viewModelScope.launch { runCatching { freeEventsRepo.refresh() } }
    }

    val state: StateFlow<ArtistDetailState> =
        combine(_name, concertRepo.getAllConcerts(), dailySongRepo.getAllSongs()) { name, all, allSongs ->
            val shows = all.filter { c ->
                c.artistName == name || c.festivals.any { it.isSelected && it.artistName == name }
            }.sortedByDescending { it.dateTime }
            val songs = allSongs.filter { it.artistName == name }.sortedByDescending { it.date }
            val ratings = shows.mapNotNull { it.rating }
            ArtistDetailState(
                artistName = name,
                shows = shows,
                songs = songs,
                totalSpent = shows.sumOf { it.ticketPrice ?: 0.0 },
                avgRating = if (ratings.isEmpty()) 0.0 else ratings.average()
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ArtistDetailState())

    private val _leaderboard = MutableStateFlow<List<com.bandary.data.remote.ArtistFan>>(emptyList())
    val leaderboard: StateFlow<List<com.bandary.data.remote.ArtistFan>> = _leaderboard.asStateFlow()

    fun isSignedIn(): Boolean = firestore.isSignedIn()
    fun myUid(): String? = firestore.currentUid()

    /** 上傳我的現場次數並抓回排行榜。 */
    fun syncLeaderboard(artist: String, myCount: Int) {
        if (artist.isBlank() || !firestore.isSignedIn()) return
        viewModelScope.launch {
            firestore.publishArtistCount(artist, myCount)
            _leaderboard.value = firestore.artistLeaderboard(artist)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    artistName: String,
    onBack: () -> Unit,
    onShowDetail: (String) -> Unit,
    viewModel: ArtistDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(artistName) { viewModel.setName(artistName) }
    val state by viewModel.state.collectAsState()
    val leaderboard by viewModel.leaderboard.collectAsState()
    val following by viewModel.isFollowing.collectAsState()
    val upcomingEvents by viewModel.upcomingEvents.collectAsState()
    // 上傳我的現場次數 + 抓排行榜（登入者）。
    LaunchedEffect(state.artistName, state.shows.size) {
        if (state.artistName.isNotBlank()) viewModel.syncLeaderboard(state.artistName, state.shows.size)
    }
    val myUid = viewModel.myUid()
    val now = remember {
        kotlinx.datetime.Clock.System.now()
            .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scrollScope = androidx.compose.runtime.rememberCoroutineScope()

    Scaffold(
        // 清單可能很長，捲下去之後要回得來。行為與其他頁共用同一個元件。
        floatingActionButton = { ScrollTopFab(listState, scrollScope) },
        topBar = {
            TopAppBar(
                title = { Text(state.artistName.ifBlank { artistName }, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleFollow() }) {
                        Icon(
                            if (following) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            if (following) "取消追蹤" else "追蹤",
                            tint = if (following) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                top = padding.calculateTopPadding() + 8.dp,
                // 這頁的 Scaffold 有 FAB，bottom 已含 FAB 高度；只補呼吸空間。
                bottom = padding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryCard("看過場次", "${state.shows.size}", " 場", highlight = true, modifier = Modifier.weight(1f))
                    SummaryCard("總花費", "NT$${state.totalSpent.toInt()}", "", modifier = Modifier.weight(1f))
                }
            }

            // 追蹤中的藝人：活動一有他的新場次，這裡會出現，每日提醒也會推播一次。
            if (upcomingEvents.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "活動的未來場次・${upcomingEvents.size}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        upcomingEvents.take(5).forEach { e ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(0.dp)
                            ) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(e.name.ifBlank { e.artistName }, style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val meta = listOfNotNull(
                                        "${e.dateTime.monthNumber}/${e.dateTime.dayOfMonth}",
                                        e.city, e.venue.takeIf { it.isNotBlank() }
                                    ).joinToString(" · ")
                                    Text(meta, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            } else if (following) {
                item {
                    Text(
                        "已追蹤。活動出現這位藝人的新場次時，會在這裡列出並提醒你一次。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (state.avgRating > 0) {
                item {
                    SummaryCard("平均評分", String.format("%.1f", state.avgRating), " 星", modifier = Modifier.fillMaxWidth())
                }
            }

            // 現場排行（跨使用者）
            item {
                if (!viewModel.isSignedIn()) {
                    Text(
                        "到「我的」用 Google 登入後，可看這位藝人的現場排行與你的名次。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else if (leaderboard.isNotEmpty()) {
                    val myRank = leaderboard.indexOfFirst { it.uid == myUid }.takeIf { it >= 0 }?.plus(1)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(2.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("現場排行", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            // 排行的定義要講清楚，否則使用者看到「第一名」不知道是跟誰比（回報：不確定第一名怎麼來的）。
                            Text(
                                "依「看過這位藝人幾場」排名，比較對象是你和你的好友。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            // 只有自己一個人時不講「第一名」——沒有人可比，那個名次沒有意義。
                            if (leaderboard.size > 1) {
                                leaderboard.firstOrNull()?.let { top ->
                                    Text("現場第一名　${top.name}　${top.count} 場",
                                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Text(
                                when {
                                    myRank == null -> "你還沒有這位藝人的現場紀錄"
                                    leaderboard.size <= 1 -> "目前只有你記錄過這位藝人・${state.shows.size} 場（加好友後就能一起比）"
                                    else -> "你排第 $myRank／${leaderboard.size} 名・${state.shows.size} 場"
                                },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            // 粉絲 PR：名次本身沒有尺度——「第 8 名」在 10 個人裡很後面、
                            // 在 500 個人裡很前面。PR 直接回答「我算不算鐵粉」。
                            // 人數不足時不給數字（見 FanPercentile.MIN_SAMPLE）。
                            val myFanCount = leaderboard.firstOrNull { it.uid == myUid }?.count ?: 0
                            val pr = remember(leaderboard, myFanCount) {
                                com.bandary.domain.model.FanPercentile.of(myFanCount, leaderboard.map { it.count })
                            }
                            if (pr != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            com.bandary.domain.model.FanPercentile.label(pr).orEmpty(),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.surface,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    Text(
                                        "　你贏過 $pr% 記錄過這位藝人的人" +
                                            (com.bandary.domain.model.FanPercentile.tier(pr)?.let { "・$it" } ?: ""),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else if (myRank != null && leaderboard.size > 1) {
                                Text(
                                    "記錄這位藝人的人還太少（要 ${com.bandary.domain.model.FanPercentile.MIN_SAMPLE} 人以上）才算得出 PR。",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            leaderboard.take(5).forEachIndexed { i, fan ->
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${i + 1}", style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Text(
                                        fan.name + (if (fan.uid == myUid) "・你" else ""),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (fan.uid == myUid) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                    Text("${fan.count} 場", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            if (state.shows.isNotEmpty()) {
                item {
                    Text("看過的場次", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp))
                }
                // 用和首頁一致的封面票根卡，而非樸素列表（回應「點進去要看到好看的票根」）。
                items(state.shows, key = { it.id }) { show ->
                    com.bandary.presentation.ui.components.ConcertCoverCard(
                        concert = show,
                        now = now,
                        heightDp = 180,
                        infoLevel = 2,
                        onClick = { onShowDetail(show.id) }
                    )
                }
            }

            // 這位藝人的選歌
            if (state.songs.isNotEmpty()) {
                item {
                    Text("喜歡的歌 · ${state.songs.size} 首", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp))
                }
                items(state.songs, key = { "song_${it.id}" }) { song ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Outlined.MusicNote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(song.songTitle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                song.albumName?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            // 從演唱會歌單記下來的歌（v21 concertId）標「現場」，和平常在聽的歌區隔。
                            if (song.concertId != null) {
                                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
                                    Text(
                                        "現場",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            Text(
                                "${song.date.monthNumber}/${song.date.dayOfMonth}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (state.shows.isEmpty() && state.songs.isEmpty()) {
                item {
                    Text("還沒有這位藝人的紀錄", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, unit: String, highlight: Boolean = false, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (unit.isNotEmpty()) Text(unit, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

