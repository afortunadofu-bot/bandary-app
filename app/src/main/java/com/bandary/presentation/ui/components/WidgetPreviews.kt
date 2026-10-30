package com.bandary.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * App 內的桌面小工具「預覽圖」——用 Compose 仿畫實際 widget 外觀（白底卡片，
 * 與加到桌面後看到的一致），讓使用者加入前先看得到樣子。純靜態示意，不接真實資料。
 */

// 對應 WidgetColors（widget 為固定白底淺色卡）。
private val WAccent = Color(0xFF6B9080)
private val WSurface = Color(0xFFFFFFFF)
private val WSurfaceVariant = Color(0xFFEAF4F4)
private val WOnSurface = Color(0xFF1E2A26)
private val WOnSurfaceVariant = Color(0xFF4A5A54)
private val WSale = Color(0xFFC76A1A)

@Composable
fun WidgetPreviewGallery(modifier: Modifier = Modifier) {
    // 單欄整寬：貼近實際桌面小工具「較寬」的比例，內容不再被擠到跑版；預覽也更完整清楚。
    // heightRatio ≈ 該小工具常見的高/寬比（清單類較高、倒數方形、月曆偏寬）。
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        WidgetPreviewFrame("即將到來清單", heightRatio = 0.62f) { UpcomingPreview() }
        WidgetPreviewFrame("下一場倒數", heightRatio = 0.55f) { CountdownPreview() }
        WidgetPreviewFrame("本月月曆", heightRatio = 0.72f) { MonthPreview() }
        WidgetPreviewFrame("開賣提醒", heightRatio = 0.5f) { SalePreview() }
    }
}

@Composable
private fun WidgetPreviewFrame(label: String, heightRatio: Float, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val h = (maxWidth * heightRatio).coerceIn(120.dp, 240.dp)
            // 不在這裡加 padding：文字類預覽自己補 12dp，圖片背景類（下一場倒數）要鋪滿到圓角邊。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(h)
                    .clip(RoundedCornerShape(16.dp))
                    .background(WSurface)
            ) { content() }
        }
        Text(
            label,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 2.dp)
        )
    }
}

@Composable
private fun UpcomingPreview() {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("即將到來", color = WAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        listOf(Triple("7/12", "落日飛車", "3 天"), Triple("7/20", "告五人", "11 天")).forEach { (d, a, n) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Box(
                    Modifier.clip(RoundedCornerShape(6.dp)).background(WSurfaceVariant)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) { Text(d, color = WOnSurface, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(6.dp))
                Text(a, color = WOnSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text(n, color = WOnSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CountdownPreview() {
    // 下一場倒數 widget 會把「該場封面」當背景（暗罩 + 白字）。預覽也用內建底圖示意這個效果，
    // 讓使用者知道加到桌面後會長這樣；沒有內建底圖時退回深色漸層。
    val context = androidx.compose.ui.platform.LocalContext.current
    val bmp = androidx.compose.runtime.remember {
        val idxs = com.bandary.presentation.ui.share.PosterBackgrounds.availableIndices(context)
        idxs.firstOrNull()?.let { com.bandary.presentation.ui.share.PosterBackgrounds.load(context, it, 320, 360) }
    }
    Box(Modifier.fillMaxSize()) {
        if (bmp != null) {
            androidx.compose.foundation.Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF3B4A44), Color(0xFF1B2622)))
                )
            )
        }
        // 暗罩讓白字清楚（同實際 widget）。
        Box(Modifier.fillMaxSize().background(Color(0x99000000)))
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Text("下一場", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("3", color = Color.White, fontSize = 46.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(4.dp))
                Text("天後", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text("落日飛車", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("7/12 · Legacy", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
        }
    }
}

@Composable
private fun MonthPreview() {
    val concertDays = setOf(12, 20)
    val saleDay = 8
    // 2026 年 7 月 1 日是週三：前面補 3 格空白，讓格子對齊真正的星期（日一二三四五六）。
    val leading = 3
    val cells: List<Int?> = List(leading) { null } + (1..31).toList()
    val rows = (cells.size + 6) / 7
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("‹", color = WAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("7 月", color = WAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text("›", color = WAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        // 星期標題列。
        Row(Modifier.fillMaxWidth()) {
            listOf("日", "一", "二", "三", "四", "五", "六").forEach {
                Text(it, color = WOnSurfaceVariant, fontSize = 8.sp, fontWeight = FontWeight.Medium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(2.dp))
        // 週列用 weight 平均撐滿整張卡（不再是固定小列高 + 底部一大片空白）。
        Column(Modifier.fillMaxWidth().weight(1f)) {
            var idx = 0
            repeat(rows) {
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    repeat(7) {
                        val d = cells.getOrNull(idx++)
                        val isConcert = d != null && d in concertDays
                        val isSale = d == saleDay
                        Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            if (d != null) {
                                Box(
                                    modifier = Modifier.size(18.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isConcert) WAccent else Color.Transparent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "$d",
                                        color = when { isConcert -> Color.White; isSale -> WSale; else -> WOnSurface },
                                        fontSize = 9.sp,
                                        fontWeight = if (isConcert || isSale) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        // 活動預覽：與實際 widget 一致，顯示「日 · 名稱」。
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(5.dp).clip(RoundedCornerShape(3.dp)).background(WAccent))
            Spacer(Modifier.width(5.dp))
            Text("7/12", color = WOnSurfaceVariant, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(5.dp))
            Text("落日飛車", color = WOnSurface, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
private fun SalePreview() {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("即將開賣", color = WSale, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        listOf("7/08" to "海邊的卡夫卡", "7/15" to "浪人祭").forEach { (d, a) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Box(
                    Modifier.clip(RoundedCornerShape(6.dp)).background(WSurfaceVariant)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) { Text(d, color = WSale, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(6.dp))
                Text(a, color = WOnSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            }
        }
    }
}
