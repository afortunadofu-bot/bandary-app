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
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.bandary.MainActivity
import dagger.hilt.android.EntryPointAccessors

/**
 * E2-10「今日選歌」桌面小工具：一鍵直達選歌頁，減少每日習慣動作的摩擦。
 * 顯示今天是否已記錄 + 目前連續天數；點一下開啟 App 並切到選歌行事曆（navTarget=songs）。
 */
class TodaySongWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ep = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val repo = ep.dailySongRepository()
        val done = runCatching { repo.getTodaySong() != null }.getOrDefault(false)
        val streak = runCatching { repo.calculateStreak() }.getOrDefault(0)
        provideContent { TodaySongContent(done, streak) }
    }
}

class TodaySongWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodaySongWidget()
}

@androidx.compose.runtime.Composable
private fun TodaySongContent(done: Boolean, streak: Int) {
    val context = LocalContext.current
    val openSongs = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
            // SINGLE_TOP 是關鍵：App 已經在跑時，少了它 Android 只會把既有 task 帶到前景、
            // 不會呼叫 onNewIntent，於是通知點進去停在你離開前那一頁，跟通知內容無關。
            Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra("navTarget", "songs")
    }
    Column(
        modifier = GlanceModifier.fillMaxSize().background(WidgetColors.surface).cornerRadius(16.dp).padding(14.dp)
            .clickable(actionStartActivity(openSongs))
    ) {
        Text("今日選歌", style = TextStyle(color = WidgetColors.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold))
        Spacer(GlanceModifier.defaultWeight())
        if (done) {
            Text("今天已記錄", style = TextStyle(color = WidgetColors.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.height(4.dp))
            Text(if (streak > 0) "連續 $streak 天" else "保持下去", maxLines = 1,
                style = TextStyle(color = WidgetColors.onSurfaceVariant, fontSize = 12.sp))
        } else {
            Text("記錄今天的一首歌", style = TextStyle(color = WidgetColors.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.height(4.dp))
            Text(if (streak > 0) "別讓連續 $streak 天斷掉" else "點一下開始", maxLines = 1,
                style = TextStyle(color = WidgetColors.onSurfaceVariant, fontSize = 12.sp))
        }
    }
}
