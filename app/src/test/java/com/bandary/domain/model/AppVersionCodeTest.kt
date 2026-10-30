package com.bandary.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 把 [AppVersionCode] 跟 **app/build.gradle.kts 的公式綁在一起**。
 *
 * 會有這組測試，是因為真的踩過：後台公告版號用舊公式
 * （`MAJOR * 10000 + MINOR * 100 + PATCH`），build.gradle.kts 用新公式，
 * 結果「有新版可更新」的提示永遠不會亮，而且完全不報錯。
 * 只改其中一邊的那天，這裡要先紅。
 */
class AppVersionCodeTest {

    private val gradle: String by lazy {
        listOf(File("build.gradle.kts"), File("app/build.gradle.kts"))
            .first { it.exists() }.readText()
    }

    @Test
    fun `公式與 build-gradle-kts 一致`() {
        val formula = Regex("""mj\s*\*\s*1000000\s*\+\s*mn\s*\*\s*1000\s*\+\s*pt""")
        assertTrue(
            "build.gradle.kts 的 versionCode 公式變了，AppVersionCode.of 要跟著改（兩邊不一致會讓更新提示永遠不亮）",
            formula.containsMatchIn(gradle.replace("_", ""))
        )
    }

    @Test
    fun `目前的版號算得出 versionCode`() {
        val name = Regex("""val appVersionName = "([0-9.]+)"""").find(gradle)!!.groupValues[1]
        val code = AppVersionCode.of(name)
        assertTrue("$name 應該算得出 versionCode", code != null && code > 0)
    }

    @Test
    fun `舊公式會撞號 新公式不會`() {
        // 舊式 MAJOR*10000 + MINOR*100 + PATCH 下，1.100.0 與 2.0.0 都是 20000。
        // 這是當初換公式的原因，順手在這裡釘住，避免有人覺得「舊的比較短」又改回去。
        assertNotEquals(AppVersionCode.of("1.100.0"), AppVersionCode.of("2.0.0"))
        assertEquals(1_100_000L, AppVersionCode.of("1.100.0"))
        assertEquals(2_000_000L, AppVersionCode.of("2.0.0"))
    }

    @Test
    fun `版號越新 versionCode 越大`() {
        val ascending = listOf("1.0.0", "1.0.1", "1.9.0", "1.107.1", "1.108.0", "2.0.0")
        val codes = ascending.map { AppVersionCode.of(it)!! }
        codes.zipWithNext().forEach { (a, b) ->
            assertTrue("$a 應該小於 $b", a < b)
        }
    }

    @Test
    fun `接受開頭的 v`() {
        assertEquals(AppVersionCode.of("1.108.0"), AppVersionCode.of("v1.108.0"))
        assertEquals(AppVersionCode.of("1.108.0"), AppVersionCode.of(" V1.108.0 "))
    }

    @Test
    fun `格式不合或超出範圍回 null`() {
        // 超範圍會進位：1.0.1000 若放過就變成 1.1.0，那是錯的版號不是大一點的版號。
        assertNull(AppVersionCode.of("1.0.1000"))
        assertNull(AppVersionCode.of("1.1000.0"))
        assertNull(AppVersionCode.of("1.2"))
        assertNull(AppVersionCode.of("1.2.3.4"))
        assertNull(AppVersionCode.of("a.b.c"))
        assertNull(AppVersionCode.of(""))
        assertNull(AppVersionCode.of("-1.0.0"))
    }
}
