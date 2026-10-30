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
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import java.util.Calendar

/**
 * 每日「今天選歌了嗎」提醒。
 *
 * 改用 AlarmManager 的**精確鬧鐘**（setExactAndAllowWhileIdle）而非 WorkManager 週期工作——
 * 週期工作會被系統為省電批次延後，導致「設定時間與實際發送時間不一致」。每次響鈴後自我重排隔天，
 * 開機後由 BOOT_COMPLETED 依設定重排。
 */

// 顯示提醒通知（Receiver 與相容用的舊 Worker 共用）。
// [streakAtRisk] > 0 時（今天還沒記錄、且昨天為止有連續天數）改用「挽救」語氣；
// 為避免對沒有連續紀錄的人也用緊迫語氣打擾，只有 streak >= 3 才會用這個框架（E-5 的防打擾規則）。
@SuppressLint("MissingPermission")
internal fun showDailyCheckInNotification(
    ctx: Context,
    streakAtRisk: Int = 0,
    justWatched: String? = null,
    watchedToday: Boolean = false
) {
    val channelId = "daily_checkin"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(channelId) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(channelId, "每日選歌提醒", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "提醒你今天選一首歌" }
            )
        }
    }
    val intent = Intent(ctx, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
            // SINGLE_TOP 是關鍵：App 已經在跑時，少了它 Android 只會把既有 task 帶到前景、
            // 不會呼叫 onNewIntent，於是通知點進去停在你離開前那一頁，跟通知內容無關。
            Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra("navTarget", "songs")
    }
    val pendingFlags =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT
    val pi = PendingIntent.getActivity(ctx, 9001, intent, pendingFlags)

    // 剛看完團的語氣最優先——這是選歌與看團生活的接點，比通用提醒有感得多。
    val (title, text) = when {
        justWatched != null ->
            (if (watchedToday) "今晚的《$justWatched》" else "昨晚的《$justWatched》") to
                "從那場的歌單挑一首記下來吧"
        streakAtRisk >= 3 ->
            "別讓 $streakAtRisk 天斷在今天" to "只差今天一首歌，連續紀錄就能繼續"
        else ->
            "記錄今天的一首歌" to "記錄一首今天在聽的歌，維持連續紀錄"
    }

    val notif = NotificationCompat.Builder(ctx, channelId)
        .setContentTitle(title)
        .setContentText(text)
        .setSmallIcon(com.bandary.R.drawable.ic_stat_bandary)
        .setContentIntent(pi)
        .setAutoCancel(true)
        .build()

    val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    if (granted) NotificationManagerCompat.from(ctx).notify(9001, notif)
}

/** 讓 Receiver 在開機時能讀到使用者的提醒設定並重排；響鈴時也要查今天是否已記錄 + 連續天數。 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface CheckInSettingsEntryPoint {
    fun settings(): SettingsDataStore
    fun dailySongRepo(): com.bandary.data.repository.DailySongRepository
    fun concertRepo(): com.bandary.data.repository.ConcertRepository
}

class DailyCheckInReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // 開機重排：依儲存的設定重新設鬧鐘。
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    val settings = EntryPointAccessors
                        .fromApplication(context.applicationContext, CheckInSettingsEntryPoint::class.java)
                        .settings()
                    if (settings.checkInEnabled.first()) {
                        DailyCheckInScheduler.schedule(context, settings.checkInTime.first())
                    }
                }
                pending.finish()
            }
            return
        }
        // 一般響鈴：今天已經記錄過就不再打擾；沒記錄則依昨天為止的連續天數決定用挽救語氣還是一般提醒。
        val pending = goAsync()
        val hour = intent.getIntExtra("hour", 21)
        val minute = intent.getIntExtra("minute", 0)
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val ep = EntryPointAccessors
                    .fromApplication(context.applicationContext, CheckInSettingsEntryPoint::class.java)
                val repo = ep.dailySongRepo()
                if (repo.getTodaySong() == null) {
                    val streakAtRisk = repo.streakEndingYesterday()
                    // 剛看完團（今天或昨天）→ 改用「從那場歌單挑一首」的語氣，把選歌接回看團生活。
                    val tz = kotlinx.datetime.TimeZone.of("Asia/Taipei")
                    val today = kotlinx.datetime.Clock.System.now().toLocalDateTime(tz).date
                    val yesterday = today.minus(1, kotlinx.datetime.DateTimeUnit.DAY)
                    val recent = runCatching {
                        ep.concertRepo().getAllConcerts().first()
                            .filter { it.dateTime.date == today || it.dateTime.date == yesterday }
                            .maxByOrNull { it.dateTime }
                    }.getOrNull()
                    showDailyCheckInNotification(
                        context, streakAtRisk,
                        justWatched = recent?.let { it.name.ifBlank { it.artistName } }?.takeIf { it.isNotBlank() },
                        watchedToday = recent?.dateTime?.date == today
                    )
                }
            }
            DailyCheckInScheduler.schedule(context, "%02d:%02d".format(hour, minute))
            pending.finish()
        }
    }
}

object DailyCheckInScheduler {

    private const val REQUEST_CODE = 9101

    private fun alarmPendingIntent(context: Context, hour: Int, minute: Int): PendingIntent {
        val intent = Intent(context, DailyCheckInReceiver::class.java).apply {
            action = "com.bandary.DAILY_CHECKIN"
            putExtra("hour", hour)
            putExtra("minute", minute)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }

    /** 排定（或重排）每天 "HH:mm" 的精確提醒。 */
    fun schedule(context: Context, time: String) {
        val parts = time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 21
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_MONTH, 1)
        }

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = alarmPendingIntent(context, hour, minute)
        val triggerAt = next.timeInMillis
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                // 未授權精確鬧鐘 → 退回可在 Doze 執行的近似鬧鐘（比週期工作準）。
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        }.onFailure {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(alarmPendingIntent(context, 0, 0))
    }
}
