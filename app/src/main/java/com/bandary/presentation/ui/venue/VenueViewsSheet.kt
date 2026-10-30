package com.bandary.presentation.ui.venue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bandary.data.remote.FirestoreService
import com.bandary.data.util.ImageStore
import com.bandary.domain.model.VenueView
import com.bandary.presentation.ui.components.EventImage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 場館座位視野圖。
 *
 * 待辦上掛了很久，一直卡在版權：場館官方的座位圖、售票網站的視野圖都是別人的作品，
 * 抓進 App 再散給所有使用者是重製散布，不是「參考」。
 *
 * 改成**使用者自己拍的照片**就沒有這個問題，而且實際上更有用——
 * 官方座位圖只告訴你位子在平面圖的哪一格，照片才看得到
 * 「這個角度會不會被柱子擋住」「看不看得到大螢幕」。
 *
 * 與活動投稿同一套規矩：上傳一律待審，管理員看過才公開。
 */
@HiltViewModel
class VenueViewsViewModel @Inject constructor(
    private val firestore: FirestoreService,
    @param:ApplicationContext private val appContext: android.content.Context
) : ViewModel() {

    private val _views = MutableStateFlow<List<VenueView>>(emptyList())
    val views: StateFlow<List<VenueView>> = _views.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun load(venue: String) {
        viewModelScope.launch {
            _loading.value = true
            firestore.venueViews(venue).onSuccess { _views.value = it }
            _loading.value = false
        }
    }

    fun isSignedIn(): Boolean = firestore.isSignedIn()

    fun submit(venue: String, section: String, note: String, uri: android.net.Uri, onDone: () -> Unit) {
        viewModelScope.launch {
            _loading.value = true
            val encoded = withContext(Dispatchers.IO) { ImageStore.encodeForUpload(appContext, uri) }
            if (encoded == null) {
                _message.value = "這張讀不到，換一張試試"
                _loading.value = false
                return@launch
            }
            firestore.addVenueView(venue, section, note, encoded)
                .onSuccess { _message.value = "已送出，管理員審核過之後其他人就看得到"; onDone() }
                .onFailure { _message.value = it.message ?: "送出失敗" }
            _loading.value = false
        }
    }

    fun clearMessage() { _message.value = null }
}

@Composable
fun VenueViewsDialog(
    venue: String,
    onDismiss: () -> Unit,
    viewModel: VenueViewsViewModel = hiltViewModel()
) {
    val views by viewModel.views.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val message by viewModel.message.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(venue) { viewModel.load(venue) }
    LaunchedEffect(message) {
        message?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearMessage()
        }
    }

    var section by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<android.net.Uri?>(null) }

    // 用共用的裁切流程：視野照常常連前排的頭、手機邊框一起入鏡，裁掉才看得出視野。
    val pick = com.bandary.presentation.ui.components.rememberPickAndCrop { uri -> pending = uri }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (venue.isBlank()) "座位視野" else venue + " · 座位視野") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    loading && views.isEmpty() -> Box(
                        modifier = Modifier.fillMaxWidth().height(90.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp) }

                    views.isEmpty() -> Text(
                        "這個場館還沒有人分享視野圖。你拍的那張可能就是第一張——" +
                            "下次進場時從座位拍一張，其他人選位子時會用得到。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    else -> LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(views, key = { it.id }) { v ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ),
                                elevation = CardDefaults.cardElevation(0.dp)
                            ) {
                                Column {
                                    EventImage(
                                        data = v.image,
                                        modifier = Modifier.fillMaxWidth().height(170.dp),
                                        contentScale = ContentScale.Crop
                                    )
                                    Column(
                                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        if (v.section.isNotBlank()) {
                                            Text(
                                                v.section,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        if (v.note.isNotBlank()) {
                                            Text(
                                                v.note,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                if (!viewModel.isSignedIn()) {
                    Text(
                        "登入之後就能分享自己的視野圖。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "分享你的視野",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = section,
                        onValueChange = { section = it },
                        label = { Text("區域 / 座位") },
                        placeholder = { Text("例：3A 區 12 排、搖滾 B 區") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("一句話心得（可留空）") },
                        // 真正有用的是這句，不是照片本身。
                        placeholder = { Text("例：左邊柱子會擋到一點，但看得到大螢幕") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { pick() }, shape = RoundedCornerShape(10.dp)) {
                            Icon(Icons.Outlined.AddAPhoto, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(if (pending == null) "選照片" else "已選一張，可重選")
                        }
                        Spacer(Modifier.size(8.dp))
                        if (pending != null) {
                            Text(
                                "送出後要等審核",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = pending != null && !loading && viewModel.isSignedIn(),
                onClick = {
                    pending?.let { uri ->
                        viewModel.submit(venue, section, note, uri) {
                            pending = null; section = ""; note = ""
                            viewModel.load(venue)
                        }
                    }
                }
            ) { Text("送出") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("關閉") } }
    )
}
