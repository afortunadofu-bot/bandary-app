package com.bandary.presentation.ui.profile

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bandary.data.billing.BillingAvailability
import com.bandary.data.billing.PhotoQuota
import com.bandary.data.billing.ProPlan
import com.bandary.data.billing.ProTier
import com.bandary.presentation.ui.settings.SettingsViewModel

private data class ProFeature(val icon: ImageVector, val title: String, val desc: String)

// 付費（Bandary Pro）功能清單。使用者本來就看得到這些被鎖的功能，解鎖後全部開放。
private val PRO_FEATURES = listOf(
    ProFeature(Icons.Outlined.Collections, "上傳多張照片", "每場演唱會除了封面，還能加上多張現場照片、票根、座位圖；張數依方案而定，見上方比較。"),
    ProFeature(Icons.Outlined.QueryStats, "詳細統計", "看團花費、藝人排行、常去場館、縣市足跡、選歌品味等完整分析。"),
    // 「移除廣告」暫時從賣點拿掉：目前 App 完全沒有接任何廣告 SDK，掛在這裡等於賣一個不存在的東西。
    // 之後真的接了廣告再把這行放回來即可，Icons.Outlined.Block 保留不用刪。
    ProFeature(Icons.Outlined.Palette, "自訂調色盤", "除了預設主題色，還能新增自己的專屬顏色。"),
    ProFeature(Icons.Outlined.DarkMode, "深色模式", "切換深色 / 淺色主題，夜間看團不刺眼。"),
    ProFeature(Icons.Outlined.Download, "下載圖片", "把票根、統計、徽章分享圖存進相簿。"),
    ProFeature(Icons.AutoMirrored.Outlined.QueueMusic, "新增歌單", "逐首記錄每場演唱會的歌單，分享時一併附上。"),
    ProFeature(Icons.Outlined.IosShare, "匯出資料", "把你的演唱會與選歌紀錄匯出備份。")
)

