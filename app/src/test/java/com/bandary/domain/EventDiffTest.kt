package com.bandary.domain

import com.bandary.domain.model.EventDiff
import com.bandary.domain.model.FreeEvent
import com.bandary.domain.model.SaleTime
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventDiffTest {

    private val base = FreeEvent(
        id = "e1",
        name = "夏日音樂祭",
        artistName = "告五人",
        venue = "台北流行音樂中心",
        city = "臺北",
        dateTime = LocalDateTime(2026, 9, 23, 19, 0)
    )

    @Test
    fun `沒改就沒有任何變更`() {
        assertTrue(EventDiff.diff(base, base.copy()).isEmpty())
    }

    @Test
    fun `只列真的變了的欄位`() {
        val after = base.copy(venue = "高雄流行音樂中心", city = "高雄")
        val changes = EventDiff.diff(base, after)
        assertEquals(listOf("場地", "縣市"), changes.map { it.label })
        assertEquals("台北流行音樂中心", changes[0].before)
        assertEquals("高雄流行音樂中心", changes[0].after)
    }

    @Test
    fun `流程欄位不算內容變更`() {
        // status / reviewComment / createdBy / editOfId 改了不該出現在修改紀錄裡。
        val after = base.copy(
            status = "pending",
            reviewComment = "資料不足",
            createdBy = "someone@example.com",
            editOfId = "e1"
        )
        assertTrue(EventDiff.diff(base, after).isEmpty())
    }

    @Test
    fun `圖片不算內容變更`() {
        assertTrue(EventDiff.diff(base, base.copy(imageUris = listOf("AAAA"), imageUri = "AAAA")).isEmpty())
    }

    @Test
    fun `票價四個欄位合成一行`() {
        val after = base.copy(ticketPrice = 500.0, minPrice = 400.0, maxPrice = 500.0)
        val changes = EventDiff.diff(base, after)
        assertEquals(1, changes.size)
        assertEquals("票價", changes[0].label)
        assertEquals("免費", changes[0].before)
        assertEquals("NT$400–NT$500", changes[0].after)
    }

    @Test
    fun `國家沒填一律當台灣 所以填上台灣不算改動`() {
        assertTrue(EventDiff.diff(base, base.copy(country = "台灣")).isEmpty())
        assertEquals(1, EventDiff.diff(base, base.copy(country = "日本")).size)
    }

    @Test
    fun `開賣時間有排序 順序不同不算改動`() {
        val a = LocalDateTime(2026, 8, 1, 12, 0)
        val b = LocalDateTime(2026, 8, 15, 12, 0)
        val x = base.copy(saleTimes = listOf(SaleTime("會員", a), SaleTime("一般", b)))
        val y = base.copy(saleTimes = listOf(SaleTime("一般", b), SaleTime("會員", a)))
        assertTrue(EventDiff.diff(x, y).isEmpty())
    }

    @Test
    fun `摘要在兩項以內列全部 超過就收斂`() {
        val two = EventDiff.diff(base, base.copy(name = "秋日音樂祭", venue = "Legacy"))
        assertEquals("改了 名稱、場地", EventDiff.summarize(two))

        val three = EventDiff.diff(base, base.copy(name = "秋日", venue = "Legacy", city = "高雄"))
        assertEquals("改了 名稱、場地 等 3 項", EventDiff.summarize(three))

        assertEquals("沒有變更", EventDiff.summarize(emptyList()))
    }

    @Test
    fun `空白值在畫面上寫成空白而不是空字串`() {
        val changes = EventDiff.diff(base.copy(note = "原本的備註"), base.copy(note = ""))
        assertEquals("（空白）", changes.single().afterText())
        assertEquals("原本的備註", changes.single().beforeText())
    }

    @Test
    fun `快照不含圖片欄位`() {
        val stripped = EventDiff.stripImages(mapOf("name" to "x", "imageUri" to "A", "imageUris" to listOf("A")))
        assertEquals(setOf("name"), stripped.keys)
    }
}
