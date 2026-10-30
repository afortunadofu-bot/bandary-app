package com.bandary.data.util

import android.content.Context
import android.content.Intent

/**
 * 把 App 推薦給朋友：開系統分享，內容是一句介紹＋Google Play 連結。
 *
 * 連結固定用正式版的套件名稱，不用 `context.packageName`——USB 測試版是 `com.bandary.app.debug`，
 * 那個 id 在商店上不存在，分享出去會變成無效連結。
 */
object ShareApp {

    const val PLAY_URL = "https://play.google.com/store/apps/details?id=com.bandary.app"

    private const val MESSAGE =
        "我在用「聽團曆 Bandary」記錄每一場演唱會與音樂祭：票根、歌單、照片、統計都收在一起，還能看朋友接下來要去哪幾場。\n" +
            "下載：$PLAY_URL"

    /**
     * [inviteCode] 有值時把邀請碼與帶 ref 的連結一起附上——
     * 使用者在「分享給朋友」時本來就是在推薦，順手帶上邀請碼不必另外做一個入口。
     */
    fun share(context: Context, text: String? = null, inviteCode: String? = null) {
        val body = text ?: if (inviteCode.isNullOrBlank()) MESSAGE else
            "我在用「聽團曆 Bandary」記錄每一場演唱會與音樂祭：票根、歌單、照片、統計都收在一起。" + NL_ +
                "用我的邀請碼 " + inviteCode + " 下載：" +
                com.bandary.domain.model.InviteCode.shareUrl(inviteCode)
        shareText(context, body)
    }

    private const val NL_ = "\n"

    private fun shareText(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "聽團曆 Bandary")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "分享聽團曆").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}
