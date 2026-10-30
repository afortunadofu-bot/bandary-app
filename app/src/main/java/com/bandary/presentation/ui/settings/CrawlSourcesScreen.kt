package com.bandary.presentation.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bandary.data.remote.FirestoreService
import com.bandary.presentation.ui.components.FullScreenOverlay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 管理員：活動蒐集來源。
 *
 * 雲端這一套其實早就在跑了——`crawlScheduled` 每天台北 06:00 抓一次，
 * 每抓完一個來源就把 `lastRunAt` / `lastCount` 寫回 `crawl_sources`，
 * `crawlNow` 也早就寫好可以手動觸發。但 **App 從來沒有讀過這些**：
 * 「上次什麼時候抓的、抓到幾筆」只能進 Firebase Console 看，
 * 新增一個來源也只能去 Console 手打文件——這就是五家售票平台一直沒設定起來的原因。
 *
 * 另外：使用者送的 `crawl_source_suggestions` 永遠只是**建議**。
 * 要有人在這個畫面按下去，它才會變成真的會被抓的來源。
 * 這個分家是刻意的——直接讓任何人寫 `crawl_sources`，
 * 等於開放任意人指定伺服器去抓任何網址。
 */
@HiltViewModel
class CrawlSourcesViewModel @Inject constructor(
    private val firestore: FirestoreService
) : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val running: Boolean = false,
        val sources: List<FirestoreService.CrawlSource> = emptyList(),
        val suggestions: List<FirestoreService.SourceSuggestion> = emptyList(),
        val message: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val sources = firestore.fetchCrawlSources().getOrElse { emptyList() }
            val suggestions = firestore.allCrawlSuggestions().getOrElse { emptyList() }
            _state.update { it.copy(loading = false, sources = sources, suggestions = suggestions) }
        }
    }

    /**
     * 手動跑一次。這支在雲端最長跑五分鐘（會開無頭瀏覽器），
     * 所以畫面要一直壓著「抓取中」，不能讓人以為沒反應又按一次。
     */
    fun runNow() {
        if (_state.value.running) return
        viewModelScope.launch {
            _state.update { it.copy(running = true, message = "抓取中…最久可能要幾分鐘。") }
            val msg = firestore.runCrawlNow().fold({ it }, { "抓取失敗：" + (it.message ?: "未知錯誤") })
            _state.update { it.copy(running = false, message = msg) }
            refresh()
        }
    }

    fun save(src: FirestoreService.CrawlSource, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            firestore.saveCrawlSource(src)
                .onSuccess { _state.update { s -> s.copy(message = "已儲存") }; onDone() }
                .onFailure { e -> _state.update { s -> s.copy(message = e.message ?: "儲存失敗") } }
            refresh()
        }
    }

    fun setActive(id: String, active: Boolean) {
        viewModelScope.launch { firestore.setCrawlSourceActive(id, active); refresh() }
    }

    fun delete(id: String) {
        viewModelScope.launch { firestore.deleteCrawlSource(id); refresh() }
    }

    /** 把一筆建議變成真的來源，並把建議標記成已採用（留著當紀錄，不刪）。 */
    fun acceptSuggestion(s: FirestoreService.SourceSuggestion) {
        viewModelScope.launch {
            firestore.saveCrawlSource(
                FirestoreService.CrawlSource(
                    url = s.url,
                    label = s.label.ifBlank { s.url },
                    active = true
                )
            ).onSuccess { firestore.setSuggestionStatus(s.id, "accepted") }
            refresh()
        }
    }

    fun rejectSuggestion(s: FirestoreService.SourceSuggestion) {
        viewModelScope.launch { firestore.setSuggestionStatus(s.id, "rejected"); refresh() }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}

/** 「上次抓取」用相對時間寫。管理員要知道的是「最近有沒有在跑」，不是精確到秒。 */
private fun relativeTime(ts: Long): String {
    if (ts <= 0L) return "還沒抓過"
    val diff = System.currentTimeMillis() - ts
    if (diff < 0) return "剛剛"
    val mins = diff / 60_000
    val hours = mins / 60
    val days = hours / 24
    return when {
        mins < 1 -> "剛剛"
        mins < 60 -> "$mins 分鐘前"
        hours < 24 -> "$hours 小時前"
        days < 30 -> "$days 天前"
        else -> "${days / 30} 個月前"
    }
}

