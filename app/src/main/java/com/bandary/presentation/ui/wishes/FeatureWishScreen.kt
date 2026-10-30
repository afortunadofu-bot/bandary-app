package com.bandary.presentation.ui.wishes

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bandary.data.local.SettingsDataStore
import com.bandary.data.remote.FirestoreService
import com.bandary.domain.model.FeatureWish
import com.bandary.domain.model.WishComment
import com.bandary.domain.model.WishStatus
import com.bandary.presentation.ui.components.EmptyState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FeatureWishViewModel @Inject constructor(
    private val firestore: FirestoreService,
    private val settings: SettingsDataStore
) : ViewModel() {

    private val _wishes = MutableStateFlow<List<FeatureWish>>(emptyList())
    val wishes: StateFlow<List<FeatureWish>> = _wishes

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    val isAdmin: StateFlow<Boolean> = MutableStateFlow(false).also { flow ->
        viewModelScope.launch { settings.isAdmin.collect { flow.value = it } }
    }

    fun isSignedIn(): Boolean = firestore.isSignedIn()
    fun myUid(): String? = firestore.currentUid()

    fun refresh() = viewModelScope.launch {
        _loading.value = true
        firestore.fetchWishes()
            .onSuccess { _wishes.value = it }
            .onFailure { _message.value = "讀取失敗：${it.message}" }
        _loading.value = false
    }

    fun submit(title: String, detail: String, appVersion: String?) = viewModelScope.launch {
        _loading.value = true
        val nickname = settings.nickname.first()
        firestore.submitWish(title, detail, nickname, appVersion)
            .onSuccess { _message.value = "已送出，謝謝你的許願" }
            .onFailure { _message.value = "送出失敗：${it.message}" }
        _loading.value = false
        refresh()
    }

    /**
     * 按讚。先在本機把那一列改掉再打雲端：等一次來回才變色會讓人以為沒按到，
     * 失敗時再把畫面重新讀回來（以雲端為準）。
     */
    fun toggleLike(wish: FeatureWish) = viewModelScope.launch {
        val next = !wish.likedByMe
        _wishes.value = _wishes.value.map {
            if (it.id == wish.id) it.copy(
                likedByMe = next,
                likeCount = (it.likeCount + if (next) 1 else -1).coerceAtLeast(0)
            ) else it
        }
        firestore.toggleWishLike(wish.id, next).onFailure {
            _message.value = "按讚失敗：${it.message}"
            refresh()
        }
    }

    fun delete(wish: FeatureWish) = viewModelScope.launch {
        firestore.deleteWish(wish.id)
            .onSuccess { _message.value = "已刪除" }
            .onFailure { _message.value = "刪除失敗：${it.message}" }
        refresh()
    }

    /** 置頂 / 取消置頂（管理員）。先改本機讓清單立刻跳位，失敗再重讀。 */
    fun togglePinned(wish: FeatureWish) = viewModelScope.launch {
        val next = !wish.pinned
        firestore.setWishPinned(wish.id, next)
            .onSuccess { _message.value = if (next) "已置頂" else "已取消置頂" }
            .onFailure { _message.value = "置頂失敗：${it.message}" }
        refresh()
    }

    fun setStatus(wish: FeatureWish, status: WishStatus) = viewModelScope.launch {
        firestore.setWishStatus(wish.id, status)
            .onFailure { _message.value = "更新失敗：${it.message}" }
        refresh()
    }

    // ── 留言 ─────────────────────────────────────────────────────
    // wishId → 留言清單。展開時才讀、讀過就留著：許願區一頁可能十幾則，
    // 一進畫面就把每則的留言都撈下來是白花流量，多數人只會點開一兩則。

    private val _comments = MutableStateFlow<Map<String, List<WishComment>>>(emptyMap())
    val comments: StateFlow<Map<String, List<WishComment>>> = _comments

    private val _commentsLoading = MutableStateFlow<Set<String>>(emptySet())
    val commentsLoading: StateFlow<Set<String>> = _commentsLoading

    fun loadComments(wishId: String, force: Boolean = false) = viewModelScope.launch {
        if (!force && _comments.value.containsKey(wishId)) return@launch
        _commentsLoading.value = _commentsLoading.value + wishId
        firestore.fetchWishComments(wishId)
            .onSuccess { _comments.value = _comments.value + (wishId to it) }
            .onFailure { _message.value = "讀取留言失敗：${it.message}" }
        _commentsLoading.value = _commentsLoading.value - wishId
    }

    fun addComment(wishId: String, text: String) = viewModelScope.launch {
        val nickname = settings.nickname.first()
        firestore.addWishComment(wishId, text, nickname)
            .onSuccess {
                bumpCommentCount(wishId, 1)
                loadComments(wishId, force = true)
            }
            .onFailure { _message.value = "留言失敗：${it.message}" }
    }

    fun deleteComment(comment: WishComment) = viewModelScope.launch {
        firestore.deleteWishComment(comment.wishId, comment.id)
            .onSuccess {
                bumpCommentCount(comment.wishId, -1)
                loadComments(comment.wishId, force = true)
            }
            .onFailure { _message.value = "刪除失敗：${it.message}" }
    }

    /**
     * 留言按讚。跟許願的讚一樣先改本機再打雲端，失敗才重讀——
     * 等一次來回才變色會讓人以為沒按到。
     */
    fun toggleCommentLike(comment: WishComment) = viewModelScope.launch {
        val next = !comment.likedByMe
        _comments.value = _comments.value.mapValues { (_, list) ->
            list.map {
                if (it.id == comment.id) it.copy(
                    likedByMe = next,
                    likeCount = (it.likeCount + if (next) 1 else -1).coerceAtLeast(0)
                ) else it
            }
        }
        firestore.toggleWishCommentLike(comment.wishId, comment.id, next).onFailure {
            _message.value = "按讚失敗：${it.message}"
            loadComments(comment.wishId, force = true)
        }
    }

    /** 本機先把留言數改掉，畫面不用等重新整理。雲端那邊由 batch 一起寫。 */
    private fun bumpCommentCount(wishId: String, delta: Int) {
        _wishes.value = _wishes.value.map {
            if (it.id == wishId) it.copy(commentCount = (it.commentCount + delta).coerceAtLeast(0)) else it
        }
    }

    fun clearMessage() { _message.value = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureWishScreen(
    onBack: () -> Unit,
    viewModel: FeatureWishViewModel = hiltViewModel()
) {
    val allWishes by viewModel.wishes.collectAsState()
    val comments by viewModel.comments.collectAsState()
    val commentsLoading by viewModel.commentsLoading.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val message by viewModel.message.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()
    val signedIn = viewModel.isSignedIn()
    val myUid = viewModel.myUid()
    var showCompose by remember { mutableStateOf(false) }
    var statusTarget by remember { mutableStateOf<FeatureWish?>(null) }
    // 0 = 全部、1 = 我的許願、2 = 我按過讚。
    // 「看得到自己許願的內容」在一面公開牆上，光靠往下捲是找不到的。
    var tab by remember { mutableStateOf(0) }
    // 哪幾則的留言是展開的。
    var expanded by remember { mutableStateOf<Set<String>>(emptySet()) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val appVersion = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    }

    // authorUid 是不可為 null 的 String，所以比對成立就隱含 myUid 非 null，
    // 不需要再檢查一次（myUid 為 null 時每一筆都不相等，結果一樣是 0）。
    val mineCount = allWishes.count { it.authorUid == myUid }
    val likedCount = allWishes.count { it.likedByMe }
    val wishes = remember(allWishes, tab, myUid) {
        when (tab) {
            1 -> allWishes.filter { myUid != null && it.authorUid == myUid }
            2 -> allWishes.filter { it.likedByMe }
            else -> allWishes
        }
    }

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
                title = { Text("功能許願區", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            if (signedIn) {
                // navigationBarsPadding：Scaffold 的 FAB 會落在畫面最底，
                // 在手勢列 / 三鍵列的裝置上會被系統列蓋住一半（實機回報）。
                ExtendedFloatingActionButton(
                    onClick = { showCompose = true },
                    icon = { Icon(Icons.Outlined.Add, null) },
                    text = { Text("我要許願") },
                    modifier = Modifier.navigationBarsPadding()
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    if (signedIn) "想要什麼功能就寫進來，也可以在別人的許願底下討論——讚數高的會先做。"
                    else "登入之後就能許願、留言、也能幫別人的許願按讚。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // 篩選列：只有登入而且真的有東西可篩時才出現，不然是一排永遠 0 的按鈕。
            if (signedIn && (mineCount > 0 || likedCount > 0)) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = tab == 0,
                            onClick = { tab = 0 },
                            label = { Text("全部 ${allWishes.size}", fontSize = 12.sp) },
                            shape = RoundedCornerShape(8.dp)
                        )
                        if (mineCount > 0) {
                            FilterChip(
                                selected = tab == 1,
                                onClick = { tab = 1 },
                                label = { Text("我的許願 $mineCount", fontSize = 12.sp) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                        if (likedCount > 0) {
                            FilterChip(
                                selected = tab == 2,
                                onClick = { tab = 2 },
                                label = { Text("我按過讚 $likedCount", fontSize = 12.sp) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }
            }
            if (loading && wishes.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            if (!loading && wishes.isEmpty() && tab != 0) {
                item {
                    EmptyState(
                        title = if (tab == 1) "你還沒有許過願" else "還沒有按過讚",
                        subtitle = if (tab == 1) "點右下角寫下你想要的功能"
                        else "看到想要的功能就按個讚，讚數高的會先做"
                    )
                }
            }
            if (!loading && allWishes.isEmpty() && tab == 0) {
                item {
                    // 不給 onClick：右下角已經有「我要許願」的 FAB，
                    // EmptyState 的可點版本會畫成一張 40dp 內距的大框線卡片，
                    // 在這頁等於一個半螢幕高的空盒子加一顆重複的按鈕。
                    EmptyState(
                        title = "還沒有人許願",
                        subtitle = if (signedIn) "第一個許願的就是你，點右下角開始"
                        else "登入之後就能許願，也能幫別人的許願按讚"
                    )
                }
            }
            items(wishes, key = { it.id }) { wish ->
                WishCard(
                    wish = wish,
                    isMine = myUid != null && wish.authorUid == myUid,
                    canDelete = signedIn && (isAdmin || wish.authorUid == myUid),
                    canLike = signedIn,
                    onLike = { viewModel.toggleLike(wish) },
                    onDelete = { viewModel.delete(wish) },
                    onStatus = if (isAdmin) ({ statusTarget = wish }) else null,
                    onTogglePin = if (isAdmin) ({ viewModel.togglePinned(wish) }) else null,
                    expanded = wish.id in expanded,
                    onToggleExpand = {
                        expanded = if (wish.id in expanded) expanded - wish.id else expanded + wish.id
                        if (wish.id in expanded) viewModel.loadComments(wish.id)
                    },
                    comments = comments[wish.id],
                    commentsLoading = wish.id in commentsLoading,
                    signedIn = signedIn,
                    myUid = myUid,
                    isAdmin = isAdmin,
                    onSendComment = { viewModel.addComment(wish.id, it) },
                    onDeleteComment = { viewModel.deleteComment(it) },
                    onLikeComment = { viewModel.toggleCommentLike(it) }
                )
            }
        }
    }

    if (showCompose) {
        var title by remember { mutableStateOf("") }
        var detail by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCompose = false },
            title = { Text("我要許願") },
            text = {
                com.bandary.presentation.ui.components.DialogBody(spacing = 10.dp) {
                    Text(
                        "會公開給其他使用者看到，附上你的暱稱。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = title, onValueChange = { title = it },
                        label = { Text("想要什麼功能 *") }, singleLine = true,
                        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = detail, onValueChange = { detail = it },
                        label = { Text("想解決什麼問題（選填）") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(110.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = title.isNotBlank() && !loading,
                    onClick = { viewModel.submit(title, detail, appVersion); showCompose = false }
                ) { Text("送出") }
            },
            dismissButton = { TextButton(onClick = { showCompose = false }) { Text("取消") } }
        )
    }

    statusTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { statusTarget = null },
            title = { Text("處理狀態") },
            text = {
                Column {
                    WishStatus.entries.forEach { s ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { viewModel.setStatus(target, s); statusTarget = null }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(s.label, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.weight(1f))
                            if (s == target.status) {
                                Text("目前", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { statusTarget = null }) { Text("關閉") } }
        )
    }
}

@Composable
private fun WishCard(
    wish: FeatureWish,
    isMine: Boolean,
    canDelete: Boolean,
    canLike: Boolean,
    onLike: () -> Unit,
    onDelete: () -> Unit,
    onStatus: (() -> Unit)?,
    onTogglePin: (() -> Unit)? = null,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    comments: List<WishComment>?,
    commentsLoading: Boolean,
    signedIn: Boolean,
    myUid: String?,
    isAdmin: Boolean,
    onSendComment: (String) -> Unit,
    onDeleteComment: (WishComment) -> Unit,
    onLikeComment: (WishComment) -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // 置頂徽章：所有人看得到「這則被官方標起來了」。
                    if (wish.pinned) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.PushPin, null,
                                    modifier = Modifier.size(10.dp),
                                    tint = MaterialTheme.colorScheme.surface
                                )
                                Spacer(Modifier.size(3.dp))
                                Text(
                                    "官方置頂", fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.surface
                                )
                            }
                        }
                    }
                    if (onTogglePin != null) {
                        TextButton(
                            onClick = onTogglePin,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) { Text(if (wish.pinned) "取消置頂" else "置頂", fontSize = 10.sp) }
                    }
                    if (wish.status != WishStatus.OPEN) {
                        Surface(
                            color = when (wish.status) {
                                WishStatus.DONE -> MaterialTheme.colorScheme.primary
                                WishStatus.PLANNED -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.outline
                            },
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.then(
                                if (onStatus != null) Modifier.clickable { onStatus() } else Modifier
                            )
                        ) {
                            Text(
                                wish.status.label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else if (onStatus != null) {
                        TextButton(
                            onClick = onStatus,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) { Text("設定狀態", fontSize = 10.sp) }
                    }
                }
                Text(
                    wish.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (wish.detail.isNotBlank()) {
                    Text(
                        wish.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4, overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        // 公開牆上大家多半是「匿名」，自己的那則要看得出來才找得到。
                        if (isMine) "${wish.authorName}（你）" else wish.authorName,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isMine) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isMine) FontWeight.SemiBold else FontWeight.Normal
                    )
                    if (canDelete) {
                        Spacer(Modifier.size(8.dp))
                        IconButton(onClick = { confirmDelete = true }, modifier = Modifier.size(28.dp)) {
                            Icon(
                                Icons.Outlined.Delete, "刪除",
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
            // 讚：數字在下、心在上，整塊可點
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable(enabled = canLike, onClick = onLike)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    if (wish.likedByMe) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (wish.likedByMe) "取消讚" else "按讚",
                    tint = if (wish.likedByMe) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    "${wish.likeCount}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (wish.likedByMe) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ── 留言區 ──────────────────────────────────────────────
        // 預設收起：一面牆上十幾則許願，每則都攤開留言會完全看不到全貌。
        Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp)) {
            TextButton(
                onClick = onToggleExpand,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.Comment, null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.size(5.dp))
                Text(
                    when {
                        expanded -> "收起討論"
                        wish.commentCount > 0 -> "討論 ${wish.commentCount}"
                        else -> "留言"
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (expanded) {
                if (commentsLoading && comments == null) {
                    Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                }
                comments?.forEach { c ->
                    CommentRow(
                        comment = c,
                        isMine = myUid != null && c.authorUid == myUid,
                        canDelete = signedIn && (isAdmin || c.authorUid == myUid),
                        canLike = signedIn,
                        onLike = { onLikeComment(c) },
                        onDelete = { onDeleteComment(c) }
                    )
                }
                if (comments != null && comments.isEmpty()) {
                    Text(
                        "還沒有人討論。說說你為什麼也想要這個功能，比按一個讚有用。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                if (signedIn) {
                    var draft by remember(wish.id) { mutableStateOf("") }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            placeholder = { Text("寫點什麼…", fontSize = 13.sp) },
                            shape = RoundedCornerShape(10.dp),
                            textStyle = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            enabled = draft.isNotBlank(),
                            onClick = { onSendComment(draft); draft = "" }
                        ) { Text("送出", fontSize = 13.sp) }
                    }
                } else {
                    Text(
                        "登入之後就能留言。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("刪除這則許願？") },
            text = { Text("刪除後其他人也看不到了，按過的讚與底下的討論一併消失。") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("刪除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
        )
    }
}

/**
 * 一則留言。排版刻意比許願卡輕：留言是附屬於許願的討論，
 * 做得跟主卡一樣重會讓整面牆看起來像兩層一樣大的清單。
 */
@Composable
private fun CommentRow(
    comment: WishComment,
    isMine: Boolean,
    canDelete: Boolean,
    canLike: Boolean,
    onLike: () -> Unit,
    onDelete: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isMine) "${comment.authorName}（你）" else comment.authorName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isMine) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (canDelete) {
                    Spacer(Modifier.size(6.dp))
                    IconButton(onClick = { confirmDelete = true }, modifier = Modifier.size(24.dp)) {
                        Icon(
                            Icons.Outlined.Delete, "刪除留言",
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            Text(comment.text, style = MaterialTheme.typography.bodySmall)
        }
        // 留言也能按讚：一串討論裡哪個說法最多人認同，比留言順序有用。
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(enabled = canLike, onClick = onLike)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Icon(
                if (comment.likedByMe) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = if (comment.likedByMe) "取消讚" else "按讚",
                tint = if (comment.likedByMe) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp)
            )
            if (comment.likeCount > 0) {
                Spacer(Modifier.size(3.dp))
                Text(
                    "${comment.likeCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (comment.likedByMe) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("刪除這則留言？") },
            text = { Text("刪除後其他人也看不到了。") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("刪除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
        )
    }
}
