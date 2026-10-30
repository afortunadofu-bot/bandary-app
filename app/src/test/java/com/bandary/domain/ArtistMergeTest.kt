package com.bandary.domain

import com.bandary.domain.model.ArtistMerge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistMergeTest {

    @Test
    fun `實機例子 三種寫法併成一組 代表名取最完整的`() {
        val groups = ArtistMerge.findGroups(
            listOf("ggteen", "公館青少年", "公館青少年GGteen", "五月天")
        )
        assertEquals(1, groups.size)
        val g = groups.first()
        assertEquals("公館青少年GGteen", g.canonical)
        assertEquals(3, g.variants.size)
        assertTrue("ggteen" in g.variants)
        assertTrue("公館青少年" in g.variants)
    }

    @Test
    fun `沒有別名的藝人不會被湊成一組`() {
        val groups = ArtistMerge.findGroups(listOf("五月天", "告五人", "蘇打綠"))
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `空白與標點不影響比對`() {
        val groups = ArtistMerge.findGroups(listOf("Fire EX.", "FireEX", "滅火器 Fire EX."))
        assertEquals(1, groups.size)
        assertEquals("滅火器 Fire EX.", groups.first().canonical)
    }

    @Test
    fun `臺與台視為同一個字`() {
        val groups = ArtistMerge.findGroups(listOf("臺北爵士大樂隊", "台北爵士大樂隊"))
        assertEquals(1, groups.size)
        assertEquals(2, groups.first().variants.size)
    }

    @Test
    fun `名字相近但無關的不會被合併`() {
        // 相似度演算法會把這兩個算成很接近——這正是不用相似度的原因。
        val groups = ArtistMerge.findGroups(listOf("告五人", "告白氣球"))
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `太短的名字不參與合併`() {
        // hue 會被 thue 之類吃掉。短名字寧可漏，不要錯。
        val groups = ArtistMerge.findGroups(listOf("hue", "thue"), minKeyLength = 4)
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `代表名在同長度時結果是穩定的`() {
        val a = ArtistMerge.findGroups(listOf("ABCD", "abcd x", "ABCD X"))
        val b = ArtistMerge.findGroups(listOf("ABCD X", "abcd x", "ABCD"))
        assertEquals(a.first().canonical, b.first().canonical)
    }

    @Test
    fun `套用到卡司時會換成代表名並去重`() {
        val groups = ArtistMerge.findGroups(listOf("公館青少年", "公館青少年GGteen"))
        val cast = listOf("五月天", "公館青少年", "公館青少年GGteen", "告五人")
        val out = ArtistMerge.applyTo(cast, groups)
        // 兩種寫法換成同一個代表名之後要收斂成一筆。
        assertEquals(listOf("五月天", "公館青少年GGteen", "告五人"), out)
    }

    @Test
    fun `沒有可合併的組時卡司原樣回傳`() {
        val cast = listOf("五月天", "告五人")
        assertEquals(cast, ArtistMerge.applyTo(cast, emptyList()))
    }

    @Test
    fun `有維基資料時代表名以維基為準`() {
        val g = ArtistMerge.findGroups(listOf("滅火器", "滅火器 Fire EX."))
            .first().copy(wikiTitle = "滅火器樂團")
        // 最長的是「滅火器 Fire EX.」，但維基的條目叫「滅火器樂團」——以維基為準。
        assertEquals("滅火器樂團", g.preferred)
        // 維基的名稱不在原本的寫法裡，所以兩個原始寫法都要被併進去。
        assertEquals(2, g.others.size)
    }

    @Test
    fun `維基查不到時退回最長的那個`() {
        val g = ArtistMerge.findGroups(listOf("公館青少年", "公館青少年GGteen")).first()
        assertEquals("公館青少年GGteen", g.preferred)
        assertEquals(1, g.others.size)
    }

    @Test
    fun `套用卡司時換成維基的正式名稱`() {
        val g = ArtistMerge.findGroups(listOf("滅火器", "滅火器 Fire EX."))
            .first().copy(wikiTitle = "滅火器樂團")
        val out = ArtistMerge.applyTo(listOf("五月天", "滅火器", "滅火器 Fire EX."), listOf(g))
        assertEquals(listOf("五月天", "滅火器樂團"), out)
    }

    @Test
    fun `代表名本身也算在 variants 裡 others 只含其他寫法`() {
        val g = ArtistMerge.findGroups(listOf("公館青少年", "公館青少年GGteen")).first()
        assertTrue(g.canonical in g.variants)
        assertTrue(g.canonical !in g.others)
        assertEquals(1, g.others.size)
    }
}
