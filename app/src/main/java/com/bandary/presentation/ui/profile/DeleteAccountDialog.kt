package com.bandary.presentation.ui.profile

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * 刪除帳號確認。要手動輸入「刪除」才能按——這是不可復原的操作，不該一個誤觸就發生。
 * 把會刪什麼、不會刪什麼（公開活動、本機紀錄）講清楚，Play 審核也看這個。
 */
@Composable
fun DeleteAccountDialog(
    email: String,
    onDelete: (onResult: (Result<Unit>) -> Unit) -> Unit,
    onDeleted: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var confirmText by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("刪除帳號") },
        text = {
            com.bandary.presentation.ui.components.DialogBody(spacing = 10.dp) {
                Text("會永久刪除「$email」在雲端的所有資料，無法復原：", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "・演唱會、選歌與雲端備份的照片\n" +
                        "・好友關係、好友代碼與共享日曆\n" +
                        "・排行榜名次、還沒公開的活動投稿與意見回饋\n" +
                        "・登入帳號本身",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "已公開的活動會保留，但會移除投稿者資訊。這支手機上的本機紀錄不會被刪；" +
                        "要一併清除，請到系統設定清除 App 資料或解除安裝。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = confirmText,
                    onValueChange = { confirmText = it },
                    label = { Text("輸入「刪除」確認") },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("刪除中，可能需要幾十秒…", style = MaterialTheme.typography.bodySmall)
                    }
                }
                errorText?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = confirmText.trim() == "刪除" && !busy,
                onClick = {
                    busy = true
                    errorText = null
                    onDelete { result ->
                        busy = false
                        result
                            .onSuccess {
                                Toast.makeText(context, "帳號已刪除", Toast.LENGTH_LONG).show()
                                onDeleted()
                            }
                            .onFailure { e -> errorText = "刪除失敗：${e.message ?: "請稍後再試"}" }
                    }
                }
            ) { Text("永久刪除", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("取消") } }
    )
}
