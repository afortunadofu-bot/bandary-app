package com.bandary.presentation.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bandary.presentation.ui.share.PosterRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 票根分享的範例：用虛構的演出資料實際跑一次 PosterRenderer，讓還沒記幾場的人先看到成品長怎樣。
 * 資料全是示意（虛構活動名），不含任何真實藝人。
 */
@Composable
fun SampleTicketDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(Unit) {
        bitmap = withContext(Dispatchers.Default) {
            runCatching {
                PosterRenderer.render(
                    context,
                    PosterRenderer.Spec(
                        width = 720, height = 1280,
                        background = PosterRenderer.Background.Preset(0),
                        accentHex = "#FFFFFF",
                        style = PosterRenderer.TypeStyle.GRID,
                        title = "秋日小夜曲",
                        subtitle = "範例樂團",
                        day = "23",
                        month = "SEP",
                        fullDate = "2026.09.23",
                        venue = "範例展演空間",
                        city = "台北",
                        blocks = listOf(PosterRenderer.Block("SETLIST", listOf("開場曲", "夏天的尾巴", "安可：再見")))
                    )
                )
            }.getOrNull()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("票根分享・範例") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "每場演出都能一鍵做成這樣的票根圖，分享到限動或存進相簿。以下為示意資料。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val b = bitmap
                    if (b == null) CircularProgressIndicator()
                    else Image(b.asImageBitmap(), contentDescription = "票根範例", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } }
    )
}
