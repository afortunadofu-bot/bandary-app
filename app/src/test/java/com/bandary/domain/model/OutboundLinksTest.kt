package com.bandary.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 外站連結清單。
 *
 * 這組測試的由來：品牌頁的連結在 2026-10-30 被發現**有一半是過期的**。
 * `OutboundLinks` 早就換成 Play 商店網址，但 `BrandFooter` 自己抄了
 * 一份、還停在封測的 Google 報名表單（Bandary 與 RelateStats 兩條都是）。
 * 兩份清單各自看起來都很合理，只有同時打開才看得出來對不上，
 * 而且**不會報錯**——使用者點下去會到一個早就關掉的報名表單。
 *
 * 所以這裡釘兩件事：
 *  1. 清單本身是乾淨的（id 不重複、網址是 https、沒有殘留的報名表單）；
 *  2. **品牌頁要用的那三個 id 真的存在**。`BrandFooter` 是照 id 取網址的，
 *     id 打錯或被改名不會編譯失敗，只會讓那一列的連結悄悄變成空字串。
 */
class OutboundLinksTest {

    @Test
    fun `id 不重複`() {
        // id 是 Firestore 文件 id 的一部分（outbound_clicks/{installId}_{id}）。
        // 重複的話兩個連結的點擊會疊在同一筆上，後台看到的數字是混在一起的。
        val ids = OutboundLinks.ALL.map { it.id }
        assertEquals("有重複的 id", ids.size, ids.distinct().size)
    }

    @Test
    fun `每一筆都填滿了`() {
        OutboundLinks.ALL.forEach { l ->
            assertTrue("id 不可以空白", l.id.isNotBlank())
            assertTrue("${l.id} 的名稱空白", l.title.isNotBlank())
            assertTrue("${l.id} 的網址空白", l.url.isNotBlank())
        }
    }

    @Test
    fun `網址一律是 https`() {
        OutboundLinks.ALL.forEach { l ->
            assertTrue("${l.id} 不是 https：${l.url}", l.url.startsWith("https://"))
        }
    }

    @Test
    fun `沒有殘留的封測報名表單`() {
        // 這就是 2026-10-30 那個 bug 的樣子。兩支 App 都上架了，
        // 品牌頁還連到報名表單等於把人送去一個關掉的頁面。
        OutboundLinks.ALL.forEach { l ->
            assertTrue(
                "${l.id} 還連到報名表單：${l.url}",
                !l.url.contains("forms.gle") && !l.url.contains("docs.google.com/forms")
            )
        }
    }

    @Test
    fun `兩支 App 連的是 Play 商店頁`() {
        listOf(OutboundLinks.BANDARY_PLAY, OutboundLinks.RELATESTATS).forEach { id ->
            val url = OutboundLinks.byId(id)!!.url
            assertTrue("$id 不是 Play 商店網址：$url", url.startsWith("https://play.google.com/store/apps/details?id="))
        }
    }

    @Test
    fun `品牌頁要用的三個 id 都存在`() {
        // BrandFooter 照 id 取網址，id 對不上不會編譯失敗，
        // 只會讓那一列變成空連結——而那要等有人在實機上點到才發現。
        listOf(
            OutboundLinks.BIRTHDAY,
            OutboundLinks.BANDARY_PLAY,
            OutboundLinks.RELATESTATS
        ).forEach { id ->
            assertNotNull("品牌頁需要的 id「$id」不在清單裡", OutboundLinks.byId(id))
        }
    }

    @Test
    fun `贊助入口不列進品牌頁`() {
        // 贊助在「支持與回饋」有自己的位置，兩邊都列會變成在兩個地方要錢。
        assertTrue(OutboundLinks.brandPage.none { it.id == OutboundLinks.DONATE })
        assertTrue("贊助本身要留在 ALL 裡（點擊統計要算它）",
            OutboundLinks.ALL.any { it.id == OutboundLinks.DONATE })
    }

    @Test
    fun `品牌頁至少要有那三個`() {
        assertTrue(OutboundLinks.brandPage.size >= 3)
    }

    @Test
    fun `byId 找不到時回 null 而不是丟例外`() {
        assertEquals(null, OutboundLinks.byId("這個不存在"))
        assertEquals(null, OutboundLinks.byId(""))
    }
}
