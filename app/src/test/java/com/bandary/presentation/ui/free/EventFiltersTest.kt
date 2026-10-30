package com.bandary.presentation.ui.free

import com.bandary.domain.model.FreeEvent
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventFiltersTest {

    private fun ev(
        city: String? = null, venue: String = "",
        price: Double? = null, min: Double? = null, max: Double? = null
    ) = FreeEvent(
        id = "e", venue = venue, city = city,
        dateTime = LocalDateTime(2026, 9, 5, 19, 0),
        ticketPrice = price, minPrice = min, maxPrice = max
    )

    @Test fun `縣市欄位優先，寫法正規化`() {
        assertEquals("臺北", eventCity(ev(city = "台北市", venue = "高雄駁二")))
    }

    @Test fun `沒填縣市時從場地名稱推`() {
        assertEquals("高雄", eventCity(ev(venue = "高雄流行音樂中心")))
        assertEquals("臺中", eventCity(ev(venue = "台中圓滿戶外劇場")))
        assertEquals("臺北", eventCity(ev(venue = "新北投公園")))
        assertEquals("連江", eventCity(ev(venue = "馬祖南竿")))
        assertNull(eventCity(ev(venue = "Legacy")))
    }

    @Test fun `免費跟卡片的免費標籤同一套規則`() {
        assertTrue(PriceBucket.FREE.matches(ev(price = null)))
        assertTrue(PriceBucket.FREE.matches(ev(price = 0.0)))
        assertFalse(PriceBucket.FREE.matches(ev(price = 300.0)))
        assertFalse(PriceBucket.UNDER_500.matches(ev(price = 0.0)))
    }

    @Test fun `單一票價落在剛好一格`() {
        val e = ev(price = 500.0)
        assertEquals(listOf(PriceBucket.UNDER_500), PriceBucket.entries.filter { it.matches(e) })
        val f = ev(price = 3500.0)
        assertEquals(listOf(PriceBucket.OVER_3000), PriceBucket.entries.filter { it.matches(f) })
    }

    @Test fun `票價區間跨格時每一格都算`() {
        val e = ev(price = 400.0, min = 400.0, max = 1200.0)
        assertEquals(
            listOf(PriceBucket.UNDER_500, PriceBucket.TO_1000, PriceBucket.TO_2000),
            PriceBucket.entries.filter { it.matches(e) }
        )
    }

    @Test fun `自訂上下限兩端都含`() {
        assertTrue(matchesCustomPrice(ev(price = 800.0), 800.0, 1500.0))
        assertTrue(matchesCustomPrice(ev(price = 1500.0), 800.0, 1500.0))
        assertFalse(matchesCustomPrice(ev(price = 1600.0), 800.0, 1500.0))
        // 票價區間只要跟自訂區間重疊就算
        assertTrue(matchesCustomPrice(ev(price = 400.0, min = 400.0, max = 900.0), 800.0, 1500.0))
    }

    @Test fun `只填一端、免費當作 0 元`() {
        assertTrue(matchesCustomPrice(ev(price = 5000.0), 3000.0, null))
        assertFalse(matchesCustomPrice(ev(price = null), 3000.0, null))
        assertTrue(matchesCustomPrice(ev(price = null), null, 500.0))
    }

    @Test fun `上下限打反會自動對調`() {
        assertTrue(matchesCustomPrice(ev(price = 1000.0), 2000.0, 500.0))
        assertEquals("NT$500–2000", customPriceLabel(2000.0, 500.0))
        assertEquals("NT$3000 以上", customPriceLabel(3000.0, null))
    }
}
