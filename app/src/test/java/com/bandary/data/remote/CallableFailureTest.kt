package com.bandary.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 雲端函式失敗的訊息。
 *
 * 這組測試釘住的不是字句，是**分得開**這件事：Spotify 搜尋與「找海報」
 * 壞掉的原因有好幾種（沒登入、沒部署、金鑰沒設、額度用完、搜尋引擎沒設網域），
 * 每一種要去不同的地方修。以前全部都長成「沒有結果」，於是實機回報只能是
 *「還沒好」。只要兩種不同的原因產生同一句話，這個功能就等於沒做。
 */
class CallableFailureTest {

    @Test
    fun `沒登入與沒部署是不同的話`() {
        val unauth = CallableFailure.describe("UNAUTHENTICATED", null)
        val notFound = CallableFailure.describe("NOT_FOUND", null)
        assertTrue("沒登入要講登入", unauth.contains("登入"))
        assertTrue("沒部署要講得出要跑什麼", notFound.contains("deploy"))
        assertTrue(unauth != notFound)
    }

    @Test
    fun `Spotify 的 400 指向金鑰`() {
        // 伺服器端拿 token 失敗就是這串（functions/index.js 的 spotifyAccessToken）。
        // 這是實機截圖上真的出現過的訊息，也是目前 Spotify 搜尋不能用最可能的原因。
        val msg = CallableFailure.describe("UNAVAILABLE", "Spotify token HTTP 400")
        assertTrue("要講金鑰，不然看了也不知道去哪修", msg.contains("金鑰"))
        assertTrue(msg.contains("400"))
    }

    @Test
    fun `圖片搜尋的 403 指向金鑰被拒`() {
        val msg = CallableFailure.describe("UNAVAILABLE", "Custom Search HTTP 403")
        assertTrue(msg.contains("金鑰"))
        assertTrue("不要說成 Spotify", !msg.contains("Spotify"))
    }

    @Test
    fun `五百系列是對方掛掉不是我的設定錯`() {
        val msg = CallableFailure.describe("UNAVAILABLE", "Custom Search HTTP 503")
        assertTrue("要講等一下再試，不要害人去翻金鑰", msg.contains("等一下"))
        assertTrue(!msg.contains("金鑰"))
    }

    @Test
    fun `額度用完照伺服器的話講`() {
        val msg = CallableFailure.describe("RESOURCE_EXHAUSTED", "圖片搜尋今天的額度用完了")
        assertEquals("圖片搜尋今天的額度用完了", msg)
    }

    @Test
    fun `額度用完但伺服器沒給訊息也要講得出來`() {
        assertTrue(CallableFailure.describe("RESOURCE_EXHAUSTED", "").contains("額度"))
        assertTrue(CallableFailure.describe("RESOURCE_EXHAUSTED", null).contains("額度"))
    }

    @Test
    fun `沒有代碼就是連不上網路`() {
        assertEquals("連不上網路", CallableFailure.describe(null, null))
        assertEquals("連不上網路", CallableFailure.describe(null, "   "))
    }

    @Test
    fun `不認得的代碼也不會變成空字串`() {
        // 最糟的結果是畫面上出現「海報搜尋失敗：」後面什麼都沒有。
        assertTrue(CallableFailure.describe("ABORTED", null).isNotBlank())
        assertTrue(CallableFailure.describe("ABORTED", "whatever").isNotBlank())
    }

    @Test
    fun `每一種代碼都講出不一樣的話`() {
        val codes = listOf(
            "UNAUTHENTICATED", "NOT_FOUND", "PERMISSION_DENIED",
            "RESOURCE_EXHAUSTED", "DEADLINE_EXCEEDED", "UNAVAILABLE", "INTERNAL"
        )
        val said = codes.map { CallableFailure.describe(it, null) }
        assertEquals("有兩種原因講同一句話，就等於沒分出來", said.size, said.distinct().size)
        assertTrue(said.none { it.isBlank() })
    }

    @Test
    fun `拆掉 ExecutionException 的殼`() {
        // Tasks.await 失敗時外面包的是 ExecutionException，它的 message 是內層的
        // toString()。不拆殼的話畫面上會出現一串 java 類別名稱。
        val inner = IllegalStateException("Custom Search HTTP 429")
        val wrapped = java.util.concurrent.ExecutionException(inner)
        val msg = CallableFailure.of(wrapped)!!
        assertTrue("不該出現類別名稱", !msg.contains("Exception"))
        assertTrue(msg.contains("額度"))
    }

    @Test
    fun `殼裡沒有東西也不會當掉`() {
        val empty = java.util.concurrent.ExecutionException(null as Throwable?)
        assertTrue(CallableFailure.of(empty)!!.isNotBlank())
    }

    @Test
    fun `沒有例外就沒有訊息`() {
        assertNull(CallableFailure.of(null))
    }

    @Test
    fun `訊息裡不會有金鑰本身`() {
        // 伺服器只回狀態碼，金鑰留在 Secret Manager。萬一哪天有人把金鑰
        // 寫進錯誤訊息，這條會擋在使用者看到之前。
        val leaked = "Custom Search HTTP 403 key=AIzaSyFAKEKEYFAKEKEYFAKEKEY"
        val msg = CallableFailure.describe("UNAVAILABLE", leaked)
        assertTrue("不該把整串訊息原樣吐出來", !msg.contains("AIza"))
    }
}
