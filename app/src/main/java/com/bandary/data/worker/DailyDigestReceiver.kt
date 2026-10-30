package com.bandary.data.worker

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.bandary.MainActivity
import com.bandary.data.local.SettingsDataStore
import com.bandary.data.repository.ConcertRepository
import com.bandary.data.repository.FreeEventsRepository
import com.bandary.domain.model.occurrenceDates
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import java.util.Calendar

/**
 * 每日「精選提醒」（E2 A 組・通知觸發）：每天早上響一次，依當天情境挑最相關的推播，平時多半 0 則：
 *  - E2-1 今天有演出 → 實用推播（開演時間 / 場館 / 取票序號）。
 *  - E2-2 昨天有演出且還缺評分 / 照片 → 補紀錄提醒（深連結編輯頁）。
 *  - E2-3 N 年前的今天曾看過演出 → 回憶推播（深連結詳情頁）。
 *  - E2-6 週四 / 週五 → 提醒這週末的免費活動。
 *
 * 沿用 DailyCheckIn 的精確鬧鐘 + 自我重排 + 開機重排做法；使用者可在「通知」設定關掉（notifyDigest）。
 */
private const val ENGAGEMENT_CHANNEL = "engagement"

@SuppressLint("MissingPermission")
private fun notifyEngagement(ctx: Context, notifId: Int, title: String, text: String, extras: (Intent) -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(ENGAGEMENT_CHANNEL) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(ENGAGEMENT_CHANNEL, "精選提醒", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "演出當天 / 補紀錄 / 回憶 / 週末活動等貼心提醒" }
            )
        }
    }
    val intent = Intent(ctx, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
            // SINGLE_TOP 是關鍵：App 已經在跑時，少了它 Android 只會把既有 task 帶到前景、
            // 不會呼叫 onNewIntent，於是通知點進去停在你離開前那一頁，跟通知內容無關。
            Intent.FLAG_ACTIVITY_SINGLE_TOP
        extras(this)
    }
    val pendingFlags =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT
    val pi = PendingIntent.getActivity(ctx, notifId, intent, pendingFlags)

    val notif = NotificationCompat.Builder(ctx, ENGAGEMENT_CHANNEL)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setSmallIcon(com.bandary.R.drawable.ic_stat_bandary)
        .setContentIntent(pi)
        .setAutoCancel(true)
        .build()

    val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    if (granted) NotificationManagerCompat.from(ctx).notify(notifId, notif)
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface DigestEntryPoint {
    fun settings(): SettingsDataStore
    fun concertRepo(): ConcertRepository
    fun freeEventsRepo(): FreeEventsRepository
    fun dailySongRepo(): com.bandary.data.repository.DailySongRepository
    fun firestore(): com.bandary.data.remote.FirestoreService
}

class DailyDigestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { DailyDigestScheduler.schedule(context) }
                pending.finish()
            }
            return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val ep = EntryPointAccessors.fromApplication(context.applicationContext, DigestEntryPoint::class.java)
                if (ep.settings().notifyDigest.first()) runDigest(context, ep)
            }
            DailyDigestScheduler.schedule(context)  // 排下一天
            pending.finish()
        }
    }
}

