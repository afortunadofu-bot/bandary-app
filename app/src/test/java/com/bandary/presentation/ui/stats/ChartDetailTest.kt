package com.bandary.presentation.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 圖表細節面板的解讀計算。
 *
 * 這一組直接對應 v1.53.0 修過的一個 bug：名次原本用「在清單裡的索引」算，
 * 但各年份場次是照年份排序、不是照場次數，於是「第 3 個年份」會被講成「第 3 名」。
 * 這種錯不會當掉、不會報錯，只會給出**看起來很合理但是錯的資訊**，
 * 人工測試幾乎不可能發現——正是最該用測試鎖住的東西。
 */
class ChartDetailTest {

    private fun factOf(d: ChartDetail?, key: String): String? =
        d?.facts?.firstOrNull { it.first == key }?.second

    // ── 名次一定要從數值算，不能用清單位置 ──

    @Test fun `名次依數值計算而不是清單順序`() {
        // 刻意照「年份」排序（不是照場次數），模擬 yearlyCounts 的實際情形。
        val data = listOf("2024" to 2, "2025" to 10, "2026" to 5)
        // 2026 是清單第 3 筆，但場次數第 2 多 → 必須是第 2 名。
        assertEquals("第 2 名／共 3 項", factOf(chartDetailOf("2026", data, " 場", null), "排名"))
        assertEquals("第 1 名／共 3 項", factOf(chartDetailOf("2025", data, " 場", null), "排名"))
        assertEquals("第 3 名／共 3 項", factOf(chartDetailOf("2024", data, " 場", null), "排名"))
    }

    @Test fun `同分並列同名次`() {
        val data = listOf("A" to 5, "B" to 5, "C" to 1)
        assertEquals("第 1 名／共 3 項", factOf(chartDetailOf("A", data, " 場", null), "排名"))
        assertEquals("第 1 名／共 3 項", factOf(chartDetailOf("B", data, " 場", null), "排名"))
        // 兩個並列第 1，下一個是第 3（不是第 2）
        assertEquals("第 3 名／共 3 項", factOf(chartDetailOf("C", data, " 場", null), "排名"))
    }

    // ── 領先第二名 ──

    @Test fun `第一名會講領先第二名多少`() {
        val data = listOf("A" to 10, "B" to 4)
        assertEquals("比第二名多 6 場", factOf(chartDetailOf("A", data, " 場", null), "領先"))
    }

    /**
     * 同分並列第 1 時，「第二名」要取第一個比自己小的值。
     * 用清單第 2 筆的話會拿到另一個並列第一名，算出「領先 0 場」這種沒意義的話。
     */
    @Test fun `並列第一名時領先要跳過同分的人`() {
        val data = listOf("A" to 5, "B" to 5, "C" to 2)
        assertEquals("比第二名多 3 場", factOf(chartDetailOf("A", data, " 場", null), "領先"))
    }

    @Test fun `只有一項時沒有領先這一列`() {
        val d = chartDetailOf("A", listOf("A" to 3), " 場", null)
        assertNull(factOf(d, "領先"))
    }

    @Test fun `非第一名會講與第一名的差距`() {
        val data = listOf("A" to 10, "B" to 4)
        assertEquals("少 6 場", factOf(chartDetailOf("B", data, " 場", null), "與第一名"))
    }

    // ── 佔比 ──

    @Test fun `佔比會附上母數`() {
        val data = listOf("A" to 6, "B" to 4)
        assertEquals("60%（共 10 場）", factOf(chartDetailOf("A", data, " 場", null), "佔比"))
    }

    // ── 與平均 ──

    @Test fun `剛好等於平均時講剛好是平均`() {
        val data = listOf("A" to 5, "B" to 5)
        assertEquals("剛好是平均", factOf(chartDetailOf("A", data, " 場", null), "與平均"))
    }

    @Test fun `高於與低於平均`() {
        val data = listOf("A" to 8, "B" to 2)   // 平均 5
        assertEquals("高於平均 3.0 場", factOf(chartDetailOf("A", data, " 場", null), "與平均"))
        assertEquals("低於平均 3.0 場", factOf(chartDetailOf("B", data, " 場", null), "與平均"))
    }

    // ── 邊界 ──

    @Test fun `找不到該項目回傳 null`() {
        assertNull(chartDetailOf("不存在", listOf("A" to 1), " 場", null))
    }

    @Test fun `全部為零時不會除以零`() {
        val d = chartDetailOf("A", listOf("A" to 0, "B" to 0), " 場", null)
        assertTrue(d != null)
        // total 會被 coerceAtLeast(1) 保護住，不會丟 ArithmeticException
        assertEquals("0%（共 1 場）", factOf(d, "佔比"))
    }

    @Test fun `數值文字帶單位`() {
        assertEquals("6 場", chartDetailOf("A", listOf("A" to 6), " 場", null)?.valueText)
    }

    @Test fun `drillCount 等於該項數值`() {
        assertEquals(6, chartDetailOf("A", listOf("A" to 6), " 場", null)?.drillCount)
    }

    // ── 分類顯示 ──

    @Test fun `全部分類一律顯示`() {
        assertTrue(showStat(STAT_CAT_ALL, STAT_CAT_TREND))
        assertTrue(showStat(STAT_CAT_ALL, STAT_CAT_TICKET))
    }

    @Test fun `選定分類只顯示該類`() {
        assertTrue(showStat(STAT_CAT_TREND, STAT_CAT_TREND))
        assertEquals(false, showStat(STAT_CAT_TREND, STAT_CAT_ARTIST))
    }

    // ── 距離表 ──

    @Test fun `縣市有無後綴都查得到同一個距離`() {
        assertEquals(distanceFromTaipei("臺北"), distanceFromTaipei("臺北市"))
        assertEquals(distanceFromTaipei("高雄"), distanceFromTaipei("高雄市"))
        assertEquals(distanceFromTaipei("新竹"), distanceFromTaipei("新竹縣"))
    }

    @Test fun `沒收錄的地名回傳中間值而不是零`() {
        // 回 0 會讓「最遠抵達」把未知地名當成臺北，永遠選不到它。
        assertEquals(100, distanceFromTaipei("東京"))
        assertEquals(100, distanceFromTaipei(""))
    }
}
