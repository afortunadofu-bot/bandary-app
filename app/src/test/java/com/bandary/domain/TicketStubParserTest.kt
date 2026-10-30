package com.bandary.domain

import com.bandary.data.util.TicketStubParser
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 票根解讀。測資是照著 ibon 購票證明單 / FamiPort 購票單 / 拓元實體票的版面謄成
 * 「OCR 會吐出來的樣子」——一行一個文字方塊、標籤與值常常被拆成兩行、有全形字。
 */
class TicketStubParserTest {

    // ── ibon 購票證明單（7-11） ─────────────────────────────────────────────
    private val ibon = listOf(
        "ibon 售票系統",
        "購票證明單",
        "代收項目",
        "告五人 2026 巡迴演唱會",
        "演出時間",
        "2026/09/23 19:30",
        "演出地點",
        "台北小巨蛋, 台北市南京東路四段2號",
        "座位",
        "B區 12排 34號",
        "票價",
        "NT$1,800",
        "交易序號",
        "A8K3M9P2",
        "訂單編號",
        "TX20260901123",
        "繳費期限 2026/09/05 23:59"
    )

    @Test
    fun `ibon 購票證明單讀得出全部欄位`() {
        val s = TicketStubParser.parse(ibon)
        assertEquals("ibon 售票系統", s.platform)
        assertEquals("7-11 ibon", s.machine)
        assertEquals("告五人 2026 巡迴演唱會", s.showName)
        assertEquals("台北小巨蛋", s.venue)
        assertEquals("臺北", s.city)
        assertEquals(LocalDate(2026, 9, 23), s.date)
        assertEquals(LocalTime(19, 30), s.time)
        assertEquals("B區12排34號", s.seat)
        assertEquals(1800.0, s.price!!, 0.001)
        assertEquals("A8K3M9P2", s.pickupNumber)
        assertEquals("TX20260901123", s.pickupTicketNo)
    }

    @Test
    fun `繳費期限不會被當成演出日期`() {
        // 繳費期限 9-05 排在演出時間 9-23 之後；若沒避開陷阱就會讀成 9-05。
        assertEquals(LocalDate(2026, 9, 23), TicketStubParser.parse(ibon).date)
    }

    // ── 全家 FamiPort ───────────────────────────────────────────────────────
    @Test
    fun `FamiPort 購票單認得機台與欄位`() {
        val s = TicketStubParser.parse(
            listOf(
                "全家便利商店 FamiPort",
                "商品名稱：草東沒有派對 演唱會",
                "場次時間：2026-11-08 20:00",
                "地點：Zepp New Taipei",
                "金額：1500",
                "繳費序號：FP99231144"
            )
        )
        assertEquals("全家 FamiPort", s.machine)
        assertEquals("草東沒有派對 演唱會", s.showName)
        assertEquals(LocalDate(2026, 11, 8), s.date)
        assertEquals(LocalTime(20, 0), s.time)
        assertEquals("Zepp New Taipei", s.venue)
        assertEquals("FP99231144", s.pickupNumber)
    }

    // ── 拓元實體票 ──────────────────────────────────────────────────────────
    @Test
    fun `拓元票根的全形字與下午時制`() {
        val s = TicketStubParser.parse(
            listOf(
                "拓元售票系統 tixCraft",
                "節目名稱　落日飛車 Sunset Rollercoaster",
                "演出時間　２０２６年１２月３１日　下午７:００",
                "演出地點　高雄流行音樂中心",
                "票價　NT$2,200"
            )
        )
        assertEquals("拓元 tixCraft", s.platform)
        assertEquals("落日飛車 Sunset Rollercoaster", s.showName)
        assertEquals(LocalDate(2026, 12, 31), s.date)
        assertEquals(LocalTime(19, 0), s.time)
        assertEquals("高雄", s.city)
    }

    @Test
    fun `民國年也讀得出來`() {
        assertEquals(LocalDate(2026, 9, 23), TicketStubParser.findDate("演出日期 115/09/23"))
    }

    @Test
    fun `西元年不會被當成民國年`() {
        assertEquals(LocalDate(2026, 1, 5), TicketStubParser.findDate("2026/01/05"))
    }

    @Test
    fun `只有月日沒有年份就不猜`() {
        assertNull(TicketStubParser.findDate("9月23日 19:30"))
        assertEquals(LocalDate(2026, 9, 23), TicketStubParser.findDate("9月23日 19:30", thisYear = 2026))
    }

    @Test
    fun `身分證不會被當成取票序號`() {
        val s = TicketStubParser.parse(listOf("取票序號", "A123456789"))
        assertNull(s.pickupNumber)
    }

    @Test
    fun `不是票根的圖不會硬湊`() {
        val s = TicketStubParser.parse(listOf("五月天", "演唱會", "好想見到你"))
        assertEquals(0, s.filledCount)
    }

    @Test
    fun `空輸入不會爆`() {
        assertEquals(0, TicketStubParser.parse(emptyList()).filledCount)
        assertEquals(0, TicketStubParser.parse(listOf("", "   ")).filledCount)
    }

    @Test
    fun `標籤與值同一行或分兩行都讀得到`() {
        val inline = TicketStubParser.parse(listOf("演出地點: 台中國家歌劇院"))
        val split = TicketStubParser.parse(listOf("演出地點", "台中國家歌劇院"))
        assertEquals("台中國家歌劇院", inline.venue)
        assertEquals("台中國家歌劇院", split.venue)
    }

    @Test
    fun `票價取較大的合計而不是單價`() {
        val s = TicketStubParser.parse(listOf("單價 1200 張數 2 合計 2400"))
        assertEquals(2400.0, s.price!!, 0.001)
    }

    @Test
    fun `讀得到東西時 filledCount 會反映出來`() {
        assertTrue(TicketStubParser.parse(ibon).filledCount >= 6)
    }
}
