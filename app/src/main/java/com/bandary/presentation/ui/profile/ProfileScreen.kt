package com.bandary.presentation.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.Gradient
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarRate
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bandary.data.local.ThemeMode
import com.bandary.data.local.ThemePalette
import com.bandary.presentation.ui.components.SectionHeader
import com.bandary.presentation.ui.components.UserAvatar
import com.bandary.presentation.ui.settings.CalendarSyncViewModel
import com.bandary.presentation.ui.settings.CloudSyncViewModel
import com.bandary.presentation.ui.settings.SettingsViewModel
import com.bandary.presentation.ui.theme.toBrand
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime

// 小額贊助：App 本身不經手金流，點了是開瀏覽器到站外平台。
//
// **這個入口不會解鎖任何 App 功能**。Pro 一律走 Google Play 帳單，兩條路完全分開
// ——這點是刻意的，見下方 SHOW_DONATE 的說明。
private const val DONATE_URL = "https://buymeacoffee.com/celinefu"
// 開發者聯絡信箱的「預設值」：管理員可在後台新增 / 編輯（存 Firestore config/contact），
// 沒設定或讀取失敗時就用這個預設。點「聯絡我們」先顯示信箱（不直接跳寄信 App）。
private const val DEV_EMAIL = "celine.fu.work@gmail.com"
/**
 * 要不要顯示站外贊助入口。
 *
 * **Google Play 付款政策的風險點**：App 內的數位商品必須走 Play 帳單。
 * 「導連到站外收錢」被視為規避帳單——但那是指**買得到東西**的情況。
 * 這裡的贊助**不解鎖任何功能、不給任何回報**，Pro 完全走 Play 帳單，
 * 所以性質是單純的 tip jar，不是替代金流。
 *
 * 這個區別就是能不能留著的關鍵，所以：
 *   - 文案不能寫成「贊助解鎖」「支持換 Pro」之類暗示有回報的說法
 *   - 不要放在 Pro 介紹頁旁邊，避免看起來是另一個購買管道
 * 真的被退件的話把這個改回 false 就能先上架，不影響其他功能。
 */