private suspend fun runDigest(ctx: Context, ep: DigestEntryPoint) {
    val tz = TimeZone.of("Asia/Taipei")
    val today = Clock.System.now().toLocalDateTime(tz).date
    val concerts = ep.concertRepo().getAllConcerts().first()

    // E2-1 今天有演出：實用推播（時間 / 場館 / 取票序號）。
    concerts.firstOrNull { today in it.occurrenceDates() }?.let { c ->
        val who = c.artistName.ifBlank { c.name }
        val timeStr = "%02d:%02d".format(c.dateTime.hour, c.dateTime.minute)
        val pickup = c.pickupNumber?.takeIf { it.isNotBlank() }?.let { "，取票序號 $it" } ?: ""
        notifyEngagement(ctx, 5001, "今天 $who", "$timeStr 於 ${c.venue}$pickup。點開看入場資訊。") {
            it.putExtra("concertId", c.id)
        }
    }

    // E2-2 昨天有演出、且還缺評分或照片：補紀錄（趁記憶新鮮）。
    val yesterday = today.minus(1, DateTimeUnit.DAY)
    concerts.firstOrNull { c ->
        yesterday in c.occurrenceDates() && (c.rating == null || (c.photoUri.isNullOrBlank() && c.photoUris.isEmpty()))
    }?.let { c ->
        val who = c.artistName.ifBlank { c.name }
        notifyEngagement(ctx, 5002, "昨晚的 $who 如何？", "趁記憶最鮮明，補上評分和照片。") {
            it.putExtra("editConcertId", c.id)
        }
    }

    // E2-3 N 年前的今天：同月同日、更早年份的演出。
    concerts.filter { c ->
        c.occurrenceDates().any { it.monthNumber == today.monthNumber && it.dayOfMonth == today.dayOfMonth && it.year < today.year }
    }.maxByOrNull { it.dateTime }?.let { c ->
        val years = today.year - c.dateTime.year
        val who = c.artistName.ifBlank { c.name }
        notifyEngagement(ctx, 5003, "$years 年前的今天", "你看了 $who。點開回顧一下？") {
            it.putExtra("concertId", c.id)
        }
    }

    // E2-5 月結小回顧：每月 1 號回顧上個月看了幾場、選了幾首歌、最常聽誰。
    if (today.dayOfMonth == 1) {
        val lastMonthEnd = today.minus(1, DateTimeUnit.DAY)
        val lastMonthStart = LocalDate(lastMonthEnd.year, lastMonthEnd.monthNumber, 1)
        val monthShows = concerts.count { c ->
            c.occurrenceDates().any { it in lastMonthStart..lastMonthEnd }
        }
        val songs = runCatching {
            ep.dailySongRepo().getSongsBetween(lastMonthStart, lastMonthEnd).first()
        }.getOrDefault(emptyList())
        val topArtist = songs.filter { it.artistName.isNotBlank() }
            .groupingBy { it.artistName }.eachCount().maxByOrNull { it.value }?.key
        if (monthShows > 0 || songs.isNotEmpty()) {
            val parts = buildList {
                if (monthShows > 0) add("看了 $monthShows 場演出")
                if (songs.isNotEmpty()) add("選了 ${songs.size} 首歌")
            }
            val tail = topArtist?.let { "，最常聽 $it" } ?: ""
            notifyEngagement(ctx, 5005, "上個月的聽團回顧", parts.joinToString("、") + tail + "。點開看完整統計。") {
                it.putExtra("navTarget", "stats")
            }
        }
    }

    // E2-13 週報：每週一回顧上週（一～日）看了幾場演出、選了幾首歌，跟月結/年度回顧同一套邏輯。
    if (today.dayOfWeek.isoDayNumber == 1) {
        val lastWeekEnd = today.minus(1, DateTimeUnit.DAY)          // 上週日
        val lastWeekStart = lastWeekEnd.minus(6, DateTimeUnit.DAY)  // 上週一
        val weekShows = concerts.count { c -> c.occurrenceDates().any { it in lastWeekStart..lastWeekEnd } }
        val weekSongs = runCatching {
            ep.dailySongRepo().getSongsBetween(lastWeekStart, lastWeekEnd).first()
        }.getOrDefault(emptyList())
        if (weekShows > 0 || weekSongs.isNotEmpty()) {
            val parts = buildList {
                if (weekShows > 0) add("看了 $weekShows 場演出")
                if (weekSongs.isNotEmpty()) add("選了 ${weekSongs.size} 首歌")
            }
            notifyEngagement(ctx, 5010, "上週的聽團週報", parts.joinToString("、") + "。點開看完整統計。") {
                it.putExtra("navTarget", "stats")
            }
        }
    }

    // E2-7 年度 Wrapped 事件化：12 月初推「你的年度聽團回顧出爐了」。
    if (today.monthNumber == 12 && today.dayOfMonth in 1..3) {
        val yearShows = concerts.count { it.dateTime.date.year == today.year }
        val yearSongs = runCatching {
            ep.dailySongRepo().getSongsBetween(LocalDate(today.year, 1, 1), today).first()
        }.getOrDefault(emptyList())
        if (yearShows > 0 || yearSongs.isNotEmpty()) {
            notifyEngagement(ctx, 5006, "${today.year} 年度聽團回顧出爐了", "看了 $yearShows 場、選了 ${yearSongs.size} 首歌。點開回顧你的這一年。") {
                it.putExtra("navTarget", "wrapped")
            }
        }
    }

    // E2-11 / E2-12 好友社交（需登入 + 有好友；靜默失敗）：
    //  E2-11 你和好友同一天、同場地都會到 → 同場通知。
    //  E2-12 好友新增了一場你沒看過的演出 → 新增通知。
    runCatching {
        val fs = ep.firestore()
        if (fs.currentUid() != null) {
            val friends = fs.fetchFriends().getOrDefault(emptyList())
            if (friends.isNotEmpty()) {
                val myUpcoming = concerts.filter { it.dateTime.date >= today }
                val initialized = ep.settings().friendSocialInitialized.first()
                val seen = ep.settings().seenFriendShows.first()
                val newSeen = mutableSetOf<String>()
                var matchNotified = false
                var addNotified = false
                friends.forEach { friend ->
                    val cal = fs.fetchSharedCalendar(friend.uid).getOrNull() ?: return@forEach
                    val who = friend.handle.takeIf { it.isNotBlank() }?.let { "@$it" }
                        ?: friend.displayName.ifBlank { "好友" }
                    val friendUpcoming = cal.shows.filter { it.dateTime.date >= today }
                    friendUpcoming.forEach { s ->
                        val key = "${friend.uid}|${s.name}|${s.dateTime.date}|${s.venue}"
                        newSeen += key
                        // E2-12：初始化過之後，第一次看到的好友演出才通知（避免首次全部當新的）。
                        if (initialized && key !in seen && !addNotified) {
                            val label = s.artistName.ifBlank { s.name }
                            notifyEngagement(ctx, 5008, "$who 新增了一場演出",
                                "$label · ${s.dateTime.monthNumber}/${s.dateTime.dayOfMonth}。點開看好友的行事曆。") {
                                it.putExtra("navTarget", "friends")
                            }
                            addNotified = true
                        }
                    }
                    // E2-11：我有一場跟這位好友同日 + 同場地。
                    if (!matchNotified) {
                        val match = myUpcoming.firstOrNull { mine ->
                            mine.venue.isNotBlank() && friendUpcoming.any {
                                it.dateTime.date == mine.dateTime.date && it.venue == mine.venue
                            }
                        }
                        if (match != null) {
                            notifyEngagement(ctx, 5007, "$who 也要去同一場",
                                "${match.dateTime.monthNumber}/${match.dateTime.dayOfMonth} ${match.venue}，你們都會到。") {
                                it.putExtra("concertId", match.id)
                            }
                            matchNotified = true
                        }
                    }
                }
                ep.settings().setSeenFriendShows(newSeen)
            }
        }
    }

    // 追蹤藝人：活動出現追蹤中藝人的未來場次時提醒一次。
    // 用「活動 id + 藝人」當 key 去重，否則每天都會再推同一場。
    runCatching {
        val followed = ep.settings().followedArtists.first()
        if (followed.isNotEmpty()) {
            ep.freeEventsRepo().refresh()
            val seen = ep.settings().seenFollowedEvents.first()
            val hits = ep.freeEventsRepo().events.value
                .filter { it.dateTime.date >= today }
                .mapNotNull { ev ->
                    com.bandary.domain.model.matchFollowedArtist(ev.artistName, followed)?.let { who -> ev to who }
                }
            val fresh = hits.filter { (ev, who) -> "${ev.id}|$who" !in seen }
            if (fresh.isNotEmpty()) {
                val (ev, who) = fresh.first()
                val more = if (fresh.size > 1) "（另有 ${fresh.size - 1} 場）" else ""
                notifyEngagement(
                    ctx, 5009, "$who 有新的活動",
                    "${ev.name.ifBlank { ev.artistName }} · ${ev.dateTime.monthNumber}/${ev.dateTime.dayOfMonth}$more"
                ) { it.putExtra("navTarget", "followed") }
            }
            // 只保留還在未來的 key，過期活動不必永久佔著。
            ep.settings().setSeenFollowedEvents(hits.map { (ev, who) -> "${ev.id}|$who" }.toSet())
        }
    }

    // E2-6 週四 / 週五：提醒這週末的免費活動。
    val iso = today.dayOfWeek.isoDayNumber
    if (iso == 4 || iso == 5) {
        runCatching { ep.freeEventsRepo().refresh() }
        val daysToSat = ((6 - iso) + 7) % 7
        val sat = today.plus(daysToSat, DateTimeUnit.DAY)
        val sun = sat.plus(1, DateTimeUnit.DAY)
        val weekendFree = ep.freeEventsRepo().events.value.filter { ev ->
            (ev.ticketPrice ?: 0.0) <= 0.0 && ev.occurrenceDates().any { it == sat || it == sun }
        }
        if (weekendFree.isNotEmpty()) {
            notifyEngagement(ctx, 5004, "這週末有 ${weekendFree.size} 場免費活動", "看看週末要不要出門走走。") {
                it.putExtra("navTarget", "events")
            }
        }
    }
}

object DailyDigestScheduler {
    private const val REQUEST_CODE = 9201
    private const val HOUR = 10   // 每天早上 10:00

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, DailyDigestReceiver::class.java).apply { action = "com.bandary.DAILY_DIGEST" }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }

    fun schedule(context: Context) {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_MONTH, 1)
        }
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context)
        val triggerAt = next.timeInMillis
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        }.onFailure { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi) }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context))
    }
}
