package com.bandary.data.worker

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.bandary.MainActivity
import com.bandary.domain.model.Concert
import com.bandary.domain.model.ReminderOffset
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import java.util.concurrent.TimeUnit

/**
 * Fires an entry reminder (e.g. "1 week before") for a concert.
 *
 * One worker is scheduled per {concertId, offset} pair, with a unique work name
 * so re-saving the concert REPLACEs the old reminder without leaving orphans.
 */
@HiltWorker
class EntryReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val concertId = inputData.getString(KEY_CONCERT_ID) ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: "演唱會提醒"
        val message = inputData.getString(KEY_MESSAGE)?.let(::fixLegacyReminderText) ?: "你的演唱會即將開始"
        val openEdit = inputData.getBoolean(KEY_OPEN_EDIT, false)
        notify(concertId, title, message, openEdit)
        return Result.success()
    }

    @SuppressLint("MissingPermission")
    private fun notify(concertId: String, title: String, message: String, openEdit: Boolean = false) {
        val ctx = applicationContext
        val channelId = "entry_reminders"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(channelId) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        channelId,
                        "入場提醒",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply { description = "演唱會入場前提醒" }
                )
            }
        }

        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
            // SINGLE_TOP 是關鍵：App 已經在跑時，少了它 Android 只會把既有 task 帶到前景、
            // 不會呼叫 onNewIntent，於是通知點進去停在你離開前那一頁，跟通知內容無關。
            Intent.FLAG_ACTIVITY_SINGLE_TOP
            // openEdit＝記錄心得提醒：直接開編輯頁（讓使用者補歌單/評分）；否則開唯讀詳情頁。
            if (openEdit) putExtra("editConcertId", concertId) else putExtra("concertId", concertId)
        }
        val pendingFlags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else PendingIntent.FLAG_UPDATE_CURRENT
        val pi = PendingIntent.getActivity(ctx, concertId.hashCode(), intent, pendingFlags)

        val notif = NotificationCompat.Builder(ctx, channelId)
            .setContentTitle(title)
            .setContentText(message)
            // 內文含日期、活動名、場地，單行會被截斷，展開時要能看完整。
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(com.bandary.R.drawable.ic_stat_bandary)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) {
            NotificationManagerCompat.from(ctx).notify(
                ("entry_" + concertId).hashCode(),
                notif
            )
        }
    }

    companion object {
        const val KEY_CONCERT_ID = "concert_id"
        const val KEY_TITLE = "title"
        const val KEY_MESSAGE = "message"
        const val KEY_OPEN_EDIT = "open_edit"
    }
}

/**
 * 1.67.6 以前排好的提醒，內文是「活動名 1 週前・場地」（把設定選項的字直接放進通知，會被讀成「一週前的演出」）。
 * 這些工作已經排進 WorkManager、要等使用者再存一次演出才會重排，所以發通知時先把舊字串轉成倒數說法。
 */
internal fun fixLegacyReminderText(message: String): String =
    ReminderOffset.entries.fold(message) { acc, o -> acc.replace(" ${o.label}・", "・${o.countdown}・") }

object EntryReminderScheduler {

    private fun tag(concertId: String, offset: ReminderOffset) =
        "entry_reminder_${concertId}_${offset.name}"

