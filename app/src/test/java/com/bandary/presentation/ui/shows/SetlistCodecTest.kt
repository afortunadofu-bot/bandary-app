package com.bandary.presentation.ui.shows

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class SetlistCodecTest {

    private val lineup = listOf("主藝人", "someshiit山姆", "告五人")

    @Test
    fun `只有卡司第一位有歌時不寫標頭 舊資料格式不變`() {
        val out = encodeSetlist(mapOf("主藝人" to listOf("A", "B")), lineup)
        assertEquals(listOf("A", "B"), out)
    }

    @Test
    fun `只有第二位以後的藝人有歌 一定要寫標頭 重開不會跑到第一位`() {
        val byArtist = mapOf("someshiit山姆" to listOf("年少的我們永遠輕狂", "揹上悲傷北上"))
        val out = encodeSetlist(byArtist, lineup)
        assertEquals(listOf("▶ someshiit山姆", "年少的我們永遠輕狂", "揹上悲傷北上"), out)
        assertEquals(byArtist, parseSetlist(out, "主藝人"))
    }

    @Test
    fun `多位藝人有歌 各自寫標頭 空白列與前後空白去掉`() {
        val out = encodeSetlist(
            mapOf("主藝人" to listOf(" A ", ""), "告五人" to listOf("◆ 安可", "B")),
            lineup
        )
        assertEquals(listOf("▶ 主藝人", "A", "▶ 告五人", "◆ 安可", "B"), out)
    }

    @Test
    fun `沒有標頭的舊資料整包歸給第一位藝人`() {
        assertEquals(mapOf("主藝人" to listOf("A", "B")), parseSetlist(listOf("A", "", "B"), "主藝人"))
    }

    @Test
    fun `整份移到另一位藝人 原區塊拿掉 目標空白列清掉`() {
        val before = mapOf("打錯的名字" to listOf("A", "◆ 安可", "B"), "someshiit山姆" to listOf(""))
        val after = moveSetlistItems(before, "打錯的名字", setOf(0, 1, 2), "someshiit山姆")
        assertFalse(after.containsKey("打錯的名字"))
        assertEquals(listOf("A", "◆ 安可", "B"), after["someshiit山姆"])
    }

    @Test
    fun `只搬幾首 保持相對順序 接在目標原有歌的後面`() {
        val before = mapOf("甲" to listOf("A", "B", "C", "D"), "乙" to listOf("X"))
        val after = moveSetlistItems(before, "甲", setOf(3, 1), "乙")
        assertEquals(listOf("A", "C"), after["甲"])
        assertEquals(listOf("X", "B", "D"), after["乙"])
    }

    @Test
    fun `移到自己或沒選任何一首 原樣回傳`() {
        val before = mapOf("甲" to listOf("A"))
        assertSame(before, moveSetlistItems(before, "甲", setOf(0), "甲"))
        assertSame(before, moveSetlistItems(before, "甲", emptySet(), "乙"))
    }
}
