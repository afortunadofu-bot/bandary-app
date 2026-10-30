package com.bandary.domain

import com.bandary.data.util.SharedTicketText
import com.bandary.data.util.TicketStubParser
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「從 Gmail 分享一封訂票確認信進來」這條路。
 *
 * 用的是與票根辨識**完全相同**的解析器——OCR 的輸出本來就是「一行一行的文字」，
 * 信件內文也是，兩邊唯一的差別是文字從哪來。這組測試要證明的就是那件事：
 * 真實信件的版面（主旨在最前面、標籤與值同一行、表格被攤平、夾雜客服廢話）
 * 餵進去也讀得出東西。
 *
 * 測資是照著常見售票平台的確認信謄的，不是真實個人資料。
 */
class SharedTicketEmailTest {

    /** 分享進來的樣子：主旨放第一行（MainActivity 會把 SUBJECT 併到 TEXT 前面）。 */
    private fun parse(raw: String) =
        TicketStubParser.parse(raw.lines().map { it.trim() }.filter { it.isNotBlank() })

    private val kktixMail = """
        【購票成功通知】告五人 2026 巡迴演唱會 台北場
        親愛的顧客您好，感謝您於 KKTIX 購票，以下是您的訂單資訊：
        活動名稱：告五人 2026 巡迴演唱會
        演出時間：2026/08/15 19:30
        活動地點：台北流行音樂中心
        座位：2樓 B區 15排 8號
        票價：NT$ 2800
        此為系統自動發送信件，請勿直接回覆。
        若有任何問題請洽客服信箱。
    """.trimIndent()

    @Test
    fun `KKTIX 確認信讀得出主要欄位`() {
        val s = parse(kktixMail)
        assertEquals("KKTIX", s.platform)
        assertEquals("告五人 2026 巡迴演唱會", s.showName)
        assertEquals("台北流行音樂中心", s.venue)
        assertEquals(LocalDate(2026, 8, 15), s.date)
        assertEquals(LocalTime(19, 30), s.time)
        assertTrue("票價應該讀得到，實際 ${s.price}", (s.price ?: 0.0) > 0.0)
    }

    @Test
    fun `客服廢話不會被當成活動名稱`() {
        // 信件尾巴那幾句「請勿直接回覆」「若有任何問題」是每封都有的雜訊。
        val s = parse(kktixMail)
        assertTrue(
            "活動名稱被雜訊汙染了：${s.showName}",
            s.showName?.contains("回覆") != true && s.showName?.contains("客服") != true
        )
    }

    @Test
    fun `拓元的日期寫法也讀得到`() {
        val s = parse(
            """
            拓元 tixCraft 訂單確認
            節目名稱：某某樂團 LIVE TOUR
            演出日期：2026-11-02 (日) 18:00
            地點：Legacy Taipei
            """.trimIndent()
        )
        assertEquals("拓元 tixCraft", s.platform)
        assertEquals(LocalDate(2026, 11, 2), s.date)
        assertEquals("Legacy Taipei", s.venue)
    }

    @Test
    fun `完全無關的文字不會硬湊出欄位`() {
        // 使用者可能誤分享一封廣告信。解不出東西要老實回空，
        // 讓審閱視窗退回逐行挑選，而不是填一堆猜的進表單。
        val s = parse(
            """
            本週最新優惠
            滿千折百，再享免運
            立即前往購物
            """.trimIndent()
        )
        assertEquals("不該猜出活動名稱", null, s.showName)
        assertNull("不該猜出場地", s.venue)
        assertNull("不該猜出日期", s.date)
        assertEquals("沒讀到任何欄位時 filledCount 應為 0", 0, s.filledCount)
    }

    @Test
    fun `空白或只有空行時不炸掉`() {
        assertEquals(0, parse("").filledCount)
        assertEquals(0, parse("\n\n   \n").filledCount)
    }

    @Test
    fun `暫存取過一次就清空`() {
        // 不清的話，轉螢幕或返回重進表單會再套用一次。
        SharedTicketText.set("訂單確認：某場演出")
        assertNotNull(SharedTicketText.take())
        assertNull("第二次就該是空的", SharedTicketText.take())
    }

    @Test
    fun `空白內容不會被存起來`() {
        SharedTicketText.set("   ")
        assertNull(SharedTicketText.take())
        SharedTicketText.set(null)
        assertNull(SharedTicketText.take())
    }
}
