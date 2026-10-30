package com.bandary.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 一首歌有多位歌手（合作、feat.）。
 *
 * 存的仍然是**一個字串**，用分隔符合寫——與演唱會卡司同一套做法，
 * 不為此改資料庫結構。但那表示三個地方必須同意同一套拆分規則：
 * 建議清單、輸入欄位、統計。任何一邊沒拆，使用者就會看到一個叫
 * 「怕胖團 ／ 告五人」的假藝人，而真正的兩位各自少算一次。
 *
 * 這組測試釘住的就是那個假設：**UI 用的分隔符，拆分器真的認得**。
 * 認錯不會報錯，只會讓統計安靜地變錯。
 */
class SongMultiArtistTest {

    /** 選歌輸入欄位用的分隔符（見 HomeScreen 的 artistSep）。 */
    private val uiSeparator = " ／ "

    @Test
    fun `UI 用的分隔符拆得開`() {
        val typed = "怕胖團" + uiSeparator + "告五人"
        assertEquals(listOf("怕胖團", "告五人"), artistNamesForStats(typed))
    }

    @Test
    fun `常見的其他寫法也吃得到`() {
        // 使用者可能從別處貼上，不會都用同一種符號。
        assertEquals(listOf("A", "B"), artistNamesForStats("A、B"))
        assertEquals(listOf("A", "B"), artistNamesForStats("A ｜ B"))
        assertEquals(listOf("A", "B"), artistNamesForStats("A / B"))
        assertEquals(listOf("A", "B"), artistNamesForStats("A\nB"))
    }

    @Test
    fun `沒有空白的斜線不拆`() {
        // AC/DC 是一個團，7/18 是日期。拆了就毀了。
        assertEquals(listOf("AC/DC"), artistNamesForStats("AC/DC"))
    }

    @Test
    fun `單一歌手仍然是一位`() {
        assertEquals(listOf("告五人"), artistNamesForStats("告五人"))
    }

    @Test
    fun `空白與 null 回空清單`() {
        assertTrue(artistNamesForStats(null).isEmpty())
        assertTrue(artistNamesForStats("").isEmpty())
        assertTrue(artistNamesForStats("   ").isEmpty())
        assertTrue("只有分隔符時不該生出空名字", artistNamesForStats(" ／ ／ ").isEmpty())
    }

    @Test
    fun `重複的同一位只算一次`() {
        // 使用者可能手滑貼兩次。
        assertEquals(listOf("告五人"), artistNamesForStats("告五人 ／ 告五人"))
    }

    @Test
    fun `統計展開後兩位各算一次`() {
        // 這就是「假藝人」問題：不拆的話排行會出現一個叫「A ／ B」的項目。
        val rows = listOf("怕胖團 ／ 告五人", "告五人", "怕胖團")
        val counts = rows.flatMap { artistNamesForStats(it) }
            .groupingBy { it }.eachCount()
        assertEquals(2, counts["怕胖團"])
        assertEquals(2, counts["告五人"])
        assertEquals("不該出現合寫的那一項", null, counts["怕胖團 ／ 告五人"])
    }

    @Test
    fun `過長的整段敘述不會被當成藝人名`() {
        // artistNamesForStats 有長度上限，擋的是沒切開的卡司或一整段文字。
        val essay = "這首歌是我在某個下雨的晚上聽到的那時候剛好走在回家的路上整個人都被打中了真的很好聽推薦給大家"
        assertTrue(artistNamesForStats(essay).isEmpty())
    }
}
