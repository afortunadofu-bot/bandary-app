package com.bandary.domain.model

import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** 入場提醒通知文字：實機回報「演出在一週後，通知卻寫 1 週前」。 */
class ReminderMessageTest {

    private val show = LocalDateTime(2026, 9, 23, 19, 30)   // 週三

    @Test
    fun `通知寫倒數而不是設定選項的字`() {
        assertEquals(
            "還有 1 週開演・9/23（三）19:30・秋分・Legacy Taipei",
            entryReminderMessage(ReminderOffset.ONE_WEEK.countdown, show, "秋分", "Legacy Taipei")
        )
    }

    @Test
    fun `沒有活動名稱時不留空欄位`() {
        assertEquals(
            "再 30 分鐘開演・9/23（三）19:30・Zepp",
            entryReminderMessage(ReminderOffset.THIRTY_MIN.countdown, show, "", "Zepp")
        )
    }

    @Test
    fun `舊版已排程的通知文字發送時轉成倒數`() {
        assertEquals(
            "秋分・還有 1 週開演・Legacy Taipei",
            com.bandary.data.worker.fixLegacyReminderText("秋分 1 週前・Legacy Taipei")
        )
        // 新格式不受影響
        val fresh = "還有 1 週開演・9/23（三）19:30・秋分・Legacy Taipei"
        assertEquals(fresh, com.bandary.data.worker.fixLegacyReminderText(fresh))
    }

    @Test
    fun `自訂提醒的倒數`() {
        assertEquals("再 45 分鐘開演", reminderCountdown(45))
        assertEquals("再 3 小時開演", reminderCountdown(180))
        assertEquals("還有 2 天開演", reminderCountdown(2880))
    }
}
