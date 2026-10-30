package com.bandary.domain

import com.bandary.domain.model.AdminAlerts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminAlertsTest {

    /** 一切健康時的基準輸入：這組進去應該什麼都不報。 */
    private fun healthy(
        pendingEvents: Int = 0,
        pendingVenueViews: Int = 0,
        upcomingEvents: Int = 50,
        totalEvents: Int = 100,
        withImage: Int = 80,
        contributorTop3Share: Double = 0.4,
        scannedUsers: Int = 100,
        active30d: Int = 70,
        proUsers: Int = 10,
        usersWithConcerts: Int = 60
    ) = AdminAlerts.of(
        pendingEvents, pendingVenueViews, upcomingEvents, totalEvents, withImage,
        contributorTop3Share, scannedUsers, active30d, proUsers, usersWithConcerts
    )

    @Test
    fun `一切正常時不報任何事`() {
        // 這條是整個設計的重點：沒事的時候它要能消失，
        // 否則就變成另一個「為了放而放」的區塊。
        assertTrue(healthy().isEmpty())
    }

    @Test
    fun `待審投稿是急件`() {
        val a = healthy(pendingEvents = 3).first()
        assertEquals(AdminAlerts.Level.URGENT, a.level)
        assertTrue(a.title.contains("3"))
        // 每一條都要講下一步做什麼，不然它只是另一個數字。
        assertTrue(a.action.isNotBlank())
    }

    @Test
    fun `急件排在注意事項前面`() {
        val list = healthy(pendingEvents = 1, contributorTop3Share = 0.9)
        assertEquals(AdminAlerts.Level.URGENT, list.first().level)
        assertEquals(AdminAlerts.Level.WATCH, list.last().level)
    }

    @Test
    fun `未來場次見底會警告`() {
        assertTrue(healthy(upcomingEvents = 5).any { it.title.contains("5") })
    }

    @Test
    fun `未來完全沒有活動是另一種說法`() {
        val a = healthy(upcomingEvents = 0).first { it.level == AdminAlerts.Level.URGENT }
        assertTrue(a.title.contains("沒有任何活動"))
    }

    @Test
    fun `還沒有任何活動時不會誤報見底`() {
        // 全新的資料庫（totalEvents = 0）不該被說「活動頁是空的」——
        // 那是還沒開始，不是出問題。
        val list = healthy(upcomingEvents = 0, totalEvents = 0, withImage = 0)
        assertTrue(list.none { it.title.contains("沒有任何活動") })
    }

    @Test
    fun `沒撈到使用者時整段跳過不報假警訊`() {
        // scannedUsers = 0 的話，所有比率都會算成 0，
        // 不擋的話會一次噴出沉睡率、啟用率、付費轉換率三條假警訊。
        val list = healthy(scannedUsers = 0, active30d = 0, proUsers = 0, usersWithConcerts = 0)
        assertTrue(list.none { it.title.contains("沉睡率") })
        assertTrue(list.none { it.title.contains("啟用率") })
        assertTrue(list.none { it.title.contains("付費轉換率") })
    }

    @Test
    fun `沉睡率超過門檻才報`() {
        assertTrue(healthy(active30d = 70).none { it.title.contains("沉睡率") })
        assertTrue(healthy(active30d = 30).any { it.title.contains("沉睡率") })
    }

    @Test
    fun `投稿集中度到風險線才報`() {
        assertTrue(healthy(contributorTop3Share = 0.79).none { it.title.contains("投稿者") })
        assertTrue(healthy(contributorTop3Share = 0.80).any { it.title.contains("投稿者") })
    }

    @Test
    fun `沒有活動時不會因為除以零而崩潰`() {
        val list = healthy(totalEvents = 0, withImage = 0, upcomingEvents = 0)
        assertTrue(list.none { it.title.contains("有圖") })
    }
}
