package com.bandary.presentation.ui.friends

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bandary.presentation.ui.components.ScrollTopFab
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bandary.data.remote.FirestoreService
import com.bandary.data.repository.FriendsRepository
import com.bandary.domain.model.Friend
import com.bandary.domain.model.FriendRequest
import com.bandary.presentation.ui.components.EmptyState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val repo: FriendsRepository,
    private val settings: com.bandary.data.local.SettingsDataStore,
    private val firestore: FirestoreService
) : ViewModel() {

    val shareSongProfile: StateFlow<Boolean> = settings.shareSongProfile
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), false)

    /**
     * 好友與排行榜看到的顯示名稱。自訂暱稱優先，其次 Google 帳號名稱。
     * 直接跟著 settings.nickname 走：改完暱稱回到這頁就是新名字，不必等下一次同步。
     * 這裡只讀不寫——暱稱只有「我的」頁面能改，兩個地方都能改同一個東西只會讓人搞混。
     */
    val myDisplayName: StateFlow<String> = settings.nickname
        .map { it?.takeIf { n -> n.isNotBlank() } ?: firestore.accountDisplayName() }
        .stateIn(
            viewModelScope,
            kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            firestore.currentDisplayName()
        )

    /**
     * 切換年度歌單分享。改完立刻重新發布一次：打開才會把聚合結果送上雲端，
     * 關掉則會把雲端既有的 songProfile 刪除（見 FirestoreService.publishMyCalendar）。
     */
    fun setShareSongProfile(v: Boolean) = viewModelScope.launch {
        settings.setShareSongProfile(v)
        if (firestore.isSignedIn()) repo.publishMyCalendar(quiet = true)
    }

    val friends: StateFlow<List<Friend>> = repo.friends
    val requests: StateFlow<List<FriendRequest>> = repo.requests
    val myHandle: StateFlow<String?> = repo.myHandle
    val discoverableByEmail: StateFlow<Boolean> = repo.discoverableByEmail
    val loading: StateFlow<Boolean> = repo.loading
    val message: StateFlow<String?> = repo.message

    fun isSignedIn(): Boolean = firestore.isSignedIn()

    init {
        // 登入就當作要分享：進到好友頁順手靜默發布一次自己的日曆。
        // 以前要自己按「更新我的共享日曆」才會有內容，加了好友卻什麼都看不到，
        // 使用者以為是壞掉（實機回報「我的活動不用特地發布，一開始就該是發布的」）。
        // 內容只有即將到來的場次，而且 shared_calendars 只有好友讀得到。
        if (isSignedIn()) {
            viewModelScope.launch {
                repo.refresh()
                runCatching { repo.publishMyCalendar(quiet = true) }
            }
        }
    }

    fun publish() = viewModelScope.launch { repo.publishMyCalendar() }
    fun search(query: String) = viewModelScope.launch { repo.searchAndRequest(query) }
    fun accept(req: FriendRequest) = viewModelScope.launch { repo.acceptRequest(req) }
    fun decline(fromUid: String) = viewModelScope.launch { repo.declineRequest(fromUid) }
    fun removeFriend(uid: String) = viewModelScope.launch { repo.removeFriend(uid) }
    /** 改成自己想要的好友代碼。唯一性由後端 transaction 保證，撞號會回錯誤訊息。 */
    fun setHandle(h: String) = viewModelScope.launch { repo.setHandle(h) }
    fun setDiscoverable(v: Boolean) = viewModelScope.launch { repo.setDiscoverableByEmail(v) }
    fun clearMessage() = repo.clearMessage()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    onBack: () -> Unit,
    onViewFriend: (uid: String, name: String) -> Unit,
    viewModel: FriendsViewModel = hiltViewModel()
) {
    val friends by viewModel.friends.collectAsState()
    val requests by viewModel.requests.collectAsState()
    val myHandle by viewModel.myHandle.collectAsState()
    val discoverable by viewModel.discoverableByEmail.collectAsState()
    val shareSongs by viewModel.shareSongProfile.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val myDisplayName by viewModel.myDisplayName.collectAsState()
    val message by viewModel.message.collectAsState()
    val context = LocalContext.current
    var showAdd by remember { mutableStateOf(false) }
    var showHandleDialog by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scrollScope = androidx.compose.runtime.rememberCoroutineScope()

    Scaffold(
        // 清單可能很長，捲下去之後要回得來。行為與其他頁共用同一個元件。
        floatingActionButton = { ScrollTopFab(listState, scrollScope) },
        topBar = {
            TopAppBar(
                title = { Text("好友的日曆", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 要加好友，對方得先有這個 App：邀請連結放在最順手的位置。
                    IconButton(onClick = { com.bandary.data.util.ShareApp.share(context) }) {
                        Icon(Icons.Outlined.Share, contentDescription = "分享給朋友")
                    }
                    IconButton(onClick = { viewModel.publish() }) {
                        Icon(Icons.Outlined.CloudUpload, contentDescription = "更新我的共享日曆")
                    }
                    IconButton(onClick = {
                        if (viewModel.isSignedIn()) showAdd = true
                        else Toast.makeText(context, "請先到「我的」用 Google 登入", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Outlined.PersonAdd, contentDescription = "加好友")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (!viewModel.isSignedIn()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(title = "請先登入", subtitle = "到「我的」用 Google 登入後即可加好友、互看日曆")
            }
            return@Scaffold
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                // 這頁的 Scaffold 有 FAB，bottom 已含 FAB 高度；只補呼吸空間。
                bottom = padding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── 我的名字 + 好友代碼 + email 搜尋開關 ──
            item {
                MyIdentityCard(
                    handle = myHandle,
                    displayName = myDisplayName,
                    discoverable = discoverable,
                    shareSongs = shareSongs,
                    onEditHandle = { showHandleDialog = true },
                    onToggleDiscoverable = { viewModel.setDiscoverable(it) },
                    onToggleShareSongs = { viewModel.setShareSongProfile(it) }
                )
            }

            // ── 好友請求 ──
            if (requests.isNotEmpty()) {
                item {
                    Text("好友請求", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp))
                }
                items(requests, key = { "req_${it.fromUid}" }) { r ->
                    RequestRow(
                        request = r,
                        onAccept = { viewModel.accept(r) },
                        onDecline = { viewModel.decline(r.fromUid) }
                    )
                }
            }

            // ── 好友清單 ──
            item {
                Text("我的好友", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp))
            }
            when {
                loading && friends.isEmpty() -> item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                    }
                }
                friends.isEmpty() -> item {
                    Text(
                        "還沒有好友。點右上角的加人圖示，輸入對方的好友代碼送出請求；對方若已開放 email 搜尋，也可用 email。" +
                            "加成好友之後，點好友那一列就會看到他即將要去的場次。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> items(friends, key = { it.uid }) { f ->
                    FriendRow(
                        friend = f,
                        onClick = { onViewFriend(f.uid, f.displayName) },
                        onRemove = { viewModel.removeFriend(f.uid) }
                    )
                }
            }
        }
    }

    if (showAdd) {
        var query by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("加好友") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "輸入對方的好友代碼，例如 @someone，送出好友請求。對方接受後你們才互為好友，也才看得到彼此的日曆。若對方已開放 email 搜尋，也可輸入 email。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("好友代碼或 email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { if (query.isNotBlank()) { viewModel.search(query); showAdd = false } },
                    enabled = query.isNotBlank()
                ) { Text("送出請求") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("取消") } }
        )
    }

    if (showHandleDialog) {
        // 已經自動配過一組，這裡預設帶現有的，改不改都行。
        var handle by remember { mutableStateOf(myHandle.orEmpty()) }
        val normalized = handle.trim().removePrefix("@").lowercase()
        val valid = Regex("^[a-z0-9_]{3,20}$").matches(normalized)
        AlertDialog(
            onDismissRequest = { showHandleDialog = false },
            title = { Text("變更好友代碼") },
            text = {
                com.bandary.presentation.ui.components.DialogBody(spacing = 8.dp) {
                    Text(
                        "好友代碼是別人加你時輸入的 ID，全站唯一，只能用英文 / 數字 / 底線 3–20 字。" +
                            "它不是你的名字——別人看到的名字是暱稱，在「我的」頁面改。改了之後，舊代碼就會釋出給別人用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        // 邊打邊過濾成合法字元，避免使用者打了中文才發現不能存。
                        value = handle,
                        onValueChange = { input ->
                            handle = input.removePrefix("@")
                                .lowercase()
                                .filter { it in 'a'..'z' || it in '0'..'9' || it == '_' }
                                .take(20)
                        },
                        label = { Text("好友代碼") },
                        prefix = { Text("@") },
                        singleLine = true,
                        isError = handle.isNotBlank() && !valid,
                        supportingText = if (handle.isNotBlank() && !valid) {
                            { Text("需 3–20 字，僅限英文、數字與底線") }
                        } else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { if (valid) { viewModel.setHandle(normalized); showHandleDialog = false } },
                    enabled = valid
                ) { Text("儲存") }
            },
            dismissButton = { TextButton(onClick = { showHandleDialog = false }) { Text("取消") } }
        )
    }
}

