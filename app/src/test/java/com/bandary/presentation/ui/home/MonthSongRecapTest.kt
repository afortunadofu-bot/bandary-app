package com.bandary.presentation.ui.home

import com.bandary.domain.model.DailySong
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 月回顧的純計算層測試。
 *
 * 重點在容易寫錯又不容易人工測出來的部分：同分排序要穩定、同名不同藝人的歌不能被併在一起、
 * 佔比與增減那句話要在對的條件下才出現。
 */
class MonthSongRecapTest {

    private var seq = 0
    private fun song(day: Int, title: String, artist: String) = DailySong(
        id = "s${seq++}",
        date = LocalDate(2026, 8, day),
        songTitle = title,
        artistName = artist
    )

    @Test
    fun `沒有紀錄時回空結果`() {
        val r = buildMonthSongRecap(emptyList())
        assertTrue(r.isEmpty)
        assertEquals(0, r.totalSongs)
        assertNull(r.headline)
    }

    @Test
    fun `總數 天數 藝人數分別計算`() {
        val r = buildMonthSongRecap(
            listOf(
                song(1, "A", "甲"),
                song(1, "B", "甲"),   // 同一天兩首＝2 首但只算 1 天
                song(2, "C", "乙")
            )
        )
        assertEquals(3, r.totalSongs)
        assertEquals(2, r.activeDays)
        assertEquals(2, r.artistCount)
    }

    @Test
    fun `藝人排行同分時以名稱排序 順序才穩定`() {
        // 資料庫回傳順序不保證，同分若不排序，每次算出來的名次會跳動、看起來像資料在變。
        // 餵入順序刻意打亂，算出來必須固定是名稱排序後的結果。
        val r = buildMonthSongRecap(listOf(song(3, "C", "丙"), song(1, "A", "甲"), song(2, "B", "乙")))
        val names = r.topArtists.map { it.name }
        assertEquals(listOf(1, 1, 1), r.topArtists.map { it.count })
        assertEquals(names.sorted(), names)
    }

    @Test
    fun `次數多的排在同分的前面`() {
        val r = buildMonthSongRecap(
            listOf(song(1, "A", "甲"), song(2, "B", "甲"), song(3, "C", "丙"), song(4, "D", "乙"))
        )
        assertEquals("甲", r.topArtists.first().name)
        assertEquals(2, r.topArtists.first().count)
        // 後兩位同為 1 首，仍照名稱排序
        assertEquals(listOf("丙", "乙"), r.topArtists.drop(1).map { it.name })
    }

    @Test
    fun `同名不同藝人的歌要分開算`() {
        val r = buildMonthSongRecap(
            listOf(
                song(1, "離開", "甲"),
                song(2, "離開", "甲"),
                song(3, "離開", "乙")
            )
        )
        val 甲的離開 = r.topSongs.first { it.artist == "甲" }
        val 乙的離開 = r.topSongs.first { it.artist == "乙" }
        assertEquals(2, 甲的離開.count)
        assertEquals(1, 乙的離開.count)
    }

    @Test
    fun `佔三成以上才會說某人是主角`() {
        // 6 首裡 3 首同一人＝50%
        val songs = listOf(
            song(1, "A", "甲"), song(2, "B", "甲"), song(3, "C", "甲"),
            song(4, "D", "乙"), song(5, "E", "丙"), song(6, "F", "丁")
        )
        val r = buildMonthSongRecap(songs)
        assertTrue(r.headline!!.contains("甲"))
        assertTrue(r.headline!!.contains("50%"))
    }

    @Test
    fun `只有一位藝人時不說佔 100%`() {
        val songs = (1..4).map { song(it, "S$it", "甲") }
        val r = buildMonthSongRecap(songs)
        assertTrue(r.headline!!.contains("只記了 甲"))
        assertTrue(!r.headline!!.contains("100%"))
    }

    @Test
    fun `沒人過三成時改講與上個月的增減`() {
        // 5 首、5 位藝人，每人 1 首＝20%，不到三成
        val songs = listOf(
            song(1, "A", "甲"), song(2, "B", "乙"), song(3, "C", "丙"),
            song(4, "D", "丁"), song(5, "E", "戊")
        )
        assertEquals("比上個月多記了 2 首。", buildMonthSongRecap(songs, previousMonthTotal = 3).headline)
        assertEquals("比上個月少記了 3 首。", buildMonthSongRecap(songs, previousMonthTotal = 8).headline)
        assertEquals("跟上個月一樣是 5 首。", buildMonthSongRecap(songs, previousMonthTotal = 5).headline)
    }

    @Test
    fun `不足四首時不講佔比 免得一首就說某人是主角`() {
        val r = buildMonthSongRecap(listOf(song(1, "A", "甲"), song(2, "B", "甲")))
        assertNull(r.headline)
    }

    @Test
    fun `沒有藝人名的紀錄不算進藝人數 但仍算進總首數`() {
        val r = buildMonthSongRecap(listOf(song(1, "A", "甲"), song(2, "B", "")))
        assertEquals(2, r.totalSongs)
        assertEquals(1, r.artistCount)
    }

    @Test
    fun `topN 生效`() {
        val songs = listOf(
            song(1, "A", "甲"), song(2, "B", "乙"), song(3, "C", "丙"),
            song(4, "D", "丁"), song(5, "E", "戊")
        )
        assertEquals(3, buildMonthSongRecap(songs).topArtists.size)
        assertEquals(2, buildMonthSongRecap(songs, topN = 2).topArtists.size)
    }

    // 月回顧要等月底：月中看到的是還沒跑完的半套數字。
    @Test
    fun `本月要到最後一天才給回顧`() {
        val midMonth = LocalDate(2026, 9, 16)
        assertTrue(isMonthRecapReady(2026, 8, midMonth))        // 已經過完的上個月
        assertTrue(!isMonthRecapReady(2026, 9, midMonth))       // 進行中的這個月
        assertTrue(!isMonthRecapReady(2026, 10, midMonth))      // 未來的月份
        assertTrue(isMonthRecapReady(2026, 9, LocalDate(2026, 9, 30)))   // 九月最後一天
        assertTrue(!isMonthRecapReady(2026, 9, LocalDate(2026, 9, 29)))
        assertTrue(isMonthRecapReady(2026, 2, LocalDate(2026, 2, 28)))   // 平年二月
        assertTrue(isMonthRecapReady(2025, 12, LocalDate(2026, 1, 1)))   // 跨年
    }
}
