package com.bandary.domain

import com.bandary.domain.model.FanPercentile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FanPercentileTest {

    @Test
    fun `看最多場的人 PR 最高`() {
        val counts = listOf(1, 1, 2, 3, 10)
        assertEquals(99, FanPercentile.of(10, counts))
    }

    @Test
    fun `同分算在自己這邊`() {
        // 五個人都看 1 場：如果只算「嚴格少於」，每個人的 PR 都會是 0，看起來像沒人理你。
        val counts = listOf(1, 1, 1, 1, 1)
        assertEquals(99, FanPercentile.of(1, counts))
    }

    @Test
    fun `中間的人落在中間`() {
        val counts = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        assertEquals(50, FanPercentile.of(5, counts))
    }

    @Test
    fun `人數不足不給數字`() {
        assertNull(FanPercentile.of(3, listOf(1, 2, 3)))
        assertEquals(FanPercentile.MIN_SAMPLE, 5)
    }

    @Test
    fun `沒看過就沒有 PR`() {
        assertNull(FanPercentile.of(0, listOf(1, 2, 3, 4, 5)))
        assertNull(FanPercentile.of(-1, listOf(1, 2, 3, 4, 5)))
    }

    @Test
    fun `永遠不會是 0 或 100`() {
        val many = List(200) { 50 }          // 全部同分 → 原始值 100
        assertEquals(99, FanPercentile.of(50, many))

        val bottom = listOf(1) + List(199) { 99 }  // 墊底 → 原始值 0
        assertEquals(1, FanPercentile.of(1, bottom))
    }

    @Test
    fun `稱呼分三段`() {
        assertEquals("死忠", FanPercentile.tier(95))
        assertEquals("死忠", FanPercentile.tier(90))
        assertEquals("常客", FanPercentile.tier(60))
        assertEquals("入坑中", FanPercentile.tier(59))
        assertNull(FanPercentile.tier(null))
    }

    @Test
    fun `標籤格式`() {
        assertEquals("PR 87", FanPercentile.label(87))
        assertNull(FanPercentile.label(null))
    }
}
