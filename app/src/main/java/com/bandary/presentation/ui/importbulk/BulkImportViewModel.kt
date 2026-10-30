package com.bandary.presentation.ui.importbulk

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bandary.data.repository.ConcertRepository
import com.bandary.domain.model.Concert
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class BulkImportViewModel @Inject constructor(
    private val concertRepo: ConcertRepository,
    @param:ApplicationContext private val appContext: Context
) : ViewModel() {

    /** 現有演唱會的「名稱或藝人 # 日期」鍵，用來標示重複（重複列預設不勾選）。 */
    val existingKeys: StateFlow<Set<String>> =
        concertRepo.getAllConcerts()
            .map { list -> list.map { dedupeKey(it) }.toSet() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** 讀取使用者選的 CSV / 文字檔內容（去 BOM、UTF-8）。 */
    fun readFile(uri: android.net.Uri, onLoaded: (String) -> Unit) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?.let { bytes ->
                            // 去掉可能的 UTF-8 BOM
                            val start = if (bytes.size >= 3 &&
                                bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) 3 else 0
                            String(bytes, start, bytes.size - start, Charsets.UTF_8)
                        }
                }.getOrNull()
            }
            if (text != null) onLoaded(text) else _message.update { "無法讀取檔案" }
        }
    }

    fun importConcerts(concerts: List<Concert>, onDone: (Int) -> Unit) {
        if (_importing.value || concerts.isEmpty()) return
        _importing.update { true }
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                var ok = 0
                concerts.forEach { c ->
                    runCatching { concertRepo.saveConcert(c) }.onSuccess { ok++ }
                }
                ok
            }
            _importing.update { false }
            onDone(saved)
        }
    }

    fun clearMessage() = _message.update { null }

    companion object {
        fun dedupeKey(c: Concert): String {
            val label = c.name.ifBlank { c.artistName }.trim().lowercase()
            return "$label#${c.dateTime.date}"
        }
    }
}
