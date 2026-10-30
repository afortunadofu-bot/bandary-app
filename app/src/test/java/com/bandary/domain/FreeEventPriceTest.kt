package com.bandary.domain

import com.bandary.domain.model.FreeEvent
import com.bandary.domain.model.countryOrDefault
import com.bandary.domain.model.priceLabel
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FreeEventPriceTest {

    private fun ev(
        price: Double? = null,
        min: Double? = null,
        max: Double? = null,
        text: String? = null,
        country: String? = null
    ) = FreeEvent(
        name = "x",
        dateTime = LocalDateTime(2026, 9, 23, 19, 0),
        ticketPrice = price, minPrice = min, maxPrice = max, priceText = text, country = country
    )

    @Test
    fun `兩端都有就給區間`() {
        assertEquals("NT$400–NT$500", ev(min = 400.0, max = 500.0).priceLabel())
    }

    @Test
    fun `區間兩端相同時不寫成區間`() {
        assertEquals("NT$800", ev(min = 800.0, max = 800.0).priceLabel())
    }

    @Test
    fun `只有下限就寫起`() {
        assertEquals("NT$300 起", ev(min = 300.0).priceLabel())
    }

    @Test
    fun `沒有區間才退回自由文字`() {
        assertEquals("早鳥 400／現場 500", ev(text = "早鳥 400／現場 500").priceLabel())
    }

    @Test
    fun `連自由文字都沒有才退回單一金額`() {
        assertEquals("NT$1,800", ev(price = 1800.0).priceLabel())
    }

    @Test
    fun `免費回 null 交給呼叫端寫免費`() {
        assertNull(ev().priceLabel())
        assertNull(ev(price = 0.0).priceLabel())
    }

    @Test
    fun `國家沒填一律當台灣`() {
        assertEquals("台灣", ev().countryOrDefault())
        assertEquals("台灣", ev(country = "   ").countryOrDefault())
        assertEquals("日本", ev(country = "日本").countryOrDefault())
    }
}
