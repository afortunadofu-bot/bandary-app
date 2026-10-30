package com.bandary.data.billing

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
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * 免費試用快到期前的提醒。試用期本身由 Play Console 的方案設定決定，這裡只負責「提前講一聲」——
 * 沒有這則通知的話，使用者多半是在被扣款當下才想起有訂閱這回事，那種驚訝會直接變成退款與負評。
 */
@HiltWorker
class TrialReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val daysLeft = inputData.getInt(KEY_DAYS_LEFT, 3)
        showNotification(daysLeft)
        return Result.success()
    }

    @SuppressLint("MissingPermission")
    private fun showNotification(daysLeft: Int) {
        val ctx = applicationContext
        val channelId = "trial_reminder"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(channelId) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(channelId, "試用到期提醒", NotificationManager.IMPORTANCE_DEFAULT)
                        .apply { description = "免費試用結束前的提醒" }
                )
            }
        }

        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
            // SINGLE_TOP 是關鍵：App 已經在跑時，少了它 Android 只會把既有 task 帶到前景、
            // 不會呼叫 onNewIntent，於是通知點進去停在你離開前那一頁，跟通知內容無關。
            Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("navTarget", "pro")
        }
        val pendingFlags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else PendingIntent.FLAG_UPDATE_CURRENT
        val pending = PendingIntent.getActivity(ctx, NOTIFY_ID, intent, pendingFlags)

        val notification = NotificationCompat.Builder(ctx, channelId)
            .setContentTitle("免費試用還剩 $daysLeft 天")
            .setContentText("試用結束後會自動開始收費。想繼續就不用做任何事；不想繼續，可以到 Google Play 取消。")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "試用結束後會自動開始收費。想繼續就不用做任何事；" +
                        "不想繼續的話，可以隨時到 Google Play 的訂閱管理取消，取消後在試用期結束前都還能繼續使用。"
                )
            )
            .setSmallIcon(com.bandary.R.drawable.ic_stat_bandary)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) NotificationManagerCompat.from(ctx).notify(NOTIFY_ID, notification)
    }

    companion object {
        const val KEY_DAYS_LEFT = "days_left"
        const val NOTIFY_ID = 5020
    }
}

object TrialReminderScheduler {
    private const val WORK_NAME = "trial_reminder"

    /** 提前幾天提醒。試用期短於這個天數時會自動縮短，見 [schedule]。 */
    private const val LEAD_DAYS = 3

    /**
     * 依「試用開始時間 + 試用天數」排一則到期前提醒。
     * 試用太短（例如 3 天）時提前 3 天會排到過去，這時改成試用期的一半，至少還來得及反應。
     */
    fun schedule(context: Context, trialStartMillis: Long, trialDays: Int) {
        if (trialDays <= 0) return
        val leadDays = if (trialDays > LEAD_DAYS) LEAD_DAYS else maxOf(1, trialDays / 2)
        val endMillis = trialStartMillis + TimeUnit.DAYS.toMillis(trialDays.toLong())
        val fireAt = endMillis - TimeUnit.DAYS.toMillis(leadDays.toLong())
        val delayMs = fireAt - System.currentTimeMillis()
        if (delayMs <= 0) return   // 已經來不及提前通知就不排，免得一裝好就跳一則過期提醒

        val request = OneTimeWorkRequestBuilder<TrialReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(TrialReminderWorker.KEY_DAYS_LEFT to leadDays))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /** 買斷或取消訂閱後就不需要這則提醒了。 */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