    /**
     * Schedule all reminders configured on the concert. Old jobs for the same
     * concert/offset combination are replaced.
     */
    fun scheduleAll(context: Context, concert: Concert) {
        val tz = TimeZone.of("Asia/Taipei")
        val showInstant = concert.dateTime.toInstant(tz)
        val nowMs = System.currentTimeMillis()

        // 自訂提醒：演出前 N 分鐘（可有多個；含相容用的舊單一 customReminderMinutes）。
        val customMinutes = (concert.extraReminderMinutes + listOfNotNull(concert.customReminderMinutes))
            .filter { it > 0 }.distinct()
        customMinutes.forEachIndexed { idx, mins ->
            val triggerMs = showInstant.minus(mins, DateTimeUnit.MINUTE, tz).toEpochMilliseconds()
            if (triggerMs > nowMs) {
                val data = workDataOf(
                    EntryReminderWorker.KEY_CONCERT_ID to concert.id,
                    EntryReminderWorker.KEY_TITLE to "${concert.artistName} 即將開唱",
                    EntryReminderWorker.KEY_MESSAGE to com.bandary.domain.model.entryReminderMessage(
                        com.bandary.domain.model.reminderCountdown(mins), concert.dateTime, concert.name, concert.venue
                    )
                )
                val customTag = "reminder_${concert.id}_custom_$idx"
                val request = OneTimeWorkRequestBuilder<EntryReminderWorker>()
                    .setInitialDelay(triggerMs - nowMs, TimeUnit.MILLISECONDS)
                    .setInputData(data)
                    .addTag(customTag)
                    .build()
                WorkManager.getInstance(context)
                    .enqueueUniqueWork(customTag, ExistingWorkPolicy.REPLACE, request)
            }
        }

        if (concert.reminders.isEmpty()) return

        concert.reminders.forEach { offset ->
            val triggerInstant = when (offset) {
                ReminderOffset.ONE_WEEK -> showInstant.minus(7, DateTimeUnit.DAY, tz)
                ReminderOffset.THREE_DAYS -> showInstant.minus(3, DateTimeUnit.DAY, tz)
                ReminderOffset.ONE_DAY -> showInstant.minus(1, DateTimeUnit.DAY, tz)
                ReminderOffset.TWO_HOURS -> showInstant.minus(2, DateTimeUnit.HOUR, tz)
                ReminderOffset.THIRTY_MIN -> showInstant.minus(30, DateTimeUnit.MINUTE, tz)
            }
            val triggerMs = triggerInstant.toEpochMilliseconds()
            // Skip past-due reminders.
            if (triggerMs <= nowMs) return@forEach

            val data = workDataOf(
                EntryReminderWorker.KEY_CONCERT_ID to concert.id,
                EntryReminderWorker.KEY_TITLE to "${concert.artistName} 即將開唱",
                EntryReminderWorker.KEY_MESSAGE to com.bandary.domain.model.entryReminderMessage(
                    offset.countdown, concert.dateTime, concert.name, concert.venue
                )
            )
            val request = OneTimeWorkRequestBuilder<EntryReminderWorker>()
                .setInitialDelay(triggerMs - nowMs, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag(tag(concert.id, offset))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(tag(concert.id, offset), ExistingWorkPolicy.REPLACE, request)
        }
    }

    private fun memoryTag(concertId: String) = "memory_reminder_$concertId"

    /**
     * 記錄心得提醒（spec §6.8.3）：演出隔天 10:00 推「昨晚的《X》如何？記下歌單和評分」，點開直達編輯頁。
     * 只在「還沒填心得」時排程（已填者不推）；使用者演出後補填並儲存時會重排、屆時已填→不再排。
     */
    fun scheduleMemoryReminder(context: Context, concert: Concert) {
        val wm = WorkManager.getInstance(context)
        // 已填心得（有評分 / 有歌單 / 有備註）→ 不推。
        val hasMemory = (concert.rating ?: 0) > 0 || concert.setlist.isNotEmpty() || !concert.notes.isNullOrBlank()
        if (hasMemory) { wm.cancelUniqueWork(memoryTag(concert.id)); return }

        val tz = TimeZone.of("Asia/Taipei")
        // 演出日的「隔天 10:00」。
        val nextDay = concert.dateTime.date.plus(1, DateTimeUnit.DAY)
        val triggerMs = kotlinx.datetime.LocalDateTime(nextDay, kotlinx.datetime.LocalTime(10, 0))
            .toInstant(tz).toEpochMilliseconds()
        val nowMs = System.currentTimeMillis()
        if (triggerMs <= nowMs) { wm.cancelUniqueWork(memoryTag(concert.id)); return }

        val data = workDataOf(
            EntryReminderWorker.KEY_CONCERT_ID to concert.id,
            EntryReminderWorker.KEY_TITLE to "昨晚的《${concert.name.ifBlank { concert.artistName }}》如何？",
            EntryReminderWorker.KEY_MESSAGE to "記下歌單和評分，累積成你的年度回顧",
            EntryReminderWorker.KEY_OPEN_EDIT to true
        )
        val request = OneTimeWorkRequestBuilder<EntryReminderWorker>()
            .setInitialDelay(triggerMs - nowMs, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag(memoryTag(concert.id))
            .build()
        wm.enqueueUniqueWork(memoryTag(concert.id), ExistingWorkPolicy.REPLACE, request)
    }

    /** Cancel every reminder for this concert across all offsets. */
    fun cancelAll(context: Context, concertId: String) {
        val wm = WorkManager.getInstance(context)
        ReminderOffset.entries.forEach { offset ->
            wm.cancelUniqueWork(tag(concertId, offset))
        }
        wm.cancelUniqueWork("reminder_${concertId}_custom")  // 舊單一自訂提醒
        // 多個自訂提醒：取消足夠多的索引（上限保守取 20，涵蓋一般使用）。
        for (i in 0 until 20) wm.cancelUniqueWork("reminder_${concertId}_custom_$i")
        wm.cancelUniqueWork(memoryTag(concertId))
    }
}
