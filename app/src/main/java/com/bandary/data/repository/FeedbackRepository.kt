package com.bandary.data.repository

import com.bandary.data.remote.FirestoreService
import com.bandary.domain.model.Feedback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** 意見回饋：使用者送出到 Firestore 共用 collection；管理員可讀取 / 刪除。 */
@Singleton
class FeedbackRepository @Inject constructor(
    private val firestore: FirestoreService
) {
    private val _items = MutableStateFlow<List<Feedback>>(emptyList())
    val items: StateFlow<List<Feedback>> = _items.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    suspend fun submit(feedback: Feedback): Boolean {
        _loading.update { true }
        val ok = firestore.submitFeedback(feedback)
            .onSuccess { _message.update { "已送出，感謝你的回饋！" } }
            .onFailure { e -> _message.update { "送出失敗：${e.message}" } }
            .isSuccess
        _loading.update { false }
        return ok
    }

    suspend fun refresh() {
        if (_loading.value) return
        _loading.update { true }
        firestore.fetchFeedback()
            .onSuccess { list -> _items.update { list } }
            .onFailure { e -> _message.update { "讀取失敗：${e.message}" } }
        _loading.update { false }
    }

    suspend fun delete(id: String) {
        firestore.deleteFeedback(id)
            .onSuccess { refresh() }
            .onFailure { e -> _message.update { "刪除失敗：${e.message}" } }
    }

    fun clearMessage() = _message.update { null }
}
