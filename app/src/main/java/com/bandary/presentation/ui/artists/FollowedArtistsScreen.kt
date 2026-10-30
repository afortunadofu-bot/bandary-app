package com.bandary.presentation.ui.artists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.bandary.presentation.ui.components.ScrollTopFab
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bandary.data.local.SettingsDataStore
import com.bandary.data.repository.ConcertRepository
import com.bandary.data.repository.FreeEventsRepository
import com.bandary.domain.model.FreeEvent
import com.bandary.domain.model.artistFollowKey
import com.bandary.domain.model.matchFollowedArtist
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

/** 一位追蹤中的藝人：看過幾次、活動裡還有幾場、最近的一場是哪一天。 */
data class FollowedArtist(
    val name: String,
    val seenCount: Int,
    val upcoming: List<FreeEvent>
)

@HiltViewModel
class FollowedArtistsViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    private val freeEventsRepo: FreeEventsRepository,
    private val firestore: com.bandary.data.remote.FirestoreService,
    concertRepo: ConcertRepository
) : ViewModel() {

    val artists: StateFlow<List<FollowedArtist>> =
        combine(
            settings.followedArtists,
            freeEventsRepo.events,
            concertRepo.getAllConcerts()
        ) { followed, events, concerts ->
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val future = events.filter { it.dateTime.date >= today }
            followed.map { name ->
                val key = artistFollowKey(name)
                FollowedArtist(
                    name = name,
                    seenCount = concerts.count { c ->
                        artistFollowKey(c.artistName) == key ||
                            c.festivals.any { it.isSelected && artistFollowKey(it.artistName) == key }
                    },
                    upcoming = future.filter { matchFollowedArtist(it.artistName, setOf(name)) != null }
                        .sortedBy { it.dateTime }
                )
            }
            // 有新場次的排前面，其次看過越多次的越前面——把「現在該注意誰」推到最上方。
            .sortedWith(compareByDescending<FollowedArtist> { it.upcoming.isNotEmpty() }
                .thenByDescending { it.seenCount }
                .thenBy { it.name })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch { runCatching { freeEventsRepo.refresh() } }
        viewModelScope.launch { runCatching { syncFollowedWithCloud() } }
    }

    /**
     * 追蹤清單的雲端對帳，讓 App 與網頁版共用同一份。
     * 雲端還沒有這個欄位＝這個帳號還沒搬過家，把本機那份推上去（不會弄丟既有追蹤）；
     * 已經有了就以雲端為準拉下來，「取消追蹤」才不會在另一台裝置被舊資料復活。
     */
    private suspend fun syncFollowedWithCloud() {
        if (!firestore.isSignedIn()) return
        val cloud = firestore.fetchFollowedArtists().getOrNull()
        if (cloud == null) firestore.publishFollowedArtists(settings.followedArtists.first())
        else settings.setFollowedArtists(cloud)
    }

    // 共用藝人資料庫。以前只找得到「自己看過」的藝人，想追蹤還沒看過的團，
    // App 裡根本沒有那個名字可選（實機回報）。
    private val _directory = kotlinx.coroutines.flow.MutableStateFlow<List<com.bandary.domain.model.ArtistEntry>>(emptyList())
    val directory: StateFlow<List<com.bandary.domain.model.ArtistEntry>> = _directory

    private val _dirLoading = kotlinx.coroutines.flow.MutableStateFlow(false)
    val dirLoading: StateFlow<Boolean> = _dirLoading

    private val _message = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    fun clearMessage() { _message.value = null }

    fun isSignedIn(): Boolean = firestore.isSignedIn()

    /** 讀整份名單（規模還小，一次拉完最單純；搜尋在本機做，打字才不會等網路）。 */
    fun loadDirectory() {
        if (_dirLoading.value || !firestore.isSignedIn()) return
        viewModelScope.launch {
            _dirLoading.value = true
            firestore.fetchArtists().onSuccess { _directory.value = it }
            _dirLoading.value = false
        }
    }

    fun follow(name: String) = viewModelScope.launch {
        val already = settings.followedArtists.first()
            .any { artistFollowKey(it) == artistFollowKey(name) }
        if (already) { _message.value = "已經在追蹤「$name」了"; return@launch }
        settings.toggleFollowArtist(name)
        runCatching { firestore.publishFollowedArtists(settings.followedArtists.first()) }
        _message.value = "已追蹤「$name」"
    }

    /** 新增到共用資料庫，順手追蹤——會按「新增」就是想追蹤這位。 */
    fun createAndFollow(name: String) = viewModelScope.launch {
        firestore.addArtist(name)
            .onSuccess { entry ->
                _directory.update { list ->
                    if (list.any { it.id == entry.id }) list else list + entry
                }
                follow(entry.name)
            }
            .onFailure { _message.value = it.message ?: "新增失敗" }
    }

    fun unfollow(name: String) = viewModelScope.launch {
        settings.toggleFollowArtist(name)
        runCatching { firestore.publishFollowedArtists(settings.followedArtists.first()) }
    }

    fun refresh() = viewModelScope.launch {
        runCatching { freeEventsRepo.refresh() }
        runCatching { syncFollowedWithCloud() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowedArtistsScreen(
    onBack: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenEvents: () -> Unit,
    viewModel: FollowedArtistsViewModel = hiltViewModel()
) {
    val artists by viewModel.artists.collectAsState()
    val directory by viewModel.directory.collectAsState()
    val dirLoading by viewModel.dirLoading.collectAsState()
    val message by viewModel.message.collectAsState()
    val signedIn = viewModel.isSignedIn()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { viewModel.refresh(); viewModel.loadDirectory() }
    LaunchedEffect(message) {
        message?.let {
            android.widget.Toast.makeText(ctx, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scrollScope = androidx.compose.runtime.rememberCoroutineScope()

    Scaffold(
        // 清單可能很長，捲下去之後要回得來。行為與其他頁共用同一個元件。
        floatingActionButton = { ScrollTopFab(listState, scrollScope) },
        topBar = {
            TopAppBar(
                title = { Text("追蹤中的藝人", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 搜尋並追蹤：資料庫裡沒有的也能當場加進去，加完大家都搜得到。
            item(key = "picker") {
                if (signedIn) {
                    com.bandary.presentation.ui.components.ArtistPicker(
                        directory = directory,
                        loading = dirLoading,
                        label = "追蹤新的藝人",
                        placeholder = "打幾個字，沒有的也可以直接加",
                        alreadyPicked = artists.map { it.name }.toSet(),
                        onPick = { viewModel.follow(it) },
                        onCreate = { viewModel.createAndFollow(it) }
                    )
                } else {
                    Text(
                        "用 Google 登入之後，就能搜尋共用的藝人資料庫、追蹤還沒看過的團。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (artists.isEmpty()) {
                item {
                    Text(
                        "還沒有追蹤任何藝人。上面直接搜尋就能追蹤，" +
                            "或到任一位藝人的頁面點右上角的愛心；之後活動出現他的新場次時會提醒你一次。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(artists, key = { it.name }) { a ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenArtist(a.name) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                a.name, style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.unfollow(a.name) }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Icons.Filled.Favorite, "取消追蹤",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Text(
                            "看過 ${a.seenCount} 場",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (a.upcoming.isEmpty()) {
                            Text(
                                "活動目前沒有他的未來場次",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            a.upcoming.take(3).forEach { e ->
                                val meta = listOfNotNull(
                                    "${e.dateTime.monthNumber}/${e.dateTime.dayOfMonth}",
                                    e.city, e.venue.takeIf { it.isNotBlank() }
                                ).joinToString(" · ")
                                Text(
                                    "${e.name.ifBlank { e.artistName }}・$meta",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable { onOpenEvents() }
                                )
                            }
                            if (a.upcoming.size > 3) {
                                Text(
                                    "還有 ${a.upcoming.size - 3} 場",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
