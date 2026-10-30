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
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.datastore.preferences.core.intPreferencesKey
import com.bandary.MainActivity
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus

private val MONTH_OFFSET_KEY = intPreferencesKey("month_offset")

class MonthCalendarWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val offset = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[MONTH_OFFSET_KEY] ?: 0
        val snap = WidgetRepository.load(context, offset)
        provideContent { MonthContent(snap) }
    }
}

class MonthCalendarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthCalendarWidget()
}

// ◀ ▶ 切換月份：改 widget 狀態的 offset 後刷新。
class PrevMonthAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, glanceId) {
            it[MONTH_OFFSET_KEY] = (it[MONTH_OFFSET_KEY] ?: 0) - 1
        }
        MonthCalendarWidget().update(context, glanceId)
    }
}
class NextMonthAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, glanceId) {
            it[MONTH_OFFSET_KEY] = (it[MONTH_OFFSET_KEY] ?: 0) + 1
        }
        MonthCalendarWidget().update(context, glanceId)
    }
}

private val WEEK_HEADERS = listOf("日", "一", "二", "三", "四", "五", "六")

@androidx.compose.runtime.Composable
private fun MonthContent(snap: WidgetSnapshot) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetColors.surface)
            .cornerRadius(16.dp)
            .padding(10.dp)
    ) {
        // 標題列：‹ 月份 ›（點月份開 App）
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = GlanceModifier.padding(horizontal = 6.dp, vertical = 2.dp).clickable(actionRunCallback<PrevMonthAction>()),
            ) { Text("‹", style = TextStyle(color = WidgetColors.accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)) }
            Box(
                modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
                contentAlignment = Alignment.Center
            ) {
                Text(snap.monthLabel, style = TextStyle(color = WidgetColors.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold))
            }
            Box(
                modifier = GlanceModifier.padding(horizontal = 6.dp, vertical = 2.dp).clickable(actionRunCallback<NextMonthAction>()),
            ) { Text("›", style = TextStyle(color = WidgetColors.accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)) }
        }
        Spacer(GlanceModifier.height(4.dp))

        // 星期列
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            WEEK_HEADERS.forEach { h ->
                Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                    Text(h, style = TextStyle(color = WidgetColors.onSurfaceVariant, fontSize = 10.sp))
                }
            }
        }
        Spacer(GlanceModifier.height(2.dp))

        // 日格：每一週用 defaultWeight() 均分剩餘高度，無論 widget 多高都不會撐爆 / 被裁切。
        buildWeeks(snap.year, snap.month).forEach { week ->
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                week.forEach { day -> DayCell(day, snap, GlanceModifier.defaultWeight()) }
            }
        }

        // 當月活動預覽：讓「哪天有什麼」不用點開 App 也看得到。
        Spacer(GlanceModifier.height(6.dp))
        if (snap.monthEvents.isEmpty()) {
            Row(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                LegendDot(WidgetColors.accent, "演出")
                Spacer(GlanceModifier.width(12.dp))
                LegendDot(WidgetColors.sale, "開賣")
            }
        } else {
            snap.monthEvents.take(3).forEach { e ->
                Row(
                    modifier = GlanceModifier.fillMaxWidth().padding(vertical = 1.dp)
                        .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = GlanceModifier.width(6.dp).height(6.dp)
                            .background(if (e.isSale) WidgetColors.sale else WidgetColors.accent).cornerRadius(3.dp)
                    ) {}
                    Spacer(GlanceModifier.width(6.dp))
                    Text(
                        "${snap.month}/${e.day}",
                        style = TextStyle(color = WidgetColors.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    )
                    Spacer(GlanceModifier.width(6.dp))
                    Text(
                        (if (e.isSale) "開賣 " else "") + e.label,
                        maxLines = 1,
                        style = TextStyle(color = WidgetColors.onSurface, fontSize = 11.sp)
                    )
                }
            }
            if (snap.monthEvents.size > 3) {
                Text(
                    "還有 ${snap.monthEvents.size - 3} 筆…",
                    style = TextStyle(color = WidgetColors.onSurfaceVariant, fontSize = 9.sp)
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun DayCell(day: Int?, snap: WidgetSnapshot, cellModifier: GlanceModifier) {
    if (day == null) {
        Box(modifier = cellModifier.fillMaxHeight()) {}
        return
    }
    val isConcert = day in snap.concertDays
    val isSale = day in snap.saleDays
    val isToday = day == snap.today

    val markBg = when {
        isConcert -> WidgetColors.accent
        isToday -> WidgetColors.surfaceVariant
        else -> ColorProvider(androidx.compose.ui.graphics.Color.Transparent)
    }
    val textColor = when {
        isConcert -> WidgetColors.onAccent
        isSale -> WidgetColors.sale
        else -> WidgetColors.onSurface
    }
    // 外層吃滿加權高度並置中；標記用固定尺寸小方塊，避免隨高度拉長變形。
    Box(modifier = cellModifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
        Box(
            modifier = GlanceModifier.size(26.dp).background(markBg).cornerRadius(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$day",
                style = TextStyle(
                    color = textColor,
                    fontSize = 12.sp,
                    fontWeight = if (isToday || isConcert || isSale) FontWeight.Bold else FontWeight.Normal
                )
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun LegendDot(color: androidx.glance.unit.ColorProvider, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = GlanceModifier.width(8.dp).height(8.dp).background(color).cornerRadius(4.dp)
        ) {}
        Spacer(GlanceModifier.width(3.dp))
        Text(label, style = TextStyle(color = WidgetColors.onSurfaceVariant, fontSize = 10.sp))
    }
}

/** 產生當月的週列表；null 代表空白格。以週日為每週第一天。 */
private fun buildWeeks(year: Int, month: Int): List<List<Int?>> {
    val first = LocalDate(year, month, 1)
    val blanks = first.dayOfWeek.isoDayNumber % 7   // 週一=1..週日=7 → 週日=0
    val daysInMonth = first.daysUntil(first.plus(1, DateTimeUnit.MONTH))
    val cells = ArrayList<Int?>()
    repeat(blanks) { cells.add(null) }
    for (d in 1..daysInMonth) cells.add(d)
    while (cells.size % 7 != 0) cells.add(null)
    return cells.chunked(7)
}
