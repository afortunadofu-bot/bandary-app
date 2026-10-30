package com.bandary.data

import com.bandary.data.util.CalendarTitleParser
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 共編日曆的標題寫法就那幾種，但拆錯的代價很高：藝人欄一旦寫進整串標題，
 * 統計、追蹤藝人、好友日曆全部跟著髒掉。所以這裡把「該拆」與「不該亂猜」都釘住。
 */
class CalendarTitleParserTest {

    @Test
    fun `藝人 @ 場地`() {
        val p = CalendarTitleParser.parse("五月天 @ 台北小巨蛋", null)
        assertEquals("五月天", p.artist)
        assertEquals("台北小巨蛋", p.venue)
        assertEquals("臺北", p.city)
    }

    @Test
    fun `藝人 - 活動名`() {
        val p = CalendarTitleParser.parse("告五人 - 帶你飛巡迴", null)
        assertEquals("告五人", p.artist)
        assertEquals("帶你飛巡迴", p.name)
    }

    @Test
    fun `方括號活動名在前`() {
        val p = CalendarTitleParser.parse("【秋分：完美的平衡】南西肯恩", null)
        assertEquals("秋分：完美的平衡", p.name)
        assertEquals("南西肯恩", p.artist)
    }

    @Test
    fun `拆不出來就整串當活動名，藝人留空`() {
        val p = CalendarTitleParser.parse("這是一個沒有格式的活動名稱", null)
        assertEquals("這是一個沒有格式的活動名稱", p.name)
        assertEquals("", p.artist)
    }

    @Test
    fun `地點欄比標題可靠，會覆蓋標題拆出來的場地`() {
        val p = CalendarTitleParser.parse("某某樂團 @ 某處", "Legacy Taipei, 100台北市中正區八德路一段1號")
        assertEquals("Legacy Taipei", p.venue)
        assertEquals("臺北", p.city)
    }

    @Test
    fun `地點只有地址時不要切壞`() {
        val p = CalendarTitleParser.parse("某某樂團", "802高雄市苓雅區四維三路21號")
        assertEquals("802高雄市苓雅區四維三路21號", p.venue)
        assertEquals("高雄", p.city)
    }

    @Test
    fun `台與臺兩種寫法都認得，統一回臺`() {
        assertEquals("臺中", CalendarTitleParser.cityOf(listOf("台中市西屯區")))
        assertEquals("臺中", CalendarTitleParser.cityOf(listOf("臺中國家歌劇院")))
    }

    @Test
    fun `找不到縣市就留空，不亂猜`() {
        assertEquals("", CalendarTitleParser.cityOf(listOf("東京ドーム", "")))
    }
}
