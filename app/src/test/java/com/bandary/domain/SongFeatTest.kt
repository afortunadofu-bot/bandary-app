package com.bandary.domain

import com.bandary.domain.model.SongFeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongFeatTest {

    @Test
    fun `組合與拆解是對稱的`() {
        val line = SongFeat.format("愛你", listOf("老王樂隊"))
        assertEquals("愛你 feat. 老王樂隊", line)
        assertEquals("愛你" to listOf("老王樂隊"), SongFeat.parse(line))
    }

    @Test
    fun `多位合作藝人`() {
        val line = SongFeat.format("派對動物", listOf("A", "B"))
        assertEquals("派對動物 feat. A、B", line)
        assertEquals(listOf("A", "B"), SongFeat.featsOf(line))
    }

    @Test
    fun `沒有合作藝人就只有歌名`() {
        assertEquals("愛你", SongFeat.format("愛你", emptyList()))
        assertEquals("愛你", SongFeat.format("愛你", listOf("", "  ")))
    }

    @Test
    fun `認得使用者會打的各種寫法`() {
        listOf(
            "愛你 ft. 老王",
            "愛你 ft 老王",
            "愛你 feat 老王",
            "愛你（feat. 老王）",
            "愛你 (feat. 老王)",
            "愛你 with 老王"
        ).forEach { line ->
            assertEquals("解析「$line」", "愛你", SongFeat.titleOf(line))
            assertEquals("解析「$line」", listOf("老王"), SongFeat.featsOf(line))
        }
    }

    @Test
    fun `不同分隔符號都拆得開`() {
        assertEquals(listOf("A", "B"), SongFeat.featsOf("歌 feat. A, B"))
        assertEquals(listOf("A", "B"), SongFeat.featsOf("歌 feat. A & B"))
        assertEquals(listOf("A", "B"), SongFeat.featsOf("歌 feat. A / B"))
    }

    @Test
    fun `歌名裡的括號不會被誤判`() {
        assertEquals("愛你（Live）", SongFeat.titleOf("愛你（Live）"))
        assertTrue(SongFeat.featsOf("愛你（Live）").isEmpty())
    }

    @Test
    fun `整列以 feat 開頭不算 因為沒有歌名`() {
        assertEquals("feat. 老王", SongFeat.titleOf("feat. 老王"))
        assertTrue(SongFeat.featsOf("feat. 老王").isEmpty())
    }

    @Test
    fun `半截的 feat 不拆`() {
        // 「愛你 feat.」後面沒有人名，拆了只會得到空的合作藝人。
        assertEquals("愛你 feat.", SongFeat.titleOf("愛你 feat."))
        assertTrue(SongFeat.featsOf("愛你 feat.").isEmpty())
    }

    @Test
    fun `統計要用 titleOf 才不會把同一首算成兩首`() {
        val lines = listOf("愛你", "愛你 feat. 老王", "愛你 ft. A")
        assertEquals(setOf("愛你"), lines.map { SongFeat.titleOf(it) }.toSet())
    }

    @Test
    fun `hasFeat`() {
        assertTrue(SongFeat.hasFeat("愛你 feat. 老王"))
        assertFalse(SongFeat.hasFeat("愛你"))
    }
}
