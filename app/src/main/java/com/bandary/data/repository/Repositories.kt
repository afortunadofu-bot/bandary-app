package com.bandary.data.repository

import android.content.Context
import com.bandary.data.local.dao.ConcertDao
import com.bandary.data.local.dao.DailySongDao
import com.bandary.data.local.entity.toDomain
import com.bandary.data.local.entity.toEntity
import com.bandary.data.local.SettingsDataStore
import com.bandary.data.local.toDraftPayload
import com.bandary.data.remote.GoogleCalendarService
import com.bandary.data.remote.TicketSaleScheduler
import com.bandary.data.worker.EntryReminderScheduler
import com.bandary.domain.model.Concert
import com.bandary.domain.model.DailySong
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConcertRepository @Inject constructor(
    private val concertDao: ConcertDao,
    private val settings: SettingsDataStore,
    private val calendar: GoogleCalendarService,
    @param:ApplicationContext private val context: Context
) {
    fun getAllConcerts(): Flow<List<Concert>> =
        concertDao.getAllConcerts().map { list -> list.map { it.toDomain() } }

    fun getNextConcert(): Flow<Concert?> {
        val now = Clock.System.now()
            .toLocalDateTime(TimeZone.of("Asia/Taipei")).toString()
        return concertDao.getNextConcert(now).map { it?.toDomain() }
    }

    fun getConcertsBetween(from: LocalDateTime, to: LocalDateTime): Flow<List<Concert>> =
        concertDao.getConcertsBetween(from.toString(), to.toString())
            .map { list -> list.map { it.toDomain() } }

    suspend fun getConcertById(id: String): Concert? =
        concertDao.getConcertById(id)?.toDomain()

    /** 只寫回圖片欄位（自動修復用），不觸發日曆匯出 / 提醒重排等 saveConcert 的副作用。 */
    suspend fun updateImagesOnly(concert: Concert) {
        concertDao.upsertConcert(concert.toEntity())
    }

    /**
     * 把「`artistName` 裡塞了整串卡司」的舊紀錄拆開：第一位留在 `artistName`，
     * 其餘補進 `festivals`（已選）。回傳 `改了幾筆 to 掃了幾筆`。
     *
     * 為什麼會有這種資料：掃海報時 OCR 回來的一行常常是整排卡司，
     * 而加藝人那段原本把整行當成「一位藝人」（v1.100.0 已修上游）。
     * 這些紀錄存下來之後，`artistName` 就是一個三十個團名黏在一起的字串，
     * 後台排行、藝人頁、統計全部把它當成一位藝人。
     *
     * **只動拆得開的那些**：
     *   - 拆不出兩位以上的不碰。
     *   - 單一名稱超過 40 字的視為髒資料，不碰（硬拆只會拆出更爛的東西）。
     *   - 已經在 `festivals` 裡的名字不重複加。
     * 走 [updateImagesOnly] 那條路（純 upsert），不觸發日曆匯出與提醒重排——
     * 這只是把同一份資料換個存法，不是使用者改了場次。
     */
    suspend fun splitLineupArtists(): Pair<Int, Int> {
        val all = concertDao.getAllConcertsForSync().map { it.toDomain() }
        var changed = 0
        all.forEach { c ->
            if (c.deletedAt != null) return@forEach
            val parts = com.bandary.domain.model.splitArtistNamesPasted(c.artistName)
                .filter { it.length <= 40 }
            if (parts.size < 2) return@forEach
            val existing = c.festivals.map { it.artistName.trim() }.toSet()
            val extra = parts.drop(1)
                .filter { it.isNotBlank() && it !in existing }
                .map { com.bandary.domain.model.FestivalSlot(
                    startTime = "", endTime = "", stage = "",
                    artistName = it, isSelected = true
                ) }
            concertDao.upsertConcert(
                c.copy(artistName = parts.first(), festivals = c.festivals + extra).toEntity()
            )
            changed++
        }
        return changed to all.size
    }

    /** 雲端同步用：含垃圾桶內的場次，deletedAt 就是要送上雲的刪除墓碑。 */
    suspend fun getAllForSync(): List<Concert> =
        concertDao.getAllConcertsForSync().map { it.toDomain() }

    /**
     * 把雲端較新的版本寫回本機。**刻意不走 saveConcert** —— 那會重蓋 updatedAt，
     * 讓這筆立刻又變成「本機比較新」，下次同步再推回雲端，來回震盪。
     * 提醒仍需重排（時間可能在另一端被改過），但不觸發日曆匯出（那只在新增時做）。
     */
    suspend fun applyFromCloud(concert: Concert) {
        concertDao.upsertConcert(concert.toEntity())
        if (concert.deletedAt == null) {
            runCatching { scheduleSaleReminderIfNeeded(concert) }
            runCatching { EntryReminderScheduler.scheduleAll(context, concert) }
        } else {
            // 在另一端被刪掉的，本機的排程也要一起收掉。
            runCatching { TicketSaleScheduler.cancel(context, concert.id) }
            runCatching { EntryReminderScheduler.cancelAll(context, concert.id) }
        }
    }

    suspend fun saveConcert(concert: Concert): Concert {
        val isNew = concert.id.isBlank()
        // 每次儲存都蓋上 updatedAt——雲端同步靠它判斷手機與網頁誰比較新。
        val toSave = (if (isNew) concert.copy(id = UUID.randomUUID().toString()) else concert)
            .copy(updatedAt = System.currentTimeMillis())
        concertDao.upsertConcert(toSave.toEntity())
        // 排程失敗（如精確鬧鐘權限、WorkManager 例外）不得中斷「儲存」本身，否則畫面會卡住無法儲存。
        runCatching { scheduleSaleReminderIfNeeded(toSave) }
        runCatching { EntryReminderScheduler.scheduleAll(context, toSave) }
        // 記錄心得提醒（演出隔天 10:00；已填心得則不排／取消）。
        runCatching { EntryReminderScheduler.scheduleMemoryReminder(context, toSave) }
        // Export to Google Calendar for NEW concerts only (avoids duplicate events on edit),
        // and only when this concert opted in (逐場自訂) + Google 日曆已連結。Best-effort。
        if (isNew && toSave.syncToCalendar && calendar.isConnected()) {
            runCatching { calendar.pushConcert(toSave) }
        }
        runCatching { com.bandary.presentation.widget.WidgetUpdater.updateAll(context) }
        return toSave
    }

    /** 刪除＝移進垃圾桶（軟刪除，保留 7 天）：取消提醒 / 開賣排程，但資料先留著可還原。 */
    suspend fun deleteConcert(id: String) {
        concertDao.softDeleteConcert(id, System.currentTimeMillis())
        TicketSaleScheduler.cancel(context, id)
        EntryReminderScheduler.cancelAll(context, id)
        runCatching { com.bandary.presentation.widget.WidgetUpdater.updateAll(context) }
    }

    /** 垃圾桶內的演唱會（依刪除時間新到舊）。 */
    fun getTrashedConcerts(): Flow<List<Concert>> =
        concertDao.getTrashedConcerts().map { list -> list.map { it.toDomain() } }

    // ── 編輯頁自動草稿（spec §8）：draftKey＝編輯既有場次的 id，新增用 "new" ──
    /** 存草稿（best-effort，失敗不影響編輯）。 */
    suspend fun saveDraft(draftKey: String, concert: Concert) {
        runCatching {
            concertDao.upsertDraft(
                com.bandary.data.local.ConcertDraftEntity(
                    draftKey = draftKey,
                    payload = concert.toDraftPayload(),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    /** 讀草稿；沒有或壞掉回 null。 */
    suspend fun loadDraft(draftKey: String): Concert? =
        runCatching { concertDao.getDraft(draftKey)?.let { com.bandary.data.local.draftPayloadToConcert(it.payload) } }.getOrNull()

    /** 清掉草稿（提交儲存 / 捨棄後呼叫）。 */
    suspend fun clearDraft(draftKey: String) {
        runCatching { concertDao.deleteDraft(draftKey) }
    }

    /** 從垃圾桶還原：清掉 deletedAt，並重新排程開賣 / 入場提醒。 */
    suspend fun restoreConcert(id: String) {
        concertDao.restoreConcert(id, System.currentTimeMillis())
        concertDao.getConcertById(id)?.toDomain()?.let { c ->
            runCatching { scheduleSaleReminderIfNeeded(c) }
            runCatching { EntryReminderScheduler.scheduleAll(context, c) }
        }
        runCatching { com.bandary.presentation.widget.WidgetUpdater.updateAll(context) }
    }

    /** 永久刪除單筆（垃圾桶內手動清除）。 */
    suspend fun permanentlyDeleteConcert(id: String) {
        concertDao.deleteConcertById(id)
    }

    /** 清掉垃圾桶內超過 7 天的演唱會（App 啟動時呼叫一次）。 */
    suspend fun purgeExpiredTrash() {
        val cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        concertDao.purgeExpiredTrash(cutoff)
    }

    /**
     * 每一組開賣時間各排一個提醒（提前幾分鐘是整場共用的設定）。
     * **一定要先 cancel 整場**：使用者刪掉其中一組時，那組的 work 不會自己消失，
     * 只重排剩下的會留下孤兒提醒，時間到照樣響。
     */
    private fun scheduleSaleReminderIfNeeded(concert: Concert) {
        TicketSaleScheduler.cancel(context, concert.id)
        if (concert.saleTimes.isEmpty()) return
        val tz = TimeZone.of("Asia/Taipei")
        // 開賣前 N 分鐘提醒（可自訂，預設 60 分）；負值＝使用者選「不提醒」。
        val mins = concert.saleReminderMinutes ?: 60
        if (mins < 0) return
        val ahead = if (mins % 1440 == 0) "${mins / 1440} 天" else if (mins % 60 == 0) "${mins / 60} 小時" else "$mins 分鐘"
        concert.saleTimes.sortedBy { it.at }.forEachIndexed { index, sale ->
            val triggerInstant = sale.at.toInstant(tz).minus(mins, DateTimeUnit.MINUTE, tz)
            // 提醒時間已過（例如從備份還原的舊場次）就不排程，否則 WorkManager 會立刻補發過期通知。
            if (triggerInstant.toEpochMilliseconds() <= System.currentTimeMillis()) return@forEachIndexed
            val round = sale.label.takeIf { it.isNotBlank() }?.let { "（$it）" }.orEmpty()
            TicketSaleScheduler.schedule(
                context = context,
                concertId = concert.id,
                triggerAtMillis = triggerInstant.toEpochMilliseconds(),
                title = "${concert.artistName} 即將開賣$round",
                message = "${concert.name} $ahead 後開賣",
                index = index
            )
        }
    }
}

@Singleton
class DailySongRepository @Inject constructor(
    private val dailySongDao: DailySongDao,
    private val settings: com.bandary.data.local.SettingsDataStore
) {
    fun getRecentSongs(limit: Int = 30): Flow<List<DailySong>> =
        dailySongDao.getRecentDailySongs(limit).map { list -> list.map { it.toDomain() } }

    fun getAllSongs(): Flow<List<DailySong>> =
        dailySongDao.getAllDailySongs().map { list -> list.map { it.toDomain() } }

    fun getSongsForDate(date: LocalDate): Flow<List<DailySong>> =
        dailySongDao.getSongsForDate(date.toString()).map { list -> list.map { it.toDomain() } }

    fun getSongsBetween(from: LocalDate, to: LocalDate): Flow<List<DailySong>> =
        dailySongDao.getSongsBetween(from.toString(), to.toString())
            .map { list -> list.map { it.toDomain() } }

    /** 明確連到某場演唱會的選歌（v21 concertId）。 */
    fun getSongsForConcert(concertId: String): Flow<List<DailySong>> =
        dailySongDao.getSongsForConcert(concertId).map { list -> list.map { it.toDomain() } }

    /** 某個歌手名下記過的所有歌。 */
    fun getSongsByArtist(artist: String): Flow<List<DailySong>> =
        dailySongDao.getSongsByArtist(artist).map { list -> list.map { it.toDomain() } }

    suspend fun getTodaySong(): DailySong? {
        val today = Clock.System.now()
            .toLocalDateTime(TimeZone.of("Asia/Taipei")).date
        return dailySongDao.getDailySongForDate(today.toString())?.toDomain()
    }

    suspend fun saveDailySong(song: DailySong): DailySong {
        val toSave = if (song.id.isBlank())
            song.copy(id = UUID.randomUUID().toString())
        else song
        dailySongDao.upsertDailySong(toSave.toEntity())
        return toSave
    }

    suspend fun deleteDailySong(id: String) {
        dailySongDao.deleteDailySongById(id)
        // 記下來，下次備份才把雲端那一筆也刪掉（備份不再用鏡像方式刪除）。
        runCatching { settings.addPendingSongDelete(id) }
    }

    /**
     * 連續紀錄天數（含今天）。[freezeDates]＝E2-4 補打卡券補回的日期集合，這些日子即使沒實際紀錄也算「有」，
     * 讓斷掉的連續紀錄能接回去。
     */
    suspend fun calculateStreak(freezeDates: Set<LocalDate> = emptySet()): Int {
        val tz = TimeZone.of("Asia/Taipei")
        var streak = 0
        var checkDate = Clock.System.now().toLocalDateTime(tz).date
        repeat(365) {
            val has = dailySongDao.countSongsForDate(checkDate.toString()) > 0 || checkDate in freezeDates
            if (has) {
                streak++
                checkDate = checkDate.minus(1, DateTimeUnit.DAY)
            } else {
                return streak
            }
        }
        return streak
    }

    /** 某天是否有選歌紀錄（E2-4 判斷「昨天是否漏打卡」用）。 */
    suspend fun hasSongOn(date: LocalDate): Boolean =
        dailySongDao.countSongsForDate(date.toString()) > 0

    /**
     * 「還沒斷掉、正懸在今天」的連續天數：從昨天往回算。今天還沒記錄時，[calculateStreak] 會直接回 0
     * （今天算進去確實是斷了），但挽救推播（E-5）要講的是「別讓這 N 天斷在今天」，所以要算到昨天為止。
     */
    suspend fun streakEndingYesterday(): Int {
        val tz = TimeZone.of("Asia/Taipei")
        var streak = 0
        var checkDate = Clock.System.now().toLocalDateTime(tz).date.minus(1, DateTimeUnit.DAY)
        repeat(365) {
            val count = dailySongDao.countSongsForDate(checkDate.toString())
            if (count > 0) {
                streak++
                checkDate = checkDate.minus(1, DateTimeUnit.DAY)
            } else {
                return streak
            }
        }
        return streak
    }
}