/**
 * 我在好友圈裡的身分卡。
 *
 * 全 App 只有一個名字：「我的」頁設定的暱稱。這張卡把它放在最上面且唯讀。
 * 下面的好友代碼不是第二個名字，是別人搜尋你用的 ID：登入後自動配一組，
 * 想改成好記的也可以，但改它不會改變別人看到的名字。
 */
@Composable
private fun MyIdentityCard(
    handle: String?,
    displayName: String,
    discoverable: Boolean,
    shareSongs: Boolean,
    onEditHandle: () -> Unit,
    onToggleDiscoverable: (Boolean) -> Unit,
    onToggleShareSongs: (Boolean) -> Unit
) {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // 暱稱是全 App 唯一的名字，這裡唯讀：兩個地方都能改同一個東西只會讓人搞混。
            Column {
                Text("我的暱稱", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "好友、排行榜、分享圖看到的都是這個名字。要改請到「我的」→ 點頭像旁的鉛筆。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            // 好友代碼：系統配發，只是別人搜尋你的 ID，不是名字，所以只給複製 / 分享。
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("我的好友代碼", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        handle?.takeIf { it.isNotBlank() }?.let { "@$it" } ?: "配發中…",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (handle.isNullOrBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "把它給朋友，對方就能加你。可以改成好記的。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val code = handle?.takeIf { it.isNotBlank() }
                IconButton(onClick = onEditHandle, enabled = code != null) {
                    Icon(Icons.Outlined.Edit, contentDescription = "變更好友代碼")
                }
                IconButton(
                    onClick = {
                        code ?: return@IconButton
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("好友代碼", "@" + code))
                        Toast.makeText(context, "已複製 @" + code, Toast.LENGTH_SHORT).show()
                    },
                    enabled = code != null
                ) { Icon(Icons.Outlined.ContentCopy, contentDescription = "複製好友代碼") }
                IconButton(
                    onClick = {
                        code ?: return@IconButton
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                android.content.Intent.EXTRA_TEXT,
                                "在聽團曆加我好友，我的好友代碼是 @" + code
                            )
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "分享好友代碼"))
                    },
                    enabled = code != null
                ) { Icon(Icons.Outlined.IosShare, contentDescription = "分享好友代碼") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("允許用 email 搜尋我", style = MaterialTheme.typography.bodyMedium)
                    Text("關閉時，別人只能用好友代碼加你。", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = discoverable, onCheckedChange = onToggleDiscoverable)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("分享我的年度歌單", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "只分享今年的統計與前幾名歌曲／藝人，不含每天的備忘與日期。關閉會把已分享的收回。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = shareSongs, onCheckedChange = onToggleShareSongs)
            }
        }
    }
}

@Composable
private fun RequestRow(request: FriendRequest, onAccept: () -> Unit, onDecline: () -> Unit) {
    val title = request.fromHandle.takeIf { it.isNotBlank() }?.let { "@$it" }
        ?: request.fromDisplayName.ifBlank { "某使用者" }
    Card(
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
                modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(title.removePrefix("@").take(1).uppercase(), fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(request.fromDisplayName.ifBlank { title }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text("$title 想加你為好友", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onAccept) {
                Icon(Icons.Outlined.Check, "接受", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDecline) {
                Icon(Icons.Outlined.Close, "婉拒", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FriendRow(friend: Friend, onClick: () -> Unit, onRemove: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
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
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    friend.displayName.take(1).uppercase(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(friend.displayName.ifBlank { friend.handle.takeIf { it.isNotBlank() }?.let { "@$it" } ?: "好友" },
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                val sub = friend.handle.takeIf { it.isNotBlank() }?.let { "@$it" } ?: friend.email
                if (sub.isNotBlank()) {
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // 整列本來就點得進好友的日曆，但畫面上沒說，等於沒人找得到。
                Text(
                    "點一下看他的日曆",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Delete, "移除好友", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
