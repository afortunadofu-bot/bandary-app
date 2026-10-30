package com.bandary.presentation.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bandary.data.local.ThemeMode
import com.bandary.data.local.ThemePalette
import com.bandary.presentation.ui.components.SectionHeader
import com.bandary.presentation.ui.theme.toBrand
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onOpenAdmin: () -> Unit = {},
    onOpenFeedback: () -> Unit = {},
    onOpenFeedbackAdmin: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var showAdminDialog by remember { mutableStateOf(false) }
    var adminCode by remember { mutableStateOf("") }
    var adminError by remember { mutableStateOf<String?>(null) }
    var showManageCodes by remember { mutableStateOf(false) }
    var showCrawlSources by remember { mutableStateOf(false) }
    var showSeedConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp
            )
        ) {
            // 帳號 / 外觀 / 通知 / 購票平台排序 都在「我的」頁設定，
            // 這裡不重複；設定頁只留折扣碼與管理者功能。
            // 「輸入折扣碼」原本在這裡也有一個，和「我的 →【兌換折扣碼】」是同一件事。
            // 留「我的」那個：這一頁實際上是**折扣碼管理後台**（編輯雲端碼、種入內建碼），
            // 一般使用者不該進來，所以整段只給管理員。
            if (state.isAdmin) {
                item { Spacer(Modifier.height(8.dp)) }
                item { SectionHeader("折扣碼") }
                item {
                    SettingsCard {
                        SettingsClickableRow(
                            icon = Icons.Outlined.Redeem,
                            title = "編輯折扣碼",
                            subtitle = "管理有哪些折扣碼與每組代碼的效用",
                            onClick = { viewModel.refreshCloudCodes(); showManageCodes = true }
                        )
                    }
                }
            }

            if (state.isAdmin) {
                item { Spacer(Modifier.height(16.dp)) }
                item { SectionHeader("管理者") }
                item {
                    SettingsCard {
                        Column {
                            // 「後台數據檢視」原本在這裡，和「我的 →【管理者】」那張大卡
                            // 去的是同一個畫面（都是 onOpenAdmin）。留那張卡：
                            // 使用者回報過「管理者介面藏太深」，那張才是淺的入口。
                            SettingsClickableRow(
                                icon = Icons.Outlined.Feedback,
                                title = "使用者回饋",
                                subtitle = "查看 / 刪除使用者送出的意見",
                                onClick = onOpenFeedbackAdmin
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            // 雲端的排程蒐集一直在跑，但 App 以前沒有任何地方讀得到
                            // lastRunAt / lastCount，也沒有地方新增來源——只能進 Console。
                            SettingsClickableRow(
                                icon = Icons.Outlined.TravelExplore,
                                title = "活動蒐集來源",
                                subtitle = "管理爬取來源、看上次抓取時間、手動抓一次",
                                onClick = { showCrawlSources = true }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            // App 內的管理員只是本機旗標；雲端那些「只有管理員能做」的操作
                            // （寫折扣碼、讀回饋、審活動）看的是 Firestore 的 admins/{uid}。
                            // 把 uid 秀出來，才知道要在 Console 建哪一份文件。
                            val uid = viewModel.myUid
                            SettingsClickableRow(
                                icon = Icons.Outlined.Key,
                                title = "雲端管理員 uid",
                                subtitle = uid ?: "尚未登入 Google 帳號",
                                onClick = {
                                    if (uid != null) {
                                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(uid))
                                    }
                                }
                            )
                            Text(
                                "在 Firebase Console 建立 admins/{上面這組 uid} 文件後，寫折扣碼、讀回饋、審活動才會通過規則。點一下複製。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
            item {
                // 署名區：與「我的」頁共用同一份，格式依品牌規範。
                // （這裡以前把版本號寫死成 1.0.0，使用者回報問題時報的版本全是錯的。）
                com.bandary.presentation.ui.components.BrandFooter(showIcon = false)
            }
        }
    }

    if (showAdminDialog) {
        AlertDialog(
            onDismissRequest = { showAdminDialog = false; adminError = null; adminCode = "" },
            title = { Text("輸入折扣碼") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = adminCode,
                        onValueChange = { adminCode = it.uppercase(); adminError = null },
                        label = { Text("折扣碼") },
                        singleLine = true,
                        isError = adminError != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (adminError != null) {
                        Text(adminError!!, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        if (viewModel.tryAdminCode(adminCode)) {
                            showAdminDialog = false
                            adminCode = ""
                            adminError = null
                        } else {
                            adminError = "折扣碼無效或已過期"
                        }
                    }
                }) { Text("兌換") }
            },
            dismissButton = {
                TextButton(onClick = { showAdminDialog = false; adminError = null; adminCode = "" }) { Text("取消") }
            }
        )
    }

    if (showCrawlSources) {
        CrawlSourcesOverlay(onDismiss = { showCrawlSources = false })
    }

    if (showManageCodes) {
        val cloudCodes by viewModel.cloudCodes.collectAsState()
        var editingCode by remember { mutableStateOf<String?>(null) }  // 非 null＝編輯既有碼（代碼不可改）
        var newCode by remember { mutableStateOf("") }
        var newEffect by remember { mutableStateOf("pro") }
        var newNote by remember { mutableStateOf("") }
        var newQty by remember { mutableStateOf("") }   // 發行數量（留空＝無限）
        var newDays by remember { mutableStateOf("") }  // 有效天數（留空＝永久）
        var copiedCode by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(copiedCode) {
            if (copiedCode != null) {
                kotlinx.coroutines.delay(1200)
                copiedCode = null
            }
        }
        fun copyCode(code: String) {
            clipboard.setText(androidx.compose.ui.text.AnnotatedString(code))
            copiedCode = code
        }
        fun resetForm() { editingCode = null; newCode = ""; newEffect = "pro"; newNote = ""; newQty = ""; newDays = "" }
        fun loadForEdit(dc: com.bandary.data.remote.FirestoreService.DiscountCode) {
            editingCode = dc.code; newCode = dc.code; newEffect = dc.effect; newNote = dc.note ?: ""
            newQty = dc.maxRedemptions?.toString() ?: ""
            newDays = dc.expiresAt?.let { ((it - System.currentTimeMillis()) / 86_400_000L).coerceAtLeast(0).toString() } ?: ""
        }
        AlertDialog(
            onDismissRequest = { showManageCodes = false },
            title = { Text(if (editingCode != null) "編輯 $editingCode" else "折扣碼管理") },
            text = {
                com.bandary.presentation.ui.components.DialogBody(spacing = 10.dp) {
                    Text(
                        "雲端折扣碼所有安裝都能兌換，可個別設定發行數量、效期與權限。效用：Pro＝解鎖進階功能；Admin＝Pro＋管理員權限。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // 雲端寫入結果：成功或失敗都要說出來，不然按了沒反應會被當成功能壞掉。
                    val codeMsg by viewModel.codeMsg.collectAsState()
                    codeMsg?.let { msg ->
                        val bad = msg.contains("失敗")
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (bad) MaterialTheme.colorScheme.errorContainer
                                else MaterialTheme.colorScheme.secondaryContainer
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(msg, style = MaterialTheme.typography.bodySmall)
                                if (bad) {
                                    // 幾乎都是這個原因：規則裡的 isAdmin() 看的是 Firestore 的 admins/{uid}，
                                    // 而 App 的管理員狀態只是本機旗標，兩者是分開的。
                                    Text(
                                        "雲端折扣碼的寫入權限看的是 Firestore 的 admins/{uid} 文件，" +
                                            "與 App 內的管理員狀態無關。請到 Firebase Console 用下面這組 uid 建立該文件。",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Text("uid：${viewModel.myUid ?: "未登入"}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                }
                                TextButton(onClick = { viewModel.clearCodeMsg() }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                                    Text("知道了", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    // 內建碼：離線可用；按「設為雲端」把它轉成雲端碼後，即可編輯數量 / 效期 / 權限。
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("內建碼・可轉為雲端碼後編輯", style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        val pending = (SettingsViewModel.PRO_CODES + SettingsViewModel.ADMIN_CODES)
                            .count { c -> cloudCodes.none { it.code.equals(c, ignoreCase = true) } }
                        if (pending > 0) {
                            TextButton(onClick = { showSeedConfirm = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Text("全部種入雲端・$pending 組", fontSize = 12.sp)
                            }
                        }
                    }
                    (SettingsViewModel.PRO_CODES.map { it to "pro" } + SettingsViewModel.ADMIN_CODES.map { it to "admin" })
                        .forEach { (c, eff) ->
                            val overridden = cloudCodes.any { it.code.equals(c, ignoreCase = true) }
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(c, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                Text(
                                    if (copiedCode == c) "已複製" else if (eff == "admin") "Admin" else "Pro",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (copiedCode == c) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                IconButton(onClick = { copyCode(c) }, modifier = Modifier.size(30.dp)) {
                                    Icon(Icons.Outlined.ContentCopy, "複製 $c", modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (overridden) {
                                    Text("已在雲端", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                } else {
                                    TextButton(onClick = { viewModel.saveCloudCode(c, eff, "由內建碼轉入") }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                        Text("設為雲端", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    // 雲端碼（可編輯 / 刪除）
                    Text("雲端碼", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    if (cloudCodes.isEmpty()) {
                        Text("尚無雲端折扣碼", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    cloudCodes.forEach { dc ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(dc.code, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                val meta = buildList {
                                    add(if (dc.effect == "admin") "Admin" else "Pro")
                                    add(if (dc.maxRedemptions != null) "已用 ${dc.redemptions}/${dc.maxRedemptions}" else "已用 ${dc.redemptions}")
                                    dc.expiresAt?.let {
                                        val days = (it - System.currentTimeMillis()) / 86_400_000L
                                        add(if (days < 0) "已過期" else "剩 ${days} 天")
                                    }
                                    if (!dc.note.isNullOrBlank()) add(dc.note!!)
                                }.joinToString(" · ")
                                Text(
                                    if (copiedCode == dc.code) "已複製" else meta,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (copiedCode == dc.code) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { copyCode(dc.code) }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Outlined.ContentCopy, "複製 ${dc.code}", modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { loadForEdit(dc) }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Outlined.Edit, "編輯", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { viewModel.deleteCloudCode(dc.code); if (editingCode == dc.code) resetForm() }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Outlined.Close, "刪除", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    // 新增 / 編輯表單
                    Text(if (editingCode != null) "編輯這組代碼" else "新增一組代碼", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = newCode,
                        onValueChange = { newCode = it.uppercase() },
                        label = { Text("折扣碼") },
                        singleLine = true, enabled = editingCode == null,  // 編輯時代碼固定
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = newEffect == "pro", onClick = { newEffect = "pro" },
                            label = { Text("Pro", fontSize = 12.sp) }, shape = RoundedCornerShape(8.dp),
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = Color.White)
                        )
                        FilterChip(
                            selected = newEffect == "admin", onClick = { newEffect = "admin" },
                            label = { Text("Admin", fontSize = 12.sp) }, shape = RoundedCornerShape(8.dp),
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = Color.White)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newQty, onValueChange = { newQty = it.filter { ch -> ch.isDigit() } },
                            label = { Text("發行數量") }, placeholder = { Text("無限") },
                            singleLine = true, modifier = Modifier.weight(1f),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = newDays, onValueChange = { newDays = it.filter { ch -> ch.isDigit() } },
                            label = { Text("有效天數") }, placeholder = { Text("永久") },
                            singleLine = true, modifier = Modifier.weight(1f),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                        )
                    }
                    OutlinedTextField(
                        value = newNote, onValueChange = { newNote = it },
                        label = { Text("備註・用途") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newCode.isNotBlank()) {
                            val max = newQty.trim().toIntOrNull()?.takeIf { it > 0 }
                            val exp = newDays.trim().toIntOrNull()?.takeIf { it > 0 }?.let { System.currentTimeMillis() + it * 86_400_000L }
                            viewModel.saveCloudCode(newCode.trim(), newEffect, newNote.trim().ifBlank { null }, max, exp)
                            resetForm()
                        }
                    },
                    enabled = newCode.isNotBlank()
                ) { Text(if (editingCode != null) "儲存變更" else "新增此碼") }
            },
            dismissButton = {
                TextButton(onClick = { if (editingCode != null) resetForm() else showManageCodes = false }) {
                    Text(if (editingCode != null) "取消編輯" else "關閉")
                }
            }
        )
    }

    // 批次寫入雲端是不可逆的動作，先問過再送。
    if (showSeedConfirm) {
        AlertDialog(
            onDismissRequest = { showSeedConfirm = false },
            title = { Text("把內建碼種入雲端") },
            text = {
                Text(
                    "會把還沒上雲的內建碼寫進 Firestore 的 discount_codes。" +
                        "已經在雲端的略過，不會覆寫既有設定或已兌換數。\n\n" +
                        "上雲之後這些碼就能設定發行數量、效期與權限；但內建碼本身仍留在 App 裡可離線兌換。"
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.seedBuiltInCodesToCloud(); showSeedConfirm = false }) { Text("寫入") }
            },
            dismissButton = { TextButton(onClick = { showSeedConfirm = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) { content() }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SettingsClickableRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
