package com.bandary.domain

import com.bandary.domain.model.VenueViewStatus
import com.bandary.domain.model.venueKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class VenueViewTest {

    @Test
    fun `臺與台視為同一個場館`() {
        assertEquals(venueKey("臺北小巨蛋"), venueKey("台北小巨蛋"))
    }

    @Test
    fun `空白與全形括號不影響比對`() {
        assertEquals(venueKey("台北小巨蛋"), venueKey(" 台北　小巨蛋 "))
        assertEquals(venueKey("Zepp New Taipei"), venueKey("ZeppNewTaipei"))
    }

    @Test
    fun `大小寫不影響比對`() {
        assertEquals(venueKey("Legacy Taipei"), venueKey("LEGACY TAIPEI"))
    }

    @Test
    fun `不做模糊比對 簡稱與全名是兩個場館`() {
        // 刻意的：寧可分開，也不要把高雄巨蛋的視野圖配到台北小巨蛋上。
        assertNotEquals(venueKey("小巨蛋"), venueKey("台北小巨蛋"))
    }

    @Test
    fun `不同場館不會互相撞鍵`() {
        assertNotEquals(venueKey("台北小巨蛋"), venueKey("高雄巨蛋"))
    }

    @Test
    fun `狀態文字`() {
        assertEquals("審核中", VenueViewStatus.label(VenueViewStatus.PENDING))
        assertEquals("未通過", VenueViewStatus.label(VenueViewStatus.REJECTED))
        assertEquals("已通過", VenueViewStatus.label(VenueViewStatus.APPROVED))
        // 舊資料沒有這個欄位時當成已通過，不要讓既有圖突然消失。
        assertEquals("已通過", VenueViewStatus.label(""))
    }
}
