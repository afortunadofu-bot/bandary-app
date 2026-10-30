package com.bandary.presentation.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bandary.data.remote.GoogleCalendarService
import com.bandary.data.remote.ImportedEvent
import com.bandary.data.repository.ConcertRepository
import com.bandary.domain.model.Concert
import com.bandary.domain.model.ConcertType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.UUID
import javax.inject.Inject

data class CalendarSyncState(
    val connected: Boolean = false,
    val email: String? = null,
    val importing: Boolean = false,
    val events: List<ImportedEvent> = emptyList(),
    val message: String? = null
)

@HiltViewModel
class CalendarSyncViewModel @Inject constructor(
    private val calendar: GoogleCalendarService,
    private val concertRepo: ConcertRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CalendarSyncState())
    val state: StateFlow<CalendarSyncState> = _state.asStateFlow()

    fun signInIntent() = calendar.signInIntent()

    fun refreshConnection() {
        _state.update { it.copy(connected = calendar.isConnected(), email = calendar.connectedEmail()) }
    }

    fun importEvents() {
        viewModelScope.launch {
            _state.update { it.copy(importing = true, message = null) }
            val events = calendar.importUpcomingEvents()
            _state.update {
                it.copy(
                    importing = false,
                    events = events,
                    message = if (events.isEmpty()) "沒有抓到即將到來的活動，或尚未授權" else null
                )
            }
        }
    }

    fun addFromEvent(event: ImportedEvent, onDone: () -> Unit) {
        viewModelScope.launch {
            val dt = parseStart(event.startIso)
            concertRepo.saveConcert(
                Concert(
                    id = UUID.randomUUID().toString(),
                    name = event.title,
                    artistName = event.title,
                    venue = event.location ?: "",
                    dateTime = dt,
                    type = ConcertType.OTHER
                )
            )
            _state.update { it.copy(message = "已加入「${event.title}」") }
            onDone()
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            calendar.disconnect()
            _state.update { CalendarSyncState(connected = false) }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun parseStart(iso: String): LocalDateTime {
        // Handles "2026-07-01T19:30:00+08:00", "...Z", or date-only "2026-07-01".
        val trimmed = iso.substringBefore("+").substringBefore("Z").trim()
        return runCatching { LocalDateTime.parse(trimmed) }.getOrElse {
            runCatching { LocalDateTime.parse(trimmed + "T00:00:00") }.getOrElse {
                Clock.System.now().toLocalDateTime(TimeZone.of("Asia/Taipei"))
            }
        }
    }
}
