package com.bandary.domain

import com.bandary.domain.model.FunnelStep
import com.bandary.domain.model.concentration
import com.bandary.domain.model.formatPercent
import com.bandary.domain.model.momGrowth
import com.bandary.domain.model.rankArtists
import com.bandary.domain.model.artistNamesForStats
import com.bandary.domain.model.trendSlope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminAnalyticsTest {

    // ── 卡司字串 ──────────────────────────────────────────────
    // 實機回報「掃描出來都是同一個藝人」的根因：整份卡司被當成一位藝人。
    // 拆分規則沿用既有的 splitArtistNamesPasted，這裡驗的是「後台拿到的結果」。

    @Test
    fun `整份卡司會拆成個別藝人`() {
        val raw = "#Mooove! / THE PINK MINDS / シークレット花言葉 / 幽夜 / MUSUBI"
        val names = artistNamesForStats(raw)
        assertEquals(5, names.size)
        assertTrue("#Mooove!" in names)
        assertTrue("MUSUBI" in names)
    }

    @Test
    fun `頓號與全形豎線都算分隔`() {
        assertEquals(3, artistNamesForStats("A、B｜C").size)
    }

    @Test
    fun `團名自己帶的單一空格不會被拆開`() {
        // 既有規則刻意不拿單一半形空格當分隔，因為很多團名本身就有空格。
        assertEquals(listOf("The Dinoaur'y skin"), artistNamesForStats("The Dinoaur'y skin"))
    }

    @Test
    fun `過長又沒有分隔的髒資料會被丟掉而不是污染排行`() {
        val junk = "這是一段很長的描述文字".repeat(10)
        assertTrue(artistNamesForStats(junk).isEmpty())
    }

    @Test
    fun `空白與 null 回空清單`() {
        assertTrue(artistNamesForStats(null).isEmpty())
        assertTrue(artistNamesForStats("   ").isEmpty())
    }

    @Test
    fun `排行以個別藝人計次而不是整串卡司`() {
        val rows = listOf("五月天 / 告五人", "告五人", "五月天 / 蘇打綠")
        val top = rankArtists(rows)
        assertEquals(3, top.size)
        assertEquals(2, top.first { it.first == "五月天" }.second)
        assertEquals(2, top.first { it.first == "告五人" }.second)
        assertEquals(1, top.first { it.first == "蘇打綠" }.second)
    }

    // ── 漏斗 ─────────────────────────────────────────────────

    @Test
    fun `漏斗的階段轉換率與整體轉換率分開算`() {
        val step = FunnelStep("記過一場", 30)
        assertEquals(0.5, step.stepRate(60), 0.0001)     // 相對上一階
        assertEquals(0.3, step.overallRate(100), 0.0001)  // 相對第一階
    }

    @Test
    fun `上一階是零時不會除以零`() {
        assertEquals(0.0, FunnelStep("x", 5).stepRate(0), 0.0001)
        assertEquals(0.0, FunnelStep("x", 5).overallRate(0), 0.0001)
    }

    // ── 集中度 ───────────────────────────────────────────────

    @Test
    fun `前三名集中度反映對少數人的依賴`() {
        val ranking = listOf("a" to 50, "b" to 30, "c" to 10, "d" to 5, "e" to 5)
        assertEquals(0.9, concentration(ranking, 3), 0.0001)
    }

    @Test
    fun `空清單的集中度是零而不是崩潰`() {
        assertEquals(0.0, concentration(emptyList()), 0.0001)
    }

    // ── 趨勢 ─────────────────────────────────────────────────

    @Test
    fun `資料不足兩期時環比回 null 而不是零`() {
        assertNull(momGrowth(emptyList()))
        assertNull(momGrowth(listOf("2026/01" to 5)))
    }

    @Test
    fun `前一期為零時環比回 null`() {
        assertNull(momGrowth(listOf("2026/01" to 0, "2026/02" to 7)))
    }

    @Test
    fun `環比算得出上升與下降`() {
        assertEquals(0.5, momGrowth(listOf("a" to 10, "b" to 15))!!, 0.0001)
        assertEquals(-0.2, momGrowth(listOf("a" to 10, "b" to 8))!!, 0.0001)
    }

    @Test
    fun `趨勢斜率看的是整段走勢不是頭尾兩點`() {
        // 整體向上，但最後一期掉下來——只看頭尾會誤判成持平。
        val rising = listOf("a" to 1, "b" to 3, "c" to 5, "d" to 7, "e" to 4)
        assertTrue(trendSlope(rising) > 0)
        val flat = listOf("a" to 5, "b" to 5, "c" to 5)
        assertEquals(0.0, trendSlope(flat), 0.0001)
    }

    // ── 百分比字串 ────────────────────────────────────────────

    @Test
    fun `百分比在小數字時保留一位小數`() {
        assertEquals("1.2%", formatPercent(0.0123))
        assertEquals("12%", formatPercent(0.1234))
        assertEquals("0%", formatPercent(0.0))
    }

    @Test
    fun `百分比遇到不是數字時給破折號而不是 NaN`() {
        assertEquals("—", formatPercent(Double.NaN))
        assertEquals("—", formatPercent(Double.POSITIVE_INFINITY))
    }
}
