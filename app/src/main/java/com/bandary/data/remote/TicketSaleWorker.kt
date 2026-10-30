package com.bandary.data.remote

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
import androidx.work.Data
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
 * Fires a local notification at the time the user expects a ticket sale to start.
 *
 * Schedule via [TicketSaleScheduler.schedule]. Cancel via [TicketSaleScheduler.cancel].
 * Requires POST_NOTIFICATIONS runtime permission on Android 13+.
 */
@HiltWorker
class TicketSaleWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val concertId = inputData.getString(KEY_CONCERT_ID) ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: "演唱會開賣"
        val message = inputData.getString(KEY_MESSAGE) ?: "票券即將開賣"
        val notifyId = inputData.getInt(KEY_NOTIFY_ID, concertId.hashCode())
        showNotification(concertId, title, message, notifyId)
        return Result.success()
    }

    @SuppressLint("MissingPermission")
    private fun showNotification(concertId: String, title: String, message: String, notifyId: Int) {
        val ctx = applicationContext
        val channelId = "ticket_sales"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(channelId) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        channelId,
                        "開賣提醒",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply { description = "演唱會開賣前推播" }
                )
            }
        }

        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
            // SINGLE_TOP 是關鍵：App 已經在跑時，少了它 Android 只會把既有 task 帶到前景、
            // 不會呼叫 onNewIntent，於是通知點進去停在你離開前那一頁，跟通知內容無關。
            Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("concertId", concertId)
        }
        val pendingFlags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else PendingIntent.FLAG_UPDATE_CURRENT
        val pending = PendingIntent.getActivity(ctx, concertId.hashCode(), intent, pendingFlags)

        val notification = NotificationCompat.Builder(ctx, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(com.bandary.R.drawable.ic_stat_bandary)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) {
            NotificationManagerCompat.from(ctx).notify(notifyId, notification)
        }
    }

    companion object {
        const val KEY_CONCERT_ID = "concert_id"
        const val KEY_TITLE = "title"
        const val KEY_MESSAGE = "message"
        const val KEY_NOTIFY_ID = "notify_id"
    }
}

object TicketSaleScheduler {
    /** 同一場所有開賣提醒共用的 tag，用來一次取消整場（多組開賣時間 → 多個 work）。 */
    private fun tag(concertId: String) = "ticket_sale_$concertId"

    /** 每一組開賣時間各自的唯一名稱。少了 index 的話多組只會留下最後排的那一個。 */
    private fun workName(concertId: String, index: Int) = "ticket_sale_${concertId}_$index"

    fun schedule(
        context: Context,
        concertId: String,
        triggerAtMillis: Long,
        title: String,
        message: String,
        index: Int = 0
    ) {
        val delayMs = triggerAtMillis - System.currentTimeMillis()
        // 觸發時間已過就不排（過去 coerceAtLeast(0) 會讓還原備份後立刻補發一堆過期開賣通知）。
        if (delayMs <= 0) {
            WorkManager.getInstance(context).cancelUniqueWork(workName(concertId, index))
            return
        }
        val data: Data = workDataOf(
            TicketSaleWorker.KEY_CONCERT_ID to concertId,
            TicketSaleWorker.KEY_TITLE to title,
            TicketSaleWorker.KEY_MESSAGE to message,
            // 通知 id 要跟著 index 走，否則第二組會蓋掉第一組那則通知。
            TicketSaleWorker.KEY_NOTIFY_ID to (concertId + "#" + index).hashCode()
        )
        val request = OneTimeWorkRequestBuilder<TicketSaleWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag(tag(concertId))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(workName(concertId, index), ExistingWorkPolicy.REPLACE, request)
    }

    /** 取消整場的所有開賣提醒（重新排程前一定要先呼叫，否則刪掉的那幾組還是會響）。 */
    fun cancel(context: Context, concertId: String) {
        WorkManager.getInstance(context).cancelAllWorkByTag(tag(concertId))
    }
}
