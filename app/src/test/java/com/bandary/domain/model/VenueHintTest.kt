package com.bandary.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 場館清單的整理。
 *
 * 使用者要求：「場館要有 key 到一半自動出現類似場館來選擇，最好都能直接帶入完整地址」。
 * 會出錯的不是比對（那套 [NameSuggest] 已經測過），而是**同一個場館被填過很多次、
 * 每次填的東西不一樣**時要留哪一份。
 */
class VenueHintTest {

    private fun rows(vararg r: Triple<String, String?, String?>) = buildVenueHints(r.toList())

    @Test
    fun `常去的排前面`() {
        val out = rows(
            Triple("Legacy Taipei", null, "台北"),
            Triple("台北小巨蛋", null, "台北"),
            Triple("Legacy Taipei", null, "台北"),
            Triple("Legacy Taipei", null, "台北")
        )
        assertEquals("去最多次的要排第一", "Legacy Taipei", out.first().name)
        assertEquals(3, out.first().count)
    }

    @Test
    fun `後來填的地址覆蓋先前的`() {
        // 場館搬家、或當初填得潦草，最新的最可能是對的。
        val out = rows(
            Triple("某 Live House", "台北市中正區舊址 1 號", "台北"),
            Triple("某 Live House", "台北市信義區新址 99 號", "台北")
        )
        assertEquals("台北市信義區新址 99 號", out.first().address)
    }

    @Test
    fun `空白不要覆蓋掉已經有的地址`() {
        // 這次沒填地址，不代表要把上次填的清掉。
        val out = rows(
            Triple("台北小巨蛋", "台北市南京東路四段 2 號", "台北"),
            Triple("台北小巨蛋", null, null),
            Triple("台北小巨蛋", "", "")
        )
        assertEquals("台北市南京東路四段 2 號", out.first().address)
        assertEquals("台北", out.first().city)
    }

    @Test
    fun `沒填過地址就是沒有 不要編一個`() {
        val out = rows(Triple("某個沒填地址的場地", null, "台中"))
        assertNull(out.first().address)
        assertEquals("沒地址就顯示縣市", "台中", out.first().hintLine)
    }

    @Test
    fun `前後空白視為同一個場館`() {
        val out = rows(
            Triple("  Legacy Taipei ", null, "台北"),
            Triple("Legacy Taipei", null, "台北")
        )
        assertEquals("應該合併成一筆", 1, out.size)
        assertEquals(2, out.first().count)
        assertEquals("名字要去掉前後空白", "Legacy Taipei", out.first().name)
    }

    @Test
    fun `空白場館名不列入`() {
        val out = rows(
            Triple("", null, "台北"),
            Triple("   ", "某地址", "台北"),
            Triple("真的場館", null, null)
        )
        assertEquals(1, out.size)
        assertEquals("真的場館", out.first().name)
    }

    @Test
    fun `完全沒有紀錄時回空清單`() {
        assertTrue(buildVenueHints(emptyList()).isEmpty())
    }

    @Test
    fun `提示行優先顯示地址`() {
        val withAddress = VenueHint("A", address = "某市某路 1 號", city = "某市")
        assertEquals("某市某路 1 號", withAddress.hintLine)
        val cityOnly = VenueHint("B", address = null, city = "某市")
        assertEquals("某市", cityOnly.hintLine)
        val neither = VenueHint("C")
        assertEquals("", neither.hintLine)
    }
}
