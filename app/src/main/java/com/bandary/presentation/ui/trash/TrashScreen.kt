package com.bandary.presentation.ui.trash

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bandary.presentation.ui.components.ScrollTopFab
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bandary.data.repository.ConcertRepository
import com.bandary.domain.model.Concert
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val concertRepo: ConcertRepository
) : ViewModel() {
    val items: StateFlow<List<Concert>> = concertRepo.getTrashedConcerts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun restore(id: String) { viewModelScope.launch { concertRepo.restoreConcert(id) } }
    fun purge(id: String) { viewModelScope.launch { concertRepo.permanentlyDeleteConcert(id) } }
}

/** 距離自動永久刪除還剩幾天（保留 7 天）。 */
private fun daysLeft(deletedAt: Long?): Int {
    if (deletedAt == null) return 7
    val elapsed = System.currentTimeMillis() - deletedAt
    val left = 7 - (elapsed / (24L * 60 * 60 * 1000)).toInt()
    return left.coerceIn(0, 7)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit = {},
    viewModel: TrashViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsState()
    var purgeTarget by remember { mutableStateOf<Concert?>(null) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scrollScope = androidx.compose.runtime.rememberCoroutineScope()

    Scaffold(
        // 清單可能很長，捲下去之後要回得來。行為與其他頁共用同一個元件。
        floatingActionButton = { ScrollTopFab(listState, scrollScope) },
        topBar = {
            TopAppBar(
                title = { Text("垃圾桶", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { pad ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad).padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("垃圾桶是空的", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("刪除的演唱會會先移到這裡，保留 7 天後自動永久刪除，期間都能還原。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@Scaffold
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(pad)
        ) {
            item {
                Text("保留 7 天後自動永久刪除，期間都能還原。",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(items, key = { it.id }) { c ->
                TrashRow(
                    concert = c,
                    onClick = { onOpenDetail(c.id) },
                    onRestore = { viewModel.restore(c.id) },
                    onPurge = { purgeTarget = c }
                )
            }
        }
    }

    purgeTarget?.let { c ->
        AlertDialog(
            onDismissRequest = { purgeTarget = null },
            title = { Text("永久刪除？") },
            text = { Text("「${c.artistName.ifBlank { c.name }}」將被永久刪除，無法還原。") },
            confirmButton = {
                TextButton(onClick = { viewModel.purge(c.id); purgeTarget = null }) { Text("永久刪除") }
            },
            dismissButton = { TextButton(onClick = { purgeTarget = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun TrashRow(concert: Concert, onClick: () -> Unit, onRestore: () -> Unit, onPurge: () -> Unit) {
    // 整列可點進唯讀詳情頁（getConcertById 不濾 deletedAt，所以垃圾桶裡的場次照樣讀得到）。
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(concert.artistName.ifBlank { concert.name }, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(2.dp))
                val date = "${concert.dateTime.monthNumber}/${concert.dateTime.dayOfMonth}"
                Text("$date · 還剩 ${daysLeft(concert.deletedAt)} 天", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            IconButton(onClick = onRestore) {
                Icon(Icons.Outlined.RestoreFromTrash, contentDescription = "還原", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onPurge) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "永久刪除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
