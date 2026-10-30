package com.bandary.presentation.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pro 頁每個付費功能的「範例」：點功能列就看得到解鎖後長怎樣，不必先付錢才知道買到什麼。
 * 內容全是示意畫面與虛構資料。「下載圖片」直接用真的票根產生器跑一張範例（見 SampleTicketDialog）。
 */
@Composable
internal fun ProFeatureDemoDialog(title: String, desc: String, onDismiss: () -> Unit) {
    if (title == "下載圖片") {
        com.bandary.presentation.ui.components.SampleTicketDialog(onDismiss = onDismiss)
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$title・範例") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                when (title) {
                    "上傳多張照片" -> PhotosDemo()
                    "詳細統計" -> StatsDemo()
                    "自訂調色盤" -> PaletteDemo()
                    "深色模式" -> DarkModeDemo()
                    "新增歌單" -> SetlistDemo()
                    "匯出資料" -> ExportDemo()
                }
                Text("以上為示意畫面。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } }
    )
}

private val demoTiles = listOf(
    Color(0xFF7A9A8C), Color(0xFFC9A9A6), Color(0xFF9FB3C8), Color(0xFFD8C8A8), Color(0xFFA7A3C4)
)

@Composable
private fun PhotosDemo() {
    val labels = listOf("封面", "現場", "票根", "座位圖", "合照")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            labels.forEachIndexed { i, l ->
                Box(
                    modifier = Modifier.weight(1f).aspectRatio(0.75f).clip(RoundedCornerShape(8.dp))
                        .background(Brush.verticalGradient(listOf(demoTiles[i], demoTiles[i].copy(alpha = 0.55f)))),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(l, color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
        }
        Text("免費 3 張・Pro 5 張・Pro+ 10 張", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatsDemo() {
    val bars = listOf("1月" to 2, "2月" to 4, "3月" to 3, "4月" to 5, "5月" to 7, "6月" to 4)
    val max = bars.maxOf { it.second }
    val primary = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DemoStat("25", "場演出")
            DemoStat("NT$38,400", "看團花費")
            DemoStat("9", "個縣市")
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(90.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            bars.forEach { (m, v) ->
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.fillMaxWidth().fillMaxHeight(v / max.toFloat() * 0.8f)
                            .clip(RoundedCornerShape(4.dp)).background(primary)
                    )
                    Text(m, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text("藝人排行：範例樂團 A ×6、範例樂團 B ×4、範例樂團 C ×3", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DemoStat(value: String, unit: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PaletteDemo() {
    val swatches = listOf(
        Color(0xFF7A9A8C), Color(0xFFC08A8A), Color(0xFF6F8FB0),
        Color(0xFFB59A5B), Color(0xFF8C6FB0), Color(0xFF3F3F46)
    )
    val ring = MaterialTheme.colorScheme.onSurface
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        swatches.forEachIndexed { i, c ->
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(c)
                    .then(if (i == 4) Modifier.border(3.dp, ring, CircleShape) else Modifier)
            )
        }
    }
}

@Composable
private fun DarkModeDemo() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PhoneMock(bg = Color(0xFFF4F4F1), fg = Color(0xFF1E1E1E), label = "淺色", modifier = Modifier.weight(1f))
        PhoneMock(bg = Color(0xFF121212), fg = Color(0xFFEDEDED), label = "深色", modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PhoneMock(bg: Color, fg: Color, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(
            Modifier.fillMaxWidth().aspectRatio(0.6f).clip(RoundedCornerShape(12.dp)).background(bg).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(Modifier.width(48.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(fg))
            repeat(3) {
                Box(Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(6.dp)).background(fg.copy(alpha = 0.12f)))
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SetlistDemo() {
    val songs = listOf("▶ 範例樂團", "1. 開場曲", "2. 夏天的尾巴", "3. 慢慢來", "◆ 安可", "4. 再見")
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        songs.forEach {
            val header = it.startsWith("▶") || it.startsWith("◆")
            Text(it, style = MaterialTheme.typography.bodySmall, fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

@Composable
private fun ExportDemo() {
    val lines = listOf(
        "日期,名稱,藝人,場地,票價",
        "2026-09-23,秋日小夜曲,範例樂團,範例展演空間,1200",
        "2026-08-15,夏日音樂祭,範例樂團 B,範例公園,2400"
    )
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp)
    ) {
        lines.forEach { Text(it, fontFamily = FontFamily.Monospace, fontSize = 10.sp) }
    }
}
