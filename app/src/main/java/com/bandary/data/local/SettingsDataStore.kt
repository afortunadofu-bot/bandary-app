package com.bandary.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import com.bandary.domain.model.artistFollowKey
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "bandary_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

// 四款預設主題色（莫蘭迪綠為預設）+ CUSTOM（使用者自訂色，最多再加 2 色湊滿 6 色）。
enum class ThemePalette(val label: String) {
    GRAY("莫蘭迪灰"),
    PINK("粉"),
    GREEN("綠"),
    BLUE("藍"),
    CUSTOM("自訂");

    companion object {
        // 舊偏好值遷移：暖色→粉、紫/藍→藍、綠→綠、自訂→自訂、未知/null→綠（新預設）。
        fun fromStored(name: String?): ThemePalette = when (name) {
            "GREEN" -> GREEN
            "PINK", "ORANGE", "BLACK" -> PINK   // 玫瑰粉 / 蜜桃橘 / 奶油黃（暖色）→ 粉
            "BLUE", "PURPLE" -> BLUE            // 霧霾藍 / 薰衣草紫 → 藍
            "GRAY" -> GRAY
            "CUSTOM" -> CUSTOM
            else -> GREEN                       // 含 null / 未知 → 莫蘭迪綠
        }
    }
}

@Singleton
class SettingsDataStore @Inject constructor(
    private val context: Context
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val THEME_PALETTE = stringPreferencesKey("theme_palette")
        val IS_PRO = booleanPreferencesKey("is_pro")
        val IS_PRO_PLUS = booleanPreferencesKey("is_pro_plus")
        val IS_ADMIN = booleanPreferencesKey("is_admin")
        val LEADERBOARD_OPT_OUT = booleanPreferencesKey("leaderboard_opt_out")
        val LOGGED_IN_EMAIL = stringPreferencesKey("logged_in_email")
        val CUSTOM_PLATFORMS = stringSetPreferencesKey("custom_platforms")
        val PLATFORM_ORDER = stringPreferencesKey("platform_order")
        // 各售票平台用哪個帳號登入的備忘（一行一個「平台	帳號」）。
        // 只存本機、不進雲端備份：這是帳號線索，同步出去的風險遠大於便利。
        val PLATFORM_ACCOUNTS = stringPreferencesKey("platform_accounts")
        val NOTIFY_SALE = booleanPreferencesKey("notify_sale")
        val NOTIFY_ENTRY = booleanPreferencesKey("notify_entry")
        val NOTIFY_CAL_SYNC = booleanPreferencesKey("notify_cal_sync")
        val NOTIFY_DIGEST = booleanPreferencesKey("notify_digest")  // 精選提醒：演出當天 / 補紀錄 / N 年前的今天 / 週末免費活動
        val HOME_LOCATION = stringPreferencesKey("home_location")  // "lat,lng,label"
        val ADMIN_GESTURE_COUNT = intPreferencesKey("admin_gesture_count")
        val CUSTOM_COLOR = stringPreferencesKey("custom_color")       // 目前選用的自訂色 "#RRGGBB"
        val CUSTOM_COLORS = stringPreferencesKey("custom_colors")     // 已儲存的自訂色（| 分隔，最多 2 個）
        val CHECKIN_ENABLED = booleanPreferencesKey("checkin_enabled")
        val CHECKIN_TIME = stringPreferencesKey("checkin_time")      // "HH:mm"
        val HOME_BASE_CITY = stringPreferencesKey("home_base_city")  // 統計最遠演唱會基準地
        val AUTO_BACKUP = stringPreferencesKey("auto_backup")        // off / daily / monthly
        val LAST_BACKUP_AT = androidx.datastore.preferences.core.longPreferencesKey("last_backup_at")
        val LAST_BACKUP_CONCERTS = intPreferencesKey("last_backup_concerts")  // 上次備份的演唱會數
        val LAST_BACKUP_SONGS = intPreferencesKey("last_backup_songs")        // 上次備份的選歌數
        val LAST_BACKUP_PHOTOS = intPreferencesKey("last_backup_photos")      // 上次備份後雲端共有幾張照片
        val LAST_BACKUP_AUTO = booleanPreferencesKey("last_backup_auto")      // 上次備份是自動跑的還是手動按的
        // 排行榜的分層公開設定。兩個都預設開啟，但都是「聚合／不可逆推」的資料：
        // 常聽藝人是名字清單、重疊比對上傳的是雜湊。逐場清單一律不上傳。
        val LB_SHARE_ARTISTS = booleanPreferencesKey("lb_share_artists")      // 公開常聽藝人
        val LB_SHARE_OVERLAP = booleanPreferencesKey("lb_share_overlap")      // 允許比對重疊
        val AVATAR_URI = stringPreferencesKey("avatar_uri")          // 自訂頭貼（file:// URI）
        val NICKNAME = stringPreferencesKey("nickname")
        // 匿名安裝識別碼。只用來讓後台算得出「有多少人在用但還沒登入」，
        // 不含任何個人資料、不跨 App 追蹤；重裝會換一組，所以它算的是安裝不是人。
        val INSTALL_ID = stringPreferencesKey("install_id")              // 自訂暱稱
        val LAST_READ_ANNOUNCEMENT = androidx.datastore.preferences.core.longPreferencesKey("last_read_announcement")  // 上次讀官方通知的時間（判斷未讀）
        val DEFAULT_REMINDERS = stringPreferencesKey("default_reminders")  // 新演唱會的預設入場提醒（ReminderOffset name，| 分隔）
        val COVER_GRADIENT_LIGHT = booleanPreferencesKey("cover_gradient_light")  // 封面漸層：true=淺、false=深
        val DISMISSED_RECAPS = stringSetPreferencesKey("dismissed_recaps")  // 被按叉關掉的月回顧（"yyyy-MM"）
        // 本機刪掉、還沒同步到雲端的選歌 id。備份只刪這些，不再「以本機為準鏡像雲端」——
        // 那會讓剛重灌、本機還是空的手機在第一次備份時把雲端選歌全部刪光。
        val PENDING_SONG_DELETES = stringSetPreferencesKey("pending_song_deletes")
        val INTERESTED_EVENTS = stringSetPreferencesKey("interested_events")       // 免費活動「有興趣要去」的 id 集合
        val HIDDEN_EVENTS = stringSetPreferencesKey("hidden_events")               // 使用者按「不想看這場」隱藏的活動 id
        val STREAK_FREEZE_TOKENS = intPreferencesKey("streak_freeze_tokens")       // E2-4 補打卡券剩餘張數
        val STREAK_FREEZE_MONTH = stringPreferencesKey("streak_freeze_month")      // 上次發券的月份 yyyy-MM
        val STREAK_FREEZE_DATES = stringSetPreferencesKey("streak_freeze_dates")   // 已用券補回的日期（yyyy-MM-dd）
        val SEEN_FRIEND_SHOWS = stringSetPreferencesKey("seen_friend_shows")       // E2-12 已通知過的好友演出 key
        val FOLLOWED_ARTISTS = stringSetPreferencesKey("followed_artists")         // 追蹤中的藝人（存原始名稱，比對時再正規化）
        val SEEN_FOLLOWED_EVENTS = stringSetPreferencesKey("seen_followed_events") // 已通知過的「追蹤藝人有新活動」key
        val FRIEND_SOCIAL_INIT = booleanPreferencesKey("friend_social_init")       // E2-12 是否已初始化過（避免首次全部當新的）
        val SHARE_SONG_PROFILE = booleanPreferencesKey("share_song_profile")       // 是否把年度歌單分享給好友（預設關）
        val CAL_LINK_ASKED = booleanPreferencesKey("cal_link_asked")               // 登入後是否已問過「要不要連結 Google 日曆」
        val SHARED_CAL_MIGRATED = booleanPreferencesKey("shared_cal_migrated")     // 共享日曆是否已搬到 private/payload（一次性）
        val START_TAB = stringPreferencesKey("start_tab")               // App 啟動時預設分頁（底部導覽 route）
        val SHOWS_DISPLAY = stringPreferencesKey("shows_display")       // 「我的演唱會」上次的顯示模式（MASONRY/LIST/BLOCKS）
        val SHOWS_SCOPE = stringPreferencesKey("shows_scope")           // 「我的演唱會」上次的時間範圍（ALL/YEAR/MONTH）
        val SHOWS_DIR = stringPreferencesKey("shows_dir")               // 「我的演唱會」上次的過去/未來篩選（ALL/UPCOMING/PAST）
        val ONBOARDED = booleanPreferencesKey("onboarded")              // 是否已完成首次啟動的 Aha 引導流程
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            // 預設淺色。與網頁版一致——兩邊第一眼的觀感必須一樣。
            // 只影響「從沒動過主題設定」的人；已經自己選過的使用者讀得到 THEME_MODE，不受影響。
            ?: ThemeMode.LIGHT
    }

    val themePalette: Flow<ThemePalette> = context.dataStore.data.map { prefs ->
        ThemePalette.fromStored(prefs[Keys.THEME_PALETTE])
    }

    // 目前選用的自訂色（palette == CUSTOM 時生效）。
    val customColorHex: Flow<String?> = context.dataStore.data.map { it[Keys.CUSTOM_COLOR] }

    // 使用者已加入的自訂色清單（最多 2 個，與 4 款預設合計上限 6 色）。
    val customColors: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[Keys.CUSTOM_COLORS]?.split("|")?.filter { it.isNotBlank() } ?: emptyList()
    }

    val isAdmin: Flow<Boolean> = context.dataStore.data.map { it[Keys.IS_ADMIN] ?: false }

    /**
     * 實際買到 / 用折扣碼換到的方案。**只有備份與還原該用這兩個**——
     * 備份時若用下面那個「含管理員」的版本，管理員的帳號會被寫上 codeUnlockedPro，
     * 之後就算拿掉管理員身分也還留著 Pro。
     */
    val isProPurchased: Flow<Boolean> = context.dataStore.data.map { it[Keys.IS_PRO] ?: false }
    val isProPlusPurchased: Flow<Boolean> = context.dataStore.data.map { it[Keys.IS_PRO_PLUS] ?: false }

    /**
     * 畫面上要用的方案判斷：**管理員一律視為最高級方案**。
     *
     * 管理員要審投稿、看後台、驗證每一個付費功能有沒有壞，卻被擋在付費牆外面很荒謬。
     * 這裡用推導而不是在管理員開通時去寫 IS_PRO：推導可以隨身分即時生效與失效，
     * 寫進 DataStore 則會在拿掉管理員之後留下一個洗不掉的 Pro。
     */
    val isPro: Flow<Boolean> = combine(isProPurchased, isAdmin) { pro, admin -> pro || admin }
    // Pro+（收藏家方案）：更高的每場照片上限。owned ⊇ Pro，所以只要是 Pro+ 就一定也是 Pro（見 BillingManager.grantPro）。
    val isProPlus: Flow<Boolean> = combine(isProPlusPurchased, isAdmin) { plus, admin -> plus || admin }

    /** 登入後問過一次「要不要連結 Google 日曆」就不再問，不管當時選的是連結還是以後再說。 */
    val calendarLinkAsked: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.CAL_LINK_ASKED] ?: false }

    suspend fun setCalendarLinkAsked(value: Boolean) {
        context.dataStore.edit { it[Keys.CAL_LINK_ASKED] = value }
    }

    /** 使用者按叉關掉的月回顧（"yyyy-MM"）。關掉就不再出現，除非之後又有新的一個月。 */
    val dismissedRecaps: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.DISMISSED_RECAPS] ?: emptySet() }

    val pendingSongDeletes: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.PENDING_SONG_DELETES] ?: emptySet() }

    suspend fun addPendingSongDelete(id: String) {
        context.dataStore.edit { it[Keys.PENDING_SONG_DELETES] = (it[Keys.PENDING_SONG_DELETES] ?: emptySet()) + id }
    }

    suspend fun clearPendingSongDeletes(ids: Set<String>) {
        context.dataStore.edit { it[Keys.PENDING_SONG_DELETES] = (it[Keys.PENDING_SONG_DELETES] ?: emptySet()) - ids }
    }

    suspend fun dismissRecap(month: String) {
        context.dataStore.edit { it[Keys.DISMISSED_RECAPS] = (it[Keys.DISMISSED_RECAPS] ?: emptySet()) + month }
    }
    // 年度排行榜：預設公開（false）、可在設定退出。跟好友共享日曆是不同的公開範圍——
    // 這裡只公開「今年看了幾場」這個數字＋暱稱，不是完整的演出清單。
    val leaderboardOptOut: Flow<Boolean> = context.dataStore.data.map { it[Keys.LEADERBOARD_OPT_OUT] ?: false }
    val lbShareArtists: Flow<Boolean> = context.dataStore.data.map { it[Keys.LB_SHARE_ARTISTS] ?: true }
    val lbShareOverlap: Flow<Boolean> = context.dataStore.data.map { it[Keys.LB_SHARE_OVERLAP] ?: true }
    suspend fun setLbShareArtists(v: Boolean) { context.dataStore.edit { it[Keys.LB_SHARE_ARTISTS] = v } }
    suspend fun setLbShareOverlap(v: Boolean) { context.dataStore.edit { it[Keys.LB_SHARE_OVERLAP] = v } }
    val loggedInEmail: Flow<String?> = context.dataStore.data.map { it[Keys.LOGGED_IN_EMAIL] }

    val customPlatforms: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.CUSTOM_PLATFORMS] ?: emptySet() }

    val platformOrder: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[Keys.PLATFORM_ORDER]?.split("|")?.filter { it.isNotBlank() } ?: emptyList()
    }

    /**
     * 「拓元是用哪個 Google 帳號買的？」——搶票前想不起來最痛。
     * 平台 → 帳號備忘。刻意用自由文字（可以寫「工作信箱」「老婆的 FB」），
     * 不要求是 Email：提示自己用的東西，不該逼人寫成標準格式。
     */
    val platformAccounts: Flow<Map<String, String>> = context.dataStore.data.map { prefs ->
        prefs[Keys.PLATFORM_ACCOUNTS].orEmpty().lineSequence()
            .mapNotNull { line ->
                val i = line.indexOf('\t')
                if (i <= 0) null else line.substring(0, i) to line.substring(i + 1)
            }
            .filter { it.second.isNotBlank() }
            .toMap()
    }

    suspend fun setPlatformAccount(platform: String, account: String) {
        context.dataStore.edit { prefs ->
            val cur = prefs[Keys.PLATFORM_ACCOUNTS].orEmpty().lineSequence()
                .mapNotNull { line ->
                    val i = line.indexOf('\t')
                    if (i <= 0) null else line.substring(0, i) to line.substring(i + 1)
                }.toMap().toMutableMap()
            if (account.isBlank()) cur.remove(platform) else cur[platform] = account.trim()
            prefs[Keys.PLATFORM_ACCOUNTS] = cur.entries.joinToString("\n") { "${it.key}\t${it.value}" }
        }
    }

    val notifySale: Flow<Boolean> = context.dataStore.data.map { it[Keys.NOTIFY_SALE] ?: true }
    val notifyEntry: Flow<Boolean> = context.dataStore.data.map { it[Keys.NOTIFY_ENTRY] ?: true }
    val notifyCalSync: Flow<Boolean> = context.dataStore.data.map { it[Keys.NOTIFY_CAL_SYNC] ?: false }
    val notifyDigest: Flow<Boolean> = context.dataStore.data.map { it[Keys.NOTIFY_DIGEST] ?: true }

    val homeLocation: Flow<String?> = context.dataStore.data.map { it[Keys.HOME_LOCATION] }

    val checkInEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.CHECKIN_ENABLED] ?: false }
    val checkInTime: Flow<String> = context.dataStore.data.map { it[Keys.CHECKIN_TIME] ?: "21:00" }
    val homeBaseCity: Flow<String> = context.dataStore.data.map { it[Keys.HOME_BASE_CITY] ?: "臺北" }
    // 預設每天備份：以前預設關閉，使用者以為有在備份，實際雲端停在幾個月前，換手機還原才發現少了一大半（實機回報）。
    // 只有登入後才會真的上傳（AutoBackupWorker 未登入時直接略過）。
    val autoBackup: Flow<String> = context.dataStore.data.map { it[Keys.AUTO_BACKUP] ?: "daily" }
    val lastBackupAt: Flow<Long> = context.dataStore.data.map { it[Keys.LAST_BACKUP_AT] ?: 0L }
    val interestedEvents: Flow<Set<String>> = context.dataStore.data.map { it[Keys.INTERESTED_EVENTS] ?: emptySet() }

    /**
     * 使用者主動隱藏的活動。只存在本機：這是「我不想看」的個人偏好，
     * 不該同步給別人，也不該影響活動本身。
     */
    val hiddenEvents: Flow<Set<String>> = context.dataStore.data.map { it[Keys.HIDDEN_EVENTS] ?: emptySet() }

    suspend fun toggleHiddenEvent(id: String) {
        context.dataStore.edit { prefs ->
            val cur = prefs[Keys.HIDDEN_EVENTS] ?: emptySet()
            prefs[Keys.HIDDEN_EVENTS] = if (id in cur) cur - id else cur + id
        }
    }

    suspend fun clearHiddenEvents() {
        context.dataStore.edit { it[Keys.HIDDEN_EVENTS] = emptySet() }
    }
    val streakFreezeTokens: Flow<Int> = context.dataStore.data.map { it[Keys.STREAK_FREEZE_TOKENS] ?: 0 }
    val streakFreezeMonth: Flow<String> = context.dataStore.data.map { it[Keys.STREAK_FREEZE_MONTH] ?: "" }
    val streakFreezeDates: Flow<Set<String>> = context.dataStore.data.map { it[Keys.STREAK_FREEZE_DATES] ?: emptySet() }
    val seenFriendShows: Flow<Set<String>> = context.dataStore.data.map { it[Keys.SEEN_FRIEND_SHOWS] ?: emptySet() }
    val followedArtists: Flow<Set<String>> = context.dataStore.data.map { it[Keys.FOLLOWED_ARTISTS] ?: emptySet() }
    val seenFollowedEvents: Flow<Set<String>> = context.dataStore.data.map { it[Keys.SEEN_FOLLOWED_EVENTS] ?: emptySet() }
    val friendSocialInitialized: Flow<Boolean> = context.dataStore.data.map { it[Keys.FRIEND_SOCIAL_INIT] ?: false }
    // 年度歌單分享：預設 false（選歌日記是私密的，要分享得自己開）。
    val shareSongProfile: Flow<Boolean> = context.dataStore.data.map { it[Keys.SHARE_SONG_PROFILE] ?: false }
    val lastBackupConcerts: Flow<Int> = context.dataStore.data.map { it[Keys.LAST_BACKUP_CONCERTS] ?: 0 }
    val lastBackupSongs: Flow<Int> = context.dataStore.data.map { it[Keys.LAST_BACKUP_SONGS] ?: 0 }
    val lastBackupPhotos: Flow<Int> = context.dataStore.data.map { it[Keys.LAST_BACKUP_PHOTOS] ?: 0 }
    val avatarUri: Flow<String?> = context.dataStore.data.map { it[Keys.AVATAR_URI] }
    val nickname: Flow<String?> = context.dataStore.data.map { it[Keys.NICKNAME] }

    /**
     * 匿名安裝識別碼；沒有就產一組存起來。
     * 隨機 UUID，跟裝置、帳號、廣告識別碼都無關。
     */
    suspend fun installId(): String {
        val existing = context.dataStore.data.map { it[Keys.INSTALL_ID] }.first()
        if (!existing.isNullOrBlank()) return existing
        val fresh = java.util.UUID.randomUUID().toString()
        context.dataStore.edit { it[Keys.INSTALL_ID] = fresh }
        return fresh
    }
    val lastReadAnnouncementAt: Flow<Long> = context.dataStore.data.map { it[Keys.LAST_READ_ANNOUNCEMENT] ?: 0L }
    val defaultReminders: Flow<List<com.bandary.domain.model.ReminderOffset>> = context.dataStore.data.map { prefs ->
        prefs[Keys.DEFAULT_REMINDERS]?.split("|")?.mapNotNull { n ->
            runCatching { com.bandary.domain.model.ReminderOffset.valueOf(n) }.getOrNull()
        } ?: listOf(com.bandary.domain.model.ReminderOffset.ONE_WEEK, com.bandary.domain.model.ReminderOffset.ONE_DAY)
    }

    suspend fun setDefaultReminders(offsets: List<com.bandary.domain.model.ReminderOffset>) {
        context.dataStore.edit { it[Keys.DEFAULT_REMINDERS] = offsets.joinToString("|") { o -> o.name } }
    }

    /** App 啟動要停在哪個底部導覽分頁（route 字串，null = 預設「我的演唱會」）。 */
    val startTab: Flow<String?> = context.dataStore.data.map { it[Keys.START_TAB] }
    suspend fun setStartTab(route: String?) {
        context.dataStore.edit { if (route.isNullOrBlank()) it.remove(Keys.START_TAB) else it[Keys.START_TAB] = route }
    }

    /** 「我的演唱會」上次停留的顯示模式 / 時間範圍，重開 App 後回到原本狀態。 */
    val showsDisplay: Flow<String?> = context.dataStore.data.map { it[Keys.SHOWS_DISPLAY] }
    suspend fun setShowsDisplay(v: String) { context.dataStore.edit { it[Keys.SHOWS_DISPLAY] = v } }
    val showsScope: Flow<String?> = context.dataStore.data.map { it[Keys.SHOWS_SCOPE] }
    suspend fun setShowsScope(v: String) { context.dataStore.edit { it[Keys.SHOWS_SCOPE] = v } }
    val showsDir: Flow<String?> = context.dataStore.data.map { it[Keys.SHOWS_DIR] }
    suspend fun setShowsDir(v: String) { context.dataStore.edit { it[Keys.SHOWS_DIR] = v } }

    /** 是否已完成首次啟動的引導流程（價值說明 → 個人化 → 權限說明 → 新增第一場）。 */
    val hasOnboarded: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDED] ?: false }
    suspend fun setOnboarded() { context.dataStore.edit { it[Keys.ONBOARDED] = true } }

    val coverGradientLight: Flow<Boolean> = context.dataStore.data.map { it[Keys.COVER_GRADIENT_LIGHT] ?: false }
    suspend fun setCoverGradientLight(v: Boolean) {
        context.dataStore.edit { it[Keys.COVER_GRADIENT_LIGHT] = v }
    }

    suspend fun setAvatarUri(uri: String?) {
        context.dataStore.edit { if (uri == null) it.remove(Keys.AVATAR_URI) else it[Keys.AVATAR_URI] = uri }
    }

    suspend fun setNickname(name: String?) {
        context.dataStore.edit { if (name.isNullOrBlank()) it.remove(Keys.NICKNAME) else it[Keys.NICKNAME] = name }
    }

    suspend fun setLastReadAnnouncementAt(ts: Long) {
        context.dataStore.edit { it[Keys.LAST_READ_ANNOUNCEMENT] = ts }
    }

    suspend fun setAutoBackup(mode: String) {
        context.dataStore.edit { it[Keys.AUTO_BACKUP] = mode }
    }

    suspend fun setLastBackupAt(ts: Long) {
        context.dataStore.edit { it[Keys.LAST_BACKUP_AT] = ts }
    }

    /** 切換免費活動「有興趣要去」標示。 */
    suspend fun toggleInterestedEvent(id: String) {
        context.dataStore.edit { prefs ->
            val cur = prefs[Keys.INTERESTED_EVENTS] ?: emptySet()
            prefs[Keys.INTERESTED_EVENTS] = if (id in cur) cur - id else cur + id
        }
    }

    /** 記錄上次備份的時間 + 數量（演唱會 / 選歌），供設定頁顯示。 */
    suspend fun setLastBackup(ts: Long, concerts: Int, songs: Int, photos: Int = -1) {
        context.dataStore.edit {
            it[Keys.LAST_BACKUP_AT] = ts
            it[Keys.LAST_BACKUP_CONCERTS] = concerts
            it[Keys.LAST_BACKUP_SONGS] = songs
            // -1 = 這次沒拿到照片數（備份失敗），保留上一次的數字，不要歸零嚇人
            if (photos >= 0) it[Keys.LAST_BACKUP_PHOTOS] = photos
        }
    }

    /** 上次備份是不是自動跑的。設定頁要能分辨「我手動按的」與「排程自己跑的」。 */
    val lastBackupAuto: Flow<Boolean> = context.dataStore.data.map { it[Keys.LAST_BACKUP_AUTO] ?: false }
    suspend fun setLastBackupAuto(auto: Boolean) {
        context.dataStore.edit { it[Keys.LAST_BACKUP_AUTO] = auto }
    }

    val sharedCalendarMigrated: Flow<Boolean> = context.dataStore.data.map { it[Keys.SHARED_CAL_MIGRATED] ?: false }

    suspend fun setSharedCalendarMigrated(v: Boolean) {
        context.dataStore.edit { it[Keys.SHARED_CAL_MIGRATED] = v }
    }

    suspend fun setShareSongProfile(v: Boolean) {
        context.dataStore.edit { it[Keys.SHARE_SONG_PROFILE] = v }
    }

    suspend fun setCheckInEnabled(v: Boolean) {
        context.dataStore.edit { it[Keys.CHECKIN_ENABLED] = v }
    }

    suspend fun setCheckInTime(time: String) {
        context.dataStore.edit { it[Keys.CHECKIN_TIME] = time }
    }

    suspend fun setHomeBaseCity(city: String) {
        context.dataStore.edit { it[Keys.HOME_BASE_CITY] = city }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setThemePalette(palette: ThemePalette) {
        context.dataStore.edit { it[Keys.THEME_PALETTE] = palette.name }
    }

    /** 選用某個自訂色（設 palette=CUSTOM 並記住色碼）。 */
    suspend fun selectCustomColor(hex: String) {
        context.dataStore.edit {
            it[Keys.CUSTOM_COLOR] = hex
            it[Keys.THEME_PALETTE] = ThemePalette.CUSTOM.name
        }
    }

    /** 新增一個自訂色到清單（最多 2 個，超過忽略）並立即選用。回傳是否成功加入。 */
    suspend fun addCustomColor(hex: String): Boolean {
        var added = false
        context.dataStore.edit { prefs ->
            val cur = prefs[Keys.CUSTOM_COLORS]?.split("|")?.filter { it.isNotBlank() } ?: emptyList()
            if (hex in cur) { added = true } // 已存在視為成功，直接選用
            else if (cur.size < 2) { prefs[Keys.CUSTOM_COLORS] = (cur + hex).joinToString("|"); added = true }
            if (added) {
                prefs[Keys.CUSTOM_COLOR] = hex
                prefs[Keys.THEME_PALETTE] = ThemePalette.CUSTOM.name
            }
        }
        return added
    }

    /** 移除一個自訂色；若移除的是目前選用色，改回綠色預設。 */
    suspend fun removeCustomColor(hex: String) {
        context.dataStore.edit { prefs ->
            val cur = prefs[Keys.CUSTOM_COLORS]?.split("|")?.filter { it.isNotBlank() } ?: emptyList()
            prefs[Keys.CUSTOM_COLORS] = (cur - hex).joinToString("|")
            if (prefs[Keys.CUSTOM_COLOR] == hex) {
                prefs.remove(Keys.CUSTOM_COLOR)
                prefs[Keys.THEME_PALETTE] = ThemePalette.GREEN.name
            }
        }
    }

    suspend fun setPro(value: Boolean) {
        context.dataStore.edit { it[Keys.IS_PRO] = value }
    }

    // 注意：跟 setPro 一樣是單向鎖——查不到 Play 購買紀錄時「絕對不」把它關掉
    // （折扣碼 / 換手機查詢中的過渡狀態都可能暫時查不到）。呼叫端只在確認擁有時才傳 true。
    suspend fun setProPlus(value: Boolean) {
        context.dataStore.edit { it[Keys.IS_PRO_PLUS] = value }
    }

    suspend fun setLeaderboardOptOut(value: Boolean) {
        context.dataStore.edit { it[Keys.LEADERBOARD_OPT_OUT] = value }
    }

    suspend fun setAdmin(value: Boolean) {
        context.dataStore.edit { it[Keys.IS_ADMIN] = value }
    }

    suspend fun setLoggedInEmail(email: String?) {
        context.dataStore.edit {
            if (email == null) it.remove(Keys.LOGGED_IN_EMAIL)
            else it[Keys.LOGGED_IN_EMAIL] = email
        }
    }

    suspend fun addCustomPlatform(name: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.CUSTOM_PLATFORMS] ?: emptySet()
            prefs[Keys.CUSTOM_PLATFORMS] = current + name
        }
    }

    suspend fun removeCustomPlatform(name: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.CUSTOM_PLATFORMS] ?: emptySet()
            prefs[Keys.CUSTOM_PLATFORMS] = current - name
        }
    }

    suspend fun setPlatformOrder(order: List<String>) {
        context.dataStore.edit { it[Keys.PLATFORM_ORDER] = order.joinToString("|") }
    }

    suspend fun setNotifySale(v: Boolean) { context.dataStore.edit { it[Keys.NOTIFY_SALE] = v } }
    suspend fun setNotifyEntry(v: Boolean) { context.dataStore.edit { it[Keys.NOTIFY_ENTRY] = v } }
    suspend fun setNotifyCalSync(v: Boolean) { context.dataStore.edit { it[Keys.NOTIFY_CAL_SYNC] = v } }
    suspend fun setNotifyDigest(v: Boolean) { context.dataStore.edit { it[Keys.NOTIFY_DIGEST] = v } }

    /** E2-4：每月發一張補打卡券（同月只發一次）。回傳是否有新發券。 */
    suspend fun grantMonthlyFreezeIfNeeded(currentMonth: String): Boolean {
        var granted = false
        context.dataStore.edit { p ->
            if (p[Keys.STREAK_FREEZE_MONTH] != currentMonth) {
                p[Keys.STREAK_FREEZE_MONTH] = currentMonth
                p[Keys.STREAK_FREEZE_TOKENS] = ((p[Keys.STREAK_FREEZE_TOKENS] ?: 0) + 1).coerceAtMost(3)
                granted = true
            }
        }
        return granted
    }

    /**
     * 追蹤 / 取消追蹤某位藝人。存原始名稱（顯示用），比對交給 artistFollowKey()。
     * 已追蹤過的同名藝人不重複加入。
     */
    suspend fun toggleFollowArtist(artist: String) {
        val name = artist.trim()
        if (name.isBlank()) return
        context.dataStore.edit { p ->
            val cur = p[Keys.FOLLOWED_ARTISTS] ?: emptySet()
            val key = artistFollowKey(name)
            val hit = cur.firstOrNull { artistFollowKey(it) == key }
            p[Keys.FOLLOWED_ARTISTS] = if (hit != null) cur - hit else cur + name
        }
    }

    /** 整份覆寫追蹤清單（雲端拉下來時用）。 */
    suspend fun setFollowedArtists(artists: Set<String>) {
        context.dataStore.edit { it[Keys.FOLLOWED_ARTISTS] = artists }
    }

    /** 更新「已通知過的追蹤藝人活動」集合。 */
    suspend fun setSeenFollowedEvents(keys: Set<String>) {
        context.dataStore.edit { it[Keys.SEEN_FOLLOWED_EVENTS] = keys }
    }

    /** E2-12：更新「已通知過的好友演出」集合（首次同時標記已初始化）。 */
    suspend fun setSeenFriendShows(keys: Set<String>) {
        context.dataStore.edit {
            it[Keys.SEEN_FRIEND_SHOWS] = keys
            it[Keys.FRIEND_SOCIAL_INIT] = true
        }
    }

    /** E2-4：用掉一張券並把某天記成「已補回」。券不足時不動作、回傳 false。 */
    suspend fun useFreezeOn(date: String): Boolean {
        var ok = false
        context.dataStore.edit { p ->
            val tokens = p[Keys.STREAK_FREEZE_TOKENS] ?: 0
            if (tokens > 0) {
                p[Keys.STREAK_FREEZE_TOKENS] = tokens - 1
                p[Keys.STREAK_FREEZE_DATES] = (p[Keys.STREAK_FREEZE_DATES] ?: emptySet()) + date
                ok = true
            }
        }
        return ok
    }

    suspend fun setHomeLocation(value: String?) {
        context.dataStore.edit {
            if (value == null) it.remove(Keys.HOME_LOCATION) else it[Keys.HOME_LOCATION] = value
        }
    }

    suspend fun incrementAdminGesture(): Int {
        var newCount = 0
        context.dataStore.edit { prefs ->
            newCount = (prefs[Keys.ADMIN_GESTURE_COUNT] ?: 0) + 1
            prefs[Keys.ADMIN_GESTURE_COUNT] = newCount
        }
        return newCount
    }

    suspend fun resetAdminGesture() {
        context.dataStore.edit { it[Keys.ADMIN_GESTURE_COUNT] = 0 }
    }
}
