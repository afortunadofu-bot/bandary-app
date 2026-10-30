package com.bandary.presentation.ui.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「我最常聽的 N 首歌」分享圖的版面計算。
 *
 * 這組測試存在的原因很具體：原本的 `rowH = 可用高度 / 歌數` 只在滿十首時是對的，
 * 三首會變成每列 392px（藝人名離歌名 118px）、一首是 1176px（離 353px）。
 * 那不是「留白多」，是看起來壞掉——而歌數少的人正是剛開始用的新使用者。
 *
 * 畫圖本身要 Android 的 Canvas，JVM 測不到；但**會出錯的是幾何**，
 * 所以把幾何抽出來測，剩下的 drawText 只是照著座標畫。
 */
class TopSongsCardLayoutTest {

    // 1080×1920 的實際值（見 TopSongsCardRenderer.render）
    private val top = 497.34f
    private val bottom = 1673.4f
    private val available = bottom - top                 // ≈ 1176
    private val ideal = available / 10                   // ≈ 117.6，滿十首的列高

    private fun layout(n: Int) =
        TopSongsCardRenderer.listLayout(top, bottom, n, ideal)

    @Test
    fun `滿十首時剛好填滿 從頂端開始`() {
        val l = layout(10)
        assertEquals(ideal, l.rowH, 0.01f)
        assertEquals(top, l.startY, 0.01f)
        assertEquals(bottom, l.startY + l.rowH * 10, 0.5f)
    }

    @Test
    fun `歌數少時列高不會被撐開`() {
        // 上限是理想列高的 1.35 倍。原本 3 首會變成 3.3 倍、1 首會變成 10 倍。
        listOf(1, 2, 3, 5).forEach { n ->
            val l = layout(n)
            assertTrue(
                "$n 首的列高 ${l.rowH} 不該超過 ${ideal * 1.35f}",
                l.rowH <= ideal * 1.35f + 0.01f
            )
        }
    }

    @Test
    fun `藝人名不會離歌名太遠`() {
        // 藝人名畫在 baseline + rowH * 0.30。原本 1 首時是 353px，整列看起來散掉。
        listOf(1, 3, 5, 10).forEach { n ->
            val gap = layout(n).rowH * 0.30f
            assertTrue("$n 首時藝人名離歌名 $gap px，太遠", gap < 50f)
        }
    }

    @Test
    fun `歌數少時整塊清單垂直置中`() {
        val l = layout(3)
        val used = l.rowH * 3
        val above = l.startY - top
        val below = bottom - (l.startY + used)
        assertTrue("上方留白 $above 與下方留白 $below 應該相等", kotlin.math.abs(above - below) < 0.5f)
        assertTrue("清單應該往下移了（置中），實際 startY=${l.startY}", l.startY > top)
    }

    @Test
    fun `列永遠排在可用範圍內`() {
        (0..10).forEach { n ->
            val l = layout(n)
            assertTrue("$n 首的起點 ${l.startY} 不該在範圍上方", l.startY >= top - 0.01f)
            assertTrue(
                "$n 首的終點 ${l.startY + l.rowH * n} 不該超出範圍",
                l.startY + l.rowH * n <= bottom + 0.01f
            )
        }
    }

    @Test
    fun `沒有歌時不要算出負數或 NaN`() {
        val l = layout(0)
        assertEquals(0f, l.rowH, 0f)
        assertEquals(top, l.startY, 0f)
    }

    @Test
    fun `可用高度為零或負時不炸掉`() {
        // 極端情況：系統字體放到最大把頁首推得很長，可用高度可能變成負的。
        val l = TopSongsCardRenderer.listLayout(900f, 800f, 5, ideal)
        assertTrue("列高不該是負的，實際 ${l.rowH}", l.rowH >= 0f)
        assertTrue("起點不該是 NaN", !l.startY.isNaN())
    }
}
