package com.bandary.data.push

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.bandary.MainActivity
import com.bandary.R
import com.google.firebase.messaging.FirebaseMessagingService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.google.firebase.messaging.RemoteMessage

/**
 * 接收 Cloud Function 送來的官方通知 FCM 推播，顯示成系統通知。
 * （點通知會開啟 App；App 內的「官方通知」收信匣為可靠備援。）
 */
@AndroidEntryPoint
class BandaryMessagingService : FirebaseMessagingService() {

    // 指定對象的公告要靠 token 才送得到，所以這支 service 需要寫 Firestore。
    @Inject lateinit var firestoreService: com.bandary.data.remote.FirestoreService


    override fun onMessageReceived(message: RemoteMessage) {
        val n = message.notification
        val title = n?.title ?: message.data["title"] ?: "Bandary 官方通知"
        val body = n?.body ?: message.data["body"] ?: ""

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
            // SINGLE_TOP 是關鍵：App 已經在跑時，少了它 Android 只會把既有 task 帶到前景、
            // 不會呼叫 onNewIntent，於是通知點進去停在你離開前那一頁，跟通知內容無關。
            Intent.FLAG_ACTIVITY_SINGLE_TOP
            // 官方通知原本沒帶任何目標，點了只會開 App 落在起始頁，跟通知內容無關。
            // 導到「我的」——官方通知收信匣就在那裡。
            putExtra("navTarget", "announcements")
        }
        // requestCode 要跟著通知走。全部用 0 的話，所有推播共用同一個 PendingIntent，
        // FLAG_UPDATE_CURRENT 會讓舊通知被新通知的內容覆蓋。
        val notifId = System.currentTimeMillis().toInt()
        val pi = PendingIntent.getActivity(
            this, notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, "announcements")
            .setSmallIcon(com.bandary.R.drawable.ic_stat_bandary)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        val mgr = getSystemService(NotificationManager::class.java)
        mgr?.notify(notifId, notif)
    }

    /**
     * token 換新時存回 `users/{uid}.fcmTokens`。
     *
     * 全體公告走 topic 就夠，但**指定對象**的公告沒辦法用 topic
     * ——topic 是「誰訂閱誰收得到」，不能只送給其中三個人。
     * 要指定對象就得知道那些人的裝置 token，所以這裡不能再是空的。
     *
     * 沒登入時不做事：token 要掛在帳號底下才有意義，之後登入會再補一次（見 BandaryApp）。
     */
    // firebase-messaging 25.1.0 把 getInstance() / getToken() / onNewToken() 整組標為棄用
    // （用 javap 查證過）。沒有「換一行」的替代寫法，是整組遷移。
    // 而 onNewToken 是 App 取得推播 token 的唯一入口——改錯就是推播安靜地停掉，
    // 正好是這個專案一直在踩的那種不報錯的失效。要改要單獨一批 + 真機收得到推播才算過。
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching { firestoreService.registerPushToken(token) }
        }
    }
}
