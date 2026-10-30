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
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.bandary.MainActivity

class UpcomingListWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = WidgetRepository.load(context)
        provideContent { UpcomingContent(snap) }
    }
}

class UpcomingListWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UpcomingListWidget()
}

@androidx.compose.runtime.Composable
private fun UpcomingContent(snap: WidgetSnapshot) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetColors.surface)
            .cornerRadius(16.dp)
            .padding(12.dp)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "即將到來",
                style = TextStyle(color = WidgetColors.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(GlanceModifier.defaultWeight())
            if (snap.upcoming.isNotEmpty()) {
                Text(
                    "${snap.upcoming.size} 場",
                    style = TextStyle(color = WidgetColors.onSurfaceVariant, fontSize = 12.sp)
                )
            }
        }
        Spacer(GlanceModifier.height(8.dp))

        if (snap.upcoming.isEmpty()) {
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "目前沒有即將到來的演出",
                    style = TextStyle(color = WidgetColors.onSurfaceVariant, fontSize = 13.sp)
                )
            }
        } else {
            LazyColumn {
                items(snap.upcoming) { c -> UpcomingRow(c) }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun UpcomingRow(c: WidgetConcert) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 日期方塊
        Box(
            modifier = GlanceModifier
                .background(WidgetColors.surfaceVariant)
                .cornerRadius(8.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                dateLabel(c),
                style = TextStyle(color = WidgetColors.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            )
        }
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                c.artist,
                maxLines = 1,
                style = TextStyle(color = WidgetColors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            )
            if (c.venue.isNotBlank()) {
                Text(
                    c.venue + (if (c.timeLabel.isNotBlank()) "  ${c.timeLabel}" else ""),
                    maxLines = 1,
                    style = TextStyle(color = WidgetColors.onSurfaceVariant, fontSize = 12.sp)
                )
            }
        }
        Spacer(GlanceModifier.width(8.dp))
        Text(
            daysLabel(c.daysUntil),
            style = TextStyle(
                color = if (c.daysUntil <= 0) WidgetColors.accent else WidgetColors.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

private fun dateLabel(c: WidgetConcert): String {
    val base = "${c.date.monthNumber}/${c.date.dayOfMonth}"
    val end = c.endDate ?: return base
    return "$base–${end.monthNumber}/${end.dayOfMonth}"
}

private fun daysLabel(days: Int): String = when {
    days <= 0 -> "今天"
    days == 1 -> "明天"
    else -> "$days 天"
}