/** launchBillingFlow 需要 Activity；Compose 拿到的 Context 可能被包了好幾層 ContextWrapper。 */
private fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProFeaturesScreen(
    onBack: () -> Unit,
    onUnlock: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    billingViewModel: ProBillingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val isPro = state.isPro
    val isProPlus = state.isProPlus
    var selectedTier by remember { mutableStateOf(if (isProPlus) ProTier.PRO_PLUS else ProTier.PRO) }
    // 點功能列看範例：還沒付錢前就能知道解鎖後長怎樣。
    var demoFeature by remember { mutableStateOf<ProFeature?>(null) }
    demoFeature?.let { f -> ProFeatureDemoDialog(f.title, f.desc, onDismiss = { demoFeature = null }) }
    var selectedLifetime by remember { mutableStateOf(true) }  // true＝一次買斷，false＝年訂閱
    val selectedPlan = when {
        selectedTier == ProTier.PRO_PLUS && selectedLifetime -> ProPlan.PLUS_LIFETIME
        selectedTier == ProTier.PRO_PLUS -> ProPlan.PLUS_YEARLY
        selectedLifetime -> ProPlan.LIFETIME
        else -> ProPlan.YEARLY
    }

    val ctx = LocalContext.current
    val activity = remember(ctx) { ctx.findActivity() }
    val availability by billingViewModel.availability.collectAsState()
    val offers by billingViewModel.offers.collectAsState()
    val owned by billingViewModel.owned.collectAsState()
    val busy by billingViewModel.busy.collectAsState()
    val message by billingViewModel.message.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            billingViewModel.clearMessage()
        }
    }

    // 價格一律以 Play 回報的為準（幣別/地區價由 Play 決定）；查不到時才退回寫死的參考價。
    fun priceOf(plan: ProPlan, fallback: String) = offers[plan]?.formattedPrice ?: fallback

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            isProPlus -> "Bandary Pro+・已解鎖"
                            isPro -> "Bandary Pro・已解鎖"
                            else -> "Bandary Pro"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    when {
                        isProPlus -> "感謝支持！你是 Pro+ 收藏家方案，以下進階功能與最高張數上限都已為你解鎖。"
                        isPro -> "感謝支持！以下進階功能都已為你解鎖。"
                        else -> "Bandary 的核心記錄功能永遠免費。以下是解鎖後可用的進階功能——現在就看得到，解鎖即可使用。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!isPro) {
                item {
                    PricingSection(
                        selectedTier = selectedTier,
                        onSelectTier = { selectedTier = it },
                        selectedLifetime = selectedLifetime,
                        onSelectLifetime = { selectedLifetime = it },
                        proBuyoutPrice = priceOf(ProPlan.LIFETIME, PRICE_BUYOUT),
                        proAnnualPrice = priceOf(ProPlan.YEARLY, PRICE_ANNUAL),
                        plusBuyoutPrice = priceOf(ProPlan.PLUS_LIFETIME, PRICE_PLUS_BUYOUT),
                        plusAnnualPrice = priceOf(ProPlan.PLUS_YEARLY, PRICE_PLUS_ANNUAL),
                        // 只有訂閱才有試用；買斷本來就是一次付清，掛「先免費試用」只會讓人以為買斷也能試。
                        trialDays = if (selectedLifetime) 0 else offers[selectedPlan]?.trialDays ?: 0,
                        availability = availability,
                        busy = busy,
                        onBuy = { activity?.let { billingViewModel.buy(it, selectedPlan) } }
                    )
                }
            } else {
                item {
                    ProStatusCard(
                        owned = owned,
                        isProPlus = isProPlus,
                        proBuyoutPrice = priceOf(ProPlan.LIFETIME, PRICE_BUYOUT),
                        plusBuyoutPrice = priceOf(ProPlan.PLUS_LIFETIME, PRICE_PLUS_BUYOUT),
                        plusAnnualPrice = priceOf(ProPlan.PLUS_YEARLY, PRICE_PLUS_ANNUAL),
                        busy = busy,
                        onBuyPlan = { plan -> activity?.let { billingViewModel.buy(it, plan) } },
                        onManageSubscription = { plan ->
                            runCatching {
                                ctx.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(billingViewModel.manageSubscriptionUrl(plan))
                                    )
                                )
                            }
                        }
                    )
                }
            }
            items(PRO_FEATURES) { f -> ProFeatureRow(f, unlocked = isPro, onClick = { demoFeature = f }) }
            item {
                // 換手機／重裝後用同一個 Google 帳號恢復；解鎖後仍保留，換機時才找得到。
                TextButton(onClick = { billingViewModel.restore() }, modifier = Modifier.fillMaxWidth()) {
                    Text("恢復購買", style = MaterialTheme.typography.labelLarge)
                }
            }
            if (!isPro) {
                item {
                    TextButton(onClick = onUnlock, modifier = Modifier.fillMaxWidth()) {
                        Text("已有折扣碼？點此輸入解鎖", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProFeatureRow(f: ProFeature, unlocked: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(f.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(f.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(f.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("點我看範例", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Icon(
                if (unlocked) Icons.Outlined.CheckCircle else Icons.Outlined.Lock,
                contentDescription = if (unlocked) "已解鎖" else "未解鎖",
                tint = if (unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// 正式付費方案：Pro／Pro+ 兩層 × 買斷／年訂閱雙軌，共 4 個商品。
// 這裡的金額只是「Play 還沒回價格時」的參考顯示；真正收多少以 Play Console 設定為準
// （改價在 Play Console 改即可，不用改 App）。商品 ID 見 BillingManager.ProPlan。
//
// 定價邏輯（v1.45 重新設計，原本 NT$120 買斷／NT$60 年訂閱的組合會讓年訂閱「兩年就回本」，
// 沒人有理由買斷，長期反而拿到更少錢）：年訂閱訂在買斷價的一半左右，
// 讓「兩年左右回本」變成使用者自己會算的誘因，越常用的人自然被導去買斷；
// Pro+ 訂在 Pro 的 2 倍價，張數也剛好 2 倍（3 → 5 → 10），比例好記、對重度使用者是
// 明顯划算的加購，同時把 ARPU 拉開，不必為了單一低價方案犧牲兩端使用者的意願。
//
// 實際售價一律以 Play Console 設定、Play 回傳的為準（改價只要改 Play Console，不必發新版）。
// 還沒從 Play 查到價格時只顯示「—」，不放寫死的數字：寫死的價格一旦跟 Play 不同，就是在顯示錯的價錢。
private const val PRICE_PENDING = "—"
private const val PRICE_BUYOUT = PRICE_PENDING
private const val PRICE_ANNUAL = PRICE_PENDING
private const val PRICE_PLUS_BUYOUT = PRICE_PENDING
private const val PRICE_PLUS_ANNUAL = PRICE_PENDING

/** 各方案每場照片上限比較：解鎖前最主要的「為什麼要付錢」說明，所以獨立成自己的區塊。 */
@Composable
private fun PhotoQuotaCompareRow(tier: ProTier?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        QuotaChip("免費", PhotoQuota.FREE, selected = tier == null)
        QuotaChip("Pro", PhotoQuota.PRO, selected = tier == ProTier.PRO)
        QuotaChip("Pro+", PhotoQuota.PRO_PLUS, selected = tier == ProTier.PRO_PLUS)
    }
}

@Composable
private fun QuotaChip(label: String, count: Int, selected: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label, style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            "$count 張", style = MaterialTheme.typography.titleMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun PricingSection(
    selectedTier: ProTier,
    onSelectTier: (ProTier) -> Unit,
    selectedLifetime: Boolean,
    onSelectLifetime: (Boolean) -> Unit,
    proBuyoutPrice: String,
    proAnnualPrice: String,
    plusBuyoutPrice: String,
    plusAnnualPrice: String,
    trialDays: Int,
    availability: BillingAvailability,
    busy: Boolean,
    onBuy: () -> Unit
) {
    val buyoutPrice = if (selectedTier == ProTier.PRO_PLUS) plusBuyoutPrice else proBuyoutPrice
    val annualPrice = if (selectedTier == ProTier.PRO_PLUS) plusAnnualPrice else proAnnualPrice
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("解鎖 Bandary Pro", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "核心記錄永遠免費；進階功能一次解鎖，兩個方案的差異只在每場能放幾張照片。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PhotoQuotaCompareRow(tier = selectedTier)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PlanCard(Modifier.weight(1f), selectedTier == ProTier.PRO, "Pro", if (selectedLifetime) proBuyoutPrice else proAnnualPrice, "每場 ${PhotoQuota.PRO} 張", "推薦") {
                    onSelectTier(ProTier.PRO)
                }
                PlanCard(Modifier.weight(1f), selectedTier == ProTier.PRO_PLUS, "Pro+", if (selectedLifetime) plusBuyoutPrice else plusAnnualPrice, "每場 ${PhotoQuota.PRO_PLUS} 張", "收藏家") {
                    onSelectTier(ProTier.PRO_PLUS)
                }
            }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedLifetime,
                    onClick = { onSelectLifetime(true) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("一次買斷") }
                SegmentedButton(
                    selected = !selectedLifetime,
                    onClick = { onSelectLifetime(false) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("年訂閱") }
            }
            // 按鈕上主打「免費試用」時，實際金額與自動續訂一定要在旁邊講清楚——
            // 這是 Play 訂閱政策的要求，也是避免到期被扣款才發現、直接變成退款與負評的關鍵。
            if (trialDays > 0) {
                Text(
                    "先免費試用 $trialDays 天，之後為 $annualPrice／年、每年自動續訂。" +
                        "到期前我們會提醒你一次，不想繼續可以隨時在 Google Play 取消。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
            val ready = availability == BillingAvailability.READY
            // Play 沒有回傳這個方案（商品未啟用、基本方案未發布）時價格是「—」，不能讓人按下去買一個查不到的東西。
            val selectedPrice = if (selectedLifetime) buyoutPrice else annualPrice
            val offerMissing = ready && selectedPrice == PRICE_PENDING
            Button(
                onClick = onBuy,
                enabled = ready && !busy && !offerMissing,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                when {
                    busy -> CircularProgressIndicator(
                        modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    availability == BillingAvailability.UNAVAILABLE -> Text("Google Play 目前無法付款")
                    !ready -> Text("連線 Google Play 中…")
                    offerMissing -> Text("這個方案暫時無法購買")
                    selectedLifetime -> Text("以 $buyoutPrice 解鎖・永久", fontWeight = FontWeight.SemiBold)
                    trialDays > 0 -> Text("免費試用 $trialDays 天", fontWeight = FontWeight.SemiBold)
                    else -> Text("訂閱 $annualPrice／年", fontWeight = FontWeight.SemiBold)
                }
            }
            Text(
                if (availability == BillingAvailability.UNAVAILABLE)
                    "這台裝置無法使用 Google Play 付款（例如未安裝 Play 商店）。你仍可用折扣碼解鎖。"
                else "透過 Google Play 購買；核心功能永遠免費。",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 已解鎖時的狀態卡。分開講清楚目前的來源（Pro／Pro+ × 買斷／訂閱），
 * 並在還不是 Pro+ 時給一個清楚的升級入口——這是轉換率最高的時機點：
 * 使用者已經付過一次錢、對 App 有信任，加購的心理門檻遠低於從零開始說服。
 */
@Composable
private fun ProStatusCard(
    owned: Set<ProPlan>,
    isProPlus: Boolean,
    proBuyoutPrice: String,
    plusBuyoutPrice: String,
    plusAnnualPrice: String,
    busy: Boolean,
    onBuyPlan: (ProPlan) -> Unit,
    onManageSubscription: (ProPlan) -> Unit
) {
    val proLifetime = ProPlan.LIFETIME in owned
    val proYearly = ProPlan.YEARLY in owned
    val plusLifetime = ProPlan.PLUS_LIFETIME in owned
    val plusYearly = ProPlan.PLUS_YEARLY in owned
    val activeYearlyPlan = if (plusYearly) ProPlan.PLUS_YEARLY else ProPlan.YEARLY
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                when {
                    plusLifetime -> "Pro+ 永久解鎖中"
                    plusYearly -> "Pro+ 年訂閱進行中"
                    proLifetime -> "永久解鎖中"
                    proYearly -> "年訂閱進行中"
                    else -> "已解鎖"
                },
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
            )
            Text(
                when {
                    plusLifetime && plusYearly ->
                        "你同時有 Pro+ 買斷與年訂閱。買斷已是永久解鎖，建議到 Google Play 取消年訂閱，避免重複扣款。"
                    plusLifetime -> "一次買斷，Pro+ 所有功能永久可用，每場最多 ${PhotoQuota.PRO_PLUS} 張照片。"
                    plusYearly -> "Pro+ 訂閱會每年自動續訂，可隨時在 Google Play 取消；每場最多 ${PhotoQuota.PRO_PLUS} 張照片。"
                    proLifetime && proYearly ->
                        "你同時有買斷與年訂閱。買斷已是永久解鎖，建議到 Google Play 取消年訂閱，避免重複扣款。"
                    proLifetime -> "一次買斷，所有進階功能永久可用，每場最多 ${PhotoQuota.PRO} 張照片。"
                    proYearly -> "訂閱會每年自動續訂，可隨時在 Google Play 取消；每場最多 ${PhotoQuota.PRO} 張照片。"
                    else -> "進階功能已為你開啟。"
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (proYearly || plusYearly) {
                OutlinedButton(onClick = { onManageSubscription(activeYearlyPlan) }, modifier = Modifier.fillMaxWidth()) {
                    Text("管理／取消訂閱")
                }
            }
            if (proYearly && !proLifetime && !plusLifetime) {
                Button(
                    onClick = { onBuyPlan(ProPlan.LIFETIME) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("改成一次買斷 $proBuyoutPrice・永久", fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "買斷後記得回 Google Play 取消年訂閱，系統不會自動幫你停。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (plusYearly && !plusLifetime) {
                Button(
                    onClick = { onBuyPlan(ProPlan.PLUS_LIFETIME) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("改成 Pro+ 買斷 $plusBuyoutPrice・永久", fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "買斷後記得回 Google Play 取消年訂閱，系統不會自動幫你停。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!isProPlus) {
                androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                Text("想放更多照片？", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "升級 Pro+，每場照片上限從 ${PhotoQuota.PRO} 張變成 ${PhotoQuota.PRO_PLUS} 張，其餘功能不變。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { onBuyPlan(ProPlan.PLUS_LIFETIME) },
                        enabled = !busy,
                        modifier = Modifier.weight(1f)
                    ) { Text("買斷 $plusBuyoutPrice") }
                    Button(
                        onClick = { onBuyPlan(ProPlan.PLUS_YEARLY) },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("年訂閱 $plusAnnualPrice") }
                }
            }
        }
    }
}

@Composable
private fun PlanCard(
    modifier: Modifier, selected: Boolean, title: String, price: String, sub: String, badge: String?, onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                if (badge != null) {
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(badge, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text(price, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
