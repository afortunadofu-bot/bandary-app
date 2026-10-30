package com.bandary.data.billing

import org.junit.Assert.assertEquals
import org.junit.Test

/** 實機回報：台幣價格顯示成「$160.00」，看不出幣別。 */
class DisplayPriceTest {
    @Test
    fun `台幣整數去掉小數並標出幣別`() {
        assertEquals("NT$160", displayPrice("$160.00", 160_000_000L, "TWD"))
        assertEquals("NT$310", displayPrice("$310.00", 310_000_000L, "TWD"))
    }

    @Test
    fun `已經有幣別的字串不重複加`() {
        assertEquals("NT$160", displayPrice("NT$160.00", 160_000_000L, "TWD"))
    }

    @Test
    fun `有小數的金額與其他幣別照原樣`() {
        assertEquals("US$4.99", displayPrice("US$4.99", 4_990_000L, "USD"))
        assertEquals("$5", displayPrice("$5.00", 5_000_000L, "USD"))
    }
}
