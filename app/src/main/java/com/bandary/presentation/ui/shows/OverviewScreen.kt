package com.bandary.presentation.ui.shows

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
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.automirrored.outlined.ViewList
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bandary.domain.model.Concert
import com.bandary.presentation.ui.components.ConcertCoverCard
import com.bandary.presentation.ui.components.EmptyState
import com.bandary.presentation.ui.components.concertArtistsLabel
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime

/**
 * 演唱會總覽（展示用）：即將到來的場次，可切換「大圖」與「清單」兩種呈現。
 * 大圖＝整幅電影感大卡；清單＝一次看到每場的簡要（日期 / 藝人 / 場地 / 倒數）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    onBack: () -> Unit,
    onShowDetail: (String) -> Unit,
    viewModel: ShowsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val now = remember { Clock.System.now().toLocalDateTime(TimeZone.of("Asia/Taipei")) }
    var listMode by remember { mutableStateOf(false) }
    val startOfMonth = remember(now) {
        kotlinx.datetime.LocalDateTime(now.year, now.monthNumber, 1, 0, 0)
    }
    val shown = state.allConcerts
        .filter { it.dateTime >= startOfMonth }
        .sortedBy { it.dateTime }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("總覽", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                },
                actions = {
                    // 清單 / 大圖切換
                    IconButton(onClick = { listMode = !listMode }) {
                        Icon(
                            if (listMode) Icons.Outlined.ViewAgenda else Icons.AutoMirrored.Outlined.ViewList,
                            contentDescription = if (listMode) "改為大圖" else "改為清單"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(title = "還沒有即將到來的演出", subtitle = "新增演唱會後就會出現在這裡")
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = if (listMode) 16.dp else 20.dp, end = if (listMode) 16.dp else 20.dp,
                    top = padding.calculateTopPadding() + 12.dp,
                    bottom = padding.calculateBottomPadding() + 32.dp
                ),
                verticalArrangement = Arrangement.spacedBy(if (listMode) 8.dp else 24.dp)
            ) {
                if (listMode) {
                    items(shown, key = { it.id }) { c ->
                        OverviewListRow(concert = c, now = now, onClick = { onShowDetail(c.id) })
                    }
                } else {
                    items(shown, key = { it.id }) { c ->
                        val idx = shown.indexOf(c)
                        val h = when (idx) { 0 -> 360; 1 -> 320; else -> 280 }
                        ConcertCoverCard(concert = c, now = now, heightDp = h, onClick = { onShowDetail(c.id) })
                    }
                }
            }
        }
    }
}

/** 清單式簡要列：左側日期方塊、中間藝人 / 場地、右側倒數。 */
@Composable
private fun OverviewListRow(concert: Concert, now: LocalDateTime, onClick: () -> Unit) {
    val days = now.date.daysUntil(concert.dateTime.date)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 日期方塊
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.size(52.dp)) {
                Text("${concert.dateTime.monthNumber}月", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text("${concert.dateTime.dayOfMonth}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    concertArtistsLabel(concert).ifBlank { concert.name },
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (concert.name.isNotBlank() && concert.artistName.isNotBlank()) {
                    Text(concert.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(Icons.Outlined.LocationOn, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        buildString {
                            concert.city?.takeIf { it.isNotBlank() }?.let { append(it).append(" · ") }
                            append(concert.venue)
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                if (days <= 0) "今天" else "$days 天",
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
