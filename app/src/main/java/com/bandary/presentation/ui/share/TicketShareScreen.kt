package com.bandary.presentation.ui.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.bandary.data.repository.ConcertRepository
import com.bandary.domain.model.Concert
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

// 底圖 selectedBg 的特殊值：純色底。其餘＝"poster:N"（內建底圖）或圖片 URI。
private const val SOLID_BG = "solid"

// 票根顯示的藝人清單（主藝人 + 選取的其他歌手，依儲存順序，去重）
private fun concertArtists(c: Concert): List<String> =
    (listOfNotNull(c.artistName.takeIf { it.isNotBlank() }) +
        c.festivals.filter { it.isSelected }.map { it.artistName })
        .filter { it.isNotBlank() }.distinct()

private fun concertArtistsLabel(c: Concert): String =
    concertArtists(c).joinToString(" / ").ifBlank { c.artistName }

@HiltViewModel
class TicketShareViewModel @Inject constructor(
    private val repo: ConcertRepository,
    private val imageSearch: com.bandary.data.remote.ImageSearchService,
    settings: com.bandary.data.local.SettingsDataStore
) : ViewModel() {
    private val _concert = MutableStateFlow<Concert?>(null)
    val concert: StateFlow<Concert?> = _concert.asStateFlow()

    // 下載圖片＝Pro 功能：解鎖狀態供分享頁判斷（分享不受限）。
    val isPro: StateFlow<Boolean> = settings.isPro
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), false)

    private val _autoBackground = MutableStateFlow<String?>(null)
    val autoBackground: StateFlow<String?> = _autoBackground.asStateFlow()

    fun load(id: String) {
        viewModelScope.launch {
            val c = repo.getConcertById(id)
            _concert.update { c }
            if (c != null && c.photoUri.isNullOrBlank() && imageSearch.isConfigured()) {
                val q = c.artistName.ifBlank { c.name }
                val results = imageSearch.searchImages("$q 演唱會", limit = 3)
                    .ifEmpty { imageSearch.searchImages("${c.venue} 舞台 空景", limit = 3) }
                _autoBackground.update { results.firstOrNull()?.fullUrl }
            }
        }
    }

    /** 重新隨機抓一張自動底圖（使用者沒設封面時才有效）。 */
    fun refreshBackground() {
        val c = _concert.value ?: return
        if (!c.photoUri.isNullOrBlank() || !imageSearch.isConfigured()) return
        viewModelScope.launch {
            val q = c.artistName.ifBlank { c.name }
            val results = imageSearch.searchImages("$q 演唱會", limit = 6)
                .ifEmpty { imageSearch.searchImages("${c.venue} 舞台 空景", limit = 6) }
            results.shuffled().firstOrNull()?.fullUrl?.let { url ->
                _autoBackground.update { url }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketShareScreen(
    concertId: String,
    onBack: () -> Unit,
    viewModel: TicketShareViewModel = hiltViewModel()
) {
    LaunchedEffect(concertId) { viewModel.load(concertId) }
    val concert by viewModel.concert.collectAsState()
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val isProUnlocked by viewModel.isPro.collectAsState()
    // 邀請碼由 uid 推導，不需要連線也算得出來。沒登入就不印 QR。
    val inviteCode = remember {
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            ?.let { com.bandary.domain.model.InviteCode.of(it) }.orEmpty()
    }
    var isExporting by remember { mutableStateOf(false) }
    var downloadMsg by remember { mutableStateOf<String?>(null) }

    // 票根＝海報：排版樣式（5 種）＋純色底（配色）＋黑/白字，與統計分享綑綁一致。
    var style by remember { mutableStateOf(PosterRenderer.TypeStyle.GRID) }
    val themePrimaryHex = String.format("#%06X", 0xFFFFFF and MaterialTheme.colorScheme.primary.toArgb())
    var customColor by remember { mutableStateOf(themePrimaryHex) }  // 純色底
    var showColorPicker by remember { mutableStateOf(false) }
    var includeSetlist by remember { mutableStateOf(false) }         // 匯出時另附一張完整歌單圖（預設不附）

    // 預設配色＝這場卡片底色（concertBaseColorHex 已在根部莫蘭迪化，票根與卡片一致）；使用者仍可自選其他色。
    var colorInitFromConcert by remember(concertId) { mutableStateOf(false) }
    LaunchedEffect(concert) {
        if (!colorInitFromConcert && concert != null) {
            customColor = com.bandary.presentation.ui.components.concertBaseColorHex(concert!!)
            colorInitFromConcert = true
        }
    }
    // 內文一律白字（票根 / 統計分享統一）。
    val textColorHex = "#FFFFFF"

    // 底圖：預設＝這場演唱會卡片顯示的同一張封面（有封面用封面，沒有就用卡片預設分配到的內建底圖那張）。
    // 使用者可改成純色、其他內建底圖、或自己上傳的照片。
    var bgTouched by remember(concertId) { mutableStateOf(false) }
    var selectedBg by remember(concertId) { mutableStateOf<String?>(null) }
    LaunchedEffect(concert) {
        if (!bgTouched && concert != null) {
            val cc = concert!!
            selectedBg = cc.photoUri?.takeIf { it.isNotBlank() }
                ?: cc.photoUris.firstOrNull { it.isNotBlank() }
                ?: PosterBackgrounds.availableIndices(context).takeIf { it.isNotEmpty() }
                    ?.let { idxs -> PosterBackgrounds.key(idxs[kotlin.math.abs(cc.id.hashCode()) % idxs.size]) }
                ?: SOLID_BG
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("票根分享", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val c = concert
            if (c != null) {
                // ── 即時預覽（PosterRenderer 直接渲染，所見即所得）──
                val previewBmp = remember(c, style, customColor, textColorHex, selectedBg, inviteCode) {
                    PosterRenderer.render(context, concertToPosterSpec(c, style, customColor, textColorHex, selectedBg, inviteCode))
                        .asImageBitmap()
                }
                Image(
                    bitmap = previewBmp,
                    contentDescription = "票根預覽",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .height(300.dp)
                        .aspectRatio(9f / 16f)
                        .clip(RoundedCornerShape(4.dp))
                )

                // ── 排版樣式：用去識別化的縮圖呈現（不寫樣式名稱）──
                Text("排版樣式", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PosterRenderer.TypeStyle.entries.forEach { ts ->
                        StyleThumb(styleItem = ts, selected = style == ts, onClick = { style = ts })
                    }
                }

                // ── 配色（純色底時的顏色）──
                Text("配色・純色底時", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ShareColors.THEME.forEach { hex ->
                        val sel = customColor.equals(hex, ignoreCase = true)
                        Box(modifier = Modifier.size(32.dp).clip(CircleShape)
                            .background(Color(AndroidColor.parseColor(hex)))
                            .border(BorderStroke(if (sel) 3.dp else 1.dp,
                                if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline), CircleShape)
                            .clickable { customColor = hex })
                    }
                    val isCustomSel = ShareColors.THEME.none { it.equals(customColor, ignoreCase = true) }
                    Box(
                        modifier = Modifier.size(32.dp).clip(CircleShape)
                            .then(
                                if (isCustomSel) Modifier.background(Color(AndroidColor.parseColor(customColor)))
                                else Modifier.background(Brush.sweepGradient(
                                    ShareColors.THEME.map { Color(AndroidColor.parseColor(it)) } + Color(AndroidColor.parseColor(ShareColors.THEME.first()))
                                ))
                            )
                            .border(BorderStroke(if (isCustomSel) 3.dp else 1.dp,
                                if (isCustomSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline), CircleShape)
                            .clickable { showColorPicker = true },
                        contentAlignment = Alignment.Center
                    ) { if (!isCustomSel) Text("＋", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold) }
                }
                if (showColorPicker) {
                    com.bandary.presentation.ui.components.ColorPickerDialog(
                        initialHex = customColor,
                        onPick = { hex -> customColor = hex; showColorPicker = false },
                        onDismiss = { showColorPicker = false }
                    )
                }

                // ── 底圖：純色 / 內建 5 張底圖 / 你上傳的演唱會照片（預設＝演唱會卡片同一張封面）──
                Text("底圖", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 純色（用上方配色的顏色）
                    BgThumb(
                        imageModel = null, solidColor = Color(AndroidColor.parseColor(customColor)), label = "純色",
                        selected = selectedBg == SOLID_BG, onClick = { selectedBg = SOLID_BG; bgTouched = true }
                    )
                    // 內建 5 張底圖
                    PosterBackgrounds.availableIndices(context).forEach { idx ->
                        val key = PosterBackgrounds.key(idx)
                        val thumb = remember(idx) { PosterBackgrounds.load(context, idx, 120, 200) }
                        BgThumb(imageModel = thumb, solidColor = null, label = null,
                            selected = selectedBg == key, onClick = { selectedBg = key; bgTouched = true })
                    }
                    // 使用者上傳的演唱會照片（封面 + 其他照片）
                    (listOfNotNull(c.photoUri) + c.photoUris).filter { it.isNotBlank() }.distinct().forEach { uri ->
                        BgThumb(imageModel = uri, solidColor = null, label = null,
                            selected = selectedBg == uri, onClick = { selectedBg = uri; bgTouched = true })
                    }
                }

                // 附上歌單：這場有記歌單時才顯示（預設關閉）。開啟後下方顯示歌單圖預覽，且匯出 / 下載會一併帶上。
                if (c.setlist.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("附上歌單", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("開啟後多分享一張歌單圖・${c.setlist.size} 首", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = includeSetlist, onCheckedChange = { includeSetlist = it })
                    }
                    if (includeSetlist) {
                        val setlistPreview = remember(c, customColor, selectedBg) {
                            buildSetlistBitmap(context, c, posterBackground(selectedBg, customColor), customColor)?.asImageBitmap()
                        }
                        if (setlistPreview != null) {
                            Text("歌單預覽", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Image(
                                bitmap = setlistPreview,
                                contentDescription = "歌單預覽",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .height(300.dp)
                                    .aspectRatio(9f / 16f)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Text(
                                "提示：部分社群 App 一次只吃第一張圖，歌單可能不會一起出現；用「下載」會把票根＋歌單兩張都存到相簿。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 匯出動作
                val doExport: () -> Unit = {
                    if (!isExporting) {
                        isExporting = true
                        scope.launch {
                            val uris = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                val ticket = renderTicketPoster(context, concertToPosterSpec(c, style, customColor, textColorHex, selectedBg, inviteCode), c.id)
                                val setlist = if (includeSetlist && c.setlist.isNotEmpty())
                                    renderSetlistImage(context, c, posterBackground(selectedBg, customColor), customColor) else null
                                listOfNotNull(ticket, setlist)
                            }
                            isExporting = false
                            if (uris.isNotEmpty()) shareImages(context, uris)
                        }
                    }
                }
                val doDownload: () -> Unit = {
                    if (!isProUnlocked) {
                        downloadMsg = "下載到相簿為 Pro 功能，可在「我的 → 付費功能」解鎖；分享不受限"
                    } else if (!isExporting) {
                        isExporting = true
                        scope.launch {
                            val savedCount = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                val stamp = System.currentTimeMillis()
                                val ticket = renderTicketPoster(context, concertToPosterSpec(c, style, customColor, textColorHex, selectedBg, inviteCode), c.id)
                                val ticketSaved = ticket?.let { com.bandary.data.util.BitmapExporter.downloadUriToGallery(context, it, "ticket_${c.id}_$stamp.png") }
                                // 歌單開關開啟時，連同歌單圖一起存到相簿（與分享一致）
                                val setlistSaved = if (includeSetlist && c.setlist.isNotEmpty()) {
                                    renderSetlistImage(context, c, posterBackground(selectedBg, customColor), customColor)
                                        ?.let { com.bandary.data.util.BitmapExporter.downloadUriToGallery(context, it, "setlist_${c.id}_$stamp.png") }
                                } else null
                                listOfNotNull(ticketSaved, setlistSaved).size
                            }
                            isExporting = false
                            downloadMsg = when {
                                savedCount >= 2 -> "已下載票根 + 歌單到相簿"
                                savedCount == 1 -> "已下載到相簿"
                                else -> "下載失敗"
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = doExport,
                        modifier = Modifier.weight(1f).height(54.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isExporting,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = null)
                        Text("分享", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(
                        onClick = doDownload,
                        modifier = Modifier.weight(1f).height(54.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isExporting
                    ) {
                        Icon(if (isProUnlocked) Icons.Outlined.Download else Icons.Outlined.Lock, contentDescription = null)
                        Text("下載", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                downloadMsg?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "解析度 1080 × 1920，存到快取後透過分享面板開啟；下載會另存一份到系統相簿。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text("讀取中…", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// 底圖縮圖：純色 / Bitmap / 圖片 URI。
@Composable
private fun BgThumb(imageModel: Any?, solidColor: Color?, label: String?, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
            .then(if (solidColor != null) Modifier.background(solidColor) else Modifier.background(MaterialTheme.colorScheme.surfaceVariant))
            .border(BorderStroke(if (selected) 3.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline), RoundedCornerShape(8.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (imageModel != null) {
            AsyncImage(model = imageModel, contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
        }
        if (label != null) {
            androidx.compose.material3.Surface(color = Color.Black.copy(alpha = 0.35f), shape = RoundedCornerShape(4.dp)) {
                Text(label, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
            }
        }
    }
}

/**
 * 排版樣式縮圖：用抽象色塊示意各樣式的構圖，不寫樣式名稱。
 * 樣式名稱（格線誌／大字出血…）與代號（A/B/C…）都猜不出長什麼樣，看構圖示意最直接。
 * 統計分享頁也用同一個元件，兩邊的樣式選擇器長得一樣。
 */
@Composable
fun StyleThumb(styleItem: PosterRenderer.TypeStyle, selected: Boolean, onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    val faint = ink.copy(alpha = 0.4f)
    Box(
        modifier = Modifier.size(width = 48.dp, height = 84.dp).clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(BorderStroke(if (selected) 2.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline), RoundedCornerShape(6.dp))
            .clickable { onClick() }
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(7.dp)) {
            val ww = size.width; val hh = size.height
            fun bar(x: Float, y: Float, bw: Float, bh: Float, c: Color) =
                drawRect(color = c, topLeft = Offset(x, y), size = Size(bw, bh))
            when (styleItem) {
                PosterRenderer.TypeStyle.GRID -> {
                    bar(0f, 0f, ww * 0.6f, hh * 0.06f, ink)
                    bar(ww * 0.68f, 0f, ww * 0.32f, hh * 0.16f, faint)
                    bar(0f, hh * 0.72f, ww * 0.45f, hh * 0.05f, faint)
                    bar(ww * 0.55f, hh * 0.72f, ww * 0.45f, hh * 0.05f, faint)
                }
                PosterRenderer.TypeStyle.BLEED -> {
                    bar(0f, hh * 0.05f, ww * 0.92f, hh * 0.5f, ink)
                    bar(0f, hh * 0.86f, ww * 0.7f, hh * 0.05f, faint)
                }
                PosterRenderer.TypeStyle.VERTICAL -> {
                    bar(ww * 0.76f, 0f, ww * 0.14f, hh * 0.72f, ink)
                    bar(0f, hh * 0.86f, ww * 0.5f, hh * 0.05f, faint)
                }
                PosterRenderer.TypeStyle.BILINGUAL -> {
                    bar(0f, hh * 0.24f, ww * 0.85f, hh * 0.14f, ink)
                    bar(0f, hh * 0.46f, ww * 0.6f, hh * 0.05f, faint)
                    bar(0f, hh * 0.6f, ww * 0.5f, hh * 0.04f, faint)
                }
                PosterRenderer.TypeStyle.MINIMAL -> {
                    bar(0f, hh * 0.45f, ww * 0.8f, hh * 0.06f, ink)
                    bar(0f, hh * 0.58f, ww * 0.4f, hh * 0.04f, faint)
                }
            }
        }
    }
}

/**
 * 把一場演唱會轉成 PosterRenderer 的 Spec（票根＝海報）：
 * 純色底（配色）+ 黑/白內文；資訊區塊（時間 / 座位 / 售票 / 歌單 / 多藝人）依排版樣式的可用空間適時呈現。
 */
/** 由 selectedBg 推出票根底圖：純色 / 內建底圖(poster:N) / 照片；null 視為純色。歌單圖也用它，確保底圖一模一樣。 */
private fun posterBackground(selectedBg: String?, bgColorHex: String): PosterRenderer.Background = when {
    selectedBg == null || selectedBg == SOLID_BG -> PosterRenderer.Background.Solid(bgColorHex)
    PosterBackgrounds.indexOfKey(selectedBg) != null ->
        PosterRenderer.Background.Preset(PosterBackgrounds.indexOfKey(selectedBg)!!)
    else -> PosterRenderer.Background.Photo(selectedBg)
}

private fun concertToPosterSpec(
    concert: Concert,
    style: PosterRenderer.TypeStyle,
    bgColorHex: String,
    textColorHex: String,
    selectedBg: String?,
    /** 邀請碼；空字串＝不在票根上印 QR（沒登入時就是這樣）。 */
    inviteCode: String = ""
): PosterRenderer.Spec {
    val background = posterBackground(selectedBg, bgColorHex)
    val dt = concert.dateTime
    fun p2(n: Int) = n.toString().padStart(2, '0')
    val monthEng = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
        .getOrElse(dt.monthNumber - 1) { "" }
    val day = p2(dt.dayOfMonth)
    val fullDate = "${dt.year}.${p2(dt.monthNumber)}.$day"  // 底部資訊條顯示日期（時間放進資訊區塊）
    val artists = concertArtists(concert)
    val artistLabel = concertArtistsLabel(concert)
    val title = concert.name.ifBlank { artistLabel }
    // 藝人一律顯示（副標）；若標題本來就是藝人（沒有場名）就不重複。
    val subtitle = artistLabel.takeIf { it.isNotBlank() && it != title }

    // 資訊區塊：時間 / 座位 / 售票。容量大的樣式多顯示、簡潔樣式只顯示前一兩行。
    val infoLines = buildList {
        add(buildString {
            append("${p2(dt.hour)}:${p2(dt.minute)} 開演")
            concert.endTime?.let { append("  –  ${p2(it.hour)}:${p2(it.minute)}") }
        })
        concert.seatInfo?.takeIf { it.isNotBlank() }?.let { add("座位　$it") }
        concert.ticketPlatform?.takeIf { it.isNotBlank() }?.let { add("售票　$it") }
    }
    val songs = concert.setlist.map { it.trim() }.filter { it.isNotBlank() }
    // 順序＝資訊 / 演出 / 歌單：GRID 會把 [0]資訊放左、[1]演出放右。
    val blocks = buildList {
        if (infoLines.isNotEmpty()) add(PosterRenderer.Block("資訊", infoLines))
        if (artists.size > 1) add(PosterRenderer.Block("演出", artists))
        if (songs.isNotEmpty()) add(PosterRenderer.Block("歌單", songs.take(8)))
    }
    return PosterRenderer.Spec(
        background = background,
        accentHex = textColorHex,        // 單色字：標題 / 日期大字也用內文色（黑或白）
        style = style,
        title = title,
        subtitle = subtitle,
        day = day,
        month = monthEng,
        fullDate = fullDate,
        venue = concert.venue,
        city = concert.city,
        blocks = blocks,
        textColorHex = textColorHex,
        // 票根本來就是大家最愛轉發的圖，把邀請 QR 印在上面比另外做一張分享圖自然得多。
        qrPayload = inviteCode.takeIf { it.isNotBlank() }?.let { com.bandary.domain.model.InviteCode.shareUrl(it) },
        qrCaption = inviteCode.takeIf { it.isNotBlank() }
    )
}

private fun renderTicketPoster(context: Context, spec: PosterRenderer.Spec, concertId: String): Uri? = runCatching {
    val bitmap = PosterRenderer.render(context, spec)
    val dir = File(context.cacheDir, "shares").apply { mkdirs() }
    val file = File(dir, "ticket_${concertId}_${spec.style.name}.png")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}.getOrNull()

/**
 * 完整歌單獨立成一張圖：底圖與票根一模一樣（純色 / 內建底圖 / 照片），標題 + 編號歌曲。
 * 歌曲太多時自動分成 2–3 欄（換新區塊），過長的歌名以 … 截斷，避免字被縮到看不見。
 */
private fun buildSetlistBitmap(context: Context, concert: Concert, background: PosterRenderer.Background, accentHex: String): Bitmap? = runCatching {
    val songs = concert.setlist.map { it.trim() }.filter { it.isNotBlank() }
    if (songs.isEmpty()) return null
    val w = 1080; val h = 1920
    // 底圖：走票根同一套 PosterRenderer 背景邏輯，並取得建議文字色。
    val (bitmap, onBg) = PosterRenderer.renderBackgroundBitmap(context, background, w, h, accentHex)
    val canvas = Canvas(bitmap)
    val gAccent = onBg
    val tx = 60f
    fun paint(size: Float, bold: Boolean, color: Int) = Paint().apply {
        this.color = color; textSize = size; isAntiAlias = true
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }
    fun ellipsize(text: String, p: Paint, maxWidth: Float): String {
        val b = Rect()
        p.getTextBounds(text, 0, text.length, b)
        if (b.width() <= maxWidth) return text
        var end = text.length - 1
        while (end > 0) {
            val t = text.substring(0, end) + "…"
            p.getTextBounds(t, 0, t.length, b)
            if (b.width() <= maxWidth) return t
            end--
        }
        return "…"
    }
    // 品牌標 + 標題
    canvas.drawText("BANDARY", tx, 120f, Paint().apply {
        color = gAccent; textSize = 44f; isAntiAlias = true; textAlign = Paint.Align.LEFT
        letterSpacing = 0.22f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    })
    canvas.drawText("SETLIST", tx, 240f, paint(72f, true, gAccent))
    drawWrappedText(canvas, concert.name.ifBlank { concertArtistsLabel(concert) }, tx, 320f, w - 120f, paint(38f, true, ColorUtils.setAlphaComponent(onBg, 0xE6)))
    // 歌曲區：先決定欄數（每欄目標行高 ~54，超過就多開一欄，最多 3 欄）。
    val startY = 430f; val endY = 1820f
    val areaH = endY - startY
    val targetLine = 54f
    val rowsPerColTarget = (areaH / targetLine).toInt().coerceAtLeast(1)
    val numCols = kotlin.math.ceil(songs.size.toDouble() / rowsPerColTarget).toInt().coerceIn(1, 3)
    val rowsPerCol = kotlin.math.ceil(songs.size.toDouble() / numCols).toInt().coerceAtLeast(1)
    val lineHeight = (areaH / rowsPerCol).coerceIn(34f, 78f)
    val fontSize = (lineHeight * 0.5f).coerceIn(20f, 40f)
    val numPaint = paint(fontSize, true, gAccent)
    val songPaint = paint(fontSize, false, ColorUtils.setAlphaComponent(onBg, 0xE6))
    val headerPaint = paint(fontSize * 1.05f, true, gAccent)
    val colGap = 36f
    val colW = (w - tx * 2 - (numCols - 1) * colGap) / numCols
    val numOffset = fontSize * 1.9f
    val prefix = com.bandary.presentation.ui.shows.SETLIST_ARTIST_PREFIX
    val sectionPrefix = com.bandary.presentation.ui.shows.SETLIST_SECTION_PREFIX
    var n = 0
    songs.forEachIndexed { i, line ->
        val col = i / rowsPerCol
        val row = i % rowsPerCol
        val cx = tx + col * (colW + colGap)
        val y = startY + lineHeight * (row + 1)
        when {
            line.startsWith(prefix) -> {
                n = 0
                canvas.drawText(ellipsize(line.removePrefix(prefix), headerPaint, colW), cx, y, headerPaint)
            }
            line.startsWith(sectionPrefix) -> {
                n = 0
                canvas.drawText(ellipsize("— ${line.removePrefix(sectionPrefix)} —", headerPaint, colW), cx, y, headerPaint)
            }
            else -> {
                n++
                canvas.drawText("$n.", cx, y, numPaint)
                canvas.drawText(ellipsize(line, songPaint, colW - numOffset), cx + numOffset, y, songPaint)
            }
        }
    }
    bitmap
}.getOrNull()

/** 歌單圖存成檔並回傳分享用 Uri。 */
private fun renderSetlistImage(context: Context, concert: Concert, background: PosterRenderer.Background, accentHex: String): Uri? = runCatching {
    val bitmap = buildSetlistBitmap(context, concert, background, accentHex) ?: return null
    val dir = File(context.cacheDir, "shares").apply { mkdirs() }
    val file = File(dir, "setlist_${concert.id}.png")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}.getOrNull()

private fun drawWrappedText(canvas: Canvas, text: String, x: Float, startY: Float, maxWidth: Float, paint: Paint) {
    if (text.isBlank()) return
    val words = text.split(" ")
    var line = ""
    var y = startY
    val lineHeight = paint.textSize * 1.1f
    for (word in words) {
        val test = if (line.isEmpty()) word else "$line $word"
        val bounds = Rect()
        paint.getTextBounds(test, 0, test.length, bounds)
        if (bounds.width() > maxWidth && line.isNotEmpty()) {
            canvas.drawText(line, x, y, paint)
            line = word
            y += lineHeight
        } else line = test
    }
    if (line.isNotEmpty()) canvas.drawText(line, x, y, paint)
}

private fun shareImage(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(intent, "分享票根")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}

/** 一次分享多張圖（票根 + 歌單）。 */
private fun shareImages(context: Context, uris: List<Uri>) {
    if (uris.size == 1) { shareImage(context, uris.first()); return }
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "image/png"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(intent, "分享票根 + 歌單")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}
