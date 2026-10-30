package com.bandary.domain

import com.bandary.domain.model.ArtistEntry
import com.bandary.domain.model.containsArtist
import com.bandary.domain.model.searchArtists
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistEntryTest {

    private val dir = listOf(
        ArtistEntry(id = "1", name = "滅火器 Fire EX.", key = "滅火器 fire ex.", aliases = listOf("Fire EX.", "滅火器")),
        ArtistEntry(id = "2", name = "落日飛車", key = "落日飛車"),
        ArtistEntry(id = "3", name = "落日飛車 Sunset Rollercoaster", key = "落日飛車 sunset rollercoaster"),
        ArtistEntry(id = "4", name = "傷心欲絕", key = "傷心欲絕", verified = true),
        ArtistEntry(id = "5", name = "傷心", key = "傷心")
    )

    @Test
    fun `別名也搜得到`() {
        val hits = dir.searchArtists("fire ex")
        assertEquals("滅火器 Fire EX.", hits.first().name)
    }

    @Test
    fun `完全相同排在開頭符合之前`() {
        val hits = dir.searchArtists("落日飛車")
        assertEquals("落日飛車", hits[0].name)
        assertEquals("落日飛車 Sunset Rollercoaster", hits[1].name)
    }

    @Test
    fun `同分時已驗證的優先`() {
        // 「傷心」對 5 號是完全相同、對 4 號是開頭符合，所以 5 號本來就該在前
        assertEquals("傷心", dir.searchArtists("傷心")[0].name)
        // 造一組同分的：兩筆都是「開頭符合」
        val tie = listOf(
            ArtistEntry(id = "a", name = "AAA 樂團", key = "aaa 樂團"),
            ArtistEntry(id = "b", name = "AAA 合唱團", key = "aaa 合唱團", verified = true)
        )
        assertEquals("AAA 合唱團", tie.searchArtists("aaa")[0].name)
    }

    @Test
    fun `大小寫與全形空白不影響比對`() {
        assertTrue(dir.containsArtist("FIRE EX."))
        assertTrue(dir.containsArtist("　落日飛車　"))
    }

    @Test
    fun `空字串不回傳任何結果`() {
        assertTrue(dir.searchArtists("").isEmpty())
        assertTrue(dir.searchArtists("   ").isEmpty())
        assertFalse(dir.containsArtist(""))
    }

    @Test
    fun `沒收錄的藝人回報為不存在`() {
        assertFalse(dir.containsArtist("還沒有人加過的團"))
        assertTrue(dir.searchArtists("還沒有人加過的團").isEmpty())
    }
}