private const val SHOW_DONATE = true

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    onOpenSettings: () -> Unit,
    onSignInClick: () -> Unit,
    onOpenFeedback: () -> Unit = {},
    onOpenProFeatures: () -> Unit = {},
    onOpenBulkImport: () -> Unit = {},
    onOpenTrash: () -> Unit = {},
    onOpenFriends: () -> Unit = {},
    onOpenImportCalendar: () -> Unit = {},
    onOpenWishes: () -> Unit = {},
    onOpenPromo: () -> Unit = {},
    onOpenBrand: () -> Unit = {},
    onOpenAdmin: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val shareNewShows by viewModel.shareNewShows.collectAsState()
    val scope = rememberCoroutineScope()
    val calVm: CalendarSyncViewModel = hiltViewModel()
    val calState by calVm.state.collectAsState()
    androidx.compose.runtime.LaunchedEffect(Unit) { calVm.refreshConnection() }
    val calSignInLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { calVm.refreshConnection() }

    val cloudVm: CloudSyncViewModel = hiltViewModel()
    val cloudState by cloudVm.state.collectAsState()
    val cloudSignInLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result -> cloudVm.handleSignIn(result.data) }
    val signInContext = androidx.compose.ui.platform.LocalContext.current
    val cloudForPrompt by cloudVm.state.collectAsState()
    var showCalendarLinkPrompt by remember { mutableStateOf(false) }
    var showSuggestSource by remember { mutableStateOf(false) }
    // 登入 Bandary 之後順手問一次要不要連結 Google 日曆——這是兩個不同的 Google 授權，
    // 使用者常以為「登入了就會同步到日曆」。只問一次，回答過就不再跳。
    val calendarLinkAsked by viewModel.calendarLinkAsked.collectAsState()
    androidx.compose.runtime.LaunchedEffect(
        state.loggedInEmail, calState.connected, calendarLinkAsked, cloudForPrompt.restorePrompt
    ) {
        if (state.loggedInEmail != null && !calState.connected && !calendarLinkAsked &&
            cloudForPrompt.restorePrompt == null
        ) {
            kotlinx.coroutines.delay(600)   // 讓還原詢問先走完，不要兩個視窗疊在一起
            showCalendarLinkPrompt = true
        }
    }
    cloudForPrompt.restorePrompt?.let { prompt ->
        val last = prompt.lastBackupAt?.let {
            java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.TAIWAN).format(java.util.Date(it))
        }
        AlertDialog(
            onDismissRequest = { cloudVm.dismissRestorePrompt() },
            title = { Text("要還原雲端的備份嗎？") },
            text = {
                Text(
                    buildString {
                        append("這個帳號的雲端備份有 ${prompt.cloudConcerts} 場演唱會、${prompt.cloudSongs} 首選歌")
                        last?.let { append("（上次備份 $it）") }
                        append("。\n\n還原會帶回這支手機還沒有的 ${prompt.newConcerts} 場演唱會、${prompt.newSongs} 首選歌與照片，")
                        append("不會刪除或覆蓋這支手機上比較新的資料。")
                    }
                )
            },
            confirmButton = { TextButton(onClick = { cloudVm.confirmRestorePrompt() }) { Text("還原") } },
            dismissButton = { TextButton(onClick = { cloudVm.dismissRestorePrompt() }) { Text("稍後") } }
        )
    }
    // 備份會讓雲端照片變少：先問。預設（主按鈕）是兩邊取多的，不會少任何一張。
    cloudForPrompt.shrinkPrompt?.let { p ->
        AlertDialog(
            onDismissRequest = { cloudVm.cancelShrink() },
            title = { Text("備份後雲端照片會變少") },
            text = {
                Text(
                    "雲端目前有 ${p.cloudPhotos} 張照片，這支手機有 ${p.localPhotos} 張。\n" +
                        "有 ${p.concerts} 場在這支手機上少了 ${p.photos} 張照片，直接備份會把雲端那些照片蓋掉。\n\n" +
                        "「合併」：兩邊取多的，雲端的照片都保留，也會補回這支手機。\n" +
                        "「以本機為準」：雲端改成跟手機一樣，少掉的 ${p.photos} 張會從雲端刪除。"
                )
            },
            confirmButton = {
                TextButton(onClick = { cloudVm.confirmShrink(overwrite = false) }) { Text("合併（建議）") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { cloudVm.cancelShrink() }) { Text("取消") }
                    TextButton(onClick = { cloudVm.confirmShrink(overwrite = true) }) {
                        Text("以本機為準", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        )
    }
    val signInToast by cloudVm.toast.collectAsState()
    LaunchedEffect(signInToast) {
        signInToast?.let {
            android.widget.Toast.makeText(signInContext, it, android.widget.Toast.LENGTH_LONG).show()
            cloudVm.clearToast()
        }
    }
    val exportCsvLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { cloudVm.exportConcertsCsv(it) } }
    val exportSongsCsvLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { cloudVm.exportDailySongsCsv(it) } }
    var showImportDialog by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(calState.events) {
        if (calState.events.isNotEmpty()) showImportDialog = true
    }
    var showAdminDialog by remember { mutableStateOf(false) }
    var adminCode by remember { mutableStateOf("") }
    var adminError by remember { mutableStateOf<String?>(null) }
    var showPlatformDialog by remember { mutableStateOf(false) }
    var newPlatformName by remember { mutableStateOf("") }
    var showCheckInTimePicker by remember { mutableStateOf(false) }
    var paletteRevealed by remember { mutableStateOf(false) }
    var showNicknameDialog by remember { mutableStateOf(false) }
    var showDeleteBackupDialog by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showCleanupPhotosDialog by remember { mutableStateOf(false) }
    // 雲端未使用照片的清理：要先算出張數給使用者看，才問要不要刪。
    // null = 還沒算 / 對話框關著。
    var orphanPhotoCount by remember { mutableStateOf<Int?>(null) }
    var countingOrphans by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }   // 使用說明 / 快速上手
    var showLanguageDialog by remember { mutableStateOf(false) }   // 多語言選擇
    var showWidgetGuide by remember { mutableStateOf(false) }      // 桌面小工具：全螢幕預覽子頁
    var showSwitchConfirm by remember { mutableStateOf(false) }
    var showAccountMenu by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    // 設定分區改成可展開 / 收合，預設收合，不把所有小功能都平鋪出來。
    var secAppearance by remember { mutableStateOf(false) }
    var secNotify by remember { mutableStateOf(false) }
    var secBackup by remember { mutableStateOf(false) }
    var secCalendar by remember { mutableStateOf(false) }
    var secPlatform by remember { mutableStateOf(false) }
    // 移除自訂平台前的確認：Pair(平台名稱, 已使用該平台的演唱會數)
    var platformPendingRemove by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var showAnnouncements by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.refreshAnnouncements() }
    // 頭貼固定 1:1：手機照片是 4:3 / 9:16，直接塞進圓形頭貼會被切掉重點，
    // 自己裁才知道要留哪裡。這裡是全 App 唯一強制比例的裁切。
    val avatarPicker = com.bandary.presentation.ui.components.rememberPickAndCrop(aspect = 1 to 1) { uri ->
        viewModel.setAvatar(uri)
    }

    val profileListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val profileScrollScope = androidx.compose.runtime.rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                actions = {
                    // 深色 / 淺色一鍵切換：完整選項（含跟隨系統）仍在「外觀 → 顯示模式」，
                    // 但每天最常用的就是換深淺，藏在兩層選單裡太難找（實機回報）。
                    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
                    val nowDark = when (state.themeMode) {
                        ThemeMode.DARK -> true
                        ThemeMode.LIGHT -> false
                        else -> systemDark
                    }
                    IconButton(onClick = { viewModel.setThemeMode(if (nowDark) ThemeMode.LIGHT else ThemeMode.DARK) }) {
                        Icon(
                            if (nowDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                            contentDescription = if (nowDark) "切換成亮色" else "切換成暗色",
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    // 官方通知：有未讀時顯示紅點徽章
                    val unread by viewModel.unreadAnnouncements.collectAsState()
                    IconButton(onClick = { showAnnouncements = true }) {
                        androidx.compose.material3.BadgedBox(badge = {
                            if (unread > 0) androidx.compose.material3.Badge { Text("$unread") }
                        }) {
                            Icon(Icons.Outlined.Notifications, contentDescription = "官方通知", modifier = Modifier.size(28.dp))
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "設定", modifier = Modifier.size(28.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            com.bandary.presentation.ui.components.ScrollTopFab(profileListState, profileScrollScope)
        },
        // 外層已經有底部導覽列，而且它自己處理手勢列的 inset（見 MainActivity 的註解）。
        // 這層若再用預設的 systemBars，等於同一段 inset 被算兩次。
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            state = profileListState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                // **不要用 padding.calculateBottomPadding()**：Material3 的 Scaffold 有
                // floatingActionButton 時會把 FAB 的高度也加進底部留白（約 72dp），
                // 加上上面那層重複的系統列 inset，署名區下面就空掉一大塊（實機回報）。
                // 這裡只留一點呼吸空間就好——最後一列是靠左的版權小字，
                // 右下角的 FAB 本來就蓋不到它。
                bottom = 16.dp
            )
        ) {
            // Hero：頭貼、暱稱、帳號狀態置中排列。
            // 以前是左右排的一小列，「點此登入」只是一行灰色小字，使用者找不到要去哪裡登入、
            // 也不知道暱稱可以改（實機回報）。改成大頭貼＋明確的一行動作文字。
            // 長按頭貼 7 次 = 隱藏的解鎖手勢。
            item {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(
                        // 上下留白與間距都收緊：原本一個頁首就吃掉半個螢幕，下面的好友、活動頁要捲才看得到。
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        val initial = state.displayName.firstOrNull()?.uppercase() ?: "U"
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .border(BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant), CircleShape)
                                .combinedClickable(
                                    onClick = {
                                        avatarPicker()
                                    },
                                    onLongClick = {
                                        scope.launch { if (viewModel.bumpAdminGesture()) viewModel.unlockProManually() }
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!state.avatarUri.isNullOrBlank()) {
                                coil.compose.AsyncImage(
                                    model = state.avatarUri,
                                    contentDescription = "頭貼",
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                                )
                            } else {
                                UserAvatar(initial.toString(), size = 64.dp)
                            }
                            // 換頭貼提示：右下角相機小圓，讓「可換圖」一看就懂
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .border(BorderStroke(2.dp, MaterialTheme.colorScheme.surfaceContainer), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.PhotoCamera, "換頭貼",
                                    tint = Color.White, modifier = Modifier.size(13.dp)
                                )
                            }
                        }

                        // 暱稱：沒設定時整行就是「點此設定暱稱」，點哪裡都會開編輯視窗。
                        val hasNickname = !state.nickname.isNullOrBlank()
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { showNicknameDialog = true }
                                .padding(horizontal = 10.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                // 全 App 同一個名字：好友頁、排行榜、分享圖顯示的都是這個字串。
                                if (hasNickname) state.displayName else "點此設定暱稱",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (hasNickname) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Icon(
                                Icons.Outlined.Edit, "編輯暱稱",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (state.loggedInEmail == null) {
                            Text(
                                "目前是本地模式，資料只存在這支手機",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            // 帳號列：email 與「帳號選項」併成同一行。
                            // 分兩行時「帳號選項」那顆 TextButton 自己就佔掉一整列（含上下間距約 44dp），
                            // 頁首有一半高度是花在那顆按鈕上。
                            Box {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { showAccountMenu = true }
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        buildString {
                                            append(state.loggedInEmail)
                                            if (state.isProPlus) append(" · Pro+") else if (state.isPro) append(" · Pro")
                                            if (state.isAdmin) append(" · Admin")
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Text(
                                        "帳號選項",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1
                                    )
                                    Icon(
                                        Icons.Outlined.ExpandMore, null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                DropdownMenu(expanded = showAccountMenu, onDismissRequest = { showAccountMenu = false }) {
                                    DropdownMenuItem(text = { Text("切換帳號") }, onClick = { showAccountMenu = false; showSwitchConfirm = true })
                                    DropdownMenuItem(
                                        text = { Text("登出", color = MaterialTheme.colorScheme.error) },
                                        onClick = { showAccountMenu = false; showLogoutConfirm = true }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── 登入 CTA：未登入時在頁首下方放明顯的登入卡片，讓「登入」不再難找 ──
            if (state.loggedInEmail == null && cloudState.configured) {
                item { Spacer(Modifier.height(12.dp)) }
                item {
                    SettingsCard {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("綁定帳號", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "綁定後資料會自動備份到雲端，換手機或重新安裝登入就能還原，也才能加好友、投稿活動。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.size(2.dp))
                            Button(
                                onClick = { cloudVm.signIn { cloudSignInLauncher.launch(cloudVm.signInIntent()) } },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                enabled = !cloudState.busy
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.Login, null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.size(8.dp))
                                Text("使用 Google 登入", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ── 好友 ──
            // 好友頁本來只有行事曆右上角一個小圖示進得去，等於藏起來。這裡補一個明確入口。
            // ── 管理者捷徑 ───────────────────────────────────────
            // 後台原本只能從「我的 → 設定 → 管理者 → 後台數據檢視」進去，
            // 四層。每天要看的東西不該藏在設定頁的最底下（實機回報「藏的很深」）。
            // 只有管理員看得到，一般使用者的畫面完全不變。
            if (state.isAdmin) {
                item { Spacer(Modifier.height(12.dp)) }
                item { SectionHeader("管理者") }
                item {
                    androidx.compose.material3.Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        elevation = androidx.compose.material3.CardDefaults.cardElevation(0.dp),
                        onClick = onOpenAdmin
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Outlined.QueryStats, null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "後台數據",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    "漏斗、轉換率、活躍與內容健康度",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Outlined.KeyboardArrowRight, null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                // 其餘管理者工具原本只在右上角齒輪那一頁，而齒輪看起來是「設定」，
                // 不會有人想到管理工具在裡面（使用者回報過「管理者介面藏太深」）。
                // 後台數據那張大卡下面直接給一列，兩個入口才在同一個地方。
                item {
                    SettingsCard {
                        ClickRow(
                            Icons.Outlined.Build, "更多管理者工具",
                            "折扣碼、使用者回饋、活動蒐集來源、雲端管理員 uid"
                        ) { onOpenSettings() }
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
            item { SectionHeader("好友") }
            item {
                SettingsCard {
                    ClickRow(Icons.Outlined.People, "好友的日曆", "加好友、看他們接下來要去哪幾場") { onOpenFriends() }
                }
            }

            // 新增演出自動送審到活動頁：預設開、可關。值存雲端，App 與網頁是同一個開關；未登入不顯示。
            shareNewShows?.let { enabled ->
                item { SectionHeader("活動頁") }
                item {
                    SettingsCard {
                        SwitchRow(
                            Icons.Outlined.EventAvailable,
                            "新增演出時分享到活動頁",
                            "只送名稱、藝人、場地、時間、票價與連結給管理員審核；照片、座位、心得不會送出",
                            enabled
                        ) { viewModel.setShareNewShows(it) }
                    }
                }
            }

            // ── Bandary Pro（付費功能一覽）──
            item { Spacer(Modifier.height(12.dp)) }
            item { SectionHeader("Bandary Pro") }
            item {
                SettingsCard {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenProFeatures() }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(Icons.Outlined.WorkspacePremium, null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                when {
                                    state.isProPlus -> "Bandary Pro+・已解鎖"
                                    state.isPro -> "Bandary Pro・已解鎖"
                                    else -> "升級 Bandary Pro"
                                },
                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium
                            )
                            Text(
                                if (state.isPro) "查看你已解鎖的進階功能"
                                else "多張照片、詳細統計、深色模式⋯",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ── Appearance ──
            item { Spacer(Modifier.height(12.dp)) }
            item { SettingsEntryRow(Icons.Outlined.Palette, "外觀", "主題色 / 深淺模式 / 預設分頁") { secAppearance = true } }
            item {
                if (secAppearance) FullScreenSettingsSheet("外觀", { secAppearance = false }) {
                SettingsCard {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Icon(Icons.Outlined.SettingsBrightness, null, tint = MaterialTheme.colorScheme.primary)
                            Text("顯示模式", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemeMode.entries.forEach { mode ->
                                val label = when (mode) {
                                    ThemeMode.SYSTEM -> "跟隨系統"; ThemeMode.LIGHT -> "亮色"; ThemeMode.DARK -> "暗色"
                                }
                                FilterChip(
                                    selected = state.themeMode == mode,
                                    onClick = { viewModel.setThemeMode(mode) },
                                    label = { Text(label, fontSize = 13.sp) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                SettingsCard {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Header — tap toggles the palette + custom-colour picker.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth().clickable { paletteRevealed = !paletteRevealed }
                        ) {
                            Icon(Icons.Outlined.Palette, null, tint = MaterialTheme.colorScheme.primary)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("主題色", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                if (!paletteRevealed) {
                                    Text("點一下展開調色盤", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            // current colour preview dot
                            Box(modifier = Modifier.size(24.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary))
                        }

                        if (paletteRevealed) {
                            // 4 款預設 + 使用者自訂色（最多 2 個，合計上限 6 色）+ 新增鈕。點一下立即套用。
                            androidx.compose.foundation.layout.FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                ThemePalette.entries.filter { it != ThemePalette.CUSTOM }.forEach { p ->
                                    val selected = state.palette == p
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(p.toBrand().primary)
                                            .then(
                                                if (selected) Modifier.border(
                                                    BorderStroke(2.5.dp, MaterialTheme.colorScheme.onSurface), CircleShape
                                                ) else Modifier
                                            )
                                            .clickable { viewModel.setPalette(p) }
                                    )
                                }
                                state.customColors.forEach { hex ->
                                    val col = runCatching { Color(android.graphics.Color.parseColor(hex)) }
                                        .getOrDefault(MaterialTheme.colorScheme.primary)
                                    val selected = state.palette == ThemePalette.CUSTOM &&
                                        state.customColorHex.equals(hex, ignoreCase = true)
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(col)
                                            .then(
                                                if (selected) Modifier.border(
                                                    BorderStroke(2.5.dp, MaterialTheme.colorScheme.onSurface), CircleShape
                                                ) else Modifier
                                            )
                                            .combinedClickable(
                                                onClick = { viewModel.selectCustomColor(hex) },
                                                onLongClick = { viewModel.removeCustomColor(hex) }
                                            )
                                    )
                                }
                                if (state.customColors.size < 2) {
                                    // 自訂調色盤＝Pro：未解鎖時點「＋」導向付費功能頁（4 款預設色維持免費）。
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), CircleShape)
                                            .clickable { if (state.isPro) showColorPicker = true else onOpenProFeatures() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            if (state.isPro) Icons.Outlined.Add else Icons.Outlined.Lock, "新增顏色",
                                            modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            Text(
                                if (state.isPro) "點色圈套用；長按自訂色可移除。最多可到 6 色。"
                                else "4 款預設色免費；新增自訂色為 Pro 功能。",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                SettingsCard {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Icon(Icons.Outlined.SettingsBrightness, null, tint = MaterialTheme.colorScheme.primary)
                            Text("起始畫面", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // 「自動」＝沒有指定，由有沒有紀錄決定（見 MainActivity.defaultStartRoute）。
                            // 沒有這個選項的話，未指定狀態會被畫成「活動」被選中，那是騙人的。
                            FilterChip(
                                selected = state.startTab == null,
                                onClick = { viewModel.setStartTab(null) },
                                label = { Text("自動", fontSize = 13.sp) },
                                shape = RoundedCornerShape(8.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = Color.White
                                )
                            )
                            com.bandary.presentation.navigation.bottomNavItems.forEach { item ->
                                FilterChip(
                                    selected = state.startTab == item.screen.route,
                                    onClick = { viewModel.setStartTab(item.screen.route) },
                                    label = { Text(item.label, fontSize = 13.sp) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                        Text(
                            "「自動」＝已經有紀錄就開在演唱會，還沒有紀錄就開在活動。也可以自己指定一個固定的分頁。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            } // end 外觀（可展開）

            // ── Widgets guide（點進去開全螢幕預覽，預覽更完整）──
            item { Spacer(Modifier.height(16.dp)) }
            item { SettingsEntryRow(Icons.Outlined.Widgets, "桌面小工具", "四款小工具預覽與加入方式") { showWidgetGuide = true } }
            item {
                if (showWidgetGuide) FullScreenSettingsSheet("桌面小工具", { showWidgetGuide = false }) {
                    SettingsCard {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                "長按手機桌面空白處 → 選「小工具 / Widgets」→ 找到「Bandary」，即可加入以下四款：",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            com.bandary.presentation.ui.components.WidgetPreviewGallery()
                            listOf(
                                "即將到來清單" to "未來場次的日期、藝人與倒數",
                                "下一場倒數" to "距離最近一場還有幾天",
                                "本月月曆" to "演出 / 開賣標記，可用 ‹ › 切換月份",
                                "開賣提醒" to "即將開賣的場次清單"
                            ).forEach { (name, desc) ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Notifications ──
            item { Spacer(Modifier.height(16.dp)) }
            item { SettingsEntryRow(Icons.Outlined.Notifications, "通知", "推播與各類提醒開關") { secNotify = true } }
            item {
                if (secNotify) FullScreenSettingsSheet("通知", { secNotify = false }) {
                SettingsCard {
                    Column {
                        SwitchRow(Icons.Outlined.Notifications, "精選提醒", "演出當天 / 補紀錄 / N 年前的今天 / 週末免費活動",
                            state.notifyDigest, viewModel::setNotifyDigest)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SwitchRow(Icons.Outlined.Notifications, "開賣推播", "收藏演出開賣前 1 小時通知",
                            state.notifySale, viewModel::setNotifySale)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SwitchRow(Icons.Outlined.Alarm, "入場提醒", "新增演唱會時的預設提醒時間",
                            state.notifyEntry, viewModel::setNotifyEntry)
                        if (state.notifyEntry) {
                            // 下拉選單挑選「多久前提醒」（可複選）。摘要顯示已選幾項。
                            var reminderMenuOpen by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                        .clickable { reminderMenuOpen = true }
                                        .background(MaterialTheme.colorScheme.surfaceContainer)
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val summary = if (state.defaultReminders.isEmpty()) "選擇提醒時間"
                                    else state.defaultReminders.sortedBy { it.ordinal }.joinToString("、") { it.label }
                                    Text(summary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        color = if (state.defaultReminders.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                                    Icon(Icons.Outlined.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                }
                                DropdownMenu(expanded = reminderMenuOpen, onDismissRequest = { reminderMenuOpen = false }) {
                                    com.bandary.domain.model.ReminderOffset.entries.forEach { r ->
                                        val sel = r in state.defaultReminders
                                        DropdownMenuItem(
                                            text = { Text(r.label) },
                                            leadingIcon = {
                                                Icon(
                                                    if (sel) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                                                    null, tint = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            },
                                            onClick = {
                                                val next = if (sel) state.defaultReminders - r else state.defaultReminders + r
                                                viewModel.setDefaultReminders(next)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SwitchRow(Icons.Outlined.Alarm, "每日選歌提醒", "每天 ${state.checkInTime} 提醒紀錄",
                            state.checkInEnabled, viewModel::setCheckInEnabled)
                        if (state.checkInEnabled) {
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { showCheckInTimePicker = true }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("提醒時間", style = MaterialTheme.typography.bodyMedium)
                                Text(state.checkInTime, style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                }
            }

            // ── Cloud backup (Firebase) ──
            item { Spacer(Modifier.height(16.dp)) }
            // 有照片但從未備份過才提醒。照片存在 App 私有空間，相簿刪掉原圖不影響，
            // 但解除安裝 / 清除資料 / 換手機會整批消失——這是使用者不會預期到的。
            item {
                val atRisk by cloudVm.photosAtRisk.collectAsState()
                if (atRisk > 0) {
                    Card(
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                        shape = RoundedCornerShape(2.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "$atRisk 場演出的照片還沒備份",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "照片存在這支手機裡，在相簿刪掉原圖不影響。但解除安裝、清除資料或換手機時會一起消失。備份一次就有保險。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { cloudVm.backup() },
                                    enabled = !cloudState.busy && cloudState.connected,
                                    shape = RoundedCornerShape(2.dp)
                                ) { Text(if (cloudState.busy) "備份中…" else "立即備份") }
                                TextButton(onClick = { secBackup = true }) { Text("開自動備份") }
                            }
                            if (!cloudState.connected) {
                                Text(
                                    "需要先用 Google 登入。",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
            item { SettingsEntryRow(Icons.Outlined.CloudSync, "雲端備份 / 同步", "登入 Google 備份、換手機還原") { secBackup = true } }
            item {
                if (secBackup) FullScreenSettingsSheet("雲端備份 / 同步", { secBackup = false }) {
                SettingsCard {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!cloudState.configured) {
                            Text("尚未設定 Firebase，需在 Console 啟用 Google 登入。",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else if (!cloudState.connected) {
                            Text("用 Google 登入後可備份 / 換手機還原。",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(
                                onClick = { cloudVm.signIn { cloudSignInLauncher.launch(cloudVm.signInIntent()) } },
                                shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth(),
                                enabled = !cloudState.busy
                            ) { Text("用 Google 登入") }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(cloudState.email ?: "", style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
                                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                TextButton(
                                    onClick = { showSwitchConfirm = true },
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    enabled = !cloudState.busy
                                ) { Text("切換帳號", fontSize = 12.sp) }
                                TextButton(onClick = { showLogoutConfirm = true }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("登出", fontSize = 12.sp) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { cloudVm.backup() },
                                    shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(vertical = 8.dp),
                                    enabled = !cloudState.busy
                                ) { Text("備份", fontSize = 14.sp) }
                                OutlinedButton(
                                    onClick = { cloudVm.restore() },
                                    shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(vertical = 8.dp),
                                    enabled = !cloudState.busy
                                ) { Text("還原", fontSize = 14.sp) }
                            }
                            // 備份設定不再收合：本來就只有兩三個選項，收起來只是多一次點擊。
                            val autoMode by cloudVm.autoBackupMode.collectAsState()
                            val lastBackup by cloudVm.lastBackupText.collectAsState()
                            Text(
                                "備份設定",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            )
                            // 上次備份（含數量）永遠顯示，不用展開；備份數量只寫在這一處。
                            lastBackup?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            run {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("自動備份", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    listOf("off" to "關", "daily" to "每日", "monthly" to "每月").forEach { (mode, label) ->
                                        val sel = autoMode == mode
                                        Surface(
                                            onClick = { cloudVm.setAutoBackup(mode) },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer
                                        ) {
                                            Text(label, fontSize = 11.sp,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                color = if (sel) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Spacer(Modifier.weight(1f))
                                    TextButton(
                                        onClick = { showDeleteBackupDialog = true },
                                        contentPadding = PaddingValues(horizontal = 4.dp),
                                        enabled = !cloudState.busy
                                    ) { Text("清空雲端資料", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                            }
                        }

                        // 照片維護：備份訊息會說「N 張在這支手機上已找不到原檔」，但以前只能一場一場
                        // 進編輯頁手動移除。這裡一次清掉所有場次裡讀不到的照片欄位。
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("照片", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.weight(1f))
                            TextButton(
                                onClick = { showCleanupPhotosDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                enabled = !cloudState.busy
                            ) { Text("清理找不到的照片", fontSize = 12.sp) }
                        }
                        // 雲端未使用的照片：備份**不會**動它們，要刪只能從這裡、
                        // 而且會先算出張數讓使用者確認。
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("雲端照片", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.weight(1f))
                            TextButton(
                                onClick = {
                                    countingOrphans = true
                                    profileScrollScope.launch {
                                        orphanPhotoCount = cloudVm.countOrphanPhotos()
                                        countingOrphans = false
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                enabled = !cloudState.busy && !countingOrphans
                            ) {
                                Text(
                                    if (countingOrphans) "計算中…" else "清理雲端未使用的照片",
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // 匯出 CSV（匯出資料）＝ Pro 功能：未解鎖時點擊改為引導到付費功能頁。
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("匯出 CSV", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (!state.isPro) {
                                Spacer(Modifier.size(4.dp))
                                Icon(Icons.Outlined.Lock, "Pro 功能", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
                            }
                            Spacer(Modifier.weight(1f))
                            TextButton(
                                onClick = {
                                    if (!state.isPro) { onOpenProFeatures(); return@TextButton }
                                    val stamp = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.TAIWAN).format(java.util.Date())
                                    exportCsvLauncher.launch("bandary_concerts_$stamp.csv")
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                enabled = !cloudState.busy
                            ) { Text("演唱會", fontSize = 12.sp) }
                            TextButton(
                                onClick = {
                                    if (!state.isPro) { onOpenProFeatures(); return@TextButton }
                                    val stamp = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.TAIWAN).format(java.util.Date())
                                    exportSongsCsvLauncher.launch("bandary_songs_$stamp.csv")
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                enabled = !cloudState.busy
                            ) { Text("選歌", fontSize = 12.sp) }
                        }

                        // 批量匯入（CSV 檔或貼上清單）＝免費，一次加入一大批演唱會。
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("批量匯入", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.weight(1f))
                            TextButton(
                                onClick = onOpenBulkImport,
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) { Text("匯入演唱會", fontSize = 12.sp) }
                        }

                        // 垃圾桶：刪除的演唱會保留 7 天可還原。
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("垃圾桶", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.weight(1f))
                            TextButton(
                                onClick = onOpenTrash,
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) { Text("刪除的演唱會", fontSize = 12.sp) }
                        }

                        if (cloudState.busy) {
                            Text("處理中…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        cloudState.message?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                }
            }

            // ── Google Calendar ──
            item { Spacer(Modifier.height(16.dp)) }
            item { SettingsEntryRow(Icons.Outlined.CalendarMonth, "Google 日曆", "連結後匯出 / 匯入日曆活動") { secCalendar = true } }
            item {
                if (secCalendar) FullScreenSettingsSheet("Google 日曆", { secCalendar = false }) {
                // 一個地方講完 Google 日曆：連哪個帳號、要不要自動同步、怎麼匯入。
                // 以前「同步開關」在「通知」裡、「連結帳號」在這裡，兩件事要去兩個頁面各設一次。
                SettingsCard {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!calState.connected) {
                            Text("連結後，新增的演唱會會自動寫進你的 Google 日曆；也能反過來把日曆上的活動挑進 Bandary。",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            com.bandary.presentation.ui.components.GoogleCalendarSamplePreview()
                            Button(
                                onClick = { calSignInLauncher.launch(calVm.signInIntent()) },
                                shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()
                            ) { Text("連結 Google 日曆") }
                        } else {
                            Text("已連結：${calState.email ?: ""}",
                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { calVm.disconnect() }) { Text("斷開連結") }
                            }
                        }
                    }
                }
                if (calState.connected) {
                    Spacer(Modifier.height(8.dp))
                    SettingsCard {
                        Column {
                            SwitchRow(
                                Icons.Outlined.CalendarMonth, "新增演出時自動寫入日曆",
                                "存檔後在 Google 日曆建立一則行程；也可以在每一場自己調整",
                                state.notifyCalSync, viewModel::setNotifyCalSync
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            ClickRow(
                                Icons.Outlined.EventAvailable, "從日曆匯入活動",
                                "挑選 Google 日曆上的活動，勾選後加進 Bandary"
                            ) { onOpenImportCalendar() }
                        }
                    }
                }
                calState.message?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp))
                }
                }
            }

            // ── Custom platforms ──
            item { Spacer(Modifier.height(16.dp)) }
            item { SettingsEntryRow(Icons.Outlined.ConfirmationNumber, "自訂購票平台", "新增 / 移除自己的售票平台") { secPlatform = true } }
            item {
                if (secPlatform) FullScreenSettingsSheet("自訂購票平台", { secPlatform = false }) {
                SettingsCard {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.customPlatforms.isEmpty()) {
                            Text("尚未新增自訂平台。內建：KKTIX / Ticket Plus / ibon / 拓元。",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            state.customPlatforms.forEach { name ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text(name, style = MaterialTheme.typography.bodyMedium)
                                    TextButton(onClick = {
                                        viewModel.countConcertsUsingPlatform(name) { count ->
                                            if (count > 0) platformPendingRemove = name to count
                                            else viewModel.removeCustomPlatform(name)
                                        }
                                    }) {
                                        Text("移除", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        Button(onClick = { showPlatformDialog = true }, shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()) { Text("新增自訂平台") }
                    }
                }
                }
            }

            // ── 兌換折扣碼 ──
            item { Spacer(Modifier.height(16.dp)) }
            item { SectionHeader("兌換折扣碼") }
            item {
                SettingsCard {
                    ClickRow(
                        Icons.Outlined.Redeem,
                        if (state.isPro) "Bandary Pro 已解鎖" else "輸入折扣碼",
                        if (state.isPro) "已解鎖全部進階功能" else "輸入折扣碼即可解鎖進階功能"
                    ) { showAdminDialog = true }
                }
            }

            // ── 帳號 ──
            // 登出與刪除帳號原本裝在【兌換折扣碼】底下，跟折扣碼完全無關。
            // 標題和內容不符比藏起來更糟：要刪帳號的人不會想到去點「兌換折扣碼」。
            if (state.loggedInEmail != null) {
                item { Spacer(Modifier.height(16.dp)) }
                item { SectionHeader("帳號") }
                item {
                    SettingsCard {
                        Column {
                            ClickRow(Icons.AutoMirrored.Outlined.Logout, "登出", state.loggedInEmail!!) { showLogoutConfirm = true }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            // Google Play 規定：能建立帳號的 App 必須能在 App 內刪除帳號。
                            var showDeleteAccount by remember { mutableStateOf(false) }
                            ClickRow(Icons.Outlined.DeleteForever, "刪除帳號", "永久刪除這個帳號在雲端的所有資料") { showDeleteAccount = true }
                            if (showDeleteAccount) {
                                DeleteAccountDialog(
                                    email = state.loggedInEmail.orEmpty(),
                                    onDelete = { onResult -> viewModel.deleteAccount(onResult) },
                                    onDeleted = { showDeleteAccount = false; cloudVm.signOut(); viewModel.logout() },
                                    onDismiss = { showDeleteAccount = false }
                                )
                            }
                        }
                    }
                }
            }

            // ── Feedback（移到設定下方，讓通知 / 備份等常用設定在上）──
            item { Spacer(Modifier.height(16.dp)) }
            item { SectionHeader("支持與回饋") }
            item {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                fun openUrl(url: String) {
                    runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
                }
                SettingsCard {
                    Column {
                        // 站外贊助。文案刻意只講「請喝咖啡」，不提任何回報或解鎖
                        // ——那是 Play 付款政策上「tip jar」與「替代金流」的分界。
                        if (SHOW_DONATE) {
                            ClickRow(
                                Icons.Outlined.VolunteerActivism,
                                "請開發者喝杯咖啡",
                                // 使用者要求拿掉「不會解鎖任何功能」。
                                // 政策上沒問題：Play 在意的是不要**暗示有回報**，
                                // 少講一句不等於多暗示什麼。剩下的「站外贊助」照樣說明了
                                // 金流不經過 App，而這一列也仍然不在 Pro 介紹頁旁邊。
                                // 真的被退件的話把 SHOW_DONATE 改回 false 就能先上架。
                                "站外贊助"
                            ) {
                                // 先記一筆再開網頁。記錄是 fire-and-forget（見 recordDonateClick），
                                // 寫不進去也不會卡住他要去的地方。
                                viewModel.recordOutboundClick(com.bandary.domain.model.OutboundLinks.DONATE)
                                openUrl(DONATE_URL)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        ClickRow(Icons.Outlined.StarRate, "給我們評分", "在 Google Play 留下你的好評") {
                            runCatching {
                                ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=${ctx.packageName}")))
                            }.onFailure { openUrl("https://play.google.com/store/apps/details?id=${ctx.packageName}") }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        // 分享這兩列從【關於與說明】搬過來：使用者在這一區的意圖是
                        // 「我要支持 / 推薦這個 App」，分享正是那件事；
                        // 擺在「版本、使用說明」旁邊反而找不到。
                        ClickRow(Icons.Outlined.Share, "分享給朋友", "把聽團曆的連結與你的邀請碼傳給朋友") {
                            com.bandary.data.util.ShareApp.share(ctx, inviteCode = currentInviteCode())
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ClickRow(Icons.Outlined.QrCode2, "分享換試用期", "發文達標可換 30 天試用期；附你的 QR") { onOpenPromo() }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        // 其他作品獨立一頁，不在這裡直接列三個連結：
                        // 它們跟 Bandary 的功能無關，混在設定列表裡會被當成這個 App 的功能
                        //（「壽星日」看起來就像生日提醒），點下去卻跳瀏覽器。
                        ClickRow(
                            Icons.Outlined.Apps, "開發者的其他作品",
                            "壽星日、RelateStats ‧ 會開啟外部網頁"
                        ) { onOpenBrand() }
                    }
                }
            }

            // ── 意見與許願 ──
            // 原本和贊助、評分混在【支持與回饋】同一張卡裡。
            // 前者是「我要支持這個 App」，這三列是「我有話要說」——意圖不同就該分開，
            // 混在一起找東西要一列一列讀。
            item { Spacer(Modifier.height(16.dp)) }
            item { SectionHeader("意見與許願") }
            item {
                SettingsCard {
                    Column {
                        // 「聯絡我們」原本是獨立一列（開一個只顯示信箱的對話框）。
                        // 那和「意見回饋」是同一件事：找開發者。合成一列，
                        // 信箱移到回饋頁底下（見 FeedbackScreen）——那裡本來就是
                        // 要說話的地方，而且寫完表單還能順手寄信。
                        ClickRow(
                            Icons.Outlined.Feedback, "意見回饋",
                            "回報問題、給滿意度，或直接寄信給開發者"
                        ) { onOpenFeedback() }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ClickRow(Icons.Outlined.Lightbulb, "功能許願區", "公開許願、幫別人的許願按讚，讚數高的先做") { onOpenWishes() }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ClickRow(
                            Icons.Outlined.TravelExplore, "建議活動來源",
                            "看到常辦演出的售票頁或粉專？丟給我，審過才會加進抓取名單"
                        ) { showSuggestSource = true }
                    }
                }
            }

            // ── About / 使用說明 ──
            item { Spacer(Modifier.height(16.dp)) }
            item { SectionHeader("關於與說明") }
            item {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val pkg = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0) }.getOrNull() }
                val versionName = pkg?.versionName ?: "—"
                val versionCode = remember(pkg) {
                    pkg?.let { androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(it) } ?: 0L
                }
                val latest by viewModel.latestVersion.collectAsState()
                LaunchedEffect(Unit) { viewModel.loadLatestVersion() }
                fun openStore() {
                    runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=${ctx.packageName}"))) }
                        .onFailure { runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=${ctx.packageName}"))) } }
                }
                SettingsCard {
                    Column {
                        // 版本相關的三列整合成兩列（實機回報：「應用程式版本跟查看更新
                        // 應該是要在設定的同一區，類似這種的盡量幫我整合」）。
                        //
                        // 原本是三列：「應用程式版本」「更新紀錄」「最新版本」。
                        // 前兩件其實是同一個問題的兩半——「我現在是哪一版」與「那一版有什麼」，
                        // 分兩列只是把一句話講兩次。而「應用程式版本」點下去開的是**使用說明**，
                        // 跟標題完全無關，那是個 bug 不是設計。
                        //
                        // 現在：第一列「版本」直接寫出目前版號 + 有沒有新版，點下去看更新紀錄
                        // （「我是哪一版」的下一個問題一定是「這版改了什麼」）；
                        // 只有**真的有新版**時才多出第二列「前往更新」——沒有新版的時候，
                        // 一列寫著「已是最新」的可點列是純粹的雜訊。
                        var showChangelog by remember { mutableStateOf(false) }
                        if (showChangelog) ChangelogDialog(currentVersion = versionName, onDismiss = { showChangelog = false })
                        // 讀不到雲端版號（離線 / 管理員還沒設）就不比對，也不要謊報「已是最新」。
                        val hasUpdate = latest != null && latest!!.versionCode > versionCode
                        ClickRow(
                            Icons.Outlined.Info,
                            "版本 v$versionName",
                            when {
                                hasUpdate -> "有新版 v${latest!!.versionName}" +
                                    (latest!!.releaseNote.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "") +
                                    " · 點這裡看這版改了什麼"
                                latest != null -> "已是最新版本 · 點一下看更新紀錄"
                                else -> "點一下看更新紀錄"
                            }
                        ) { showChangelog = true }
                        if (hasUpdate) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            ClickRow(
                                Icons.Outlined.SystemUpdate,
                                "前往更新",
                                "到 Google Play 更新到 v${latest!!.versionName}"
                            ) { openStore() }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        // 說明原本是兩列：「使用說明」（App 內快速上手）與
                        // 「完整說明與常見問題」（網頁手冊）。對使用者來說那是同一個問題
                        // 「我不會用，去哪看」，分兩列只是要他先選一個他還不知道差別的選項。
                        // 併成一列，網頁手冊的連結放在快速上手對話框的最後面——
                        // 看完還不夠的人才需要它，順序剛好。
                        ClickRow(
                            Icons.AutoMirrored.Outlined.MenuBook, "使用說明",
                            "快速上手指引；看完還不夠可以開網頁版完整手冊"
                        ) { showGuide = true }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        // 英文版還沒翻完，先停用整列：讓人點進去挑了語言卻沒有真的切換，
                        // 比直接告訴他「還不能用」糟糕得多（實機回報）。
                        ClickRow(
                            Icons.Outlined.Language,
                            androidx.compose.ui.res.stringResource(com.bandary.R.string.settings_language),
                            "英文版準備中，暫時無法切換",
                            enabled = false
                        ) { showLanguageDialog = true }
                    }
                }
            }

            item {
                // 頁尾的品牌落款：標誌＋名稱＋版號。
                // 以前只是一行灰色小字，捲到底看不出這是誰做的 App。
                // 署名區：全 App 共用同一份（BrandFooter），格式依品牌規範。
                com.bandary.presentation.ui.components.BrandFooter(
                    contactEmail = DEV_EMAIL
                )
            }
        }
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false; calVm.clearMessage() },
            title = { Text("從 Google 日曆匯入") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                    items(calState.events) { ev ->
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .clickable {
                                    calVm.addFromEvent(ev) {}
                                    showImportDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp)
                        ) {
                            Text(ev.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                buildString {
                                    append(ev.startIso.substringBefore("T"))
                                    ev.location?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showImportDialog = false }) { Text("關閉") } }
        )
    }

    if (showAdminDialog) {
        AlertDialog(
            onDismissRequest = { showAdminDialog = false; adminError = null; adminCode = "" },
            title = { Text("輸入折扣碼") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = adminCode, onValueChange = { adminCode = it.uppercase(); adminError = null },
                        label = { Text("折扣碼") },
                        singleLine = true, isError = adminError != null, modifier = Modifier.fillMaxWidth()
                    )
                    if (adminError != null) Text(adminError!!, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        if (viewModel.tryAdminCode(adminCode)) { showAdminDialog = false; adminCode = ""; adminError = null }
                        else adminError = "折扣碼無效或已過期"
                    }
                }) { Text("兌換") }
            },
            dismissButton = { TextButton(onClick = { showAdminDialog = false }) { Text("取消") } }
        )
    }

    if (showPlatformDialog) {
        AlertDialog(
            onDismissRequest = { showPlatformDialog = false; newPlatformName = "" },
            title = { Text("新增自訂購票平台") },
            text = {
                OutlinedTextField(value = newPlatformName, onValueChange = { newPlatformName = it },
                    label = { Text("平台名稱") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = newPlatformName.trim()
                    if (n.isNotBlank()) viewModel.addCustomPlatform(n)
                    showPlatformDialog = false; newPlatformName = ""
                }) { Text("新增") }
            },
            dismissButton = { TextButton(onClick = { showPlatformDialog = false; newPlatformName = "" }) { Text("取消") } }
        )
    }

    if (showAnnouncements) {
        val announcements by viewModel.announcements.collectAsState()
        // 開啟即標記為已讀（清掉未讀紅點）
        androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.markAnnouncementsRead() }
        AlertDialog(
            onDismissRequest = { showAnnouncements = false },
            title = { Text("官方通知") },
            text = {
                if (announcements.isEmpty()) {
                    Text("目前沒有官方通知。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(
                        modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        announcements.forEach { a ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(a.title.ifBlank { "無標題" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(a.body, style = MaterialTheme.typography.bodyMedium)
                                val d = kotlinx.datetime.Instant.fromEpochMilliseconds(a.createdAt)
                                    .toLocalDateTime(kotlinx.datetime.TimeZone.of("Asia/Taipei")).date
                                Text("${d.year}/${d.monthNumber}/${d.dayOfMonth}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAnnouncements = false }) { Text("關閉") } }
        )
    }

    platformPendingRemove?.let { (name, count) ->
        AlertDialog(
            onDismissRequest = { platformPendingRemove = null },
            title = { Text("移除「$name」？") },
            text = { Text("已有 $count 場演唱會使用此購票平台。移除後這些演唱會仍會保留原本填寫的「$name」，但它不再出現在平台選單中。確定要移除嗎？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeCustomPlatform(name)
                    platformPendingRemove = null
                }) { Text("移除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { platformPendingRemove = null }) { Text("取消") } }
        )
    }

    if (showSwitchConfirm) {
        AlertDialog(
            onDismissRequest = { showSwitchConfirm = false },
            title = { Text("切換帳號") },
            text = { Text("將登出目前帳號「${state.loggedInEmail ?: ""}」並改用其他 Google 帳號登入，要繼續嗎？") },
            confirmButton = {
                TextButton(onClick = {
                    showSwitchConfirm = false
                    cloudVm.signIn { cloudSignInLauncher.launch(cloudVm.signInIntent()) }
                }) { Text("切換帳號") }
            },
            dismissButton = { TextButton(onClick = { showSwitchConfirm = false }) { Text("取消") } }
        )
    }


    if (showLanguageDialog) {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val current = com.bandary.data.local.LocaleManager.getLang(ctx)
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(androidx.compose.ui.res.stringResource(com.bandary.R.string.settings_language), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    listOf(
                        "" to com.bandary.R.string.language_system,
                        "zh" to com.bandary.R.string.language_zh,
                        "en" to com.bandary.R.string.language_en
                    ).forEach { (code, res) ->
                        val sel = current == code
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                com.bandary.data.local.LocaleManager.setLang(ctx, code)
                                showLanguageDialog = false
                                (ctx as? android.app.Activity)?.recreate()
                            }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                if (sel) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                                null, tint = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(androidx.compose.ui.res.stringResource(res), style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLanguageDialog = false }) { Text("關閉") } }
        )
    }

    if (showGuide) {
        val ctx0 = androidx.compose.ui.platform.LocalContext.current
        AlertDialog(
            onDismissRequest = { showGuide = false },
            title = { Text("快速上手", fontWeight = FontWeight.Bold) },
            text = {
                com.bandary.presentation.ui.components.DialogBody(spacing = 10.dp) {
                    @Composable
                    fun step(title: String, body: String) {
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    step("演唱會", "右下角「＋」新增看過或想看的演出，可加照片、票根、歌單與心得。")
                    step("行事曆", "以月曆檢視所有演出，並管理提醒。")
                    step("活動", "瀏覽免費 / 售票活動，也可投稿；有興趣可一鍵加入自己的演唱會。")
                    step("統計", "查看你的觀演數據；圖表可點進去看該類的所有場次。")
                    step("選歌", "每天記錄正在聽的歌，累積屬於你的音樂日記與連續紀錄。")
                    step("票根分享", "在演出詳情或統計頁，把你的紀錄做成圖片分享出去。")
                    step("我的", "用 Google 登入可雲端備份、換手機還原、加好友、調整通知與外觀。")
                }
            },
            // 網頁版完整手冊原本是外面獨立一列「完整說明與常見問題」。
            // 併進來放在對話框的按鈕上：看完快速上手還不夠的人才需要它，順序剛好；
            // 而原本在外面那一列，是要人先在兩個他還不知道差別的選項裡選一個。
            dismissButton = {
                TextButton(onClick = {
                    showGuide = false
                    runCatching {
                        ctx0.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://bandary.web.app/help")
                            )
                        )
                    }
                }) { Text("看完整手冊") }
            },
            confirmButton = { TextButton(onClick = { showGuide = false }) { Text("知道了") } }
        )
    }

    if (showSuggestSource) {
        var url by remember { mutableStateOf("") }
        var label by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        val sugCtx = androidx.compose.ui.platform.LocalContext.current
        AlertDialog(
            onDismissRequest = { showSuggestSource = false },
            title = { Text("建議活動來源") },
            text = {
                com.bandary.presentation.ui.components.DialogBody(spacing = 10.dp) {
                    Text(
                        "送出的來源不會直接開始抓取——我會先看過內容與對方網站的使用條款，" +
                            "確認可以抓、也抓得到東西，才會加進每天的抓取名單。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = url, onValueChange = { url = it },
                        label = { Text("網址 *") },
                        placeholder = { Text("https://…") },
                        singleLine = true, shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = label, onValueChange = { label = it },
                        label = { Text("名稱") },
                        placeholder = { Text("例如：The Wall Live House") },
                        singleLine = true, shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = note, onValueChange = { note = it },
                        label = { Text("補充（選填）") },
                        placeholder = { Text("這個頁面都放什麼、多久更新一次") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(90.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = url.isNotBlank() && !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            val r = cloudVm.suggestSource(url, label, note)
                            busy = false
                            android.widget.Toast.makeText(
                                sugCtx,
                                r ?: "已送出，謝謝！審過會加進抓取名單",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            if (r == null) showSuggestSource = false
                        }
                    }
                ) { Text("送出") }
            },
            dismissButton = { TextButton(onClick = { showSuggestSource = false }) { Text("取消") } }
        )
    }

    if (showCalendarLinkPrompt) {
        AlertDialog(
            onDismissRequest = {
                showCalendarLinkPrompt = false
                viewModel.markCalendarLinkAsked()
            },
            title = { Text("要順便連結 Google 日曆嗎？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "登入 Bandary 和連結 Google 日曆是兩件事。連結之後，新增的演唱會會自動寫進你的日曆。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    com.bandary.presentation.ui.components.GoogleCalendarSamplePreview()
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showCalendarLinkPrompt = false
                    viewModel.markCalendarLinkAsked()
                    calSignInLauncher.launch(calVm.signInIntent())
                }) { Text("連結") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCalendarLinkPrompt = false
                    viewModel.markCalendarLinkAsked()
                }) { Text("以後再說") }
            }
        )
    }

    if (showCleanupPhotosDialog) {
        AlertDialog(
            onDismissRequest = { showCleanupPhotosDialog = false },
            title = { Text("清理找不到的照片") },
            text = {
                Text(
                    "會檢查每一場演唱會，把「這支手機上已經找不到原檔」的照片欄位移除"
                        + "（原圖被刪、或是從別台裝置還原回來、本機沒有那些檔案）。\n\n"
                        + "只動這支手機上的欄位，**雲端那份照片不會被刪**——"
                        + "之後從別的裝置還原時還在。看得到的照片也不會被碰。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showCleanupPhotosDialog = false
                    cloudVm.cleanupMissingPhotos()
                }) { Text("開始清理", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showCleanupPhotosDialog = false }) { Text("取消") } }
        )
    }

    orphanPhotoCount?.let { count ->
        AlertDialog(
            onDismissRequest = { orphanPhotoCount = null },
            title = { Text(if (count > 0) "刪除雲端 $count 張未使用的照片？" else "雲端沒有未使用的照片") },
            text = {
                Text(
                    if (count == 0)
                        "雲端的每一張照片目前都還被某一場演唱會引用著，沒有需要清理的。"
                    else
                        "這 $count 張在雲端還在，但這支手機上已經沒有任何場次引用它們。\n\n" +
                            "常見原因是原圖被相簿刪了、或你用過「清理找不到的照片」。" +
                            "雲端這份很可能是**唯一剩下的副本**，刪掉就真的沒了。\n\n" +
                            "不確定就先按取消——留著只佔一點空間，備份不會因此變慢。"
                )
            },
            confirmButton = {
                if (count > 0) {
                    TextButton(onClick = {
                        cloudVm.deleteOrphanPhotos(count)
                        orphanPhotoCount = null
                    }) { Text("確定刪除 $count 張", color = MaterialTheme.colorScheme.error) }
                } else {
                    TextButton(onClick = { orphanPhotoCount = null }) { Text("知道了") }
                }
            },
            dismissButton = {
                if (count > 0) TextButton(onClick = { orphanPhotoCount = null }) { Text("取消") }
            }
        )
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("登出帳號") },
            text = { Text("登出後本機資料會保留，之後可用其他 Google 帳號重新登入。要登出「${state.loggedInEmail ?: ""}」嗎？") },
            confirmButton = {
                TextButton(onClick = {
                    // 完整登出：同時清掉 Firebase / Google 連線與本機的登入 / 權限狀態。
                    cloudVm.signOut(); viewModel.logout(); showLogoutConfirm = false
                }) { Text("登出", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showLogoutConfirm = false }) { Text("取消") } }
        )
    }

    if (showColorPicker) {
        com.bandary.presentation.ui.components.ColorPickerDialog(
            initialHex = state.customColorHex ?: "#6B9080",
            onPick = { hex -> viewModel.addCustomColor(hex); showColorPicker = false },
            onDismiss = { showColorPicker = false }
        )
    }

    if (showDeleteBackupDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteBackupDialog = false },
            title = { Text("清空雲端帳號資料") },
            // 這段警告在網頁版上線後必須改寫：雲端不再只是「備份」，網頁版讀寫的就是同一份，
            // 清掉之後網頁會變成空的；只在網頁新增、還沒同步回手機的場次會直接消失，救不回來。
            text = {
                Text(
                    "將永久刪除這個帳號在雲端的所有演唱會與選歌。這支手機上的資料不受影響，" +
                        "但網頁版讀的就是這份雲端資料——清空後網頁會變成空的。\n\n" +
                        "如果你曾在網頁新增或修改過，而這支手機還沒同步過，那些內容會直接消失、無法復原。" +
                        "建議先在上面按一次同步，確認手機已收到最新資料再清空。"
                )
            },
            confirmButton = {
                TextButton(onClick = { cloudVm.deleteBackup(); showDeleteBackupDialog = false }) {
                    Text("清空", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteBackupDialog = false }) { Text("取消") } }
        )
    }

    if (showNicknameDialog) {
        var nick by remember { mutableStateOf(state.nickname ?: "") }
        AlertDialog(
            onDismissRequest = { showNicknameDialog = false },
            title = { Text("自訂暱稱") },
            text = {
                OutlinedTextField(value = nick, onValueChange = { nick = it },
                    label = { Text("暱稱") }, placeholder = { Text("顯示在這裡的名字") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = { viewModel.setNickname(nick.trim().ifBlank { null }); showNicknameDialog = false }) { Text("儲存") }
            },
            dismissButton = { TextButton(onClick = { showNicknameDialog = false }) { Text("取消") } }
        )
    }

    if (showCheckInTimePicker) {
        val parts = state.checkInTime.split(":")
        val pickerState = rememberTimePickerState(
            initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 21,
            initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showCheckInTimePicker = false },
            title = { Text("選擇提醒時間") },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    val hh = pickerState.hour.toString().padStart(2, '0')
                    val mm = pickerState.minute.toString().padStart(2, '0')
                    viewModel.setCheckInTime("$hh:$mm")
                    showCheckInTimePicker = false
                }) { Text("確定") }
            },
            dismissButton = { TextButton(onClick = { showCheckInTimePicker = false }) { Text("取消") } }
        )
    }
}

// 可展開 / 收合的設定分區標題：整列可點，右側箭頭指示狀態。
@Composable
private fun CollapsibleHeader(title: String, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onToggle() }.padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Icon(
            if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            if (expanded) "收合" else "展開",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 設定子頁：點設定列後全螢幕彈出（仿 Meta 設定 → 進到獨立頁面寫詳細設定）。
 * 內容維持在原本的 composable 作用域，這裡只是把它包成一個可捲動的全螢幕頁。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullScreenSettingsSheet(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    // 用共用的 FullScreenOverlay：它預設就讓開系統列。
    // 這裡原本是自己開 Dialog、沒有任何 inset 處理，標題列會躲到狀態列底下。
    com.bandary.presentation.ui.components.FullScreenOverlay(onDismiss = onClose) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                content()
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** 設定入口列：一張卡片 + 一列（圖示 / 標題 / 副標 / ›），點了開子頁。 */
@Composable
private fun SettingsEntryRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    SettingsCard { ClickRow(icon, title, subtitle, onClick = onClick) }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        // 深色主題下卡片與背景幾乎同為近黑，加一圈細邊界讓每個區塊清楚分隔。
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(1.dp)
    ) { content() }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** 目前登入者的邀請碼；沒登入回 null（分享時就不附邀請碼）。 */
private fun currentInviteCode(): String? =
    com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        ?.let { com.bandary.domain.model.InviteCode.of(it) }
        ?.takeIf { it.isNotBlank() }

@Composable
private fun ClickRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    /** false＝整列變灰且點不動。用在「功能還沒好」的列，比讓人點進去發現沒用誠實。 */
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.38f
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            )
            Text(
                subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}
