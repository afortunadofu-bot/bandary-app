package com.bandary

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltAndroidApp
class BandaryApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var settings: com.bandary.data.local.SettingsDataStore

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // 官方通知的推播頻道（Android O+）。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel("announcements", "官方通知", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Bandary 官方發布的通知" }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
        // 訂閱官方通知 topic（讓 Cloud Function 的 FCM 推播能送達本機，App 沒開也收得到）。
        runCatching { FirebaseMessaging.getInstance().subscribeToTopic("announcements") }
        // 每日精選提醒鬧鐘（演出當天 / 補紀錄 / N 年前的今天 / 週末免費活動）。冪等，重排同一 PendingIntent。
        runCatching { com.bandary.data.worker.DailyDigestScheduler.schedule(this) }
        // 自動備份排程：以前只有在設定頁切換時才排，從沒切過的人（預設值）永遠不會自動備份。
        // UPDATE 策略不會重設週期，每次啟動呼叫是安全的。
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching {
                com.bandary.data.worker.AutoBackupScheduler.apply(this@BandaryApp, settings.autoBackup.first())
            }
        }
    }
}
