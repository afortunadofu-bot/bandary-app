package com.bandary.presentation.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bandary.data.local.SettingsDataStore
import com.bandary.data.local.ThemeMode
import com.bandary.data.local.ThemePalette
import com.bandary.data.worker.DailyCheckInScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SettingsState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val palette: ThemePalette = ThemePalette.GREEN,
    val customColorHex: String? = null,
    val customColors: List<String> = emptyList(),
    val coverGradientLight: Boolean = false,
    val isPro: Boolean = false,
    val isProPlus: Boolean = false,
    val isAdmin: Boolean = false,
    val loggedInEmail: String? = null,
    val notifySale: Boolean = true,
    val notifyEntry: Boolean = true,
    val notifyCalSync: Boolean = false,
    val notifyDigest: Boolean = true,
    val checkInEnabled: Boolean = false,
    val checkInTime: String = "21:00",
    val homeBaseCity: String = "臺北",
    val customPlatforms: Set<String> = emptySet(),
    val platformOrder: List<String> = emptyList(),
    val avatarUri: String? = null,
    val nickname: String? = null,
    /**
     * 全 App 唯一對外顯示的名字：暱稱優先，沒設就退回 Google 帳號名稱 / email 前綴。
     * 「我的」頁、好友頁、排行榜、分享圖都讀這一個，才不會同一個人出現兩種稱呼。
     */
    val displayName: String = "",
    val defaultReminders: List<com.bandary.domain.model.ReminderOffset> =
        listOf(com.bandary.domain.model.ReminderOffset.ONE_WEEK, com.bandary.domain.model.ReminderOffset.ONE_DAY),
    val startTab: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    private val firestore: com.bandary.data.remote.FirestoreService,
    private val concertRepo: com.bandary.data.repository.ConcertRepository,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private data class ThemeBundle(
        val mode: ThemeMode, val palette: ThemePalette, val coverLight: Boolean,
        val customHex: String?, val customColors: List<String>
    )
    private data class AccountBundle(val isPro: Boolean, val isProPlus: Boolean, val isAdmin: Boolean, val email: String?)
    private data class NotifBundle(
        val sale: Boolean, val entry: Boolean, val cal: Boolean,
        val checkInEnabled: Boolean, val checkInTime: String
    )
    private data class PlatformBundle(
        val customs: Set<String>, val order: List<String>, val homeBaseCity: String
    )
    private data class ProfileBundle(
        val avatar: String?, val nickname: String?,
        val defaultReminders: List<com.bandary.domain.model.ReminderOffset>,
        val startTab: String?, val notifyDigest: Boolean
    )

    val state: StateFlow<SettingsState> = combine(
        combine(
            settings.themeMode, settings.themePalette, settings.coverGradientLight,
            settings.customColorHex, settings.customColors
        ) { m, p, cl, ch, cc ->
            ThemeBundle(m, p, cl, ch, cc)
        },
        combine(settings.isPro, settings.isProPlus, settings.isAdmin, settings.loggedInEmail) { p, pp, a, e ->
            AccountBundle(p, pp, a, e)
        },
        combine(
            settings.notifySale, settings.notifyEntry, settings.notifyCalSync,
            settings.checkInEnabled, settings.checkInTime
        ) { s, e, c, ce, ct -> NotifBundle(s, e, c, ce, ct) },
        combine(
            settings.customPlatforms, settings.platformOrder, settings.homeBaseCity
        ) { c, o, h -> PlatformBundle(c, o, h) },
        combine(settings.avatarUri, settings.nickname, settings.defaultReminders, settings.startTab, settings.notifyDigest) { a, n, dr, st, nd -> ProfileBundle(a, n, dr, st, nd) }
    ) { theme, account, notif, plat, profile ->
        SettingsState(
            themeMode = theme.mode,
            palette = theme.palette,
            customColorHex = theme.customHex,
            customColors = theme.customColors,
            coverGradientLight = theme.coverLight,
            isPro = account.isPro,
            isProPlus = account.isProPlus,
            isAdmin = account.isAdmin,
            loggedInEmail = account.email,
            notifySale = notif.sale,
            notifyEntry = notif.entry,
            notifyCalSync = notif.cal,
            notifyDigest = profile.notifyDigest,
            checkInEnabled = notif.checkInEnabled,
            checkInTime = notif.checkInTime,
            homeBaseCity = plat.homeBaseCity,
            customPlatforms = plat.customs,
            platformOrder = plat.order,
            // 自己設過的頭貼優先；沒設過就用 Google 帳號的大頭貼。
            avatarUri = profile.avatar?.takeIf { it.isNotBlank() } ?: firestore.accountPhotoUrl(),
            nickname = profile.nickname,
            displayName = profile.nickname?.takeIf { it.isNotBlank() } ?: firestore.accountDisplayName(),
            defaultReminders = profile.defaultReminders,
            startTab = profile.startTab
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsState())

    /**
     * 有沒有任何演唱會紀錄。null＝還在讀，呼叫端要等——起始分頁只會在 NavHost 第一次組成時
     * 讀一次，先用錯的值渲染之後就改不掉了。
     *
     * 用途：使用者沒有自己指定起始分頁時，決定要落在「演唱會」還是「活動」。
     */
    val hasAnyConcert: StateFlow<Boolean?> =
        concertRepo.getAllConcerts()
            .map { list -> list.isNotEmpty() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * 「新增演出時分享到活動頁」：存雲端（users/{uid}），App 與網頁共用。
     * null＝未登入或還在讀，畫面不顯示開關。
     */
    private val _shareNewShows = kotlinx.coroutines.flow.MutableStateFlow<Boolean?>(null)
    val shareNewShows: StateFlow<Boolean?> = _shareNewShows

    init {
        viewModelScope.launch {
            // 讀取**失敗**時維持 null（那一列就不顯示），不要退回 true。
            //
            // 送出端 (FreeEventsRepository.submitFromConcert) 把讀取失敗當成「不送」
            // ——那是刻意的，寧可少送一筆也不要違反使用者的選擇。
            // UI 這邊若退回 true，使用者會看到開關是開的、實際上卻不會送，
            // 兩邊對同一個狀態講不同的話。讀不到就不要宣稱知道。
            //
            // （欄位不存在不算失敗：那種情況 fetch 本身就回 true，兩邊一致。）
            if (firestore.isSignedIn()) _shareNewShows.value = firestore.fetchShareNewShowsToEvents().getOrNull()
        }
    }

    /** 登入後是否已問過「要不要連結 Google 日曆」。 */
    val calendarLinkAsked: StateFlow<Boolean> =
        settings.calendarLinkAsked.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), true)

    fun markCalendarLinkAsked() = viewModelScope.launch { settings.setCalendarLinkAsked(true) }

    fun setShareNewShows(enabled: Boolean) = viewModelScope.launch {
        val previous = _shareNewShows.value
        _shareNewShows.value = enabled
        firestore.setShareNewShowsToEvents(enabled).onFailure { _shareNewShows.value = previous }
    }

    /** 換頭貼：把選的圖複製進 App 內部檔案（穩定、可長期讀）後存 URI。 */
    fun setAvatar(uri: android.net.Uri) = viewModelScope.launch {
        val saved = withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val dir = java.io.File(context.filesDir, "avatar").apply { mkdirs() }
                val file = java.io.File(dir, "avatar_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { input.copyTo(it) }
                }
                android.net.Uri.fromFile(file).toString()
            }.getOrNull()
        }
        if (saved != null) {
            settings.setAvatarUri(saved)
            // 頭貼跟著帳號走：已登入就立刻備份，換手機或重灌後登入會自動帶回來。
            if (firestore.isSignedIn()) runCatching { firestore.backupProfile(settings.nickname.first(), saved) }
        }
    }

    fun setNickname(name: String?) = viewModelScope.launch {
        settings.setNickname(name)
        // 暱稱是好友頁與排行榜顯示的名字，改完要立刻讓雲端（與網頁版）跟上，
        // 不然要等到下次發布共享日曆才會同步，中間顯示的都還是舊名字。
        firestore.preferredDisplayName = name?.takeIf { it.isNotBlank() }
        runCatching { firestore.publishNickname(name) }
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }
    fun setPalette(p: ThemePalette) = viewModelScope.launch { settings.setThemePalette(p) }
    fun selectCustomColor(hex: String) = viewModelScope.launch { settings.selectCustomColor(hex) }
    /** 新增自訂色（最多 2 個）；回傳是否成功（滿了回 false）。 */
    fun addCustomColor(hex: String, onResult: (Boolean) -> Unit = {}) = viewModelScope.launch {
        onResult(settings.addCustomColor(hex))
    }
    fun removeCustomColor(hex: String) = viewModelScope.launch { settings.removeCustomColor(hex) }
    fun setCoverGradientLight(v: Boolean) = viewModelScope.launch { settings.setCoverGradientLight(v) }
    fun setNotifySale(v: Boolean) = viewModelScope.launch { settings.setNotifySale(v) }
    fun setNotifyEntry(v: Boolean) = viewModelScope.launch { settings.setNotifyEntry(v) }
    fun setDefaultReminders(offsets: List<com.bandary.domain.model.ReminderOffset>) =
        viewModelScope.launch { settings.setDefaultReminders(offsets) }
    fun setNotifyCalSync(v: Boolean) = viewModelScope.launch { settings.setNotifyCalSync(v) }
    fun setNotifyDigest(v: Boolean) = viewModelScope.launch { settings.setNotifyDigest(v) }

    // 聯絡信箱（管理員在後台維護；「聯絡我們」顯示，讀不到就用 UI 端預設值）。
    private val _contactEmails = kotlinx.coroutines.flow.MutableStateFlow<List<String>>(emptyList())
    val contactEmails: StateFlow<List<String>> = _contactEmails
    fun loadContactEmails() = viewModelScope.launch {
        firestore.fetchContactEmails().onSuccess { _contactEmails.value = it }
    }

    // 最新版本（管理員在後台公告；設定頁「最新版本」用來跟本機版本比對）。
    // null = 還沒讀到 / 雲端沒設定 / 離線，UI 端就只顯示本機版本，不亂報「已是最新」。
    private val _latestVersion =
        kotlinx.coroutines.flow.MutableStateFlow<com.bandary.data.remote.FirestoreService.AppVersionInfo?>(null)
    val latestVersion: StateFlow<com.bandary.data.remote.FirestoreService.AppVersionInfo?> = _latestVersion
    fun loadLatestVersion() = viewModelScope.launch {
        firestore.fetchLatestVersion().onSuccess { _latestVersion.value = it }
    }

    fun setCheckInEnabled(v: Boolean) = viewModelScope.launch {
        settings.setCheckInEnabled(v)
        if (v) DailyCheckInScheduler.schedule(context, settings.checkInTime.first())
        else DailyCheckInScheduler.cancel(context)
    }
    fun setCheckInTime(time: String) = viewModelScope.launch {
        settings.setCheckInTime(time)
        if (settings.checkInEnabled.first()) DailyCheckInScheduler.schedule(context, time)
    }
    /**
     * 記一次外站連結被點擊（[com.bandary.domain.model.OutboundLinks] 裡的 id）。
     *
     * **失敗不要影響使用者**——他按那一列的目的是去那個網站，
     * 統計寫不進去是我們的問題，不是他的。所以不回報結果、不擋住開啟網頁。
     */
    fun recordOutboundClick(linkId: String) = viewModelScope.launch {
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        }.getOrDefault("")
        runCatching { firestore.touchOutboundClick(linkId, settings.installId(), version) }
    }

    fun setHomeBaseCity(city: String) = viewModelScope.launch { settings.setHomeBaseCity(city) }
    fun setStartTab(route: String?) = viewModelScope.launch { settings.setStartTab(route) }
    fun addCustomPlatform(name: String) = viewModelScope.launch { settings.addCustomPlatform(name) }
    fun removeCustomPlatform(name: String) = viewModelScope.launch { settings.removeCustomPlatform(name) }

    /** 回傳目前有多少場演唱會的購票平台使用了 [name]（用於移除前提醒）。 */
    fun countConcertsUsingPlatform(name: String, onResult: (Int) -> Unit) = viewModelScope.launch {
        val n = concertRepo.getAllConcerts().first().count { it.ticketPlatform == name }
        onResult(n)
    }
    fun setPlatformOrder(order: List<String>) = viewModelScope.launch { settings.setPlatformOrder(order) }
    fun unlockProManually() = viewModelScope.launch { settings.setPro(true) }
    /** 刪除帳號（雲端資料＋登入帳號）。結果回到主執行緒；成功後由畫面接著做完整登出。 */
    fun deleteAccount(onResult: (Result<Unit>) -> Unit) = viewModelScope.launch {
        onResult(firestore.deleteMyAccount())
    }

    /**
     * 登出。
     *
     * **不清掉 Pro**：折扣碼解鎖是「這個人買到／換到的東西」，不是登入狀態的一部分。
     * 以前這裡連 setPro(false) 一起做，於是使用者一登出解鎖就沒了
     * （實機回報「折扣碼要綁帳號，不能因為登出就不見」）；
     * 更糟的是在未登入狀態兌換的碼根本沒備份到雲端，登出等於永久遺失。
     *
     * **管理員仍然清掉**：那是權限不是所有物，登出後本來就不該留著；
     * 而且 Firestore 規則那邊也認帳號不認本機旗標，留著只會讓畫面顯示一堆按不動的功能。
     * 重新登入時 afterSignIn() 會從雲端把它帶回來。
     */
    fun logout() = viewModelScope.launch {
        settings.setLoggedInEmail(null)
        settings.setAdmin(false)
    }

    /**
     * 兌換折扣碼。大小寫 / 前後空白都接受。
     * - Pro 解鎖碼：解鎖全部進階功能。
     * - 管理員碼：額外開啟管理員功能（新增免費活動等）。
     * 先比對內建碼（離線可用），再查雲端 discount_codes（管理員可自行新增 / 刪除）。
     */
    suspend fun tryAdminCode(code: String): Boolean {
        val c = code.trim().uppercase()
        // 先查雲端：雲端碼可設效期 / 發行數量 / 權限，並可覆寫內建碼。
        when (val r = firestore.redeemDiscountCode(c)) {
            is com.bandary.data.remote.FirestoreService.RedeemOutcome.Ok -> {
                settings.setPro(true)
                if (r.effect == "admin") settings.setAdmin(true)
                // 記到帳號上，換手機登入同一個帳號才帶得回來。
                firestore.backupEntitlements(pro = true, admin = r.effect == "admin")
                return true
            }
            // 存在但過期 / 額滿：明確拒絕，不讓內建碼繞過限制。
            com.bandary.data.remote.FirestoreService.RedeemOutcome.Invalid -> return false
            // 雲端沒有這組 → 退回內建碼（離線可用、無限）。
            com.bandary.data.remote.FirestoreService.RedeemOutcome.NotFound -> Unit
        }
        when (c) {
            in PRO_CODES -> {
                settings.setPro(true)
                firestore.backupEntitlements(pro = true, admin = false)
                return true
            }
            in ADMIN_CODES -> {
                settings.setPro(true); settings.setAdmin(true)
                firestore.backupEntitlements(pro = true, admin = true)
                return true
            }
        }
        return false
    }

    // ── 官方通知（民眾端收信匣）──────────────────────────────
    private val _announcements = kotlinx.coroutines.flow.MutableStateFlow<List<com.bandary.data.remote.FirestoreService.Announcement>>(emptyList())
    val announcements: StateFlow<List<com.bandary.data.remote.FirestoreService.Announcement>> = _announcements
    val lastReadAnnouncementAt: StateFlow<Long> = settings.lastReadAnnouncementAt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    // 未讀數（最新通知時間 > 上次已讀時間）
    val unreadAnnouncements: StateFlow<Int> = combine(_announcements, lastReadAnnouncementAt) { list, lastRead ->
        list.count { it.createdAt > lastRead }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun refreshAnnouncements() = viewModelScope.launch {
        firestore.fetchAnnouncements().onSuccess { _announcements.value = it }
    }
    fun markAnnouncementsRead() = viewModelScope.launch { settings.setLastReadAnnouncementAt(System.currentTimeMillis()) }

    // ── 管理員：編輯雲端折扣碼 ─────────────────────────────────
    private val _cloudCodes = kotlinx.coroutines.flow.MutableStateFlow<List<com.bandary.data.remote.FirestoreService.DiscountCode>>(emptyList())
    val cloudCodes: StateFlow<List<com.bandary.data.remote.FirestoreService.DiscountCode>> = _cloudCodes

    // 雲端寫入的結果訊息。原本失敗是靜默的——按了「設為雲端」沒反應，看起來就像功能壞掉，
    // 實際上多半是 Firestore 規則的 isAdmin() 不通過（要有 admins/{uid} 文件）。
    private val _codeMsg = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val codeMsg: StateFlow<String?> = _codeMsg
    fun clearCodeMsg() { _codeMsg.value = null }

    /** 目前登入帳號的 uid，用來在 Firebase Console 建立 admins/{uid}。 */
    val myUid: String? get() = firestore.currentUid()

    fun refreshCloudCodes() = viewModelScope.launch {
        firestore.fetchDiscountCodes()
            .onSuccess { _cloudCodes.value = it }
            .onFailure { _codeMsg.value = "讀取雲端折扣碼失敗：${it.message}" }
    }

    fun saveCloudCode(
        code: String, effect: String, note: String?,
        maxRedemptions: Int? = null, expiresAt: Long? = null
    ) = viewModelScope.launch {
        firestore.saveDiscountCode(code, effect, note, maxRedemptions, expiresAt)
            .onSuccess { _codeMsg.value = "已寫入 ${code.trim().uppercase()}"; refreshCloudCodes() }
            .onFailure { _codeMsg.value = "寫入失敗：${it.message}" }
    }

    /**
     * 把所有還沒上雲的內建碼一次種進 Firestore（已存在的略過，不覆寫既有設定與已兌換數）。
     * 只在使用者於折扣碼管理裡確認後才呼叫。
     */
    fun seedBuiltInCodesToCloud() = viewModelScope.launch {
        val existing = _cloudCodes.value.map { it.code.uppercase() }.toSet()
        val todo = (PRO_CODES.map { it to "pro" } + ADMIN_CODES.map { it to "admin" })
            .filter { (c, _) -> c.uppercase() !in existing }
        if (todo.isEmpty()) { _codeMsg.value = "內建碼都已經在雲端了"; return@launch }
        var ok = 0
        var lastErr: String? = null
        todo.forEach { (c, eff) ->
            firestore.saveDiscountCode(c, eff, "由內建碼轉入")
                .onSuccess { ok++ }
                .onFailure { lastErr = it.message }
        }
        _codeMsg.value = if (ok == todo.size) "已種入 $ok 組內建碼"
            else "種入 $ok / ${todo.size} 組，失敗原因：$lastErr"
        refreshCloudCodes()
    }

    fun deleteCloudCode(code: String) = viewModelScope.launch {
        firestore.deleteDiscountCode(code)
            .onSuccess { _codeMsg.value = "已刪除 $code"; refreshCloudCodes() }
            .onFailure { _codeMsg.value = "刪除失敗：${it.message}" }
    }

    companion object {
        // Pro 解鎖碼（享全部進階功能）
        val PRO_CODES = setOf("BANDARY-PRO", "BANDARY-VIP", "LIVEHOUSE")
        // 管理員碼（Pro + 管理員，可新增免費活動）
        val ADMIN_CODES = setOf("BANDARY-1218", "DEV-ADMIN", "BANDARY-ADMIN")
    }

    suspend fun bumpAdminGesture(): Boolean {
        val n = settings.incrementAdminGesture()
        return if (n >= 7) {
            settings.resetAdminGesture()
            true
        } else false
    }
}
