package com.bandary.presentation.ui.stats

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bandary.data.repository.ConcertRepository
import com.bandary.domain.model.Concert
import com.bandary.domain.model.priceBucketLabel
import com.bandary.domain.model.typeLabel
import com.bandary.domain.model.weekdayLabel
import com.bandary.presentation.ui.components.ConcertCoverCard
import com.bandary.presentation.ui.components.EmptyState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

// 統計頁「點某排行 / 圖表 → 看符合的所有場次」用的篩選類型。
enum class ShowFilterType(val routeKey: String, val label: String) {
    VENUE("venue", "場館"),
    CITY("city", "縣市"),
    PLATFORM("platform", "售票平台"),
    TYPE("type", "類型"),
    COUNTRY("country", "國家"),
    PRICE("price", "票價區間"),
    WEEKDAY("weekday", "星期"),
    YEAR("year", "年份"),
    MONTH("month", "月份"),
    COMPANION("companion", "同行者");

    companion object {
        fun fromKey(key: String?): ShowFilterType = entries.firstOrNull { it.routeKey == key } ?: VENUE
    }
}

@HiltViewModel
class ShowFilterViewModel @Inject constructor(
    concertRepo: ConcertRepository
) : ViewModel() {
    private val _filter = MutableStateFlow(ShowFilterType.VENUE to "")

    fun setFilter(type: ShowFilterType, value: String) { _filter.value = type to value }

    val shows: StateFlow<List<Concert>> =
        combine(_filter, concertRepo.getAllConcerts()) { (type, value), all ->
            if (value.isBlank()) emptyList()
            else all.filter { c ->
                when (type) {
                    ShowFilterType.VENUE -> c.venue == value
                    ShowFilterType.CITY -> c.city == value
                    ShowFilterType.PLATFORM -> c.ticketPlatform == value
                    ShowFilterType.TYPE -> c.typeLabel() == value
                    ShowFilterType.COUNTRY -> (c.country?.takeIf { it.isNotBlank() } ?: "台灣") == value
                    ShowFilterType.PRICE -> c.priceBucketLabel() == value
                    ShowFilterType.WEEKDAY -> c.weekdayLabel() == value
                    ShowFilterType.YEAR -> c.dateTime.year.toString() == value
                    ShowFilterType.MONTH -> "%04d/%02d".format(c.dateTime.year, c.dateTime.monthNumber) == value
                    ShowFilterType.COMPANION -> c.companions.any { it.trim() == value }
                }
            }.sortedByDescending { it.dateTime }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowFilterScreen(
    filterType: ShowFilterType,
    value: String,
    onBack: () -> Unit,
    onShowDetail: (String) -> Unit,
    viewModel: ShowFilterViewModel = hiltViewModel()
) {
    LaunchedEffect(filterType, value) { viewModel.setFilter(filterType, value) }
    val shows by viewModel.shows.collectAsState()
    val now = remember { Clock.System.now().toLocalDateTime(TimeZone.of("Asia/Taipei")) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("${filterType.label}　${shows.size} 場", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (shows.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(title = "沒有符合的場次", subtitle = "${filterType.label}：$value")
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(shows, key = { it.id }) { c ->
                    ConcertCoverCard(concert = c, now = now, heightDp = 180, infoLevel = 2, onClick = { onShowDetail(c.id) })
                }
            }
        }
    }
}
