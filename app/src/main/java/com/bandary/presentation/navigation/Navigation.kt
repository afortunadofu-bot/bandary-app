package com.bandary.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Shows : Screen("shows")
    object Overview : Screen("overview")
    object Calendar : Screen("calendar")
    object Stats : Screen("stats")
    object Profile : Screen("profile")
    object Settings : Screen("settings")
    object Login : Screen("login")
    object FreeEvents : Screen("free_events")
    object AdminDashboard : Screen("admin_dashboard")
    object Feedback : Screen("feedback")
    object FeatureWishes : Screen("feature_wishes")
    object PromoShare : Screen("promo_share")
    object Brand : Screen("brand")
    object FeedbackAdmin : Screen("feedback_admin")
    object ProFeatures : Screen("pro_features")
    object Friends : Screen("friends")
    object ImportCalendar : Screen("import_calendar")
    object BulkImport : Screen("bulk_import")

    object FriendCalendar : Screen("friend_calendar/{friendUid}/{friendName}") {
        fun createRoute(uid: String, name: String) =
            "friend_calendar/$uid/" + android.net.Uri.encode(name)
    }

    object AddShow : Screen("add_show?concertId={concertId}&duplicateId={duplicateId}&date={date}&scan={scan}") {
        fun createRoute(concertId: String? = null, duplicateId: String? = null, date: String? = null, scan: Boolean = false): String {
            val params = mutableListOf<String>()
            concertId?.let { params += "concertId=$it" }
            duplicateId?.let { params += "duplicateId=$it" }
            date?.let { params += "date=$it" }
            if (scan) params += "scan=true"
            return if (params.isEmpty()) "add_show" else "add_show?" + params.joinToString("&")
        }
    }


    object TicketShare : Screen("ticket_share/{concertId}") {
        fun createRoute(id: String) = "ticket_share/$id"
    }

    object ConcertDetail : Screen("concert_detail/{concertId}") {
        fun createRoute(id: String) = "concert_detail/$id"
    }

    object FollowedArtists : Screen("followed_artists")

    object ArtistDetail : Screen("artist_detail/{artistName}") {
        fun createRoute(name: String) = "artist_detail/" + android.net.Uri.encode(name)
    }

    // 統計頁點某排行 → 看符合的所有場次（type = venue / city / platform / type）。
    object ShowFilter : Screen("show_filter/{type}/{value}") {
        fun createRoute(type: String, value: String) = "show_filter/$type/" + android.net.Uri.encode(value)
    }

    // 年度聽團回顧（E2-7）：逐頁 story。year=0 表示自動挑最近有資料的年份。
    object Wrapped : Screen("wrapped/{year}") {
        fun createRoute(year: Int = 0) = "wrapped/$year"
    }

    // 垃圾桶：刪除的演唱會保留 7 天可還原。
    object Trash : Screen("trash")

    // 全站年度排行榜：誰今年看最多場（yearly_leaderboard/{year}/entries）。
    object Leaderboard : Screen("leaderboard")
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val labelRes: Int,   // 多語言：優先用字串資源顯示（label 保留當備用）
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

// Order: 演唱會(倒數，主頁) → 行事曆(演出+選歌合併) → 活動 → 統計 → 我的
val bottomNavItems = listOf(
    BottomNavItem(Screen.Shows, "演唱會", com.bandary.R.string.nav_shows, Icons.Filled.ConfirmationNumber, Icons.Outlined.ConfirmationNumber),
    BottomNavItem(Screen.Calendar, "行事曆", com.bandary.R.string.nav_calendar, Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
    BottomNavItem(Screen.FreeEvents, "活動", com.bandary.R.string.nav_events, Icons.Filled.CardGiftcard, Icons.Outlined.CardGiftcard),
    BottomNavItem(Screen.Stats, "統計", com.bandary.R.string.nav_stats, Icons.Filled.BarChart, Icons.Outlined.BarChart),
    BottomNavItem(Screen.Profile, "我的", com.bandary.R.string.nav_profile, Icons.Filled.Person, Icons.Outlined.Person)
)
