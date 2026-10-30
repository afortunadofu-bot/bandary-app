package com.bandary.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「即將到來 / 歷史紀錄」的分流。
 *
 * 實機回報：「活動結束後補填結束時間，但活動仍然停留在『即將到來』分頁，
 * 不知道怎麼讓它移去『歷史紀錄』。」原因是分頁用「本月第一天」判定，
 * 跟使用者填的任何欄位都無關——他真的沒辦法，只能等月底換月。
 */
class ConcertEndedTest {

    private fun concert(
        date: LocalDate,
        start: LocalTime = LocalTime(19, 0),
        endTime: LocalTime? = null,
        endDate: LocalDate? = null,
        extraDates: List<LocalDate> = emptyList()
    ) = Concert(
        id = "t",
        name = "測試",
        artistName = "測試",
        dateTime = LocalDateTime(date, start),
        endTime = endTime,
        endDate = endDate,
        extraDates = extraDates
    )

    private val now = LocalDateTime(LocalDate(2026, 10, 12), LocalTime(14, 0))

    @Test
    fun `補填過去的結束時間 就會變成歷史`() {
        // 這就是回報的情境：今天早上的場次，事後補填 12:00 結束。
        val c = concert(LocalDate(2026, 10, 12), start = LocalTime(10, 0), endTime = LocalTime(12, 0))
        assertTrue("補填了過去的結束時間，應該算結束", c.hasEnded(now))
    }

    @Test
    fun `本月已經過去的場次算歷史 不用等換月`() {
        // 舊的判定（dateTime >= 本月第一天）會把這場留在「即將到來」直到 11 月。
        val c = concert(LocalDate(2026, 10, 3))
        assertTrue("10/3 的場次在 10/12 應該是歷史", c.hasEnded(now))
    }

    @Test
    fun `今天的場次 沒填結束時間就整天都還在即將到來`() {
        // 刻意不猜「開演後三小時」：猜短了會讓人在現場看到自己的演出被歸成歷史。
        val c = concert(LocalDate(2026, 10, 12), start = LocalTime(10, 0))
        assertFalse("今天的場次沒填結束時間，今天內不該算結束", c.hasEnded(now))
        assertEquals(LocalTime(23, 59, 59), c.endMoment().time)
    }

    @Test
    fun `今天還沒開演的當然沒結束`() {
        val c = concert(LocalDate(2026, 10, 12), start = LocalTime(19, 0), endTime = LocalTime(22, 0))
        assertFalse(c.hasEnded(now))
    }

    @Test
    fun `未來的場次沒結束`() {
        assertTrue(concert(LocalDate(2026, 12, 1)).hasEnded(now).not())
        assertTrue(concert(LocalDate(2027, 1, 1)).hasEnded(now).not())
    }

    @Test
    fun `連續多日活動 要演到最後一天才算結束`() {
        // 10/10 開始、10/15 結束：第一天過了不代表活動結束。
        val c = concert(LocalDate(2026, 10, 10), endDate = LocalDate(2026, 10, 15))
        assertFalse("還在進行中的多日活動不該算結束", c.hasEnded(now))
        assertEquals(LocalDate(2026, 10, 15), c.endMoment().date)
    }

    @Test
    fun `非連續巡演 要到最後一場才算結束`() {
        // 巡演 10/1、10/20：第一場過了，但整個巡演還沒完。
        val c = concert(LocalDate(2026, 10, 1), extraDates = listOf(LocalDate(2026, 10, 20)))
        assertFalse("巡演還有場次，不該算結束", c.hasEnded(now))
        assertEquals(LocalDate(2026, 10, 20), c.endMoment().date)
    }

    @Test
    fun `多日活動全部結束才算歷史`() {
        val c = concert(LocalDate(2026, 9, 1), endDate = LocalDate(2026, 9, 5))
        assertTrue(c.hasEnded(now))
    }

    @Test
    fun `結束時間跟著最後一天 不是開始那天`() {
        // endTime 是「最後一天的幾點結束」，不該被套到第一天去。
        val c = concert(
            LocalDate(2026, 10, 10),
            endDate = LocalDate(2026, 10, 15),
            endTime = LocalTime(22, 0)
        )
        assertEquals(LocalDateTime(LocalDate(2026, 10, 15), LocalTime(22, 0)), c.endMoment())
    }
}
