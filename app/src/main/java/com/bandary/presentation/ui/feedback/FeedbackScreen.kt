package com.bandary.presentation.ui.feedback

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bandary.data.remote.FirestoreService
import com.bandary.data.repository.FeedbackRepository
import com.bandary.domain.model.Feedback
import com.bandary.domain.model.FeedbackCategory
import com.bandary.presentation.ui.components.EmptyState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FeedbackViewModel @Inject constructor(
    private val repo: FeedbackRepository,
    private val firestore: FirestoreService
) : ViewModel() {

    /**
     * 後台設定的聯絡信箱。「聯絡我們」原本是「我的」頁獨立一列，
     * 開一個只顯示信箱的對話框——那和「意見回饋」是同一件事：找開發者。
     * 合併之後信箱放在這一頁，寫完表單還能順手寄信。
     *
     * 讀的是後台可改的 config/contact（不是寫死的預設值），
     * 所以換信箱不需要重新上架。讀不到就留空，UI 端不顯示那一塊。
     */
    private val _contactEmails = kotlinx.coroutines.flow.MutableStateFlow<List<String>>(emptyList())
    val contactEmails: StateFlow<List<String>> = _contactEmails
    fun loadContactEmails() = viewModelScope.launch {
        firestore.fetchContactEmails().onSuccess { _contactEmails.value = it }
    }

    val loading: StateFlow<Boolean> = repo.loading
    val message: StateFlow<String?> = repo.message
    val items: StateFlow<List<Feedback>> = repo.items

    fun isSignedIn(): Boolean = firestore.isSignedIn()

    fun submit(
        category: String,
        area: String,
        issueType: String,
        message: String,
        contact: String,
        rating: Int,
        nps: Int,
        usageFrequency: String,
        favoriteFeature: String,
        role: String,
        extra: FeedbackExtra,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            val ok = repo.submit(
                Feedback(
                    category = category,
                    area = area.ifBlank { null },
                    issueType = issueType.ifBlank { null },
                    message = message.trim(),
                    contact = contact.trim().ifBlank { null },
                    rating = rating.takeIf { it in 1..5 },
                    nps = nps.takeIf { it in 0..10 },
                    usageFrequency = usageFrequency.ifBlank { null },
                    favoriteFeature = favoriteFeature.trim().ifBlank { null },
                    role = role.ifBlank { null },
                    source = extra.source.ifBlank { null },
                    wishes = extra.wishes.takeIf { it.isNotEmpty() }?.joinToString("、"),
                    painPoint = extra.painPoint.trim().ifBlank { null },
                    payIntent = extra.payIntent.ifBlank { null },
                    appVersion = extra.appVersion.ifBlank { null }
                )
            )
            if (ok) onDone()
        }
    }

    fun refresh() = viewModelScope.launch { repo.refresh() }
    fun delete(id: String) = viewModelScope.launch { repo.delete(id) }
    fun clearMessage() = repo.clearMessage()
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FeedbackScreen(
    onBack: () -> Unit,
    viewModel: FeedbackViewModel = hiltViewModel()
) {
    val loading by viewModel.loading.collectAsState()
    val message by viewModel.message.collectAsState()
    val context = LocalContext.current

    // 預設「問題回報」：來這裡的人大多是遇到問題。
    var category by remember { mutableStateOf(FeedbackCategory.BUG) }
    // 區塊與細項都可複選：一個問題常常同時牽涉好幾處（例如備份失敗又導致照片不見）。
    var areas by remember { mutableStateOf(setOf<String>()) }       // 哪些區塊
    var issueTypes by remember { mutableStateOf(setOf<String>()) }  // 哪些問題 / 建議
    // 問題回報、功能建議都要先選區塊與細項；「其他」不強迫。
    val needsArea = category != FeedbackCategory.OTHER
    var text by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var rating by remember { mutableStateOf(0) }   // 滿意度 0=未評、1–5
    var role by remember { mutableStateOf("") }            // 身分
    var usageFreq by remember { mutableStateOf("") }       // 使用頻率
    var nps by remember { mutableStateOf(-1) }             // 推薦分數 -1=未評、0–10
    var favFeature by remember { mutableStateOf("") }      // 最常用功能
    var fullForm by remember { mutableStateOf(false) }     // false=簡易版、true=完整版（含 KPI 欄位）
    // 完整版多問的幾題（實機回報「希望問多一點」）：來源、想加強的方向、困擾、付費意願。
    var source by remember { mutableStateOf("") }
    var wishes by remember { mutableStateOf(setOf<String>()) }
    var painPoint by remember { mutableStateOf("") }
    var payIntent by remember { mutableStateOf("") }
    val appVersion = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("意見回饋", fontWeight = FontWeight.Bold) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 常見問題（可展開）──────────────────────────────
            FaqSection()

            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text("意見回饋", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            // 簡易 / 完整 兩種表單切換：簡易只填類型與想說的話；完整多收身分 / 頻率 / 滿意度 / NPS 等 KPI。
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !fullForm,
                    onClick = { fullForm = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("簡易") }
                SegmentedButton(
                    selected = fullForm,
                    onClick = { fullForm = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("完整") }
            }
            Text(
                if (fullForm) "填得越完整，越能幫助我們判斷改進方向。除了「想說的話」以外皆為選填。"
                else "只想快速反映？選好類型、區塊，再寫想說的話即可；想幫更多可切到「完整」。",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ── 先分類：類型 → 哪個區塊 → 哪種問題 ──────────────────
            // 放在表單最前面：開發者一眼就知道要去看 App 的哪裡、是哪一種問題。
            FeedbackFieldLabel("回饋類型")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(FeedbackCategory.BUG, FeedbackCategory.FEATURE, FeedbackCategory.OTHER).forEach { c ->
                    FilterChip(
                        selected = category == c,
                        onClick = {
                            // 換類型時細項清單會變，原本選的細項不一定還在，一併清掉。
                            if (category != c) issueTypes = emptySet()
                            category = c
                        },
                        label = { Text(FeedbackCategory.label(c), fontSize = 13.sp) },
                        shape = RoundedCornerShape(8.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            // 區塊選項有十幾個，攤成晶片會佔掉大半個畫面；改成可複選的下拉選單。
            FeedbackFieldLabel(if (needsArea) "哪個區塊？（可複選）*" else "哪個區塊？（可複選）")
            MultiSelectDropdown(
                options = FeedbackCategory.AREAS,
                selected = areas,
                onToggle = { a -> areas = if (a in areas) areas - a else areas + a },
                placeholder = "選擇區塊"
            )

            val issueOptions = FeedbackCategory.issueTypes(category)
            if (issueOptions.isNotEmpty()) {
                FeedbackFieldLabel(if (category == FeedbackCategory.BUG) "哪種問題？（可複選）*" else "哪種建議？（可複選）*")
                MultiSelectDropdown(
                    options = issueOptions,
                    selected = issueTypes,
                    onToggle = { t -> issueTypes = if (t in issueTypes) issueTypes - t else issueTypes + t },
                    placeholder = if (category == FeedbackCategory.BUG) "選擇問題類型" else "選擇建議類型"
                )
            }

            if (fullForm) {
                // 身分
                FeedbackFieldLabel("你的身分")
                ChipGroup(
                    options = ROLE_OPTIONS,
                    selected = role,
                    onSelect = { role = if (role == it) "" else it }
                )

                // 使用頻率
                FeedbackFieldLabel("使用頻率")
                ChipGroup(
                    options = USAGE_OPTIONS,
                    selected = usageFreq,
                    onSelect = { usageFreq = if (usageFreq == it) "" else it }
                )

                // 來源
                FeedbackFieldLabel("從哪裡知道 Bandary？")
                ChipGroup(
                    options = SOURCE_OPTIONS,
                    selected = source,
                    onSelect = { source = if (source == it) "" else it }
                )
            }

            if (fullForm) {
                // 滿意度（可選）：1–5 顆星
                FeedbackFieldLabel("整體滿意度")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..5).forEach { i ->
                        IconButton(onClick = { rating = if (rating == i) 0 else i }) {
                            Icon(
                                if (i <= rating) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = "$i 星",
                                tint = if (i <= rating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 推薦分數（NPS 0–10）
                FeedbackFieldLabel("有多願意把 Bandary 推薦給朋友？0 分不會，10 分非常願意")
                NpsRow(value = nps, onSelect = { nps = if (nps == it) -1 else it })

                // 最常用 / 最喜歡的功能
                OutlinedTextField(
                    value = favFeature,
                    onValueChange = { favFeature = it },
                    label = { Text("最常用或最喜歡的功能") },
                    placeholder = { Text("例如 票根分享、統計、選歌日曆…") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // 想加強的方向（可複選）
                FeedbackFieldLabel("最希望我們加強哪些地方？（可複選）")
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    WISH_OPTIONS.forEach { w ->
                        FilterChip(
                            selected = w in wishes,
                            onClick = { wishes = if (w in wishes) wishes - w else wishes + w },
                            label = { Text(w, fontSize = 13.sp) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                // 最困擾的地方
                OutlinedTextField(
                    value = painPoint,
                    onValueChange = { painPoint = it },
                    label = { Text("用起來最困擾或最不順的地方") },
                    placeholder = { Text("例如 某個按鈕找不到、輸入太麻煩…") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(96.dp)
                )

                // 付費意願
                FeedbackFieldLabel("對 Pro 付費方案的看法")
                ChipGroup(
                    options = PAY_OPTIONS,
                    selected = payIntent,
                    onSelect = { payIntent = if (payIntent == it) "" else it }
                )
            }

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("想說的話 *") },
                placeholder = { Text("描述問題、建議或使用心得…") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(160.dp)
            )

            OutlinedTextField(
                value = contact,
                onValueChange = { contact = it },
                label = { Text("聯絡方式") },
                placeholder = { Text("方便回覆時填寫，可留空") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    if (!viewModel.isSignedIn()) {
                        Toast.makeText(context, "請先到「我的」用 Google 登入", Toast.LENGTH_SHORT).show()
                    } else if (needsArea && areas.isEmpty()) {
                        Toast.makeText(context, "請選是哪個區塊", Toast.LENGTH_SHORT).show()
                    } else if (FeedbackCategory.issueTypes(category).isNotEmpty() && issueTypes.isEmpty()) {
                        Toast.makeText(context, "請選是哪一種", Toast.LENGTH_SHORT).show()
                    } else if (text.isBlank()) {
                        Toast.makeText(context, "請填寫想說的話", Toast.LENGTH_SHORT).show()
                    } else {
                        val extra = FeedbackExtra(source, wishes.toList(), painPoint, payIntent, appVersion)
                        // 依選項清單的順序存，列表上看起來才一致（不是依點選順序）。
                        val areaText = FeedbackCategory.AREAS.filter { it in areas }.joinToString("、")
                        val issueText = FeedbackCategory.issueTypes(category).filter { it in issueTypes }.joinToString("、")
                        viewModel.submit(category, areaText, issueText, text, contact, rating, nps, usageFreq, favFeature, role, extra) {
                            text = ""; contact = ""; category = FeedbackCategory.BUG; areas = emptySet(); issueTypes = emptySet()
                            rating = 0; nps = -1; usageFreq = ""; favFeature = ""; role = ""
                            source = ""; wishes = emptySet(); painPoint = ""; payIntent = ""
                            Toast.makeText(context, "已送出，感謝你的回饋", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = !loading,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("送出回饋", fontWeight = FontWeight.SemiBold)
                }
            }

            Text(
                "你的回饋會傳送給開發者。私人資料請勿填寫於內容中。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 直接寄信：原本是「我的」頁的「聯絡我們」一列，合併過來。
            // 有些事填表單講不清楚（要附檔、要來回討論），那時候需要的是信箱。
            // 讀不到信箱就整塊不顯示，不要留一個空標題。
            val contactEmails by viewModel.contactEmails.collectAsState()
            val clip = androidx.compose.ui.platform.LocalClipboardManager.current
            LaunchedEffect(Unit) { viewModel.loadContactEmails() }
            if (contactEmails.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    "要附檔或想直接聊，也可以寄信：",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                contactEmails.forEach { mail ->
                    TextButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_SENDTO,
                                        android.net.Uri.parse("mailto:$mail")
                                    )
                                )
                            }.onFailure {
                                // 沒有郵件 App 的機器：至少讓他複製得到。
                                clip.setText(androidx.compose.ui.text.AnnotatedString(mail))
                                Toast.makeText(context, "已複製 $mail", Toast.LENGTH_SHORT).show()
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) { Text(mail, fontSize = 13.sp) }
                }
            }
        }
    }
}

private val ROLE_OPTIONS = listOf("樂迷", "主辦或工作人員", "學生", "其他")
private val USAGE_OPTIONS = listOf("每天", "每週", "每月", "偶爾")
private val SOURCE_OPTIONS = listOf("朋友推薦", "Threads / IG", "Play 商店搜尋", "活動現場", "其他")
private val WISH_OPTIONS = listOf("活動資料更多更準", "好友功能", "統計圖表", "票根與分享圖", "選歌紀錄", "介面操作", "網頁版", "iOS 版")
private val PAY_OPTIONS = listOf("已購買", "考慮購買", "價格偏高", "功能不夠吸引", "不打算付費")

/** 完整版額外問的題目，一起傳給 ViewModel。 */
data class FeedbackExtra(
    val source: String,
    val wishes: List<String>,
    val painPoint: String,
    val payIntent: String,
    val appVersion: String
)

@Composable
private fun FeedbackFieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ChipGroup(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { opt ->
            FilterChip(
                selected = selected == opt,
                onClick = { onSelect(opt) },
                label = { Text(opt, fontSize = 13.sp) },
                shape = RoundedCornerShape(8.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = Color.White
                )
            )
        }
    }
}

/**
 * 可複選的下拉選單。點開後每一項前面有勾選框，點了不會關閉（才能一次勾好幾個），
 * 選完按最下面的「完成」或點外面收起。欄位上顯示已選的項目，用「、」串起來。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MultiSelectDropdown(
    options: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    placeholder: String
) {
    var expanded by remember { mutableStateOf(false) }
    androidx.compose.material3.ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = options.filter { it in selected }.joinToString("、"),
            onValueChange = {},
            readOnly = true,
            placeholder = { Text(placeholder) },
            trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        // ExposedDropdownMenu 是 ExposedDropdownMenuBoxScope 的成員函式，只能這樣直接呼叫，
        // 寫成 androidx.compose.material3.ExposedDropdownMenu 會找不到。
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { opt ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(opt) },
                    leadingIcon = {
                        androidx.compose.material3.Checkbox(
                            checked = opt in selected,
                            onCheckedChange = { onToggle(opt) }
                        )
                    },
                    onClick = { onToggle(opt) }
                )
            }
            androidx.compose.material3.HorizontalDivider()
            androidx.compose.material3.DropdownMenuItem(
                text = {
                    Text(
                        if (selected.isEmpty()) "完成" else "完成（已選 ${selected.size} 項）",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                onClick = { expanded = false }
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun NpsRow(value: Int, onSelect: (Int) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (0..10).forEach { n ->
            val on = value == n
            androidx.compose.material3.Surface(
                onClick = { onSelect(n) },
                shape = RoundedCornerShape(8.dp),
                color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.height(38.dp).width(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "$n",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ── 常見問題（FAQ）：可展開的 Q&A 區塊 ─────────────────────────
private val FAQ_ITEMS = listOf(
    "我的資料會不會不見？" to "演唱會與選歌都存在手機本機；用 Google 登入後可雲端備份、換手機也能同步回來。",
    "需要付費嗎？" to "目前所有功能皆免費。若願意，可到「我的」用小額贊助支持後續開發。",
    "怎麼把演唱會同步到 Google 日曆？" to "新增或編輯演唱會時，開啟「同步到 Google 日曆」即可；也能從行事曆頁匯入既有活動。",
    "統計頁為什麼沒有資料？" to "先新增演唱會並填上日期，過了演出日或標記為已看後，統計與圖表就會自動出現。",
    "找不到好友怎麼辦？" to "到「我的」→「好友的日曆」，把畫面上的好友代碼給對方，或輸入對方的代碼送出請求；也可以開啟「允許用 email 搜尋我」讓對方用 email 找到你。雙方都接受之後才互為好友。",
    "投稿的活動沒有出現？" to "使用者投稿的活動需經管理員審核通過後才會公開；可在活動頁的「我的投稿」查看狀態。"
)

@Composable
private fun FaqSection() {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("常見問題", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "收合" else "展開"
            )
        }
        if (expanded) {
            FAQ_ITEMS.forEach { (q, a) ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                    Text("Q. $q", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(a, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Text("送出回饋前，先看看常見問題也許更快找到答案。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── 管理員：查看使用者回饋 ─────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackAdminScreen(
    onBack: () -> Unit,
    viewModel: FeedbackViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val message by viewModel.message.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("使用者回饋 · ${items.size}", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "重新整理")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        when {
            loading && items.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
            }
            items.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(title = "目前沒有回饋", subtitle = "使用者送出意見後會出現在這裡")
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items, key = { it.id }) { f ->
                    FeedbackCard(f, onDelete = { viewModel.delete(f.id) })
                }
            }
        }
    }
}

@Composable
private fun FeedbackCard(f: Feedback, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .height(22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(6.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Text(
                            // 類型 · 區塊 · 細項，一眼看出是哪裡的哪種問題。
                            listOfNotNull(
                                FeedbackCategory.label(f.category),
                                f.area?.takeIf { it.isNotBlank() },
                                f.issueType?.takeIf { it.isNotBlank() }
                            ).joinToString(" · "),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.height(28.dp)) {
                    Icon(Icons.Outlined.Delete, "刪除", modifier = Modifier.height(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // KPI 摘要：滿意度 / NPS / 身分 / 使用頻率 / 最愛功能（有填才顯示）
            val chips = buildList {
                f.rating?.let { add("滿意度 $it/5") }
                f.nps?.let { add("NPS $it/10") }
                f.role?.takeIf { it.isNotBlank() }?.let { add(it) }
                f.usageFrequency?.takeIf { it.isNotBlank() }?.let { add("使用 $it") }
                f.favoriteFeature?.takeIf { it.isNotBlank() }?.let { add("愛用：$it") }
                f.source?.takeIf { it.isNotBlank() }?.let { add("來源：$it") }
                f.payIntent?.takeIf { it.isNotBlank() }?.let { add("付費：$it") }
                f.appVersion?.takeIf { it.isNotBlank() }?.let { add("v$it") }
            }
            if (chips.isNotEmpty()) {
                Text(
                    chips.joinToString("　·　"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(f.message, style = MaterialTheme.typography.bodyMedium)
            f.wishes?.takeIf { it.isNotBlank() }?.let {
                Text("想加強：$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            f.painPoint?.takeIf { it.isNotBlank() }?.let {
                Text("困擾：$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            f.contact?.takeIf { it.isNotBlank() }?.let {
                Text("聯絡：$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                f.createdBy?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(formatTime(f.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun formatTime(epochMillis: Long): String {
    val d = java.util.Date(epochMillis)
    val fmt = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.TAIWAN)
    return fmt.format(d)
}
