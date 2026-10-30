package com.bandary.presentation.ui.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bandary.data.repository.FriendsRepository
import com.bandary.domain.model.SharedCalendar
import com.bandary.domain.model.SharedShow
import com.bandary.presentation.ui.components.EmptyState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FriendCalendarViewModel @Inject constructor(
    private val repo: FriendsRepository,
    private val concertRepo: com.bandary.data.repository.ConcertRepository,
    private val songRepo: com.bandary.data.repository.DailySongRepository,
    private val freeEventsRepo: com.bandary.data.repository.FreeEventsRepository
) : ViewModel() {

    /** 「我們都喜歡」：好友年度歌單與我自己同年度紀錄的交集（歌名 / 藝人）。 */
    private val _commonSongs = MutableStateFlow<List<String>>(emptyList())
    val commonSongs: StateFlow<List<String>> = _commonSongs.asStateFlow()

    private val _commonArtists = MutableStateFlow<List<String>>(emptyList())
    val commonArtists: StateFlow<List<String>> = _commonArtists.asStateFlow()

    private val _calendar = MutableStateFlow<SharedCalendar?>(null)
    val calendar: StateFlow<SharedCalendar?> = _calendar.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    // 好友有興趣的免費活動（依 id 對照全站免費活動目錄解析出標題等資訊）。
    private val _interestedEvents = MutableStateFlow<List<com.bandary.domain.model.FreeEvent>>(emptyList())
    val interestedEvents: StateFlow<List<com.bandary.domain.model.FreeEvent>> = _interestedEvents.asStateFlow()

    // 我自己也還沒買票（購票狀況＝無）的比對鍵集合：名稱 + 日期。
    // 好友的想搶票若命中，就顯示「你也想搶」——這就是「你和 X 都想搶這張」的互相標記。
    private val _myWantedKeys = MutableStateFlow<Set<String>>(emptySet())
    val myWantedKeys: StateFlow<Set<String>> = _myWantedKeys.asStateFlow()

    private fun wantKey(name: String, date: kotlinx.datetime.LocalDate) =
        "${name.trim().lowercase()}#$date"

    fun load(friendUid: String) {
        viewModelScope.launch {
            _loading.value = true
            val cal = repo.viewCalendar(friendUid)
            _calendar.value = cal
            freeEventsRepo.refresh()
            val ids = cal?.interestedFreeEventIds.orEmpty().toSet()
            _interestedEvents.value = if (ids.isEmpty()) emptyList()
                else freeEventsRepo.events.value.filter { it.id in ids }
            // 算出我自己想搶的票鍵集合。
            val mine = runCatching { concertRepo.getAllConcerts().first() }.getOrDefault(emptyList())
            _myWantedKeys.value = mine.filter {
                it.ticketStatus == com.bandary.domain.model.TicketStatus.NONE
            }.map { wantKey(it.name.ifBlank { it.artistName }, it.dateTime.date) }.toSet()
            // 「我們都喜歡」：拿好友公開的前幾名，和我自己同年度的紀錄取交集。
            // 只在本機比對——我的歌單不會因為要算交集就上傳。
            val profile = cal?.songProfile
            if (profile != null) {
                val mySongs = runCatching { songRepo.getAllSongs().first() }.getOrDefault(emptyList())
                    .filter { it.date.year == profile.year }
                val myTitles = mySongs.map { it.songTitle.trim().lowercase() }.toSet()
                val myArtists = mySongs.map { it.artistName.trim().lowercase() }.toSet()
                _commonSongs.value = profile.topSongs
                    .filter { it.title.trim().lowercase() in myTitles }
                    .map { it.title }
                _commonArtists.value = profile.topArtists
                    .filter { it.trim().lowercase() in myArtists }
            } else {
                _commonSongs.value = emptyList()
                _commonArtists.value = emptyList()
            }
            _loading.value = false
        }
    }

    /** 好友的這場想搶票，我自己是否也標了想搶。 */
    fun bothWant(s: SharedShow): Boolean =
        s.wantTicket && wantKey(s.name.ifBlank { s.artistName }, s.dateTime.date) in _myWantedKeys.value

    /** 把好友公開的演出加入我的演出清單（同名 + 同日的略過，避免重複）。 */
    fun importAll(shows: List<SharedShow>) {
        viewModelScope.launch {
            val existing = runCatching { concertRepo.getAllConcerts().first() }.getOrDefault(emptyList())
            fun isDup(s: SharedShow) = existing.any {
                it.name.trim().equals(s.name.trim(), ignoreCase = true) && it.dateTime.date == s.dateTime.date
            }
            var added = 0
            shows.filterNot { isDup(it) }.forEach { s ->
                concertRepo.saveConcert(
                    com.bandary.domain.model.Concert(
                        id = java.util.UUID.randomUUID().toString(),
                        name = s.name,
                        artistName = s.artistName,
                        venue = s.venue,
                        city = s.city,
                        dateTime = s.dateTime,
                        notes = "從好友日曆加入"
                    )
                )
                added++
            }
            _message.value = if (added > 0) "已加入 $added 場到我的演出" else "沒有可加入的，都已存在"
        }
    }

    fun clearMessage() { _message.value = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendCalendarScreen(
    friendUid: String,
    friendName: String,
    onBack: () -> Unit,
    viewModel: FriendCalendarViewModel = hiltViewModel()
) {
    val calendar by viewModel.calendar.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val message by viewModel.message.collectAsState()
    val interestedEvents by viewModel.interestedEvents.collectAsState()
    val commonSongs by viewModel.commonSongs.collectAsState()
    val commonArtists by viewModel.commonArtists.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(friendUid) { viewModel.load(friendUid) }
    LaunchedEffect(message) {
        message?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$friendName 的日曆", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    val shows = calendar?.shows.orEmpty()
                    if (shows.isNotEmpty()) {
                        androidx.compose.material3.TextButton(onClick = { viewModel.importAll(shows) }) {
                            Text("加入我的演出")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        val shows = calendar?.shows.orEmpty()
        val wanted = shows.filter { it.wantTicket }
        when {
            loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
            }
            shows.isEmpty() && interestedEvents.isEmpty() && calendar?.songProfile == null ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = "還沒有可看的行程",
                    subtitle = "好友日曆只會顯示對方「即將到來」的場次；已經過去的不會出現。等對方新增下一場就看得到了。"
                )
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 年度歌單（對方自行開啟分享才有）＋「我們都喜歡」的交集。
                calendar?.songProfile?.let { profile ->
                    item {
                        FriendSongProfileCard(
                            profile = profile,
                            friendName = friendName,
                            commonSongs = commonSongs,
                            commonArtists = commonArtists
                        )
                    }
                    item { androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
                }

                if (wanted.isNotEmpty()) {
                    item {
                        Text("想搶的票", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    items(wanted) { s -> SharedShowCard(s, bothWant = viewModel.bothWant(s), friendName = friendName) }
                    item { androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
                }
                if (interestedEvents.isNotEmpty()) {
                    item {
                        Text("想去的活動", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    items(interestedEvents) { e -> InterestedEventCard(e) }
                    item { androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
                }
                if (shows.isNotEmpty()) {
                    item {
                        Text("全部演出", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(shows) { s -> SharedShowCard(s, bothWant = viewModel.bothWant(s), friendName = friendName) }
                }
            }
        }
    }
}

/**
 * 好友的年度歌單卡：只有聚合結果（總數 / 現場數 / 前幾名）。
 * 「我們都喜歡」放最上面——這是社群化真正有價值的部分，不是單純看別人的清單。
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FriendSongProfileCard(
    profile: com.bandary.domain.model.SharedSongProfile,
    friendName: String,
    commonSongs: List<String>,
    commonArtists: List<String>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "$friendName 的 ${profile.year} 年歌單",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                buildString {
                    append("${profile.totalSongs} 首")
                    if (profile.liveSongs > 0) append(" · 其中 ${profile.liveSongs} 首在現場記下")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (commonSongs.isNotEmpty() || commonArtists.isNotEmpty()) {
                androidx.compose.material3.Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("我們都喜歡", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        if (commonArtists.isNotEmpty()) {
                            Text(commonArtists.joinToString("、"), style = MaterialTheme.typography.bodyMedium)
                        }
                        if (commonSongs.isNotEmpty()) {
                            Text(
                                commonSongs.joinToString("、"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (profile.topArtists.isNotEmpty()) {
                Text("最常聽的藝人", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(profile.topArtists.joinToString("、"), style = MaterialTheme.typography.bodyMedium)
            }
            if (profile.topSongs.isNotEmpty()) {
                Text("最常回放", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                profile.topSongs.take(5).forEachIndexed { i, s ->
                    Text(
                        "${i + 1}. ${s.title}" + (s.artist.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SharedShowCard(show: SharedShow, bothWant: Boolean = false, friendName: String = "") {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    show.name.ifBlank { show.artistName },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (show.wantTicket) {
                    Text(
                        "想搶",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            // 「你和 X 都想搶這張」——雙方都標了想搶時的醒目標記。
            if (bothWant) {
                Text(
                    if (friendName.isNotBlank()) "你和 $friendName 都想搶這張" else "你也想搶這張",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            if (show.artistName.isNotBlank() && show.name.isNotBlank()) {
                Text(show.artistName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "${show.dateTime.year}/${show.dateTime.monthNumber}/${show.dateTime.dayOfMonth} " +
                    "${show.dateTime.hour.toString().padStart(2, '0')}:${show.dateTime.minute.toString().padStart(2, '0')}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
            if (show.venue.isNotBlank() || !show.city.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Outlined.LocationOn, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        buildString {
                            show.city?.takeIf { it.isNotBlank() }?.let { append(it).append(" · ") }
                            append(show.venue)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun InterestedEventCard(event: com.bandary.domain.model.FreeEvent) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                event.name.ifBlank { event.artistName },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Text(
                "${event.dateTime.year}/${event.dateTime.monthNumber}/${event.dateTime.dayOfMonth} " +
                    "${event.dateTime.hour.toString().padStart(2, '0')}:${event.dateTime.minute.toString().padStart(2, '0')}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
            if (event.venue.isNotBlank() || !event.city.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Outlined.LocationOn, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        buildString {
                            event.city?.takeIf { it.isNotBlank() }?.let { append(it).append(" · ") }
                            append(event.venue)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