@Composable
fun CrawlSourcesOverlay(
    onDismiss: () -> Unit,
    viewModel: CrawlSourcesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }

    // 非 null＝正在編輯這一筆（id 空白代表新增）。
    var editing by remember { mutableStateOf<FirestoreService.CrawlSource?>(null) }
    var confirmDelete by remember { mutableStateOf<FirestoreService.CrawlSource?>(null) }

    FullScreenOverlay(onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "活動蒐集來源",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { editing = FirestoreService.CrawlSource() }) {
                    Icon(Icons.Outlined.Add, "新增來源")
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "關閉") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "每天台北時間 06:00 自動抓一次啟用中的來源，抓到的一律寫成待審，不會自動公開。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilledTonalButton(
                                    onClick = { viewModel.runNow() },
                                    enabled = !state.running,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    if (state.running) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (state.running) "抓取中…" else "立即抓取")
                                }
                                Spacer(Modifier.weight(1f))
                                if (state.loading) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                }
                            }
                            state.message?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                item {
                    Text(
                        "來源 · ${state.sources.size}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (state.sources.isEmpty() && !state.loading) {
                    item {
                        Text(
                            "還沒有任何來源。按右上角「＋」新增，或從下方的使用者建議採用一筆。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(state.sources, key = { it.id }) { src ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        src.label.ifBlank { "（沒有名稱）" },
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        src.url,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Switch(
                                    checked = src.active,
                                    onCheckedChange = { viewModel.setActive(src.id, it) }
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    // 這兩個數字就是整個畫面存在的理由：以前只能進 Console 看。
                                    "上次抓取：" + relativeTime(src.lastRunAt) +
                                        if (src.lastRunAt > 0) " · ${src.lastCount} 筆" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { editing = src }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Outlined.Edit, "編輯", modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { confirmDelete = src }, modifier = Modifier.size(36.dp)) {
                                    Icon(
                                        Icons.Outlined.Delete, "刪除",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                // ── 使用者送的建議 ──────────────────────────────────
                val pending = state.suggestions.filter { it.status == "pending" }
                if (pending.isNotEmpty()) {
                    item {
                        Column {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "使用者建議 · ${pending.size}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "建議不會被自動抓。按「採用」才會變成真的來源——開放任何人直接指定要抓的網址太危險。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    items(pending, key = { it.id }) { sug ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(0.dp)
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    sug.label.ifBlank { sug.url },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    sug.url,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                if (sug.note.isNotBlank()) {
                                    Text(
                                        sug.note,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row {
                                    Spacer(Modifier.weight(1f))
                                    TextButton(onClick = { viewModel.rejectSuggestion(sug) }) { Text("退掉") }
                                    TextButton(onClick = { viewModel.acceptSuggestion(sug) }) { Text("採用") }
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    editing?.let { src ->
        var url by remember(src.id) { mutableStateOf(src.url) }
        var label by remember(src.id) { mutableStateOf(src.label) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (src.id.isBlank()) "新增來源" else "編輯來源") },
            text = {
                com.bandary.presentation.ui.components.DialogBody(spacing = 10.dp) {
                    OutlinedTextField(
                        value = label, onValueChange = { label = it },
                        label = { Text("名稱") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = url, onValueChange = { url = it },
                        label = { Text("網址") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "可以是活動列表頁、iCal 檔或帶 schema.org/Event 標記的頁面——" +
                            "抓取端會依序嘗試，純 fetch 拿不到才開無頭瀏覽器。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "加進來之前請先確認對方的使用條款與 robots.txt。責任在加來源的人。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = url.isNotBlank(),
                    onClick = {
                        viewModel.save(src.copy(url = url, label = label)) { }
                        editing = null
                    }
                ) { Text("儲存") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("取消") } }
        )
    }

    confirmDelete?.let { src ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("刪除來源") },
            text = {
                Text(
                    "要刪掉「" + src.label.ifBlank { src.url } + "」嗎？" +
                        "已經抓進來的活動不會被刪，只是以後不再從這個來源抓。"
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(src.id); confirmDelete = null }) {
                    Text("刪除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("取消") } }
        )
    }
}
