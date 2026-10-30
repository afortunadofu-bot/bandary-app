package com.bandary.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * App 內「更新紀錄」的防呆：每次改版號都要一起補一筆，否則 App 裡看到的最新紀錄會停在舊版本。
 * 版號從 app/build.gradle.kts 的 appVersionName 直接讀（專案沒開 BuildConfig），兩邊不一致這裡就會紅。
 */
class AppChangelogTest {

    private fun parts(v: String) = v.split(".").map { it.toInt() }

    @Test
    fun `最新一筆就是目前的版號`() {
        val gradle = listOf(File("build.gradle.kts"), File("app/build.gradle.kts")).first { it.exists() }.readText()
        val current = Regex("""val appVersionName = "([0-9.]+)"""").find(gradle)!!.groupValues[1]
        assertEquals(current, APP_CHANGELOG.first().version)
    }

    @Test
    fun `由新到舊排列 版號不重複 日期不倒退`() {
        APP_CHANGELOG.zipWithNext().forEach { (newer, older) ->
            val a = parts(newer.version)
            val b = parts(older.version)
            val cmp = a.zip(b).map { (x, y) -> x.compareTo(y) }.firstOrNull { it != 0 } ?: 0
            assertTrue("${newer.version} 應該比 ${older.version} 新", cmp > 0)
            assertTrue("${newer.version} 的日期不能早於 ${older.version}", newer.date >= older.date)
        }
    }

    @Test
    fun `每筆都簡短 格式正確`() {
        APP_CHANGELOG.forEach { e ->
            assertTrue("${e.version} 日期格式", Regex("""\d{4}-\d{2}-\d{2}""").matches(e.date))
            assertTrue("${e.version} 至少一條", e.items.isNotEmpty())
            e.items.forEach { item ->
                assertTrue("${e.version}「$item」超過 40 字，App 內紀錄要簡潔", item.length <= 40)
            }
        }
    }
}
