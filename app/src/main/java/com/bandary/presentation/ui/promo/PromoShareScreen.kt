package com.bandary.presentation.ui.promo

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bandary.data.local.SettingsDataStore
import com.bandary.data.remote.FirestoreService
import com.bandary.domain.model.InviteCode
import com.bandary.domain.model.PROMO_MIN_LIKES
import com.bandary.domain.model.PROMO_TRIAL_DAYS
import com.bandary.domain.model.PromoReward
import com.bandary.domain.model.PromoStatus
import com.bandary.domain.model.PromoSubmission
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PromoShareViewModel @Inject constructor(
    private val firestore: FirestoreService,
    private val settings: SettingsDataStore
) : ViewModel() {

    private val _mine = MutableStateFlow<List<PromoSubmission>>(emptyList())
    val mine: StateFlow<List<PromoSubmission>> = _mine

    private val _pending = MutableStateFlow<List<PromoSubmission>>(emptyList())
    val pending: StateFlow<List<PromoSubmission>> = _pending

    private val _reward = MutableStateFlow<PromoReward?>(null)
    val reward: StateFlow<PromoReward?> = _reward

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    val isAdmin: StateFlow<Boolean> = MutableStateFlow(false).also { flow ->
        viewModelScope.launch { settings.isAdmin.collect { flow.value = it } }
    }

    fun isSignedIn(): Boolean = firestore.isSignedIn()

    /** 邀請碼由 uid 推導，不需要連線也算得出來。 */
    fun inviteCode(): String = firestore.currentUid()?.let { InviteCode.of(it) }.orEmpty()

    /** 兌換中（按鈕要壓住，不然會重複送出）。 */
    private val _redeeming = MutableStateFlow(false)
    val redeeming: StateFlow<Boolean> = _redeeming.asStateFlow()

    /**
     * 進到這頁就把自己的碼登記到雲端索引。
     *
     * 為什麼在這裡做：邀請碼是 uid 的單向雜湊，別人輸入我的碼時，
     * 伺服器要有 碼 → uid 的對照才找得到我。沒登記過的人，
     * 他發出去的碼別人兌換會得到「找不到這組邀請碼」。
     * 冪等，而且失敗不吵使用者——下次再進這頁會再試一次。
     */
    fun ensureInviteRegistered() = viewModelScope.launch {
        if (firestore.isSignedIn()) firestore.registerInviteCode()
    }

    fun redeem(code: String) = viewModelScope.launch {
        _redeeming.value = true
        firestore.redeemInviteCode(code)
            .onSuccess {
                _message.value = "兌換成功，你和對方都拿到 $it 天試用期"
                refresh()
            }
            .onFailure { _message.value = it.message ?: "兌換失敗" }
        _redeeming.value = false
    }

    fun refresh() = viewModelScope.launch {
        _loading.value = true
        firestore.myPromoSubmissions().onSuccess { _mine.value = it }
        _reward.value = firestore.myPromoReward()
        if (settings.isAdmin.first()) {
            firestore.pendingPromoSubmissions().onSuccess { _pending.value = it }
        }
        _loading.value = false
    }

    fun submit(platform: String, url: String, likes: Int, note: String) = viewModelScope.launch {
        _loading.value = true
        val nickname = settings.nickname.first()
        firestore.submitPromo(platform, url, likes, note, nickname)
            .onSuccess { _message.value = "已送出，等管理員審核" }
            .onFailure { _message.value = "送出失敗：${it.message}" }
        _loading.value = false
        refresh()
    }

    fun review(submission: PromoSubmission, approve: Boolean, comment: String) = viewModelScope.launch {
        firestore.reviewPromo(submission, approve, comment)
            .onSuccess { _message.value = if (approve) "已核准並發出試用期" else "已標為未通過" }
            .onFailure { _message.value = "審核失敗：${it.message}" }
        refresh()
    }

    fun clearMessage() { _message.value = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromoShareScreen(
    onBack: () -> Unit,
    viewModel: PromoShareViewModel = hiltViewModel()
) {
    val mine by viewModel.mine.collectAsState()
    val pending by viewModel.pending.collectAsState()
    val reward by viewModel.reward.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val message by viewModel.message.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()
    val signedIn = viewModel.isSignedIn()
    val context = androidx.compose.ui.platform.LocalContext.current

    val redeeming by viewModel.redeeming.collectAsState()
    var redeemInput by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    androidx.compose.runtime.LaunchedEffect(signedIn) { viewModel.ensureInviteRegistered() }

    val code = remember(signedIn) { viewModel.inviteCode() }
    val shareUrl = remember(code) { if (code.isBlank()) "" else InviteCode.shareUrl(code) }
    // QR 在背景執行緒產：zxing 編碼加上點陣圖填色，在低階機上會讓第一幀卡住。
    val qr by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, shareUrl) {
        value = if (shareUrl.isBlank()) null
        else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            com.bandary.data.util.QrCode.generate(shareUrl, 640)
        }
    }

    var showSubmit by remember { mutableStateOf(false) }
    var reviewTarget by remember { mutableStateOf<PromoSubmission?>(null) }

    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(message) {
        message?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("分享換試用期", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (!signedIn) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "登入之後才能拿到你的邀請碼。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 目前的試用期 ────────────────────────────────────
            reward?.takeIf { it.isActive() }?.let { r ->
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("試用期進行中", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Text(
                                "還有 ${r.daysLeft()} 天",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ── 邀請碼與 QR ─────────────────────────────────────
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(
                        Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val bmp = qr
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "邀請 QR code",
                                modifier = Modifier.size(200.dp)
                            )
                        } else {
                            Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            }
                        }
                        Text("你的邀請碼", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            code,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 4.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                                    cm?.setPrimaryClip(android.content.ClipData.newPlainText("邀請連結", shareUrl))
                                    android.widget.Toast.makeText(context, "已複製邀請連結", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("複製連結", fontSize = 13.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(
                                            android.content.Intent.EXTRA_TEXT,
                                            "我在用「聽團曆 Bandary」記錄每一場演唱會——票根、歌單、照片、統計都收在一起。\n" +
                                                "用我的邀請碼 $code 下載：$shareUrl"
                                        )
                                    }
                                    context.startActivity(android.content.Intent.createChooser(intent, "分享聽團曆"))
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Outlined.Share, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("分享", fontSize = 13.sp)
                            }
                        }
                        Text(
                            "這組 QR 也會印在你匯出的票根上，別人掃了就能直接下載。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── 兌換朋友的邀請碼 ────────────────────────────────
            // 在這版之前整條邀請流程是**單向的**：算得出自己的碼、印得出 QR、
            // 分享得出去，但全 App 沒有任何地方可以輸入一組碼。
            // 給出去的碼不會發生任何事，等於這個傳播機制根本沒有接起來。
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "用朋友的邀請碼",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "輸入朋友給你的 6 碼，雙方各拿 14 天試用期。每個帳號只能兌換一次。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.OutlinedTextField(
                                value = redeemInput,
                                // 邊打邊正規化：邀請碼會被手打與口述，
                                // 0/O、1/I/L 這幾組最常被看錯，normalize 會把它們修回字母表裡的字元。
                                onValueChange = { redeemInput = InviteCode.normalize(it) },
                                label = { Text("邀請碼") },
                                singleLine = true,
                                textStyle = androidx.compose.material3.LocalTextStyle.current.copy(
                                    letterSpacing = 4.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            androidx.compose.material3.FilledTonalButton(
                                onClick = { viewModel.redeem(redeemInput); redeemInput = "" },
                                enabled = signedIn && !redeeming && InviteCode.isValid(redeemInput),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                if (redeeming) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("兌換")
                                }
                            }
                        }
                        if (!signedIn) {
                            Text(
                                "要先登入才能兌換——獎勵是發給帳號的，不是發給這台手機。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ── 規則 ────────────────────────────────────────────
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("怎麼拿試用期", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Text("1. 發一篇介紹聽團曆的貼文，附上你的邀請碼或 QR",
                            style = MaterialTheme.typography.bodySmall)
                        Text("2. 貼文達到 $PROMO_MIN_LIKES 個讚",
                            style = MaterialTheme.typography.bodySmall)
                        Text("3. 回來這裡送出貼文連結",
                            style = MaterialTheme.typography.bodySmall)
                        Text("4. 審核通過就送 $PROMO_TRIAL_DAYS 天試用期",
                            style = MaterialTheme.typography.bodySmall)
                        Text(
                            "讚數會由管理員點開貼文確認，所以連結要是公開可見的。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = { showSubmit = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("我發文了，送出審核") }
            }

            // ── 我送出過的 ──────────────────────────────────────
            if (mine.isNotEmpty()) {
                item {
                    Text("我送出的", style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                }
                items(mine, key = { it.id }) { s -> SubmissionRow(s, showWho = false) }
            }

            // ── 管理員：待審 ────────────────────────────────────
            if (isAdmin && pending.isNotEmpty()) {
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
                item {
                    Text("待審核（${pending.size}）", style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.tertiary)
                }
                items(pending, key = { "p-" + it.id }) { s ->
                    Column {
                        SubmissionRow(s, showWho = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { reviewTarget = s }) { Text("審核", fontSize = 13.sp) }
                            TextButton(onClick = {
                                val i = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(s.postUrl)
                                )
                                runCatching { context.startActivity(i) }
                            }) { Text("開啟貼文", fontSize = 13.sp) }
                        }
                    }
                }
            }

            if (loading) {
                item {
                    Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }

    if (showSubmit) {
        var platform by remember { mutableStateOf("Threads") }
        var url by remember { mutableStateOf("") }
        var likes by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSubmit = false },
            title = { Text("送出審核") },
            text = {
                com.bandary.presentation.ui.components.DialogBody(spacing = 10.dp) {
                    OutlinedTextField(
                        value = platform, onValueChange = { platform = it },
                        label = { Text("平台") }, singleLine = true,
                        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = url, onValueChange = { url = it },
                        label = { Text("貼文連結 *") }, singleLine = true,
                        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = likes, onValueChange = { likes = it.filter { c -> c.isDigit() } },
                        label = { Text("目前讚數") }, singleLine = true,
                        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = note, onValueChange = { note = it },
                        label = { Text("想補充的（選填）") },
                        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "送出後就不能自己改內容了（審核中可以整筆撤回）。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = url.isNotBlank() && !loading,
                    onClick = {
                        viewModel.submit(platform, url, likes.toIntOrNull() ?: 0, note)
                        showSubmit = false
                    }
                ) { Text("送出") }
            },
            dismissButton = { TextButton(onClick = { showSubmit = false }) { Text("取消") } }
        )
    }

    reviewTarget?.let { target ->
        var comment by remember(target.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { reviewTarget = null },
            title = { Text("審核 ${target.nickname} 的推廣") },
            text = {
                com.bandary.presentation.ui.components.DialogBody(spacing = 8.dp) {
                    Text("自填讚數：${target.claimedLikes}（門檻 $PROMO_MIN_LIKES）",
                        style = MaterialTheme.typography.bodySmall)
                    Text(
                        "請先點「開啟貼文」確認實際讚數——這個數字是使用者自己填的。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = comment, onValueChange = { comment = it },
                        label = { Text("審核意見（選填）") },
                        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "核准會立刻發出 $PROMO_TRIAL_DAYS 天試用期。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.review(target, true, comment); reviewTarget = null }) {
                    Text("核准")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.review(target, false, comment); reviewTarget = null }) {
                    Text("不通過", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }
}

@Composable
private fun SubmissionRow(s: PromoSubmission, showWho: Boolean) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (showWho) "${s.nickname} · ${s.platform}" else s.platform,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    s.status.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = when (s.status) {
                        PromoStatus.APPROVED -> MaterialTheme.colorScheme.primary
                        PromoStatus.REJECTED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Text(s.postUrl, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            Text("自填讚數 ${s.claimedLikes}", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            s.reviewComment.takeIf { it.isNotBlank() }?.let {
                Text("管理員：$it", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
