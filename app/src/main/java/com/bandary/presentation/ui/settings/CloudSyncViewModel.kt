package com.bandary.presentation.ui.settings

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bandary.data.local.SettingsDataStore
import com.bandary.data.remote.FirestoreService
import com.bandary.data.repository.ConcertRepository
import com.bandary.data.repository.DailySongRepository
import com.bandary.data.worker.AutoBackupScheduler
import com.bandary.domain.model.typeLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CloudState(
    val configured: Boolean = false,
    val connected: Boolean = false,
    val email: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
    /** 登入後發現雲端有備份：問使用者要不要還原到這支手機。null＝不用問。 */
    val restorePrompt: RestorePrompt? = null,
    /** 這次備份會讓雲端照片變少：先停下來問使用者。null＝不用問。 */
    val shrinkPrompt: FirestoreService.ShrinkPreview? = null
)

data class RestorePrompt(
    val cloudConcerts: Int,
    val cloudSongs: Int,
    val lastBackupAt: Long?,
    /** 雲端有、這支手機沒有（或雲端比較新）的場次數：還原實際會帶進來幾場。 */
    val newConcerts: Int,
    val newSongs: Int
)

@HiltViewModel
class CloudSyncViewModel @Inject constructor(
    private val firestore: FirestoreService,
    private val concertRepo: ConcertRepository,
    private val dailySongRepo: DailySongRepository,
    private val settings: SettingsDataStore,
    @param:ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(CloudState())
    val state: StateFlow<CloudState> = _state.asStateFlow()

    val autoBackupMode: StateFlow<String> =
        settings.autoBackup.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "daily")

    val lastBackupText: StateFlow<String?> =
        // 明確給 Any?：六個不同型別的 flow 合起來時，T 會被推成
        // `Comparable<*> & java.io.Serializable` 這種交集型別。能編，但下面全靠
        // `values[i] as T` 取值，推斷一變就是執行時才炸。寫出來比較誠實。
        kotlinx.coroutines.flow.combine<Any?, String?>(
            settings.lastBackupAt, settings.lastBackupConcerts,
            settings.lastBackupSongs, settings.lastBackupAuto, settings.autoBackup,
            settings.lastBackupPhotos
        ) { values ->
            val ts = values[0] as Long
            val concerts = values[1] as Int
            val songs = values[2] as Int
            val wasAuto = values[3] as Boolean
            val mode = values[4] as String
            val photos = values[5] as Int
            if (ts <= 0L) {
                // 從沒備份過時也要講清楚排程狀態，否則使用者只看到一片空白，
                // 無從判斷自動備份到底有沒有在運作。
                when (mode) {
                    "daily" -> "自動備份：每天一次（尚未跑過第一次）"
                    "monthly" -> "自動備份：每月一次（尚未跑過第一次）"
                    else -> null
                }
            } else {
                val time = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.TAIWAN).format(java.util.Date(ts))
                val how = if (wasAuto) "自動" else "手動"
                val schedule = when (mode) {
                    "daily" -> "，之後每天自動備份一次"
                    "monthly" -> "，之後每月自動備份一次"
                    else -> "，自動備份目前關閉"
                }
                "上次備份（$how）：$time$schedule\n共 $concerts 場演唱會、$songs 首選歌、$photos 張照片"
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * 「照片有風險」：有照片、卻從來沒備份過。
     *
     * 選圖時 ImageStore 會把圖複製進 App 私有空間，所以在相簿刪掉或搬移原圖都不影響；
     * 但那份複本隨 App 一起消失——解除安裝、清除資料、換手機就真的沒了。
     * 自動備份預設是關的，所以這種人等於完全沒有保險，值得主動提醒一次。
     * 沒照片、或已經備份過的人不顯示，避免變成人人都看到的雜訊。
     */
    val photosAtRisk: StateFlow<Int> =
        kotlinx.coroutines.flow.combine(
            concertRepo.getAllConcerts(), settings.lastBackupAt
        ) { concerts, lastBackup ->
            if (lastBackup > 0L) 0
            else concerts.count { c ->
                !c.photoUri.isNullOrBlank() || c.photoUris.isNotEmpty() ||
                    !c.seatMapUri.isNullOrBlank() || c.timetableUris.isNotEmpty()
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun setAutoBackup(mode: String) {
        viewModelScope.launch {
            settings.setAutoBackup(mode)
            AutoBackupScheduler.apply(appContext, mode)
        }
    }

    init { refresh() }

    fun refresh() {
        _state.update {
            it.copy(
                configured = firestore.isConfigured(),
                connected = firestore.isSignedIn(),
                email = firestore.currentEmail()
            )
        }
    }

    fun signInIntent(): Intent = firestore.signInIntent()

    /** 登入結果的一次性提示（Toast）。登入按鈕在頁面最上方，結果卻只寫在下面的備份卡片，使用者會以為按了沒反應。 */
    private val _toast = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val toast: kotlinx.coroutines.flow.StateFlow<String?> = _toast
    fun clearToast() { _toast.value = null }

    fun handleSignIn(data: Intent?) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            val result = firestore.signInWithIntent(data)
            result.onSuccess { email ->
                if (email != null) settings.setLoggedInEmail(email)
                _state.update { it.copy(busy = false, message = "已登入 $email") }
                _toast.value = "已登入 $email"
                afterSignIn()
            }.onFailure { e ->
                val text = "登入失敗：${signInErrorText(e)}"
                _state.update { it.copy(busy = false, message = text) }
                _toast.value = text
            }
            refresh()
        }
    }

    /**
     * 把 Google 登入的錯誤代碼翻成看得懂的原因。代碼一定要留著：
     * 10（DEVELOPER_ERROR）幾乎都是簽章指紋沒登記到 Firebase，這是 Play 版最常見的登入失敗原因。
     */
    private fun signInErrorText(e: Throwable): String {
        val api = (e as? com.google.android.gms.common.api.ApiException)
            ?: (e.cause as? com.google.android.gms.common.api.ApiException)
        return when (api?.statusCode) {
            null -> e.message ?: "未知錯誤"
            12501 -> "已取消登入"
            7 -> "網路連線失敗，請稍後再試（代碼 7）"
            10 -> "App 簽章未在 Firebase 登記（代碼 10）"
            else -> "Google 登入錯誤（代碼 ${api.statusCode}）"
        }
    }

    /** 登入後檢查雲端備份時抓回來的資料，按下「還原」時直接用，不必再下載一次。 */
    private var pendingSummary: FirestoreService.CloudSummary? = null

    /**
     * 登入成功後：
     * 1. 頭貼與暱稱跟著帳號走：這支手機還沒設定的話，直接從雲端帶回來。
     * 2. 雲端有備份、而且有這支手機沒有的資料 → 問要不要還原。
     * 3. 雲端完全沒資料、這支手機有 → 立刻備份一次，不必等自動備份。
     */
    private fun afterSignIn() {
        viewModelScope.launch {
            restoreProfileIfEmpty()
            // 折扣碼解鎖的 Pro / 管理員跟著帳號走，換手機登入就帶回來（只會開啟、不會關掉）。
            val (cloudPro, cloudAdmin) = firestore.restoreEntitlements()
            if (cloudPro) settings.setPro(true)
            if (cloudAdmin) settings.setAdmin(true)
            // 反向補寫：在**未登入**狀態兌換的折扣碼，當下沒有 uid 可以寫，
            // backupEntitlements 會靜靜失敗，那組碼就只存在這支手機上。
            // 登入的這一刻補上去，之後換裝置才帶得回來。
            val localPro = settings.isProPurchased.first()
            val localAdmin = settings.isAdmin.first()
            if ((localPro && !cloudPro) || (localAdmin && !cloudAdmin)) {
                firestore.backupEntitlements(pro = localPro, admin = localAdmin)
            }
            val summary = firestore.fetchCloudSummary().getOrNull() ?: return@launch
            val localConcerts = concertRepo.getAllForSync().associateBy { it.id }
            val localSongIds = dailySongRepo.getAllSongs().first().map { it.id }.toSet()
            val newConcerts = summary.concerts.count { c ->
                val l = localConcerts[c.id]
                l == null || c.updatedAt > l.updatedAt
            }
            val newSongs = summary.songs.count { it.id !in localSongIds }
            if (newConcerts > 0 || newSongs > 0) {
                pendingSummary = summary
                _state.update {
                    it.copy(restorePrompt = RestorePrompt(summary.concerts.size, summary.songs.size, summary.lastBackupAt, newConcerts, newSongs))
                }
            } else if (summary.concerts.isEmpty() && summary.songs.isEmpty() && localConcerts.isNotEmpty()) {
                backup()
            }
        }
    }

    fun dismissRestorePrompt() {
        pendingSummary = null
        _state.update { it.copy(restorePrompt = null) }
    }

    fun confirmRestorePrompt() {
        val summary = pendingSummary
        _state.update { it.copy(restorePrompt = null) }
        pendingSummary = null
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            val msg = if (summary != null) restoreFrom(summary)
            else firestore.fetchCloudSummary().fold({ restoreFrom(it) }, { "還原失敗：${it.message}" })
            _state.update { it.copy(busy = false, message = msg) }
            _toast.value = msg
            // 還原完立刻同步一次：這支手機原本就有、雲端沒有的，也順便備份上去。
            backup()
        }
    }

    private suspend fun restoreProfileIfEmpty() {
        runCatching {
            val localAvatar = settings.avatarUri.first()
            val localNick = settings.nickname.first()
            if (!localAvatar.isNullOrBlank() && !localNick.isNullOrBlank()) return
            val p = firestore.restoreProfile()
            if (localAvatar.isNullOrBlank()) p.avatarFileUri?.let { settings.setAvatarUri(it) }
            if (localNick.isNullOrBlank()) p.nickname?.let { settings.setNickname(it) }
        }
    }

    /**
     * 合併式還原：只帶回「這支手機沒有、或雲端比較新」的場次與選歌，不覆蓋本機較新的內容，
     * 也不刪除任何本機資料。場次用 applyFromCloud 寫入，保留雲端的 updatedAt，不會被當成本機新改的。
     */
    private suspend fun restoreFrom(summary: FirestoreService.CloudSummary): String = runCatching {
        val local = concertRepo.getAllForSync().associateBy { it.id }
        val toApply = summary.concerts.filter { c ->
            val l = local[c.id]
            l == null || c.updatedAt > l.updatedAt
        }
        // 換手機 / 重灌後原本的圖片檔已不存在：換成從雲端下載回本機的檔案。
        firestore.materializeImages(toApply).forEach { concertRepo.applyFromCloud(it) }
        // 這支手機已經有、而且不比雲端舊的場次，以前還原時完全不看——雲端多的照片就還原不回來。
        // 現在把雲端有、本機沒有的照片補進來（兩邊取多的），文字資料與 updatedAt 保留本機的。
        val applied = toApply.map { it.id }.toSet()
        val photoMerged = summary.concerts.mapNotNull { c ->
            val l = local[c.id] ?: return@mapNotNull null
            if (c.id in applied || l.deletedAt != null || c.deletedAt != null) return@mapNotNull null
            if (com.bandary.data.util.ConcertImageMerge.missingCount(l, c) == 0) return@mapNotNull null
            com.bandary.data.util.ConcertImageMerge.union(l, c)
        }
        val photosBack = photoMerged.sumOf { m -> com.bandary.data.util.ConcertImageMerge.missingCount(local.getValue(m.id), m) }
        firestore.materializeImages(photoMerged).forEach { concertRepo.updateImagesOnly(it) }
        val localSongIds = dailySongRepo.getAllSongs().first().map { it.id }.toSet()
        val newSongs = summary.songs.filter { it.id !in localSongIds }
        newSongs.forEach { dailySongRepo.saveDailySong(it) }
        restoreProfileIfEmpty()
        "已從雲端還原 ${toApply.size} 場演唱會、${newSongs.size} 首選歌" +
            (if (photosBack > 0) "，並補回 $photosBack 張照片" else "")
    }.getOrElse { "還原失敗：${it.message}" }

    /** 照片沒有全部備份成功時，講清楚是哪一種問題。 */
    /**
     * 備份結果的照片那一段。**不管成功與否都要講張數**——
     * 以前只有出事才顯示細節，順利時只寫「同步完成」，使用者根本不知道照片到底有沒有上去
     * （照片備份曾經整整壞了一段時間都沒人發現）。
     */
    private fun imageReportText(r: FirestoreService.ImageBackupReport): String = buildString {
        append("照片：新上傳 ${r.uploaded} 張、先前已備份 ${r.alreadyBackedUp} 張")
        if (r.unreadable > 0) append("；${r.unreadable} 張在這支手機上已找不到原檔，無法備份")
        if (r.failed > 0) append("；${r.failed} 張上傳失敗（${r.firstError ?: "未知原因"}），請稍後再按一次備份")
        // 講出來但不動它。使用者要看得到「雲端還留著這些」，
        // 才不會以為備份完就是雲端的全貌。
        if (r.orphanedInCloud > 0) {
            append("；雲端另有 ${r.orphanedInCloud} 張沒有被任何場次引用，已保留不刪")
        }
    }

    /**
     * 備份前先比對雲端：本機版本比較新、但照片比雲端少的場次，直接備份會把雲端的照片蓋掉。
     * 有這種情況就先跳確認視窗（合併 / 以本機為準 / 取消），沒有就照常備份。
     */
    fun backup() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null, shrinkPrompt = null) }
            val preview = firestore.previewShrink(concertRepo.getAllForSync()).getOrNull()
            if (preview != null && preview.photos > 0) {
                _state.update { it.copy(busy = false, shrinkPrompt = preview) }
            } else {
                runBackup(FirestoreService.ImageSyncMode.MERGE)
            }
        }
    }

    /** 確認視窗的選擇：overwrite = false 兩邊取多的；true 以本機為準（雲端照片會變少）。 */
    fun confirmShrink(overwrite: Boolean) {
        _state.update { it.copy(shrinkPrompt = null) }
        viewModelScope.launch {
            runBackup(if (overwrite) FirestoreService.ImageSyncMode.OVERWRITE else FirestoreService.ImageSyncMode.MERGE)
        }
    }

    fun cancelShrink() = _state.update { it.copy(shrinkPrompt = null, message = "已取消備份，雲端資料沒有變動") }

    private suspend fun runBackup(mode: FirestoreService.ImageSyncMode) {
        _state.update { it.copy(busy = true, message = null) }
        // 演唱會走雙向同步（含垃圾桶墓碑）；雲端較新的先寫回本機，再回報結果。
        val r1 = firestore.syncConcerts(concertRepo.getAllForSync(), mode)
        // 從雲端補回來的照片先下載成本機檔，畫面上才看得到。
        r1.getOrNull()?.pulled?.let { firestore.materializeImages(it) }?.forEach { concertRepo.applyFromCloud(it) }
        val songs = dailySongRepo.getAllSongs().first()
        val deletedSongs = settings.pendingSongDeletes.first()
        val r2 = firestore.backupDailySongs(songs, deletedSongs)
        if (r2.isSuccess) settings.clearPendingSongDeletes(deletedSongs)
        // 圖片一起備份（壓縮後存雲端；已備份過的不重傳）。失敗不影響資料備份結果。
        // **備份一律不刪雲端照片。** 以前選「以本機為準」時會順手清掉雲端沒被引用的圖，
        // 而「移除讀不到的照片」剛好會把本機欄位清空——兩個一起用就是把雲端那份也刪了
        // （實機災情：50 幾張剩 7 張，而且還原不回來）。
        // 要刪改走「清理雲端未使用的照片」，由使用者看到張數後自己確認。
        val concerts = concertRepo.getAllConcerts().first()
        val r3 = firestore.backupImages(concerts)
        firestore.backupProfile(settings.nickname.first(), settings.avatarUri.first())
        val photo = r3.getOrNull()
        val msg = if (r1.isSuccess && r2.isSuccess) {
            val sync = r1.getOrNull()
            settings.setLastBackup(
                System.currentTimeMillis(), concerts.size, r2.getOrDefault(0),
                // 雲端共幾張＝這次新上傳 + 先前已備份；拿不到就傳 -1，保留上一次的數字
                photos = photo?.let { it.uploaded + it.alreadyBackedUp } ?: -1
            )
            settings.setLastBackupAuto(false)   // 這條路徑是使用者自己按的
            val pulled = sync?.pulled?.size ?: 0
            val merged = sync?.photosMerged ?: 0
            val head = buildString {
                append(if (pulled > 0) "同步完成，從雲端更新了 $pulled 場" else "同步完成")
                if (merged > 0) append("，兩邊合併補回 $merged 張照片")
            }
            when {
                !r3.isSuccess -> "$head，但照片備份失敗：${r3.exceptionOrNull()?.message}"
                photo != null -> "$head。${imageReportText(photo)}"
                else -> head
            }
        } else "同步失敗：${(r1.exceptionOrNull() ?: r2.exceptionOrNull())?.message}"
        _state.update { it.copy(busy = false, message = msg) }
    }

    /**
     * 把所有場次裡「本機已經找不到原檔」的照片欄位移除。
     * 這種欄位在畫面上是破圖、照片張數照算、備份也傳不上去，只能整批清。
     * 只動圖片欄位，其他資料不變；存回後 updatedAt 會更新，下次備份就同步到雲端，換裝置不會再帶回來。
     */
    /** 雲端未被引用的照片張數；確認對話框要顯示的數字。 */
    suspend fun countOrphanPhotos(): Int =
        firestore.countOrphanImages(concertRepo.getAllConcerts().first())

    /** 使用者確認後才刪。[expected] 是對話框上顯示的張數，對不上就整個放棄。 */
    fun deleteOrphanPhotos(expected: Int) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            val concerts = concertRepo.getAllConcerts().first()
            val r = firestore.deleteOrphanImages(concerts, expected)
            _state.update {
                it.copy(
                    busy = false,
                    message = r.fold(
                        { n -> "已刪除雲端 $n 張未使用的照片" },
                        { e -> "刪除失敗：${e.message}" }
                    )
                )
            }
        }
    }

    fun cleanupMissingPhotos() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            // 先試著從雲端把照片抓回來：換手機後「找不到原檔」的照片多半雲端還有，
            // 直接清掉再備份，會連雲端那份一起弄丟。
            val before = concertRepo.getAllConcerts().first()
            val repaired = firestore.repairLocalImages(before)
            repaired.forEachIndexed { i, c -> if (c !== before[i]) concertRepo.updateImagesOnly(c) }
            val concerts = repaired
            var removed = 0
            var touched = 0
            concerts.forEach { c ->
                val missing = firestore.missingImageUris(c).toSet()
                if (missing.isEmpty()) return@forEach
                removed += missing.size
                touched++
                concertRepo.saveConcert(
                    c.copy(
                        photoUri = c.photoUri?.takeIf { it !in missing },
                        seatMapUri = c.seatMapUri?.takeIf { it !in missing },
                        photoUris = c.photoUris.filterNot { it in missing },
                        timetableUris = c.timetableUris.filterNot { it in missing },
                        dayCovers = c.dayCovers.map { if (it in missing) "" else it }
                    )
                )
            }
            val msg = if (removed == 0) "沒有找不到原檔的照片"
            else "已移除 $removed 張找不到原檔的照片（$touched 場）。記得再備份一次，換裝置就不會再帶回來。"
            _state.update { it.copy(busy = false, message = msg) }
            _toast.value = msg
        }
    }

    /**
     * 送出爬蟲來源建議。回傳 null＝成功，否則是要顯示的錯誤訊息。
     * 這只是「建議」：寫進 crawl_source_suggestions，排程讀的是 crawl_sources，
     * 管理員審過才會手動建過去——不會有人送了網址就讓伺服器去抓。
     */
    suspend fun suggestSource(url: String, label: String, note: String): String? =
        firestore.suggestCrawlSource(url, label, note)
            .fold({ null }, { it.message ?: "送出失敗" })

    fun restore() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            val msg = firestore.fetchCloudSummary().fold({ restoreFrom(it) }, { "還原失敗：${it.message}" })
            _state.update { it.copy(busy = false, message = msg) }
            _toast.value = msg
        }
    }

    /** 取消 / 刪除雲端上所有備份。 */
    fun deleteBackup() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            firestore.deleteAllBackup()
                .onSuccess {
                    settings.setLastBackup(0L, 0, 0, photos = 0)
                    _state.update { it.copy(busy = false, message = "已刪除雲端所有備份") }
                }
                .onFailure { e -> _state.update { it.copy(busy = false, message = "刪除失敗：${e.message}") } }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            firestore.signOut()
            settings.setLoggedInEmail(null)
            _state.update { it.copy(message = "已登出雲端") }
            refresh()
        }
    }

    /**
     * 登入 / 換帳號共用入口：先登出目前的 Google 連線，再由 UI 叫出帳號選擇畫面。
     * 必須先登出，選擇器才會出現——否則會沿用裝置上快取的上一個帳號，導致「無法換帳號」。
     * 未登入時呼叫也安全（登出為 no-op），因此登入與切換帳號都走這條路徑。
     */
    fun signIn(onReady: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            firestore.signOut()
            settings.setLoggedInEmail(null)
            refresh()
            _state.update { it.copy(busy = false) }
            onReady()
        }
    }

    /** 匯出演唱會到本地 CSV（使用者自選存檔位置的 Uri）。免登入即可用。 */
    fun exportConcertsCsv(uri: android.net.Uri) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            val result = runCatching {
                val concerts = concertRepo.getAllConcerts().first()
                val csv = buildConcertsCsv(concerts)
                appContext.contentResolver.openOutputStream(uri)?.use { os ->
                    // UTF-8 BOM：Excel 開啟中文才不會亂碼。
                    os.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                    os.write(csv.toByteArray(Charsets.UTF_8))
                } ?: error("無法開啟檔案")
                concerts.size
            }
            result
                .onSuccess { n -> _state.update { it.copy(busy = false, message = "已匯出 $n 場演唱會到 CSV") } }
                .onFailure { e -> _state.update { it.copy(busy = false, message = "匯出失敗：${e.message}") } }
        }
    }

    /** 匯出每日選歌到本地 CSV。免登入即可用。 */
    fun exportDailySongsCsv(uri: android.net.Uri) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            val result = runCatching {
                val songs = dailySongRepo.getAllSongs().first()
                val csv = buildDailySongsCsv(songs)
                appContext.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                    os.write(csv.toByteArray(Charsets.UTF_8))
                } ?: error("無法開啟檔案")
                songs.size
            }
            result
                .onSuccess { n -> _state.update { it.copy(busy = false, message = "已匯出 $n 首選歌到 CSV") } }
                .onFailure { e -> _state.update { it.copy(busy = false, message = "匯出失敗：${e.message}") } }
        }
    }

    private fun buildDailySongsCsv(songs: List<com.bandary.domain.model.DailySong>): String {
        fun esc(s: String?): String {
            val v = s ?: ""
            return if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' })
                "\"" + v.replace("\"", "\"\"") + "\"" else v
        }
        val header = listOf("日期", "歌名", "歌手", "專輯", "感受", "備註")
        val sb = StringBuilder()
        sb.append(header.joinToString(",")).append("\r\n")
        songs.sortedWith(compareByDescending<com.bandary.domain.model.DailySong> { it.date }.thenBy { it.rank })
            .forEach { s ->
                sb.append(
                    listOf(
                        esc(s.date.toString()), esc(s.songTitle), esc(s.artistName),
                        esc(s.albumName), esc(s.reaction?.label), esc(s.memo)
                    ).joinToString(",")
                ).append("\r\n")
            }
        return sb.toString()
    }

    private fun buildConcertsCsv(concerts: List<com.bandary.domain.model.Concert>): String {
        fun esc(s: String?): String {
            val v = s ?: ""
            return if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' })
                "\"" + v.replace("\"", "\"\"") + "\"" else v
        }
        val header = listOf(
            "名稱", "藝人", "場地", "縣市", "國家", "開始日期時間", "結束日", "結束時間",
            "類型", "票務狀態", "票價", "購票平台", "座位", "評分", "備忘錄"
        )
        val sb = StringBuilder()
        sb.append(header.joinToString(",")).append("\r\n")
        concerts.sortedBy { it.dateTime }.forEach { c ->
            sb.append(
                listOf(
                    esc(c.name), esc(c.artistName), esc(c.venue), esc(c.city), esc(c.country),
                    esc(c.dateTime.toString()), esc(c.endDate?.toString()), esc(c.endTime?.toString()),
                    esc(c.typeLabel()), esc(c.ticketStatus.label),
                    esc(c.ticketPrice?.toInt()?.toString()), esc(c.ticketPlatform),
                    esc(c.seatInfo), esc(c.rating?.toString()), esc(c.notes)
                ).joinToString(",")
            ).append("\r\n")
        }
        return sb.toString()
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}
