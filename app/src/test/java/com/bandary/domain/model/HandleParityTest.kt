package com.bandary.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 好友代碼推導規則的 App ↔ 網頁一致性測試。
 *
 * 兩邊各有一份實作（Kotlin 的 [cleanHandleSegment]、網頁 `suggestedHandleBase` 裡的 `clean`），
 * 規則只要有一邊改了，同一個人在手機與網頁自動配到的代碼就會不一樣。
 * 下面每一筆的期望值都是照網頁那份正則算出來的：
 *   `s.toLowerCase().replace(/[^a-z0-9]/g,'_').replace(/_+/g,'_').replace(/^_+|_+$/g,'')`
 */
class HandleParityTest {

    private fun assertClean(input: String, expected: String) =
        assertEquals("輸入「$input」", expected, cleanHandleSegment(input))

    @Test
    fun `英數原樣保留 只轉小寫`() {
        assertClean("Bandary", "bandary")
        assertClean("USER123", "user123")
    }

    @Test
    fun `非英數轉底線並收成一個`() {
        assertClean("a  b", "a_b")
        assertClean("a---b", "a_b")
        assertClean("Mr. Child", "mr_child")
    }

    @Test
    fun `頭尾的底線要去掉`() {
        assertClean("__abc__", "abc")
        assertClean(" abc ", "abc")
        assertClean("...abc...", "abc")
    }

    @Test
    fun `中文洗完是空字串 呼叫端要往下一個來源退`() {
        assertClean("嵐", "")
        assertClean("五月天", "")
        assertClean("　", "")
    }

    @Test
    fun `中英混合只留英數的部分`() {
        assertClean("嵐 Arashi", "arashi")
        assertClean("阿福 fu", "fu")
    }

    @Test
    fun `email 前綴常見寫法`() {
        assertClean("afortunado.fu", "afortunado_fu")
        assertClean("first+tag", "first_tag")
    }

    @Test
    fun `空字串與全符號都回空`() {
        assertClean("", "")
        assertClean("!!!", "")
    }

    @Test
    fun `全形空白與標點也算非英數`() {
        assertClean("ab　cd", "ab_cd")
        assertClean("ab，cd", "ab_cd")
    }

    @Test
    fun `已經是合法代碼的字串會原樣通過`() {
        // 使用者自己改過的代碼再被拿去推導時，不能被改動。
        assertClean("my_handle_9", "my_handle_9")
    }
}
