package com.bandary.presentation.ui.notice

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bandary.data.remote.FirestoreService
import com.bandary.presentation.ui.components.DialogBody
import com.bandary.presentation.ui.settings.SettingsViewModel

/**
 * App 內的**彈出式通知**：只有管理員發了通知，才會跳。
 *
 * ## 誰決定要跳
 * **管理員按「發送」的那一刻**，不是 App 自己判斷。
 *
 * 這一點是刻意的。原本的作法是比對版號——雲端公告的版號比本機新就自動跳「有新版」。
 * 那個作法有兩個問題：
 *
 *  1. **跳出來的時機不是你決定的。** 版號一公告，所有人下次開 App 都會被攔一次，
 *     就算你只是先把版號填好、還沒想通知人。
 *  2. **它會在你沒預期的時候再跳一次。** 任何「自動判斷」都得再加一層「什麼時候不要跳」的規則，
 *     而那層規則永遠會有漏掉的情況。
 *
 * 所以現在只有一個來源：**沒看過的官方通知**。要通知人就去後台按發送（版本卡的
 * 「同時通知所有人」或通知卡的「發送官方通知」都會發），要安靜就不要按。
 * 版號本身還是會寫進雲端，「我的 → 關於與說明 → 最新版本」那一列照樣會比對出可更新——
 * 那一列是使用者自己去看的，不會攔人。
 *
 * ## 這則通知剛好在講更新的話
 * 如果這台裝置的版本真的比雲端公告的舊，對話框會多一顆「前往更新」。
 * 條件是**真的比較舊**才給，不是看通知內容猜——寧可少一顆按鈕，
 * 也不要在已經是最新版的人面前放一顆按了沒用的「前往更新」。
 *
 * ## 不會重複跳
 * 按「知道了」就等於已讀，不再跳。有多則未讀時只跳最新的那一則，
 * 其餘留在「我的」頁的收信匣——連續彈三個對話框比不彈還糟。
 */
@Composable
fun AppNoticeHost(viewModel: SettingsViewModel) {
    val ctx = LocalContext.current
    val pkg = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0) }.getOrNull()
    }
    val installedCode = remember(pkg) {
        pkg?.let { androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(it) } ?: 0L
    }

    val latest by viewModel.latestVersion.collectAsState()
    val announcements by viewModel.announcements.collectAsState()
    val lastRead by viewModel.lastReadAnnouncementAt.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadLatestVersion()
        viewModel.refreshAnnouncements()
    }

    val notice = announcements.firstOrNull { it.createdAt > lastRead } ?: return

    // installedCode == 0 代表連本機版號都讀不到。讀不到就不要猜「你的比較舊」，
    // 不然會在已經是最新版的人面前放一顆按了沒用的按鈕。
    val canUpdate = installedCode > 0L && (latest?.versionCode ?: 0L) > installedCode

    AnnouncementNoticeDialog(
        notice = notice,
        updateVersionName = latest?.versionName?.takeIf { canUpdate },
        onConfirm = { viewModel.markAnnouncementsRead() },
        onOpenStore = {
            viewModel.markAnnouncementsRead()
            openPlayStore(ctx)
        }
    )
}

@Composable
private fun AnnouncementNoticeDialog(
    notice: FirestoreService.Announcement,
    updateVersionName: String?,
    onConfirm: () -> Unit,
    onOpenStore: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onConfirm,
        icon = {
            Icon(
                Icons.Outlined.Campaign, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text(notice.title.ifBlank { "官方通知" }) },
        text = {
            DialogBody(spacing = 8.dp) {
                Text(notice.body, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "這則通知會留在「我的」頁的通知收信匣裡。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        // 有新版可更新時，「前往更新」才是主要動作；否則只有「知道了」。
        confirmButton = {
            if (updateVersionName != null) {
                TextButton(onClick = onOpenStore) { Text("前往更新 v$updateVersionName") }
            } else {
                TextButton(onClick = onConfirm) { Text("知道了") }
            }
        },
        // 沒有更新可按時整個不給 dismissButton（而不是給一個空的槽），
        // 否則對話框底下會留一塊莫名的空白。
        dismissButton = if (updateVersionName != null) {
            { TextButton(onClick = onConfirm) { Text("稍後再說") } }
        } else null
    )
}

/** 開 Google Play 的 App 頁；沒裝 Play 商店就退回網頁版。 */
private fun openPlayStore(ctx: android.content.Context) {
    val market = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        android.net.Uri.parse("market://details?id=${ctx.packageName}")
    )
    runCatching { ctx.startActivity(market) }.onFailure {
        runCatching {
            ctx.startActivity(
                android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://play.google.com/store/apps/details?id=${ctx.packageName}")
                )
            )
        }
    }
}
