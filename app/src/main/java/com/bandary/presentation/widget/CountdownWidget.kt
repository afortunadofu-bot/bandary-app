package com.bandary.presentation.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.bandary.MainActivity

class CountdownWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = WidgetRepository.load(context)
        val next = snap.upcoming.firstOrNull()
        val bmp = next?.imageUri?.let { loadWidgetBitmap(context, it, 500, 800) }
        provideContent { CountdownContent(next, bmp) }
    }
}

class CountdownWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CountdownWidget()
}

@androidx.compose.runtime.Composable
private fun CountdownContent(next: WidgetConcert?, bmp: android.graphics.Bitmap? = null) {
    val context = LocalContext.current
    // 有封面圖時：圖片鋪底 + 暗罩 + 白字（背景跟著此活動的封面走）。
    if (next != null && bmp != null) {
        Box(modifier = GlanceModifier.fillMaxSize().cornerRadius(16.dp)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))) {
            androidx.glance.Image(
                provider = androidx.glance.ImageProvider(bmp),
                contentDescription = null,
                contentScale = androidx.glance.layout.ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize()
            )
            Box(modifier = GlanceModifier.fillMaxSize().background(androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color(0x99000000)))) {}
            Column(modifier = GlanceModifier.fillMaxSize().padding(14.dp)) {
                Text("下一場", style = TextStyle(color = androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color.White), fontSize = 11.sp, fontWeight = FontWeight.Bold))
                Spacer(GlanceModifier.defaultWeight())
                val white = androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color.White)
                if (next.daysUntil <= 0) {
                    Text("今天", style = TextStyle(color = white, fontSize = 42.sp, fontWeight = FontWeight.Bold))
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${next.daysUntil}", style = TextStyle(color = white, fontSize = 46.sp, fontWeight = FontWeight.Bold))
                        Spacer(GlanceModifier.width(4.dp))
                        Text("天後", style = TextStyle(color = white, fontSize = 13.sp))
                    }
                }
                Spacer(GlanceModifier.height(8.dp))
                Text(next.artist, maxLines = 1, style = TextStyle(color = white, fontSize = 15.sp, fontWeight = FontWeight.Bold))
                Text(subLabel(next), maxLines = 1, style = TextStyle(color = white, fontSize = 11.sp))
            }
        }
        return
    }
    if (next == null) {
        // 空狀態也維持編輯風版型：標籤在上、留白置中提示，保持一致美感。
        Column(
            modifier = GlanceModifier.fillMaxSize().background(WidgetColors.surface).cornerRadius(16.dp).padding(14.dp)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
        ) {
            Text("下一場", style = TextStyle(color = WidgetColors.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.defaultWeight())
            Text("—", style = TextStyle(color = WidgetColors.onSurface, fontSize = 40.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.height(6.dp))
            Text("目前沒有安排", maxLines = 1, style = TextStyle(color = WidgetColors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold))
            Text("點一下新增演出", maxLines = 1, style = TextStyle(color = WidgetColors.onSurfaceVariant, fontSize = 11.sp))
        }
        return
    }
    // 編輯風：左上標籤、超大倒數、藝人與日期靠左
    Column(
        modifier = GlanceModifier.fillMaxSize().background(WidgetColors.surface).cornerRadius(16.dp).padding(14.dp)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
    ) {
        Text("下一場", style = TextStyle(color = WidgetColors.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold))
        Spacer(GlanceModifier.defaultWeight())
        if (next.daysUntil <= 0) {
            Text("今天", style = TextStyle(color = WidgetColors.accent, fontSize = 42.sp, fontWeight = FontWeight.Bold))
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${next.daysUntil}", style = TextStyle(color = WidgetColors.onSurface, fontSize = 46.sp, fontWeight = FontWeight.Bold))
                Spacer(GlanceModifier.width(4.dp))
                Text("天後", style = TextStyle(color = WidgetColors.onSurfaceVariant, fontSize = 13.sp))
            }
        }
        Spacer(GlanceModifier.height(8.dp))
        Text(next.artist, maxLines = 1, style = TextStyle(color = WidgetColors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold))
        Text(subLabel(next), maxLines = 1, style = TextStyle(color = WidgetColors.onSurfaceVariant, fontSize = 11.sp))
    }
}

private fun subLabel(c: WidgetConcert): String {
    val date = "${c.date.monthNumber}/${c.date.dayOfMonth}"
    val time = if (c.timeLabel.isNotBlank()) " ${c.timeLabel}" else ""
    val venue = if (c.venue.isNotBlank()) " · ${c.venue}" else ""
    return "$date$time$venue"
}
