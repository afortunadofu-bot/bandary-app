package com.bandary.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 輸入時的藝人建議。
 *
 * 實機回報：「沒有自動把相似的整合，或輸入到一半問你是不是這個（不考慮字序那種）」。
 * 原本兩個頁面各寫一份 `contains`，而 `contains` 是照順序比的。
 */
class NameSuggestTest {

    private val known = listOf(
        "公館青少年GGteen", "告五人", "滅火器 Fire EX.", "落日飛車 Sunset Rollercoaster",
        "草東沒有派對", "deca joins", "茄子蛋"
    )

    private fun s(q: String) = NameSuggest.suggest(q, known)

    @Test
    fun `字序顛倒也比得到`() {
        // 這就是回報裡「不考慮字序那種」。
        assertTrue("青少年公館 應該比到 公館青少年GGteen", s("青少年公館").contains("公館青少年GGteen"))
        assertTrue("GGteen公館 也要比到", s("GGteen公館").contains("公館青少年GGteen"))
        assertTrue("人五告 應該比到 告五人", s("人五告").contains("告五人"))
    }

    @Test
    fun `空白與大小寫不影響`() {
        assertTrue(s("公館 青少年").contains("公館青少年GGteen"))
        assertTrue(s("ggteen").contains("公館青少年GGteen"))
        assertTrue(s("滅火器 fire").contains("滅火器 Fire EX."))
        assertTrue(s("DECA JOINS").isEmpty() || s("DECA JOIN").contains("deca joins"))
    }

    @Test
    fun `前綴排在包含前面`() {
        val r = NameSuggest.suggest("落日", listOf("落日飛車 Sunset Rollercoaster", "夏日落日"))
        assertEquals("前綴的要排第一", "落日飛車 Sunset Rollercoaster", r.first())
    }

    @Test
    fun `打得比資料庫裡的名字更長也比得到`() {
        // 他打了完整寫法，庫裡只有短的。
        val r = NameSuggest.suggest("滅火器 Fire EX. 樂團", listOf("滅火器"))
        assertTrue("應該建議庫裡的「滅火器」，實際 $r", r.contains("滅火器"))
    }

    @Test
    fun `完全相同的不建議`() {
        // 他已經打完了，再列一次是廢話。
        assertFalse(s("告五人").contains("告五人"))
        assertFalse(s("告 五 人").contains("告五人"))
    }

    @Test
    fun `已經填在表單上的不建議`() {
        val r = NameSuggest.suggest("告", known, exclude = listOf("告五人"))
        assertFalse("已經填過的不該再建議", r.contains("告五人"))
    }

    @Test
    fun `太短的片段不做字序無關比對`() {
        // 「一人」這種兩個字在中文團名裡會撞到一堆無關的；建議變雜訊就沒人看了。
        val r = NameSuggest.suggest("人告", listOf("告五人"))
        assertTrue("兩個字不該觸發字序無關比對，實際 $r", r.isEmpty())
        // 三個字開始才有辨識度。
        assertTrue(NameSuggest.suggest("人五告", listOf("告五人")).contains("告五人"))
    }

    @Test
    fun `多打一個字 要建議正確的名字`() {
        // 「告告五人」是打錯，而這時候建議「告五人」正是使用者要的——
        // 「問你是不是這個」本來就包含「你打錯了，是不是這個」。
        // （走的是「打的字包含庫裡的名字」那一層，不是字序無關那一層。）
        assertTrue(NameSuggest.suggest("告告五人", listOf("告五人")).contains("告五人"))
    }

    @Test
    fun `字序無關那一層要算重複次數`() {
        // 「五告告人」把字打散又多一個「告」：字序無關只看字元有沒有在候選裡，
        // 若不算次數就會比中「告五人」。多出來的字代表這不是同一個名字的另一種寫法。
        assertTrue(NameSuggest.suggest("五告告人", listOf("告五人")).isEmpty())
    }

    @Test
    fun `空字串與純符號回空`() {
        assertTrue(NameSuggest.suggest("", known).isEmpty())
        assertTrue(NameSuggest.suggest("   ", known).isEmpty())
        assertTrue(NameSuggest.suggest("・／", known).isEmpty())
    }

    @Test
    fun `不會超過上限 也不會有重複`() {
        val many = List(50) { "樂團$it" } + listOf("樂團0")
        val r = NameSuggest.suggest("樂團", many, limit = 6)
        assertTrue("最多 6 筆，實際 ${r.size}", r.size <= 6)
        assertEquals("不該有重複", r.size, r.distinct().size)
    }

    @Test
    fun `完全不相關的不會被列出來`() {
        val r = s("五月天")
        assertFalse("草東沒有派對 跟 五月天 無關", r.contains("草東沒有派對"))
        assertFalse("茄子蛋 跟 五月天 無關", r.contains("茄子蛋"))
    }
}
