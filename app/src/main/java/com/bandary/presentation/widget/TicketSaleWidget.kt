package com.bandary.presentation.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

class TicketSaleWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = WidgetRepository.load(context)
        provideContent { TicketSaleContent(snap) }
    }
}

class TicketSaleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TicketSaleWidget()
}

@androidx.compose.runtime.Composable
private fun TicketSaleContent(snap: WidgetSnapshot) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetColors.surface)
            .cornerRadius(16.dp)
            .padding(12.dp)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java).apply {
                // 點開賣提醒小工具 → 直接進願望清單（E2-9），一次盯完所有想搶的開賣。
                // 少了 SINGLE_TOP，App 已經在跑時不會呼叫 onNewIntent，點了只會回到原本那一頁。
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("navTarget", "wishlist")
            }))
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "即將開賣",
                style = TextStyle(color = WidgetColors.sale, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(GlanceModifier.defaultWeight())
            if (snap.upcomingSales.isNotEmpty()) {
                Text(
                    "${snap.upcomingSales.size} 場",
                    style = TextStyle(color = WidgetColors.onSurfaceVariant, fontSize = 12.sp)
                )
            }
        }
        Spacer(GlanceModifier.height(8.dp))

        if (snap.upcomingSales.isEmpty()) {
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "目前沒有即將開賣的場次",
                    style = TextStyle(color = WidgetColors.onSurfaceVariant, fontSize = 13.sp)
                )
            }
        } else {
            LazyColumn {
                items(snap.upcomingSales) { s -> SaleRow(s) }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun SaleRow(s: WidgetSale) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .background(WidgetColors.surfaceVariant)
                .cornerRadius(8.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${s.date.monthNumber}/${s.date.dayOfMonth}",
                style = TextStyle(color = WidgetColors.sale, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            )
        }
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                s.artist,
                maxLines = 1,
                style = TextStyle(color = WidgetColors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            )
            Text(
                "開賣 ${s.timeLabel}" + if (s.name.isNotBlank() && s.name != s.artist) "  ${s.name}" else "",
                maxLines = 1,
                style = TextStyle(color = WidgetColors.onSurfaceVariant, fontSize = 12.sp)
            )
        }
        Spacer(GlanceModifier.width(8.dp))
        Text(
            daysLabelSale(s.daysUntil),
            style = TextStyle(
                color = if (s.daysUntil <= 0) WidgetColors.sale else WidgetColors.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

private fun daysLabelSale(days: Int): String = when {
    days <= 0 -> "今天"
    days == 1 -> "明天"
    else -> "$days 天"
}
