package com.bandary.domain

import com.bandary.domain.model.InviteCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteCodeTest {

    /**
     * 這三組值是用 `functions/index.js` 的 `inviteCodeOf()` 跑出來的。
     *
     * 為什麼要釘死：邀請碼現在有**兩份實作**——App 這邊算出自己的碼拿去分享，
     * 雲端的 `registerInvite` 重算一次來驗證那組碼真的屬於呼叫者。
     * 兩邊只要有一個字元對不上，所有邀請碼就全部失效，而且是靜悄悄地失效
     * （查不到 uid → 兌換回「找不到這組邀請碼」，看起來像使用者打錯）。
     * 任何一邊改了演算法，這個測試要先紅。
     */
    @Test
    fun `與 Cloud Function 的實作算出同一組碼`() {
        assertEquals("9E4VM6", InviteCode.of("abc123"))
        assertEquals("DEV7PC", InviteCode.of("xyz"))
        assertEquals("TUTWA6", InviteCode.of("a1b2c3d4e5f6g7h8"))
    }

    @Test
    fun `同一個 uid 永遠得到同一組碼`() {
        val a = InviteCode.of("abc123XYZ")
        val b = InviteCode.of("abc123XYZ")
        assertEquals(a, b)
        assertEquals(InviteCode.LENGTH, a.length)
    }

    @Test
    fun `不同 uid 給不同的碼`() {
        assertTrue(InviteCode.of("uid-one") != InviteCode.of("uid-two"))
    }

    @Test
    fun `只用字母表裡的字元`() {
        listOf("a", "bbbb", "9f8e7d6c", "使用者", "").forEach { uid ->
            val code = InviteCode.of(uid)
            assertTrue("「$code」含字母表外的字元", code.all { it in InviteCode.ALPHABET })
        }
    }

    @Test
    fun `空 uid 回空字串而不是亂碼`() {
        assertEquals("", InviteCode.of(""))
        assertEquals("", InviteCode.of("   "))
    }

    @Test
    fun `字母表不含容易看錯的字元`() {
        listOf('0', 'O', '1', 'I', 'L', '2', 'Z', '5', 'S', '8', 'B').forEach {
            assertFalse("$it 不該在字母表裡", it in InviteCode.ALPHABET)
        }
    }

    @Test
    fun `輸入正規化會修掉常見的手打錯誤`() {
        // 使用者看到 Q 打成 0、看到 J 打成 1，都要救得回來。
        assertEquals("QJ", InviteCode.normalize("0 1"))
        assertEquals("QJ", InviteCode.normalize("o-l"))
        assertEquals("A", InviteCode.normalize("b"))
        assertEquals("X", InviteCode.normalize("s"))
    }

    @Test
    fun `正規化不會超過長度`() {
        assertEquals(InviteCode.LENGTH, InviteCode.normalize("AAAAAAAAAAAAAA").length)
    }

    @Test
    fun `合法性檢查`() {
        assertTrue(InviteCode.isValid(InviteCode.of("someone")))
        assertFalse(InviteCode.isValid("ABC"))          // 太短
        assertFalse(InviteCode.isValid("ABCDEFG"))      // 太長
        assertFalse(InviteCode.isValid("AAAAA0"))       // 含字母表外的 0
    }

    @Test
    fun `分享網址帶得出也解得回`() {
        val code = InviteCode.of("uid-xyz")
        val url = InviteCode.shareUrl(code)
        assertTrue(url.contains("ref=$code"))
        assertEquals(code, InviteCode.fromUrl(url))
    }

    @Test
    fun `不是邀請碼的網址解出 null 而不是硬猜`() {
        assertNull(InviteCode.fromUrl("https://bandary.web.app/"))
        assertNull(InviteCode.fromUrl("https://bandary.web.app/?ref=zzz"))
        assertNull(InviteCode.fromUrl("隨便一段字"))
    }
}
