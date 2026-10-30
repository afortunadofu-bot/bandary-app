package com.bandary.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 使用者的演出紀錄自動變成公開活動時，**票價不可以照抄**。
 *
 * 使用者填的是「我這張花了多少」——那是一個票種、一個座位區的價格，
 * 不代表這場只有那一個價位。寫錯的票價比沒有票價糟得多：
 * 沒票價的人會自己去售票頁查，看到錯票價的人則會直接相信。
 */
class EventSubmissionPriceTest {

    private fun concert(price: Double?) = Concert(
        id = "c1",
        name = "某某巡迴演唱會",
        artistName = "某某樂團",
        dateTime = LocalDateTime(LocalDate(2026, 8, 15), LocalTime(19, 30)),
        venue = "台北流行音樂中心",
        ticketPrice = price
    )

    @Test
    fun `付費場次不寫死金額`() {
        // 2800 是他買的那張，不是這場的票價。
        val e = concert(2800.0).toEventSubmission()!!
        assertEquals("應標記成付費但金額未知（-1.0）", -1.0, e.ticketPrice!!, 0.001)
        assertFalse("不該把 2800 寫進去", e.ticketPrice == 2800.0)
    }

    @Test
    fun `免費場次維持 null`() {
        // FreeEvent 裡 null 與 0 都代表免費，是既有慣例；
        // 不要在這裡多製造一種寫法。
        assertNull(concert(0.0).toEventSubmission()!!.ticketPrice)
    }

    @Test
    fun `沒填票價就是沒有`() {
        assertNull(concert(null).toEventSubmission()!!.ticketPrice)
    }

    @Test
    fun `不會順手編出價格區間`() {
        // 一張票推不出區間。minPrice / maxPrice 要留給管理員或爬蟲補。
        val e = concert(2800.0).toEventSubmission()!!
        assertNull(e.minPrice)
        assertNull(e.maxPrice)
        assertNull(e.priceText)
    }

    @Test
    fun `其他欄位照樣帶過去`() {
        // 只擋票價，不要連帶把能用的資訊也丟掉。
        val e = concert(2800.0).toEventSubmission()!!
        assertEquals("某某巡迴演唱會", e.name)
        assertEquals("台北流行音樂中心", e.venue)
        assertEquals(LocalDate(2026, 8, 15), e.dateTime.date)
    }

    // ── 來源判斷：審核要分開 ────────────────────────────────
    private fun event(createdBy: String?) = FreeEvent(
        id = "e1",
        name = "某活動",
        dateTime = LocalDateTime(LocalDate(2026, 8, 15), LocalTime(19, 0)),
        createdBy = createdBy
    )

    @Test
    fun `爬蟲寫入的認得出來`() {
        // functions/crawl.js 固定寫 "crawler:<來源名稱>"。
        assertTrue(event("crawler:KKTIX").isFromCrawler())
        assertTrue(event("crawler:https://example.com/events").isFromCrawler())
    }

    @Test
    fun `使用者投稿不是爬蟲`() {
        assertFalse(event("someone@example.com").isFromCrawler())
        assertFalse("沒有投稿者也算使用者端，不要塞進爬蟲那一堆", event(null).isFromCrawler())
        assertFalse(event("").isFromCrawler())
    }

    @Test
    fun `名字裡剛好有 crawler 的信箱不會被誤判`() {
        // 比的是前綴而不是「包含」，所以這個不會中。
        assertFalse(event("web.crawler.fan@example.com").isFromCrawler())
    }
}
