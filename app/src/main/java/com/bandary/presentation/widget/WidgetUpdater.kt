package com.bandary.presentation.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

/** 資料變動時刷新所有已放置的桌面小工具。呼叫端需自行包 runCatching。 */
object WidgetUpdater {
    suspend fun updateAll(context: Context) {
        runCatching { UpcomingListWidget().updateAll(context) }
        runCatching { CountdownWidget().updateAll(context) }
        runCatching { MonthCalendarWidget().updateAll(context) }
        runCatching { TicketSaleWidget().updateAll(context) }
    }
}
