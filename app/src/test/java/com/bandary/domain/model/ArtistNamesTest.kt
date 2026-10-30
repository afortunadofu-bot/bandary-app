package com.bandary.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 藝人名稱的拆分與正規化。
 *
 * 這一組是整個 App 最容易「悄悄壞掉」的邏輯：拆錯不會當掉、不會報錯，
 * 只是統計數字慢慢對不上，而且要累積一陣子才看得出來。三種拆分規則的差異
 * （打字用 / 貼上用 / 顯示用）也很容易在改動時互相汙染。
 */
class ArtistNamesTest {

    // ── artistFollowKey：決定「兩個名字是不是同一位藝人」 ──

    @Test fun `大小寫與前後空白不影響比對`() {
        assertEquals(artistFollowKey("  Mr Child  "), artistFollowKey("MR CHILD"))
    }

    @Test fun `全形空格視同半形空格`() {
        assertEquals(artistFollowKey("落日　飛車"), artistFollowKey("落日 飛車"))
    }

    @Test fun `連續空白收斂成一個`() {
        assertEquals(artistFollowKey("Mr   Child"), artistFollowKey("Mr Child"))
    }

    /**
     * 這條是網頁版曾經寫錯的地方：如果把空白「全部拿掉」而不是「收成一個」，
     * 這兩個名字會被判成同一位。兩端的判定必須一致，否則同一個帳號在
     * App 與網頁會對「有沒有在追蹤這位藝人」給出不同答案。
     */
    @Test fun `有空白與沒空白是不同的藝人`() {
        assertNotEquals(artistFollowKey("Mr Child"), artistFollowKey("MrChild"))
    }

    @Test fun `空字串不會爆炸`() {
        assertEquals("", artistFollowKey(""))
        assertEquals("", artistFollowKey("   "))
    }

    // ── splitArtistNames：顯示 / 統計用的完整拆分 ──

    @Test fun `全形與半形分隔符都認得`() {
        assertEquals(
            listOf("告五人", "落日飛車", "椅子樂團"),
            splitArtistNames("告五人｜落日飛車、椅子樂團")
        )
    }

    @Test fun `去掉日期前綴與純日期`() {
        assertEquals(
            listOf("告五人", "落日飛車"),
            splitArtistNames("7/18：告五人｜7/19: 落日飛車｜7/20")
        )
    }

    @Test fun `重複的名字只留一個`() {
        assertEquals(listOf("告五人"), splitArtistNames("告五人、告五人"))
    }

    // ── splitArtistNamesExplicit：打字途中用的保守拆分 ──

    /**
     * 打字時絕對不能用空白拆。團名中間有空格、或還沒打完就被切成好幾列時，
     * 使用者的體感是「按兩下空格就自己多一位」，而且拆掉之後合不回來。
     */
    @Test fun `打字時空白不拆`() {
        assertEquals(listOf("Sunset  Rollercoaster"), splitArtistNamesExplicit("Sunset  Rollercoaster"))
        assertEquals(listOf("A / B"), splitArtistNamesExplicit("A / B"))
    }

    @Test fun `打字時仍認明確的分隔符`() {
        assertEquals(listOf("A", "B"), splitArtistNamesExplicit("A｜B"))
    }

    // ── splitArtistNamesPasted：貼整份卡司用 ──

    @Test fun `貼上時前後有空白的斜線要拆`() {
        assertEquals(listOf("A", "B", "C"), splitArtistNamesPasted("A / B / C"))
    }

    /** AC/DC、7/18 這種沒有空白的斜線不能拆，否則團名會被切成兩半。 */
    @Test fun `貼上時沒有空白的斜線不拆`() {
        assertEquals(listOf("AC/DC"), splitArtistNamesPasted("AC/DC"))
    }

    // ── matchFollowedArtist ──

    @Test fun `卡司裡有追蹤的藝人時回傳追蹤名單裡的原名`() {
        // 卡司寫成不同大小寫也要對得上，而且回傳的要是名單裡的原名（顯示用），
        // 不是卡司裡的寫法——UI 要顯示使用者自己輸入的那個名字。
        assertEquals("Mr Child", matchFollowedArtist("告五人｜MR CHILD", setOf("Mr Child")))
    }

    /**
     * 記錄一個容易誤解的既有行為：splitArtistNames 把「兩個以上的半形空白」當成分隔符
     * （見 ARTIST_SEPARATORS 的 \x20{2,}），所以卡司字串裡的 "MR  CHILD" 會被拆成兩位，
     * 不會被 artistFollowKey 收斂成 "mr child"。
     *
     * 這是刻意的：卡司常寫成「A  B  C」用空白排版。但也代表**團名本身若含連續空白，
     * 在卡司字串裡會被拆開**——追蹤比對時要留意。單一空白不受影響。
     */
    @Test fun `卡司字串裡的連續空白是分隔符而不是名字的一部分`() {
        assertEquals(listOf("MR", "CHILD"), splitArtistNames("MR  CHILD"))
        assertEquals(listOf("MR CHILD"), splitArtistNames("MR CHILD"))
        // 因此帶連續空白的卡司寫法對不上追蹤名單——這是預期行為，不是 bug。
        assertNull(matchFollowedArtist("MR  CHILD", setOf("Mr Child")))
    }

    @Test fun `卡司裡沒有追蹤的藝人時回傳 null`() {
        assertNull(matchFollowedArtist("告五人、落日飛車", setOf("五月天")))
    }

    @Test fun `空卡司或空名單回傳 null`() {
        assertNull(matchFollowedArtist("", setOf("告五人")))
        assertNull(matchFollowedArtist("告五人", emptySet()))
    }

    private fun assertNotEquals(a: Any?, b: Any?) =
        org.junit.Assert.assertNotEquals(a, b)
}
