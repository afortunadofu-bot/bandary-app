package com.bandary.presentation.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bandary.data.local.SettingsDataStore
import com.bandary.data.repository.ConcertRepository
import com.bandary.presentation.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    private val concertRepo: ConcertRepository
) : ViewModel() {
    // null＝尚未判斷完（避免畫面閃爍）；true＝要顯示引導；false＝不用顯示。
    // 既有使用者（已有演唱會資料但從沒設過這個新欄位）直接補記為已完成，不會打斷他們。
    val shouldShowOnboarding: StateFlow<Boolean?> =
        combine(settings.hasOnboarded, concertRepo.getAllConcerts()) { onboarded, concerts -> onboarded to concerts }
            .map { (onboarded, concerts) ->
                when {
                    onboarded -> false
                    concerts.isNotEmpty() -> { settings.setOnboarded(); false }
                    else -> true
                }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setStartTab(route: String) = viewModelScope.launch { settings.setStartTab(route) }
    fun finish() = viewModelScope.launch { settings.setOnboarded() }
}

/**
 * 首次啟動想先做的事。決定引導最後一步導向哪裡，同時寫入起始分頁——
 * 對第一次開 App、還沒看過任何頁面的人來說，「想做什麼」比「常用哪頁」好回答。
 */
private enum class StartIntent(
    val title: String,
    val cardDesc: String,
    val startTab: String,
    val actionLabel: String,
    val actionRoute: String?,
    val finalDesc: String
) {
    TRACK_UPCOMING(
        title = "記下接下來要看的場次",
        cardDesc = "幫你倒數，開賣和入場前提醒你。",
        startTab = Screen.Shows.route,
        actionLabel = "新增你的第一場演唱會",
        actionRoute = Screen.AddShow.createRoute(),
        finalDesc = "看過的、買了票的、還在搶票的都可以先記——不用一次填完，之後隨時能補。"
    ),
    IMPORT_PAST(
        title = "把過去看過的補回來",
        cardDesc = "一次匯入一批，統計和年度回顧立刻就有內容。",
        startTab = Screen.Shows.route,
        actionLabel = "去批量匯入",
        actionRoute = Screen.BulkImport.route,
        finalDesc = "用 CSV 或直接貼上清單，一次補進一大批；也能改從 Google 日曆匯入。匯完統計頁馬上就有東西看。"
    ),
    BROWSE_FREE(
        title = "先逛逛有什麼活動",
        cardDesc = "大家共同維護的清單，想去的一鍵收藏。",
        startTab = Screen.FreeEvents.route,
        actionLabel = "去看活動",
        actionRoute = null,
        finalDesc = "活動是大家一起維護的清單，看到想去的一鍵就能收藏成自己的場次。"
    )
}

/**
 * 首次啟動的引導流程（了解價值 → 個人化意圖 → 權限請求 → 開始）。
 * 每一步都能跳過，跳過不影響任何功能——引導只是加速理解，不是必經關卡。
 * onStart 帶著使用者選的意圖對應路徑（null＝直接落在起始分頁，不再往上疊頁）。
 */
@Composable
fun OnboardingScreen(
    onStart: (route: String?) -> Unit,
    onFinish: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    var step by remember { mutableIntStateOf(0) }
    var intent by remember { mutableStateOf(StartIntent.TRACK_UPCOMING) }
    val totalSteps = 4

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 頂部進度點
            StepProgressRow(step, totalSteps)

            AnimatedContent(targetState = step, label = "onboarding-step") { s ->
                when (s) {
                    0 -> ValueStep()
                    1 -> PersonalizeStep(
                        selected = intent,
                        onPick = { picked -> intent = picked; viewModel.setStartTab(picked.startTab) }
                    )
                    2 -> PermissionStep()
                    else -> FirstStep(intent)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        if (step < totalSteps - 1) step++
                        else { viewModel.finish(); onStart(intent.actionRoute) }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        if (step < totalSteps - 1) "繼續" else intent.actionLabel,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                TextButton(
                    onClick = { viewModel.finish(); onFinish() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (step < totalSteps - 1) "跳過引導" else "之後再說",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StepProgressRow(step: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { i ->
            Box(
                modifier = Modifier
                    .height(4.dp)
                    .weight(1f)
                    .background(
                        if (i <= step) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

// ── 1. 了解價值：一兩句話說清楚為什麼要用這個 app ──
@Composable
private fun ValueStep() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Bandary", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
        Text(
            "把你看過的每一場現場，變成一份會累積的檔案。",
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold
        )
        Text(
            "接下來的場次幫你倒數、提醒開賣；去過的自動變成統計、年度回顧和可以分享的票根——不用再翻聊天紀錄找日期、自己算場次和花費。",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "資料存在你的手機，不登入也能完整使用；核心功能全部免費。",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── 2. 個人化：想先做什麼（決定引導終點，同時寫入起始分頁）──
@Composable
private fun PersonalizeStep(selected: StartIntent, onPick: (StartIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("你想先做哪件事？", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "選一個當作起點，也會設成開啟 App 的起始畫面，之後隨時可以在設定改。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StartIntent.entries.forEach { option ->
                IntentOption(option = option, selected = selected == option, onClick = { onPick(option) })
            }
        }
    }
}

@Composable
private fun IntentOption(option: StartIntent, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                option.title,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface
            )
            Text(
                option.cardDesc,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── 3. 請求權限：說清楚理由、好處、拒絕後怎麼辦，保持透明 ──
@Composable
private fun PermissionStep() {
    val context = LocalContext.current
    var requested by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { requested = true }
    val alreadyGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("開賣 / 入場前，提醒你一下", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "開啟通知後，Bandary 會在開賣前、入場前、每日紀錄提醒你——不會用來推播其他內容。" +
                "\n\n不開也完全沒關係：所有記錄、統計、分享功能都照常使用，只是不會收到提醒，可以之後在設定裡再打開。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !alreadyGranted) {
            OutlinedButton(
                onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (requested) "已詢問過，仍可在設定開啟" else "開啟通知") }
        } else {
            Text("通知已經開啟。", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

// ── 4. 開始：依第 2 步選的意圖，帶到對應的第一個動作 ──
@Composable
private fun FirstStep(intent: StartIntent) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(intent.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            intent.finalDesc,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(0.dp))
    }
}
