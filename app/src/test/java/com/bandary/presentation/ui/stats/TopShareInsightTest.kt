package com.bandary.presentation.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [topShareInsight] 的防呆。重點不是字串長相，而是**什麼時候不該造句**——
 * 硬擠一句「占 100%」比留白更糟。
 */
class TopShareInsightTest {

    @Test
    fun `講出最多的那一類與占比`() {
        val s = topShareInsight(listOf("演唱會" to 7, "音樂節" to 2, "免費" to 1), " 場")
        assertEquals("最多的是「演唱會」，7 場，占 70%", s)
    }

    @Test
    fun `只有一類時不造句`() {
        // 「最多的是 X，占 100%」等於沒說話，而且會讓人以為漏了別的類別。
        assertNull(topShareInsight(listOf("演唱會" to 9), " 場"))
        // 其他類別都是 0 的時候，實際上也只有一類。
        assertNull(topShareInsight(listOf("演唱會" to 9, "音樂節" to 0), " 場"))
    }

    @Test
    fun `沒資料時不造句`() {
        assertNull(topShareInsight(emptyList(), " 場"))
        assertNull(topShareInsight(listOf("演唱會" to 0, "音樂節" to 0), " 場"))
    }

    @Test
    fun `並列最多時不挑其中一個講`() {
        // 挑哪一個都是任意的，講「並列」才誠實。
        val s = topShareInsight(listOf("週五" to 4, "週六" to 4, "週日" to 1), " 首")
        assertTrue("應該要講並列，實際是：$s", s!!.contains("並列最多"))
        assertTrue(s.contains("2 項"))
        assertTrue(s.contains("4 首"))
    }

    @Test
    fun `占比用整數向下取整 不會出現 101`() {
        val s = topShareInsight(listOf("A" to 1, "B" to 1, "C" to 1, "D" to 1), "")!!
        // 四項並列 → 走並列那條，不報占比。
        assertTrue(s.contains("並列最多"))

        val t = topShareInsight(listOf("A" to 2, "B" to 1), "")!!
        assertTrue("2/3 應該是 66%，實際：$t", t.contains("66%"))
    }

    @Test
    fun `單位可以留空`() {
        val s = topShareInsight(listOf("A" to 3, "B" to 1))!!
        assertEquals("最多的是「A」，3，占 75%", s)
    }
}
