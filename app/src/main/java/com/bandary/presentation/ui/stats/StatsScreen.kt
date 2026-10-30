package com.bandary.presentation.ui.stats

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.bandary.data.repository.ConcertRepository
import com.bandary.data.util.BitmapExporter
import com.bandary.domain.model.Concert
import com.bandary.domain.model.ConcertType
import com.bandary.domain.model.typeLabel
import com.bandary.domain.model.DailySong
import com.bandary.domain.model.TicketStatus
import com.bandary.presentation.ui.components.EmptyState
import com.bandary.presentation.ui.components.ListScrubber
import com.bandary.presentation.ui.components.swipeHorizontalPage
import com.bandary.presentation.ui.components.SectionHeader
import com.bandary.presentation.ui.theme.BandaryColors
import com.bandary.presentation.ui.theme.Radius
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.daysUntil
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import kotlin.math.roundToInt

// ── Models ────────────────────────────────────────────────────
enum class StatsPeriod(val label: String) {
    MONTH("本月"), QUARTER("本季"), YEAR("今年"), ALL("全部")
}

// KPI 那行最多列幾位並列第一的藝人，超過就寫「等 N 位」。
private const val TIED_NAME_LIMIT = 3

data class StatsBand(val artistName: String, val count: Int, val total: Int)
data class VenueCount(val venue: String, val count: Int)
data class ArtistSpend(val name: String, val amount: Double)

data class StatsData(
    val totalShows: Int = 0,
    val totalSpent: Double = 0.0,
    val totalArtists: Int = 0,
    val totalVenues: Int = 0,
    val freeCount: Int = 0,
    val avgRating: Double = 0.0,
    val travelKm: Int = 0,
    val farthestCity: String? = null,
    val baseCity: String = "臺北",
    val busiestMonth: Int? = null,
    val topVenue: String? = null,
    val monthly: List<Pair<Int, Int>> = emptyList(),
    val bands: List<StatsBand> = emptyList(),
    val venues: List<VenueCount> = emptyList(),
    val artistSpend: List<ArtistSpend> = emptyList(),  // 每位藝人花費（音樂祭算原價、不均分）
    val platforms: List<Pair<String, Int>> = emptyList(),  // 售票平台分布
    val cities: List<Pair<String, Int>> = emptyList(),   // 縣市分布
    val monthlySpend: List<Pair<Int, Int>> = emptyList(),  // 每月花費（月 → 金額）
    val companions: List<Pair<String, Int>> = emptyList(), // 最常一起去的同行者（名 → 次數）
    val revisitRate: Int = 0,          // 藝人回訪率：看過 ≥2 次的藝人占比 %
    val revisitedArtists: Int = 0,     // 看過 ≥2 次的藝人數
    val priceBuckets: List<Pair<String, Int>> = emptyList(), // 票價分布（區間 → 場次數）
    val yearHeat: List<YearHeat> = emptyList(),        // 年 × 月 熱力圖（每年 12 個月的場次數）
    val weekdayCounts: List<Int> = emptyList(),        // 星期一..日 的場次數（共 7 格）
    val cumulativeTimeline: List<Pair<String, Int>> = emptyList(), // 累積演唱會（yyyy/MM → 到當月為止累積場數）
    val monthlyTimeline: List<Pair<String, Int>> = emptyList(),    // 每月場次（yyyy/MM → 當月場數，與 cumulativeTimeline 同鍵）
    val cumulativeSpendTimeline: List<Pair<String, Int>> = emptyList(), // 累積花費（yyyy/MM → 到當月為止累積金額）
    val monthlySpendTimeline: List<Pair<String, Int>> = emptyList(),    // 每月花費（yyyy/MM → 當月金額，與累積花費同鍵）
    val yearlyCounts: List<Pair<String, Int>> = emptyList(),       // 各年份場次
    val typeBreakdown: List<Pair<String, Int>> = emptyList(),      // 演出類型分布
    val countries: List<Pair<String, Int>> = emptyList(),          // 國家分布
    val artistMinutes: List<Pair<String, Int>> = emptyList(),      // 每位藝人累積觀看時長（分鐘）
    val platformSpend: List<Pair<String, Int>> = emptyList(),      // 每個售票平台累積花費
    val yearlyMinutes: List<Pair<String, Int>> = emptyList(),      // 每年在現場的總時數（分鐘）
    val totalAttendance: Int = 0,                                  // 總人次＝場次 × 每場同行人數（含自己）
    val firstShow: LocalDate? = null,  // 這批已看完場次的最早 / 最晚日期（分享「全部時間」時當時間依據）
    val lastShow: LocalDate? = null
)

/** 熱力圖一列：某一年 12 個月各自的場次數。 */
data class YearHeat(val year: Int, val counts: List<Int>)

enum class ShareTemplate(val label: String) {
    MIDNIGHT("莫蘭迪灰"), SUNSET("莫蘭迪棕"), FOREST("莫蘭迪綠")
}

// Wrapped 數字排版
// 排版：一律靠左（呼應票根），差別在資訊塊置頂或置底。
enum class StatsLayout(val label: String) {
    TOP("左上對齊"), BOTTOM("左下對齊")
}

// 分享內容區塊（可自由組合）
enum class ShareContent(val label: String) {
    OVERVIEW("現場總覽"),   // 場次 / 藝人數 / 場館數 / 花費 / 交通
    ARTISTS("最愛藝人"),    // 最常看的藝人 TOP
    VENUES("常去場館"),     // 常去場館 TOP
    SONGS("喜歡的歌"),      // 選歌數 + 最常選的歌 + 最愛藝人（選歌）
    TIMELINE("看團＋選歌時間軸")  // 每場演出之後選了哪些歌，取代脫離場次的排行榜
}

data class SharePreset(val label: String, val content: Set<ShareContent>)

// 幾種預設組合
val SHARE_PRESETS = listOf(
    SharePreset("現場回顧", setOf(ShareContent.OVERVIEW, ShareContent.ARTISTS, ShareContent.VENUES)),
    SharePreset("音樂品味", setOf(ShareContent.SONGS, ShareContent.ARTISTS)),
    SharePreset("聽團時間軸", setOf(ShareContent.TIMELINE)),
    SharePreset("完整", ShareContent.entries.toSet()),
    SharePreset("只有數字", setOf(ShareContent.OVERVIEW))
)

/**
 * 看團＋選歌時間軸：每場演出之後（到下一場之前）選了哪些歌，取代脫離場次的排行榜。
 * 用日期區間歸屬，不強求 DailySong.concertId 有連結（那個要使用者手動選才會有，涵蓋率太低）。
 * 只留「之後真的有選歌」的場次——沒有歌可配的場次放進時間軸沒有意義，直接略過。
 * 由新到舊，最多取 [limit] 場給海報用（版面有限）。
 */
private fun buildConcertSongTimeline(
    concerts: List<Concert>,
    songs: List<DailySong>,
    limit: Int = 4
): List<Pair<Concert, List<DailySong>>> {
    val sorted = concerts.sortedBy { it.dateTime.date }
    return sorted.mapIndexedNotNull { i, c ->
        val start = c.dateTime.date
        val end = sorted.getOrNull(i + 1)?.dateTime?.date ?: start.plus(14, DateTimeUnit.DAY)
        val after = songs.filter { it.date >= start && it.date < end }.sortedBy { it.date }
        if (after.isEmpty()) null else c to after
    }.sortedByDescending { it.first.dateTime }.take(limit)
}

/** 時間軸單行文字："8/15 告五人 → 入陣曲、擁抱、如煙（+2）"。最多列 3 首，其餘用 +N 收攏。 */
private fun formatTimelineLine(c: Concert, songs: List<DailySong>): String {
    val date = "${c.dateTime.date.monthNumber}/${c.dateTime.date.dayOfMonth}"
    val who = c.artistName.ifBlank { c.name }
    val titles = songs.map { it.songTitle }.distinct()
    val shown = titles.take(3).joinToString("、")
    val more = (titles.size - 3).let { if (it > 0) "（+$it）" else "" }
    return "$date $who → $shown$more"
}

// 分享配色跟隨 App 主題四色（橄欖綠 / 暖灰 / 霧粉 / 霧藍，同 Theme.kt 的 ThemePalette 四色）。
private val THEME_SHARE_COLORS = listOf("#6B9080", "#8C8B85", "#CE8BA6", "#6F9BC4")

// 數據卡的低飽和色調盤：讓一整排數據卡不再一片灰、依欄目上色（重點）。

// CIS 2026-08：跟網頁版／App 圖示同一組橄欖綠色階，色號見 web/about.html 的 --c1~--c6
// （Oklab 算出來的，同一色相 H≈127° 只變色相角度，不是隨手挑的）。這裡跟 Theme.kt 的
// GREEN_PALETTE 無關，是 Stats 頁自己獨立的配色，換這裡不影響 App 的 Material 主題色。
//
// ## 一套顏色不夠，但五套到處用更糟
// 這一頁的顏色分成兩種用途，混用是之前最大的問題：
//
//  1. **資料系列色** [STAT_SERIES]：甜甜圈的每一塊、文字雲的名次、兩條要對比的曲線。
//     這裡顏色**真的在表達東西**（「這一塊是音樂節」），所以必須彼此可分辨。
//  2. **強調色** [STAT_ACCENT]：單一張卡的色條、圖表的柱子、進度圈。
//     這裡只有一個系列，顏色沒有要區分什麼——**所以就用主色一個**。
//
// 以前兩種用途共用同一個 5 色色盤，結果是：並排的「藝人 / 場館」一個綠一個藍、
// 柱狀圖是粉紅、復訪率是霧藍、價格分布是棕色——**全部都是裝飾，沒有一個在表達資料**。
// 整頁因此看起來像色票本，而不像一個產品。
private val STAT_ACCENT = Color(0xFF6B9080)   // 沙綠，與主題主色同一支

/**
 * 資料系列色：**只給「同一張圖裡要分辨好幾個系列」的地方用。**
 *
 * 色相刻意拉開（綠 / 藍 / 粉 / 棕 / 紫）才分得出來，但明度與彩度壓在同一個莫蘭迪
 * 調性上，所以放在一起仍然是一家人。想給單一張卡上色的話，請用 [STAT_ACCENT]。
 */
private val STAT_SERIES = listOf(
    Color(0xFF6B9080), // 莫蘭迪綠
    Color(0xFF6F9BC4), // 霧藍
    Color(0xFFCE8BA6), // 藕粉
    Color(0xFFB08968), // 暖棕
    Color(0xFF8C8BB0)  // 藕紫
)

/**
 * 區塊卡的強調色。**不管標題是什麼，一律回主色。**
 *
 * 原本是 `STAT_ACCENTS[abs(label.hashCode()) % size]`——依標題雜湊挑色。
 * 那個做法的根本問題不是「挑得不夠均勻」，而是**它挑的顏色不代表任何事**：
 * 把「各年份場次」改名成「年份場次」，整張卡換個顏色，但它表達的東西一模一樣。
 * 顏色既然跟內容無關，就不該由內容決定——也不該存在。
 *
 * 保留這個函式（而不是把呼叫端全部改掉）是為了留住這個決定的說明：
 * 以後想再給區塊卡上色的人會先看到這段。
 */
private fun statAccent(@Suppress("UNUSED_PARAMETER") label: String): Color = STAT_ACCENT

// 分享底圖的特殊值：把本期所有演唱會封面拼成一張拼圖當底圖。
private const val COLLAGE_BG = "collage"

/**
 * 把多張封面 center-crop 拼成「填滿整個 w×h、不留空格」的拼圖（retro 照片牆風）。
 * 作法：依畫面比例挑列數讓每格盡量接近方形，再把所有封面平均分配到各列，
 * 每列自行等分該列寬度——最後一列不會出現空格，整張畫面都被照片填滿。沒有封面回 null。
 */
private fun buildCoverCollage(context: Context, uris: List<String>, w: Int, h: Int): Bitmap? {
    val covers = uris.filter { it.isNotBlank() }.take(24)
    if (covers.isEmpty()) return null
    val n = covers.size
    // 列數 ≈ sqrt(n × 高寬比)，讓每格接近方形（直式 9:16 會偏多列）。
    val rows = Math.round(Math.sqrt(n * (h.toDouble() / w.toDouble()))).toInt().coerceIn(1, n)
    // 把 n 張平均分到 rows 列：前 remainder 列各多放一張，確保每列都填滿、無空格。
    val base = n / rows
    val remainder = n % rows
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(AndroidColor.parseColor("#111111"))
    val paint = Paint().apply { isAntiAlias = true; isFilterBitmap = true }
    val rowH = h.toFloat() / rows
    var idx = 0
    for (row in 0 until rows) {
        val countInRow = base + if (row < remainder) 1 else 0
        if (countInRow <= 0) continue
        val cellW = w.toFloat() / countInRow
        val top = row * rowH
        for (col in 0 until countInRow) {
            val uri = covers.getOrNull(idx++) ?: continue
            val left = col * cellW
            val cover = loadBitmapScaled(context, uri, cellW.toInt(), rowH.toInt()) ?: continue
            val scale = maxOf(cellW / cover.width, rowH / cover.height)
            val dw = cover.width * scale; val dh = cover.height * scale
            val dl = left + (cellW - dw) / 2f; val dt = top + (rowH - dh) / 2f
            canvas.save()
            canvas.clipRect(left, top, left + cellW, top + rowH)
            canvas.drawBitmap(cover, null, RectF(dl, dt, dl + dw, dt + dh), paint)
            canvas.restore()
        }
    }
    return bmp
}

private fun loadBitmapScaled(context: Context, uri: String, reqW: Int, reqH: Int): Bitmap? = runCatching {
    fun open() = if (uri.startsWith("http")) java.net.URL(uri).openStream()
    else context.contentResolver.openInputStream(android.net.Uri.parse(uri))
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / sample > reqW.coerceAtLeast(1) * 2 || bounds.outHeight / sample > reqH.coerceAtLeast(1) * 2) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    open()?.use { BitmapFactory.decodeStream(it, null, opts) }
}.getOrNull()

data class SongRank(val name: String, val count: Int, val pct: Int)
data class SongTitleRank(val title: String, val artist: String, val count: Int)

data class SongStats(
    val totalSongs: Int = 0,
    val distinctArtists: Int = 0,
    val topArtist: String? = null,
    val topArtistCount: Int = 0,
    val daysWithSongs: Int = 0,
    val topArtists: List<SongRank> = emptyList(),   // 前幾名藝人 + 佔全部選歌百分比
    val topSongs: List<SongTitleRank> = emptyList(),  // 前幾名的歌
    val reactions: List<Pair<String, Int>> = emptyList(),  // 感受標籤分布（精彩 / 收藏 / 感動 / 難忘）
    val topAlbums: List<Pair<String, Int>> = emptyList(),  // 最常選的專輯
    val monthlySongs: List<Pair<Int, Int>> = emptyList(),  // 每月選歌數
    val avgPerActiveDay: Double = 0.0,                     // 有紀錄的日子平均每天幾首
    val longestStreak: Int = 0,                            // 最長連續紀錄天數
    val liveSongs: Int = 0,                                // 從演唱會歌單記下的（concertId 非 null）
    val weekdaySongs: List<Pair<String, Int>> = emptyList() // 週一～週日各記了幾首
)

/**
 * 成就里程碑（E2-8）。unit＝維度單位（場/組/座/縣市），threshold＝達標門檻，
 * current＝目前累積值，earned＝是否已達標。標題與副標供徽章卡與分享圖使用。
 */
data class Milestone(
    val kind: String,        // "shows" / "artists" / "venues" / "cities"
    val label: String,       // 例：看了 100 場
    val caption: String,     // 例：累積演出場次
    val threshold: Int,
    val current: Int,
    val badge: String        // 分享圖大字（例："100"）
) {
    val earned: Boolean get() = current >= threshold
}

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val concertRepo: ConcertRepository,
    private val dailySongRepo: com.bandary.data.repository.DailySongRepository,
    private val settings: com.bandary.data.local.SettingsDataStore
) : ViewModel() {

    private val tz = TimeZone.of("Asia/Taipei")

    // 下載圖片＝Pro 功能：解鎖狀態供分享面板判斷。
    val isPro: StateFlow<Boolean> = settings.isPro
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _period = MutableStateFlow(StatsPeriod.YEAR)
    val period: StateFlow<StatsPeriod> = _period.asStateFlow()

    // 期間位移：0 = 目前，-1 = 上一個週/月/季/年，+1 = 下一個
    private val _offset = MutableStateFlow(0)

    val homeBaseCity: StateFlow<String> = settings.homeBaseCity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "臺北")

    private val allConcerts: StateFlow<List<Concert>> = concertRepo.getAllConcerts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 目前檢視的期間標籤（含位移，例：2026 年 3 月 / 2026 Q1 / 3/2–3/8）
    val periodLabel: StateFlow<String> = combine(_period, _offset) { p, off ->
        windowFor(p, off, Clock.System.now().toLocalDateTime(tz).date).label
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsPeriod.YEAR.label)

    // 分享圖用的年份（依目前期間）
    val year: StateFlow<Int> = combine(_period, _offset) { p, off ->
        windowFor(p, off, Clock.System.now().toLocalDateTime(tz).date).year
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Clock.System.now().toLocalDateTime(tz).year)

    val songStats: StateFlow<SongStats> = combine(_period, _offset, dailySongRepo.getAllSongs()) { period, off, songs ->
        val today = Clock.System.now().toLocalDateTime(tz).date
        val filtered = songs.filter { inPeriod(it.date, today, period, off) }
        // 一首歌可以有多位歌手，存成同一個字串。這裡要拆開再算，
        // 否則「怕胖團 ／ 告五人」會變成一個叫那串字的假藝人，
        // 而真正的兩位各自少算一次——排行與占比就全錯了。
        val byArtist = filtered.flatMap { com.bandary.domain.model.artistNamesForStats(it.artistName) }
            .groupingBy { it }.eachCount()
        val top = byArtist.maxByOrNull { it.value }
        val total = filtered.size.coerceAtLeast(1)
        val topArtists = byArtist.entries.sortedByDescending { it.value }.take(5)
            .map { SongRank(it.key, it.value, it.value * 100 / total) }
        val bySong = filtered.filter { it.songTitle.isNotBlank() }
            .groupingBy { it.songTitle to it.artistName }.eachCount()
        // 取 10 不取 5：分享圖是「我最常聽的十首歌」，那是這個梗的標準長度；
        // 而畫面上多列五首也不會洗掉其他區塊。
        val topSongs = bySong.entries.sortedByDescending { it.value }.take(10)
            .map { SongTitleRank(it.key.first, it.key.second, it.value) }
        // 感受標籤分布（依 enum 固定順序呈現）
        val reactionCounts = filtered.mapNotNull { it.reaction }.groupingBy { it }.eachCount()
        val reactions = com.bandary.domain.model.SongReaction.entries
            .map { it.label to (reactionCounts[it] ?: 0) }.filter { it.second > 0 }
        val topAlbums = filtered.mapNotNull { it.albumName?.takeIf { a -> a.isNotBlank() } }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(5).map { it.key to it.value }
        val monthlySongs = (1..12).map { m -> m to filtered.count { it.date.monthNumber == m } }
        val distinctDays = filtered.map { it.date }.distinct().sorted()
        val avgPerActiveDay = if (distinctDays.isNotEmpty()) filtered.size.toDouble() / distinctDays.size else 0.0
        // 最長連續紀錄天數
        var longest = 0; var cur = 0; var prev: kotlinx.datetime.LocalDate? = null
        distinctDays.forEach { d ->
            cur = if (prev != null && prev!!.daysUntil(d) == 1) cur + 1 else 1
            if (cur > longest) longest = cur; prev = d
        }
        // 星期分布：看得出「週末才有空聽歌」還是每天都聽。DayOfWeek 的 ordinal 是週一＝0。
        val weekLabels = listOf("一", "二", "三", "四", "五", "六", "日")
        val weekdaySongs = weekLabels.mapIndexed { i, label ->
            label to filtered.count { it.date.dayOfWeek.ordinal == i }
        }
        SongStats(
            totalSongs = filtered.size,
            distinctArtists = byArtist.size,
            topArtist = top?.key,
            topArtistCount = top?.value ?: 0,
            daysWithSongs = filtered.map { it.date }.distinct().size,
            topArtists = topArtists,
            topSongs = topSongs,
            reactions = reactions,
            topAlbums = topAlbums,
            monthlySongs = monthlySongs,
            avgPerActiveDay = avgPerActiveDay,
            longestStreak = longest,
            liveSongs = filtered.count { it.concertId != null },
            weekdaySongs = weekdaySongs
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SongStats())

    // 時間軸分享卡用：跟 data 同一套區間篩選，但保留原始 Concert（data 只留聚合後的統計）。
    val periodConcerts: StateFlow<List<Concert>> = combine(_period, _offset, allConcerts) { period, off, all ->
        val today = Clock.System.now().toLocalDateTime(tz).date
        all.filter { inPeriod(it.dateTime.date, today, period, off) && it.lastOccurrence() < today }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val data: StateFlow<StatsData> = combine(_period, _offset, allConcerts, settings.homeBaseCity) { period, off, all, base ->
        val today = Clock.System.now().toLocalDateTime(tz).date
        // 只計已看完的表演：演出日期（多日以結束日為準）< 今天，未來場次不進統計。
        val filtered = all.filter { inPeriod(it.dateTime.date, today, period, off) && it.lastOccurrence() < today }
        // 回訪率用：此區間開始日「之前」就看過的藝人集合（全部時間＝null）。
        val winStart = windowFor(period, off, today).start
        val priorArtists = winStart?.let { ws ->
            all.filter { it.lastOccurrence() < ws }.flatMap { artistNamesOf(it) }.toSet()
        }
        computeStats(filtered, base, priorArtists)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsData())

    // 成就里程碑（E2-8）：以「已看完的所有場次」為準（不受目前期間篩選影響）。
    val milestones: StateFlow<List<Milestone>> = allConcerts.map { all ->
        val today = Clock.System.now().toLocalDateTime(tz).date
        val done = all.filter { it.lastOccurrence() < today }
        val shows = done.size
        val artists = done.flatMap { artistNamesOf(it) }.map { it.trim() }.filter { it.isNotBlank() }.toSet().size
        val venues = done.mapNotNull { it.venue?.trim() }.filter { it.isNotBlank() }.toSet().size
        val cities = done.mapNotNull { it.city?.trim() }.filter { it.isNotBlank() }.toSet().size
        fun tiersFor(kind: String, caption: String, current: Int, tiers: List<Int>, verb: String, unit: String): List<Milestone> =
            tiers.map { t -> Milestone(kind, "$verb $t $unit", caption, t, current, "$t") }
        (tiersFor("shows", "累積演出場次", shows, listOf(1, 10, 50, 100, 200, 300), "看了", "場") +
            tiersFor("artists", "看過不同藝人", artists, listOf(5, 10, 25, 50, 100), "認識了", "組") +
            tiersFor("venues", "踏過不同場館", venues, listOf(3, 10, 20, 40), "去過", "座") +
            tiersFor("cities", "走過不同縣市", cities, listOf(3, 6, 10, 15), "走過", "個"))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 本期各演唱會的封面（一場一張，優先封面、否則取第一張照片），供分享拼圖填滿底圖。同 data 一樣只計已看完的場次。
    val periodCovers: StateFlow<List<String>> = combine(_period, _offset, allConcerts) { period, off, all ->
        val today = Clock.System.now().toLocalDateTime(tz).date
        all.filter { inPeriod(it.dateTime.date, today, period, off) && it.lastOccurrence() < today }
            .sortedBy { it.dateTime }
            .mapNotNull { c -> (c.photoUri ?: c.photoUris.firstOrNull())?.takeIf { it.isNotBlank() } }
            .distinct()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 曾選這首歌的所有紀錄（依日期新到舊），供 K-2 點歌曲看歷史日期用。
    val allDailySongs: StateFlow<List<DailySong>> = dailySongRepo.getAllSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 已看完的判定：多日場次以最後一天（endDate / extraDates 最大值）為準。 */
    private fun Concert.lastOccurrence(): LocalDate {
        val dates = buildList {
            add(dateTime.date)
            endDate?.let { add(it) }
            addAll(extraDates)
        }
        return dates.maxOrNull() ?: dateTime.date
    }

    // 目前是否停在「當前區間」（offset 0）——供 UI 決定是否顯示「回到現在」。
    val atCurrentPeriod: StateFlow<Boolean> = _offset.map { it == 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setPeriod(p: StatsPeriod) { _period.value = p; _offset.value = 0 }
    fun stepPeriod(delta: Int) { _offset.value += delta }
    fun resetPeriod() { _offset.value = 0 }

    // 簡化的年份選擇：null = 全部，否則某一年（用 YEAR 期間 + 位移到該年）。
    private val nowYear = Clock.System.now().toLocalDateTime(tz).year
    val selectedYear: StateFlow<Int?> = combine(_period, _offset) { p, off ->
        if (p == StatsPeriod.ALL) null else nowYear + off
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), nowYear)
    fun setYear(year: Int?) {
        if (year == null) { _period.value = StatsPeriod.ALL; _offset.value = 0 }
        else { _period.value = StatsPeriod.YEAR; _offset.value = year - nowYear }
    }
    // 有資料的年份清單（供下拉選單）。
    val availableYears: StateFlow<List<Int>> = allConcerts.map { list ->
        list.map { it.dateTime.year }.distinct().sortedDescending().ifEmpty { listOf(nowYear) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf(nowYear))
    fun setHomeBaseCity(city: String) = viewModelScope.launch { settings.setHomeBaseCity(city) }

    // 選歌統計的鑽取預覽：就地編輯（改感受 / 備註）與刪除某天的一首歌。
    fun updateSong(song: DailySong) = viewModelScope.launch { dailySongRepo.saveDailySong(song) }
    fun deleteSong(id: String) = viewModelScope.launch { dailySongRepo.deleteDailySong(id) }

    private data class Window(val start: LocalDate?, val end: LocalDate?, val label: String, val year: Int)

    private fun windowFor(period: StatsPeriod, offset: Int, today: LocalDate): Window = when (period) {
        StatsPeriod.ALL -> Window(null, null, "全部", today.year)
        StatsPeriod.YEAR -> {
            val y = today.year + offset
            Window(LocalDate(y, 1, 1), LocalDate(y, 12, 31), "$y 年", y)
        }
        StatsPeriod.MONTH -> {
            val total = today.year * 12 + (today.monthNumber - 1) + offset
            val y = total / 12; val m = total % 12 + 1
            val start = LocalDate(y, m, 1)
            val end = start.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
            Window(start, end, "$y 年 $m 月", y)
        }
        StatsPeriod.QUARTER -> {
            val q0 = (today.monthNumber - 1) / 3
            val totalQ = today.year * 4 + q0 + offset
            val y = totalQ / 4; val q = totalQ % 4
            val start = LocalDate(y, q * 3 + 1, 1)
            val end = start.plus(3, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
            Window(start, end, "$y 年 Q${q + 1}", y)
        }
    }

    private fun inPeriod(date: LocalDate, today: LocalDate, period: StatsPeriod, offset: Int): Boolean {
        val w = windowFor(period, offset, today)
        return if (w.start == null || w.end == null) true else date in w.start..w.end
    }

    // 這場活動計入統計的藝人：音樂祭用有選取的時程團名；一般演唱會用主藝人 + 其他表演者。
    private fun artistNamesOf(c: Concert): List<String> {
        val fest = c.festivals.filter { it.isSelected && it.artistName.isNotBlank() }.map { it.artistName }
        return if (c.type == ConcertType.FESTIVAL) fest
        else (listOfNotNull(c.artistName.takeIf { it.isNotBlank() }) + fest).distinct()
    }

    // priorArtists = 此區間開始日之前就看過的藝人（供「回訪率」用）；null＝全部時間（改用看過 ≥2 次）。
    private fun computeStats(list: List<Concert>, baseCity: String, priorArtists: Set<String>?): StatsData {
        if (list.isEmpty()) return StatsData(baseCity = baseCity)
        val baseDist = distanceFromTaipei(baseCity)
        fun relDist(city: String) = kotlin.math.abs(distanceFromTaipei(city) - baseDist)
        val ratings = list.mapNotNull { it.rating }
        val monthly = (1..12).map { m -> m to list.count { it.dateTime.monthNumber == m } }
        val artistNames = list.flatMap { artistNamesOf(it) }
        val bandsTotal = artistNames.size.coerceAtLeast(1)
        // 每位藝人花費：票價平均分給該場的藝人（票價 ÷ 團數），與現場時數同一套邏輯。
        // 原本每個團都記全額，一張 2500 的音樂祭票有 10 團就會算成 25000，總花費完全失真。
        // 嘉賓（feat）不計入，也不佔分母——票不是為了嘉賓買的。
        // 這段提到 bands 排序之前算，因為同場次數的藝人要拿花費當同分依據。
        val spendByArtist = mutableMapOf<String, Double>()
        list.forEach { c ->
            val price = c.ticketPrice ?: 0.0
            if (price <= 0.0) return@forEach
            val featNames = c.festivals.filter { it.feat }.map { it.artistName.trim() }.toSet()
            val main = artistNamesOf(c).filterNot { it.trim() in featNames }
            if (main.isEmpty()) return@forEach
            val each = price / main.size
            main.forEach { spendByArtist[it] = (spendByArtist[it] ?: 0.0) + each }
        }
        // 每位藝人第一次看到的日期：場次數同分時，用「認識比較久」當第二層同分依據。
        val firstSeenByArtist = mutableMapOf<String, kotlinx.datetime.LocalDateTime>()
        list.forEach { c ->
            artistNamesOf(c).forEach { a ->
                val cur = firstSeenByArtist[a]
                if (cur == null || c.dateTime < cur) firstSeenByArtist[a] = c.dateTime
            }
        }
        // 最愛藝人排名：場次數多的排前面；同分先比花了多少錢在這位藝人身上（花越多代表越死忠），
        // 還是同分就比認識多久（第一次看到的日期越早，代表跟越久的老朋友）。
        val bands = artistNames.groupingBy { it }.eachCount()
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenByDescending { spendByArtist[it.key] ?: 0.0 }
                    .thenBy { firstSeenByArtist[it.key] }
            )
            .map { StatsBand(it.key, it.value, bandsTotal) }
        val venues = list.filter { it.venue.isNotBlank() }
            .groupingBy { it.venue }.eachCount()
            .entries.sortedByDescending { it.value }
            .map { VenueCount(it.key, it.value) }
        // 這裡保留較多筆，實際顯示幾筆由畫面決定（預設前五 + 展開全部）。
        val artistSpend = spendByArtist.entries.sortedByDescending { it.value }.take(40)
            .map { ArtistSpend(it.key, it.value) }
        val withCity = list.filter { !it.city.isNullOrBlank() }
        val platforms = list.mapNotNull { it.ticketPlatform?.takeIf { p -> p.isNotBlank() } }
            .groupingBy { it }.eachCount().entries
            .sortedByDescending { it.value }.map { it.key to it.value }
        val cities = withCity.mapNotNull { it.city?.takeIf { c -> c.isNotBlank() } }
            .groupingBy { it }.eachCount().entries
            .sortedByDescending { it.value }.map { it.key to it.value }
        // 每月花費
        val monthlySpend = (1..12).map { m ->
            m to list.filter { it.dateTime.monthNumber == m }.sumOf { it.ticketPrice ?: 0.0 }.toInt()
        }
        // 最常一起去的同行者
        val companions = list.flatMap { it.companions }.map { it.trim() }.filter { it.isNotBlank() }
            .groupingBy { it }.eachCount().entries
            .sortedByDescending { it.value }.map { it.key to it.value }
        // 藝人回訪率：
        //  · 某年 / 某區間 → 「這位藝人在此區間之前(不管哪一年)也看過」的占比。
        //  · 全部時間 → 沒有「之前」的概念，退回「看過 ≥2 次的藝人」占比。
        val artistCounts = artistNames.groupingBy { it }.eachCount()
        val distinctArtistCount = artistCounts.size
        val revisitedArtists = if (priorArtists != null) artistCounts.keys.count { it in priorArtists }
        else artistCounts.count { it.value >= 2 }
        val revisitRate = if (distinctArtistCount > 0) revisitedArtists * 100 / distinctArtistCount else 0
        // 票價分布：免費（0 / 免費場次）與 1–500 分開。
        val freeShows = list.count { it.type == ConcertType.FREE || it.ticketPrice == 0.0 }
        val paidPrices = list.mapNotNull { it.ticketPrice?.takeIf { p -> p > 0 } }
        // 含「免費」共 6 個區間。
        val priceBuckets = listOf(
            "免費" to freeShows,
            "1–800" to paidPrices.count { it <= 800 },
            "800–1.5k" to paidPrices.count { it > 800 && it <= 1500 },
            "1.5k–2.5k" to paidPrices.count { it > 1500 && it <= 2500 },
            "2.5k–4k" to paidPrices.count { it > 2500 && it <= 4000 },
            "4k+" to paidPrices.count { it > 4000 }
        )
        // 年 × 月 熱力圖：每年一列，12 個月各自場次數。
        val yearHeat = list.groupBy { it.dateTime.year }.toSortedMap().map { (year, shows) ->
            val counts = IntArray(12)
            shows.forEach { counts[it.dateTime.monthNumber - 1]++ }
            YearHeat(year, counts.toList())
        }
        // 星期分布：一週哪幾天最常看演出（週一=0 .. 週日=6）。
        val weekdayCounts = IntArray(7).also { arr ->
            list.forEach { arr[(it.dateTime.dayOfWeek.isoDayNumber - 1).coerceIn(0, 6)]++ }
        }.toList()
        // 累積演唱會（每月）：把「第一場～最後一場」之間的每個月都列進去（沒看的月份補 0），
        // 這樣長條與累積曲線才是連續、正規的時間序列（曲線在沒看的月份會是水平段）。
        val monthCounts = list.groupingBy { "%04d/%02d".format(it.dateTime.year, it.dateTime.monthNumber) }.eachCount()
        val allMonths = mutableListOf<String>()
        list.minByOrNull { it.dateTime }?.dateTime?.let { firstDt ->
            val lastDt = list.maxByOrNull { it.dateTime }!!.dateTime
            var y = firstDt.year; var mo = firstDt.monthNumber
            while (y < lastDt.year || (y == lastDt.year && mo <= lastDt.monthNumber)) {
                allMonths.add("%04d/%02d".format(y, mo))
                mo++; if (mo > 12) { mo = 1; y++ }
            }
        }
        var running = 0
        val cumulativeTimeline = allMonths.map { ym -> running += (monthCounts[ym] ?: 0); ym to running }
        val monthlyTimeline = allMonths.map { ym -> ym to (monthCounts[ym] ?: 0) }
        // 累積花費（每月）：與累積場數同一組月份鍵，值為到當月為止的總花費（含免費場＝0）。
        val monthSpend = list.groupBy { "%04d/%02d".format(it.dateTime.year, it.dateTime.monthNumber) }
            .mapValues { (_, v) -> v.sumOf { it.ticketPrice ?: 0.0 }.toInt() }
        var runSpend = 0
        val cumulativeSpendTimeline = allMonths.map { ym -> runSpend += (monthSpend[ym] ?: 0); ym to runSpend }
        val monthlySpendTimeline = allMonths.map { ym -> ym to (monthSpend[ym] ?: 0) }
        // 各年份場次
        val yearlyCounts = list.groupingBy { it.dateTime.year }.eachCount()
            .toSortedMap().map { (y, c) -> y.toString() to c }
        // 演出類型分布
        val typeBreakdown = list.groupingBy { it.typeLabel() }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key to it.value }
        // 國家分布
        val countries = list.map { it.country?.takeIf { c -> c.isNotBlank() } ?: "台灣" }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key to it.value }
        // 每位藝人累積觀看時長（分鐘）：
        //  · 音樂節 → 每個選取的團固定 40 分；該 slot 有填實際時間就改用實際時間。
        //  · 其他演出 → 有填結束時間就用開演→結束；沒填就用「類型預設時長」（演唱會＝19–21＝120 分）。
        //  · 跨午夜（end < start）自動 +24h，計入該場所有藝人。
        fun hmToMin(s: String): Int? {
            val p = s.trim().split(":")
            if (p.size != 2) return null
            val h = p[0].toIntOrNull() ?: return null
            val mm = p[1].toIntOrNull() ?: return null
            return h * 60 + mm
        }
        fun dur(startMin: Int, endMin: Int) = (endMin - startMin).let { if (it < 0) it + 24 * 60 else it }
        // 沒有結束時間時，依演出類型給的預設整場時長（分鐘）。
        fun defaultDuration(type: ConcertType): Int = when (type) {
            ConcertType.CONCERT -> 120       // 19–21
            ConcertType.FAN_MEETING -> 90
            ConcertType.ONLINE -> 120
            ConcertType.FREE -> 90
            ConcertType.FESTIVAL -> 40       // 通常走 slot；每團預設 40 分
            ConcertType.EXHIBITION -> 90
            ConcertType.STAGE_PLAY -> 150
            ConcertType.MUSICAL -> 150
            ConcertType.TALK_SHOW -> 120
            ConcertType.OTHER -> 120
        }
        val artistMin = mutableMapOf<String, Int>()
        list.forEach { c ->
            val selFest = c.festivals.filter { it.isSelected && it.artistName.isNotBlank() }
            // FEAT（客串／嘉賓）不計入現場時數：只上台唱一兩首，卻要記整場時長會嚴重灌水。
            // 但他們仍算在「看過哪些藝人」裡（artistNamesOf 不過濾 feat）。
            val featNames = c.festivals.filter { it.feat }.map { it.artistName.trim() }.toSet()
            if (c.type == ConcertType.FESTIVAL && selFest.isNotEmpty()) {
                // 音樂節：每個選取的團預設 40 分；slot 有填實際時間就用實際時間。
                selFest.filterNot { it.feat }.forEach { s ->
                    val st = hmToMin(s.startTime); val en = hmToMin(s.endTime)
                    val d = if (st != null && en != null) dur(st, en) else 40
                    artistMin[s.artistName] = (artistMin[s.artistName] ?: 0) + d
                }
            } else {
                val total = c.endTime?.let { dur(c.dateTime.hour * 60 + c.dateTime.minute, it.hour * 60 + it.minute).takeIf { x -> x > 0 } }
                    ?: defaultDuration(c.type)
                val main = artistNamesOf(c).filterNot { it.trim() in featNames }
                // 一場有多組藝人（拼盤 / 雙主場）時，總時長平均分給各組——
                // 每組都算整場時長會讓「現場時數」隨藝人數灌水，兩組就變兩倍。
                val each = if (main.isNotEmpty()) total / main.size else total
                main.forEach { a -> artistMin[a] = (artistMin[a] ?: 0) + each }
            }
        }
        // 每個售票平台累積花費：看得出錢主要流去哪一家。
        val platformSpend = list.filter { (it.ticketPrice ?: 0.0) > 0 && !it.ticketPlatform.isNullOrBlank() }
            .groupBy { it.ticketPlatform!!.trim() }
            .map { (p, cs) -> p to cs.sumOf { it.ticketPrice ?: 0.0 }.toInt() }
            .sortedByDescending { it.second }
        // 每年在現場的總時數：一場只算一次（不是各藝人相加），沒填結束時間就用類型預設值推估。
        val yearlyMinutes = list.groupBy { it.dateTime.year }
            .map { (y, cs) ->
                y.toString() to cs.sumOf { c ->
                    c.endTime?.let { dur(c.dateTime.hour * 60 + c.dateTime.minute, it.hour * 60 + it.minute).takeIf { x -> x > 0 } }
                        ?: defaultDuration(c.type)
                }
            }.sortedBy { it.first }
        // 總人次＝每場的參與人數相加（自己 + 同行者）。看得出「帶了多少人一起去」。
        val totalAttendance = list.sumOf { 1 + it.companions.count { n -> n.isNotBlank() } }
        val artistMinutes = artistMin.entries.filter { it.value > 0 }
            .sortedByDescending { it.value }.map { it.key to it.value }
        return StatsData(
            totalShows = list.size,
            totalSpent = list.sumOf { it.ticketPrice ?: 0.0 },
            totalArtists = artistNames.distinct().size,
            totalVenues = venues.size,
            freeCount = list.count { it.type == ConcertType.FREE || it.ticketPrice == 0.0 },
            avgRating = if (ratings.isEmpty()) 0.0 else ratings.average(),
            travelKm = withCity.sumOf { (relDist(it.city ?: baseCity) * 2.0).toInt() },
            // 國外場次原本落在 distanceFromTaipei 的 else 分支（100km），永遠贏不過高雄（360km），
            // 導致「最遠」永遠只在台灣挑。非台灣一律視為比島內任何地方都遠。
            farthestCity = withCity.maxByOrNull { c ->
                val isOverseas = (c.country ?: "台灣").let { it != "台灣" && it != "臺灣" && it.isNotBlank() }
                if (isOverseas) 100000 else relDist(c.city ?: baseCity)
            }?.city,
            baseCity = baseCity,
            busiestMonth = monthly.maxByOrNull { it.second }?.takeIf { it.second > 0 }?.first,
            topVenue = venues.firstOrNull()?.venue,
            monthly = monthly,
            bands = bands,
            venues = venues,
            artistSpend = artistSpend,
            platforms = platforms,
            cities = cities,
            monthlySpend = monthlySpend,
            companions = companions,
            revisitRate = revisitRate,
            revisitedArtists = revisitedArtists,
            priceBuckets = priceBuckets,
            yearHeat = yearHeat,
            weekdayCounts = weekdayCounts,
            cumulativeTimeline = cumulativeTimeline,
            monthlyTimeline = monthlyTimeline,
            cumulativeSpendTimeline = cumulativeSpendTimeline,
            monthlySpendTimeline = monthlySpendTimeline,
            yearlyCounts = yearlyCounts,
            typeBreakdown = typeBreakdown,
            countries = countries,
            artistMinutes = artistMinutes,
            platformSpend = platformSpend,
            yearlyMinutes = yearlyMinutes,
            totalAttendance = totalAttendance,
            firstShow = list.minByOrNull { it.dateTime }?.dateTime?.date,
            lastShow = list.maxByOrNull { it.dateTime }?.dateTime?.date
        )
    }
}

/** 排行「展開看全部 / 收合」切換列。 */
@Composable
private fun ExpandToggle(expanded: Boolean, total: Int, onToggle: () -> Unit) {
    androidx.compose.material3.TextButton(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(if (expanded) "收合" else "展開全部 $total 項", fontSize = 13.sp)
    }
}

/**
 * 可收合的統計區塊標題：標題 + 項目數 + 展開 / 收合箭頭。以 stickyHeader 釘在頂端，
 * 展開長排行捲到一半時仍看得到、隨時可點它收合（不必捲到最底找收合鈕）。
 * 因為會浮在內容上，需給不透明底色。
 */
/**
 * 把「名稱 → 數值」的排行資料轉成一張可分享的橫條卡片。
 * 統計頁大部分區塊都是這個形狀，集中在這裡組，各區塊只要給標題與資料。
 */
private fun barsCard(
    title: String, period: String, accentHex: String,
    rows: List<Pair<String, Int>>, unit: String, caption: String? = null
) = com.bandary.presentation.ui.share.StatCardRenderer.Card(
    title = title,
    period = period,
    kind = com.bandary.presentation.ui.share.StatCardRenderer.Kind.BARS,
    rows = rows.map { (name, n) ->
        com.bandary.presentation.ui.share.StatCardRenderer.Row(name, n.toDouble(), "$n$unit")
    },
    caption = caption,
    accentHex = accentHex
)


// 各區標題：不再是可收合的開關。每區的內容現在就是一張圖表，收起來只會讓人多點一次，
// 而且收合狀態還會讓 sticky header 疊在一起看不出層級，所以整區一律直接顯示。
@Composable
private fun StatSectionHeader(title: String, count: Int, onShare: (() -> Unit)? = null) {
    // 這是 stickyHeader 的內容，會浮在內容之上。原本只有一層跟背景同色的底，
    // 捲動時內容從它底下穿過去，看起來就是糊在一起——使用者因此完全感覺不到有標題釘著。
    // 加一條主色短標記 + 底線，讓它在任何時候都讀得出「這是段落標題」。
    Column(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.padding(end = 8.dp).size(width = 3.dp, height = 14.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // 單張圖表分享：把這一段資料單獨算成一張方形卡片，不必先進分享頁再重挑內容。
            if (onShare != null) {
                IconButton(onClick = onShare, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Outlined.IosShare, contentDescription = "分享這張圖表",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        androidx.compose.material3.HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun StatsScreen(
    onShareWrapped: () -> Unit = {},
    onOpenArtist: (String) -> Unit = {},
    onOpenVenue: (String) -> Unit = {},
    onOpenCity: (String) -> Unit = {},
    onOpenPlatform: (String) -> Unit = {},
    onOpenFilter: (String, String) -> Unit = { _, _ -> },  // 圖表鑽取：(routeKey, value) → 看符合的場次
    onOpenWrapped: (Int) -> Unit = {},  // 年度回顧（E2-7），帶目前選取年份（0＝全部/最近）
    onOpenFollowed: () -> Unit = {},    // 追蹤中的藝人
    onOpenLeaderboard: () -> Unit = {}, // 全站年度排行榜
    viewModel: StatsViewModel = hiltViewModel()
) {
    val period by viewModel.period.collectAsState()
    val periodLabel by viewModel.periodLabel.collectAsState()
    val atCurrentPeriod by viewModel.atCurrentPeriod.collectAsState()
    val selectedYear by viewModel.selectedYear.collectAsState()
    val availableYears by viewModel.availableYears.collectAsState()
    val data by viewModel.data.collectAsState()
    val songStats by viewModel.songStats.collectAsState()
    val periodCovers by viewModel.periodCovers.collectAsState()
    val allDailySongs by viewModel.allDailySongs.collectAsState()
    val periodConcerts by viewModel.periodConcerts.collectAsState()
    val milestones by viewModel.milestones.collectAsState()
    var badgeTarget by remember { mutableStateOf<Milestone?>(null) }
    var songHistoryTarget by remember { mutableStateOf<SongTitleRank?>(null) }
    var artistHistoryTarget by remember { mutableStateOf<String?>(null) }
    // 搜尋寫過的歌：排行榜只看得到前幾名，想找某一首（或某句備註）以前只能自己翻月曆。
    var songQuery by remember { mutableStateOf("") }
    // 資料還少（< 5 筆）時保留「看範例」：0 筆自動展開，有一些資料時由使用者自己點開。
    var showShowsSample by remember { mutableStateOf(false) }
    var showSongsSample by remember { mutableStateOf(false) }
    var songSearchEditing by remember { mutableStateOf<DailySong?>(null) }
    // 「全部選歌紀錄」：TOP 榜只看得到前幾名，這裡列出每一筆。預設收合，展開後可換排序。
    var songListExpanded by remember { mutableStateOf(false) }
    var songListSort by remember { mutableStateOf(0) }   // 0＝日期新到舊、1＝藝人、2＝歌名
    val songHits = remember(songQuery, allDailySongs) {
        val q = songQuery.trim()
        if (q.isBlank()) emptyList()
        else allDailySongs.filter {
            it.songTitle.contains(q, ignoreCase = true) ||
                it.artistName.contains(q, ignoreCase = true) ||
                it.memo?.contains(q, ignoreCase = true) == true
        }.sortedByDescending { it.date }
    }
    // 完整清單（不分期間）。依排序方式插入分組標題：日期→年月、藝人→藝人名、歌名→不分組。
    val fullSongList = remember(allDailySongs, songListSort) {
        val sorted = when (songListSort) {
            1 -> allDailySongs.sortedWith(compareBy({ it.artistName }, { it.songTitle }, { it.date.toString() }))
            2 -> allDailySongs.sortedWith(compareBy({ it.songTitle }, { it.date.toString() }))
            else -> allDailySongs.sortedByDescending { it.date }
        }
        var last: String? = null
        sorted.map { song ->
            val group = when (songListSort) {
                1 -> song.artistName.ifBlank { "未填藝人" }
                2 -> null
                else -> "${song.date.year} 年 ${song.date.monthNumber} 月"
            }
            val header = group?.takeIf { it != last }
            if (group != null) last = group
            header to song
        }
    }
    var showShareSheet by remember { mutableStateOf(false) }
    var showBaseCityDialog by remember { mutableStateOf(false) }
    // 點圖表元素先開細節面板（講清楚這個數字的意義），要看場次再從面板進去。
    var chartDetail by remember { mutableStateOf<ChartDetail?>(null) }
    // 單張圖表分享：算圖與開系統分享單都在這裡，各區塊只要把 Card 丟進來。
    val shareCtx = LocalContext.current
    val shareAccent = MaterialTheme.colorScheme.primary.toArgb()
    // 按下分享後：先記下要算哪張卡，實際算圖交給背景執行緒，算完再開預覽。
    // 原本是在主執行緒直接 render 完就叫系統分享單——1080×1080 的 Canvas 繪製
    // 會把主執行緒卡住（畫面停格），而且使用者在看到系統分享單之前完全不知道圖長什麼樣。
    var pendingCard by remember {
        mutableStateOf<com.bandary.presentation.ui.share.StatCardRenderer.Card?>(null)
    }
    var previewBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val shareCard: (com.bandary.presentation.ui.share.StatCardRenderer.Card) -> Unit = { card ->
        pendingCard = card
    }
    LaunchedEffect(pendingCard) {
        val card = pendingCard ?: return@LaunchedEffect
        previewBitmap = withContext(kotlinx.coroutines.Dispatchers.Default) {
            runCatching { com.bandary.presentation.ui.share.StatCardRenderer.render(shareCtx, card) }.getOrNull()
        }
        // 算完就清掉，否則同一張卡再按一次不會重新觸發（key 沒變）。
        pendingCard = null
    }

    // 「我最常聽的 N 首歌」：9:16 的直式分享圖，走另一支渲染器。
    // 跟上面的資料卡取捨相反——那張是為了看得懂，這張是為了被轉發。
    var pendingTopSongs by remember {
        mutableStateOf<com.bandary.presentation.ui.share.TopSongsCardRenderer.Card?>(null)
    }
    LaunchedEffect(pendingTopSongs) {
        val card = pendingTopSongs ?: return@LaunchedEffect
        previewBitmap = withContext(kotlinx.coroutines.Dispatchers.Default) {
            runCatching {
                com.bandary.presentation.ui.share.TopSongsCardRenderer.render(shareCtx, card)
            }.getOrNull()
        }
        pendingTopSongs = null
    }
    var customBaseInput by remember { mutableStateOf("") }
    var statMode by remember { mutableStateOf(0) }  // 0 = 看現場, 1 = 選歌
    // 各排行「展開看全部」狀態（預設只顯示前五）
    // 圖表分類：頁面本身很長不是問題（每張圖都有它的意義），問題是沒有段落、
    // 只能從頭捲到尾。選一類就只留那一類，「全部」維持完整順序。
    var statCat by rememberSaveable { mutableStateOf(STAT_CAT_ALL) }
    var expVenues by remember { mutableStateOf(false) }
    var expBands by remember { mutableStateOf(false) }
    var expSpend by remember { mutableStateOf(false) }
    // 各排行區塊「整段收合」狀態（預設收合，避免一次看不完；點標題展開）
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showScrollTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 3 } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("演出統計", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                actions = {
                    // 年份選擇併進標題列，不再單獨佔一行。
                    var yearMenu by remember { mutableStateOf(false) }
                    Box {
                        androidx.compose.material3.Surface(
                            onClick = { yearMenu = true },
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 5.dp, bottom = 5.dp)) {
                                Text(selectedYear?.let { "$it 年" } ?: "全部時間", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Icon(Icons.Outlined.KeyboardArrowDown, null, modifier = Modifier.size(16.dp))
                            }
                        }
                        androidx.compose.material3.DropdownMenu(expanded = yearMenu, onDismissRequest = { yearMenu = false }) {
                            androidx.compose.material3.DropdownMenuItem(text = { Text("全部時間") }, onClick = { viewModel.setYear(null); yearMenu = false })
                            availableYears.forEach { y ->
                                androidx.compose.material3.DropdownMenuItem(text = { Text("$y 年") }, onClick = { viewModel.setYear(y); yearMenu = false })
                            }
                        }
                    }
                    IconButton(onClick = onOpenLeaderboard) {
                        Icon(Icons.Outlined.EmojiEvents, contentDescription = "年度排行榜")
                    }
                    IconButton(onClick = { showShareSheet = true }) {
                        Icon(Icons.Outlined.Share, contentDescription = "分享")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (showScrollTop) {
                SmallFloatingActionButton(
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ) { Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "回到頂部") }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
      // 統計頁很長，右側加一條可拖的快捲把手（與「我的演唱會」同款，但這裡按比例跳）。
      // 左右滑切換「看現場 ↔ 選歌」。兩個模式共用同一個 LazyColumn（模式選擇器本身是 item 0），
      // 拆成 HorizontalPager 要動整個清單結構，風險大；這裡用手勢切換即可，效果一樣。
      // 圖表自己的水平拖曳有 consume，不會被這層搶走。
      Box(
          Modifier.fillMaxSize().swipeHorizontalPage(
              onPrev = { if (statMode != 0) { statMode = 0; scope.launch { listState.animateScrollToItem(0) } } },
              onNext = { if (statMode != 1) { statMode = 1; scope.launch { listState.animateScrollToItem(0) } } }
          )
      ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                // +96dp 讓最後一列不被右下角的 FAB 遮住（與其他頁一致）。
                // Scaffold 有 floatingActionButton 時，這裡拿到的 bottom 已經含了 FAB 的
                // 高度與間距（M3 自己算進去）。再加 96dp 就是加第二次。
                bottom = padding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 看現場 / 選歌 切換（避免一次滑很長）
            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf("看現場", "選歌").forEachIndexed { i, label ->
                        SegmentedButton(
                            selected = statMode == i,
                            onClick = { statMode = i; scope.launch { listState.animateScrollToItem(0) } },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = 2)
                        ) { Text(label) }
                    }
                }
            }

            // ══ 看現場 ══
            if (statMode == 0) {
            if (data.totalShows == 0) {
                item {
                    EmptyState(
                        title = "這段期間還沒有演出",
                        subtitle = "換個區間，或去新增演唱會開始累積數據"
                    )
                }
                item { SamplePreviewHeader() }
                sampleStatCards()
            }
            item {
                val tiedWatch = data.bands.maxOfOrNull { it.count }
                    ?.let { m -> data.bands.filter { it.count == m } }.orEmpty()
                val tiedSpend = data.artistSpend.maxOfOrNull { it.amount }
                    ?.let { m -> data.artistSpend.filter { it.amount == m } }.orEmpty()
                WrappedHeroCard(
                    period = selectedYear?.let { "$it 年度數據" } ?: "全部時間數據",
                    shows = data.totalShows,
                    spent = data.totalSpent.toInt(),
                    // 並列第一：最多顯示 TIED_NAME_LIMIT 位，實際人數另外傳進去做「等 N 位」。
                    mostWatched = tiedWatch.take(TIED_NAME_LIMIT),
                    topSpender = tiedSpend.take(TIED_NAME_LIMIT),
                    tiedWatchCount = tiedWatch.size,
                    tiedSpendCount = tiedSpend.size
                )
            }

            // 年度聽團回顧（E2-7）入口：帶目前選取的年份（沒選＝0 由回顧頁自己挑最近有資料的年）。
            if (data.totalShows > 0 || songStats.totalSongs > 0) {
                item { WrappedEntryCard(onClick = { onOpenWrapped(selectedYear ?: 0) }) }
            }

            item {
                // 概覽數字從「10 張彩色卡、分 5 個 item」改成一張 StatGrid（見下方 StatGrid 的說明）。
                // 這 10 個數字彼此無關、單位也不同，本質是一張對照表，不是 10 個各自獨立的重點。
                val totalH = data.yearlyMinutes.sumOf { it.second } / 60
                val avgPer = if (data.totalShows > 0) (data.totalSpent / data.totalShows).toInt() else 0
                // 看場頻率：第一場到最後一場的天數 ÷ (場數-1)，約幾天看一場。
                val fs = data.firstShow; val ls = data.lastShow
                val daysPer = if (fs != null && ls != null && data.totalShows > 1)
                    (fs.daysUntil(ls) / (data.totalShows - 1)) else 0
                StatGrid(
                    listOf(
                        StatCell("藝人", "${data.totalArtists}", "組"),
                        StatCell("場館", "${data.totalVenues}", "個"),
                        StatCell("去過縣市", "${data.cities.size}", "個"),
                        StatCell("同行夥伴", "${data.companions.size}", "位"),
                        StatCell("交通距離", "${data.travelKm}", "km"),
                        StatCell("最遠演唱會", data.farthestCity ?: "\u2014"),
                        // 總人次＝每場的自己 + 同行者相加；現場總時數一場只算一次。
                        StatCell("總人次", "${data.totalAttendance}", "人次"),
                        StatCell("現場總時數", if (totalH > 0) "$totalH" else "\u2014", if (totalH > 0) "小時" else ""),
                        // 平均每場花費（含免費場＝分母用總場數）。
                        StatCell("平均每場", if (avgPer > 0) "NT$$avgPer" else "\u2014"),
                        StatCell("看場頻率", if (daysPer > 0) "$daysPer" else "\u2014", if (daysPer > 0) "天/場" else "")
                    )
                )
            }
            item {
                Surface(
                    onClick = { showBaseCityDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("距離基準地：${data.baseCity}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("點選更換", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            // 分類切換：上面的 KPI 卡是總覽，永遠顯示；以下的圖表依分類過濾。
            item {
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(STAT_CATS) { c ->
                        FilterChip(
                            selected = statCat == c,
                            onClick = { statCat = c },
                            label = { Text(c, fontSize = 13.sp) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

            // 注意力排序：先高層次「整體摘要」→ 成就徽章 → 成長曲線 → 分布圖表 → 排行 → 次要細項。
            if (showStat(statCat, STAT_CAT_TREND) && data.totalShows >= 2 && data.firstShow != null && data.lastShow != null) {
                item { FunFactsCard(firstShow = data.firstShow!!, lastShow = data.lastShow!!, totalShows = data.totalShows, monthly = data.monthlyTimeline) }
            }
            // 成就里程碑（E2-8）：已達標的徽章可點開生成分享圖。全時累積，不受期間篩選。
            if (showStat(statCat, STAT_CAT_TREND) && milestones.any { it.earned }) {
                item { AchievementsCard(milestones = milestones, onOpenBadge = { badgeTarget = it }) }
            }
            // 累積演唱會（成長趨勢，重點圖表）。與下面的累積花費用不同主色 + 各自的解讀，
            // 否則兩張同款曲線疊在一起看起來像同一張圖印了兩遍。
            if (showStat(statCat, STAT_CAT_TREND) && data.cumulativeTimeline.size >= 2) {
                item {
                    val months = data.monthlyTimeline.size.coerceAtLeast(1)
                    val active = data.monthlyTimeline.count { it.second > 0 }
                    LineChartCard(
                        title = "累積演唱會・每月",
                        data = data.cumulativeTimeline,
                        monthly = data.monthlyTimeline,
                        accent = STAT_SERIES[0],
                        insight = "這 $months 個月裡有 $active 個月你有進場"
                    )
                }
            }
            // 累積花費（與累積場數同款曲線，金額版）——只在有花過錢時才顯示。
            if (showStat(statCat, STAT_CAT_TREND) && data.cumulativeSpendTimeline.size >= 2 && (data.cumulativeSpendTimeline.lastOrNull()?.second ?: 0) > 0) {
                item {
                    val total = data.cumulativeSpendTimeline.last().second
                    val peak = data.monthlySpendTimeline.maxByOrNull { it.second }
                    LineChartCard(
                        title = "累積花費・每月",
                        data = data.cumulativeSpendTimeline,
                        monthly = data.monthlySpendTimeline,
                        formatValue = { "NT$$it" },
                        accent = STAT_SERIES[3],
                        insight = peak?.takeIf { it.second > 0 }?.let {
                            "最貴的一個月是 ${it.first}，NT$${it.second}，占總花費 ${it.second * 100 / total.coerceAtLeast(1)}%"
                        }
                    )
                }
            }
            // 「月度場次分布」已移除：下面的年 × 月熱力圖同樣是月份維度，但多了年份這一軸，
            // 資訊完全涵蓋它，兩張放一起只是把同一件事講兩次。
            if (showStat(statCat, STAT_CAT_TREND) && data.yearlyCounts.size >= 2) {
                stickyHeader { StatSectionHeader("各年份場次", data.yearlyCounts.size) }
                item { LabeledBarCard(title = "", data = data.yearlyCounts,
                    insight = topShareInsight(data.yearlyCounts, " 場"), onRow = { lbl ->
                    chartDetail = chartDetailOf(lbl, data.yearlyCounts, " 場") { onOpenFilter("year", lbl) } }) }
            }
            // 演出類型分布（演唱會 / 音樂節 / 免費…）——甜甜圈圖
            if (showStat(statCat, STAT_CAT_TICKET) && data.typeBreakdown.any { it.second > 0 }) {
                stickyHeader { StatSectionHeader("演出類型分布", data.typeBreakdown.size) }
                item { DonutChartCard(title = "", data = data.typeBreakdown,
                    insight = topShareInsight(data.typeBreakdown, " 場"), onSlice = { lbl ->
                    chartDetail = chartDetailOf(lbl, data.typeBreakdown, " 場") { onOpenFilter("type", lbl) } }) }
            }
            // 國家分布（台灣 / 海外）
            if (showStat(statCat, STAT_CAT_PLACE) && (data.countries.size >= 2 || (data.countries.size == 1 && data.countries.first().first != "台灣"))) {
                stickyHeader { StatSectionHeader("國家分布", data.countries.size) }
                item { LabeledBarCard(title = "", data = data.countries,
                    insight = topShareInsight(data.countries, " 場"), onRow = { lbl ->
                    chartDetail = chartDetailOf(lbl, data.countries, " 場") { onOpenFilter("country", lbl) } }) }
            }
            // 看最久的藝人（依開演 / 結束時間、音樂祭各團 slot 時間累加）
            if (showStat(statCat, STAT_CAT_ARTIST) && data.artistMinutes.isNotEmpty()) {
                stickyHeader { StatSectionHeader("藝人累積觀看時長", data.artistMinutes.size) }
                item { DurationRankCard(title = "", data = data.artistMinutes, onRow = onOpenArtist) }
            }
            // 每個售票平台花了多少錢：與「平台分布」互補——常用不一定花最多。
            if (showStat(statCat, STAT_CAT_TICKET) && data.platformSpend.isNotEmpty()) {
                item {
                    LabeledBarCard(
                        title = "各售票平台花費",
                        data = data.platformSpend,
                        onRow = { onOpenPlatform(it) },
                        formatValue = { "NT$${"%,d".format(it)}" },
                        restUnit = ""
                    )
                }
            }
            // 每年在現場的總時數（小時）。
            if (showStat(statCat, STAT_CAT_TREND) && data.yearlyMinutes.any { it.second > 0 }) {
                item {
                    LabeledBarCard(
                        title = "每年在現場的時數",
                        data = data.yearlyMinutes.map { (y, m) -> y to (m / 60) },
                        onRow = { onOpenFilter("year", it) },
                        maxRows = 20,
                        formatValue = { "$it 小時" },
                        restUnit = " 小時"
                    )
                }
            }
            // 看過的藝人文字雲：看越多次字越大，比排行更有整體感。
            if (showStat(statCat, STAT_CAT_ARTIST) && data.bands.size >= 3) {
                item { ArtistWordCloudCard(data.bands, onClick = onOpenArtist) }
            }
            // 年 × 月 熱力圖：哪一年哪個月最常跑演出，一眼看出季節性與爆發年。
            if (showStat(statCat, STAT_CAT_TREND) && data.yearHeat.any { row -> row.counts.any { it > 0 } }) {
                item { MonthYearHeatmapCard(rows = data.yearHeat, onYear = { onOpenFilter("year", it.toString()) }) }
            }
            // 星期分布：習慣週幾看演出。
            if (showStat(statCat, STAT_CAT_TREND) && data.weekdayCounts.any { it > 0 }) {
                item { WeekdayBarsCard(counts = data.weekdayCounts, onDay = { day -> onOpenFilter("weekday", com.bandary.domain.model.WEEKDAY_LABELS[day]) }) }
            }
            // 「每月花費」已移除：上面的累積花費圖，長條本來就是逐月金額，
            // 而且走的是真實時間軸（含年份），比這張把各年同月加總的版本更準。
            // 藝人回訪率 + 票價分布（有趣的 insight，不只是數字）
            if (showStat(statCat, STAT_CAT_ARTIST) && data.totalArtists > 0) {
                item { RevisitRateCard(rate = data.revisitRate, revisited = data.revisitedArtists, total = data.totalArtists) }
            }
            if (showStat(statCat, STAT_CAT_TICKET) && data.priceBuckets.any { it.second > 0 }) {
                item { PriceHistogramCard(buckets = data.priceBuckets, onBucket = { onOpenFilter("price", it) }) }
            }
            // ── 各區排序：由「最有情感連結」往「最工具性」排，引導視覺往下走 ──
            //   樂手排行（誰陪你最多場）→ 花在誰身上 → 去哪些場館 → 跟誰一起 → 走過哪些縣市 → 在哪買票。
            // 長清單一律只先給前幾名，其餘點「展開全部」再看，避免一區洗掉整個畫面。

            // 追蹤中的藝人入口：放在藝人排行正上方，因為使用者是在這裡認出「我常看誰」才會想追蹤。
            if (showStat(statCat, STAT_CAT_ARTIST)) item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable { onOpenFollowed() },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Favorite, null, tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("追蹤中的藝人", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("活動出現他們的新場次時提醒你", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (showStat(statCat, STAT_CAT_ARTIST) && data.bands.isNotEmpty()) {
                stickyHeader {
                    StatSectionHeader("看最多次的藝人", data.bands.size) {
                        shareCard(barsCard("看最多次的藝人", periodLabel, argbToHex(shareAccent),
                            data.bands.map { it.artistName to it.count }, " 場",
                            "看過 ${data.totalArtists} 組藝人"))
                    }
                }
                // 前三名頒獎台，第 4 名之後接同款排行列；預設只到第 10 名。
                if (data.bands.size >= 2) {
                    item {
                        PodiumCard(
                            data.bands.take(3).map { it.artistName to "${it.count} 場" },
                            onClick = { onOpenArtist(it) }
                        )
                    }
                }
                val restBands = data.bands.drop(3)
                // 頒獎台已占前三，列表預設只補到第 5 名（前三 + 2）。
                val shownBands = if (expBands) restBands else restBands.take(2)
                items(shownBands) { band ->
                    val rank = data.bands.indexOf(band) + 1
                    BandAttendanceRow(rank = rank, band = band, onClick = { onOpenArtist(band.artistName) })
                }
                if (restBands.size > 2) item { ExpandToggle(expBands, data.bands.size) { expBands = !expBands } }
            }

            // 沒資料的區塊也保留標題 + 一行「還沒有資料」，讓人知道有這個統計、只是還沒累積到。
            if (!showStat(statCat, STAT_CAT_ARTIST)) Unit
            else if (data.artistSpend.isEmpty()) {
                stickyHeader { StatSectionHeader("每位藝人花費", 0) }
                item { StatEmptyRow("還沒有付費場次") }
            } else {
                stickyHeader {
                    StatSectionHeader("每位藝人花費", data.artistSpend.size) {
                        shareCard(com.bandary.presentation.ui.share.StatCardRenderer.Card(
                            title = "每位藝人花費", period = periodLabel,
                            kind = com.bandary.presentation.ui.share.StatCardRenderer.Kind.BARS,
                            rows = data.artistSpend.map {
                                com.bandary.presentation.ui.share.StatCardRenderer.Row(
                                    it.name, it.amount, "NT$" + "%,d".format(it.amount.toInt()))
                            },
                            caption = "總花費 NT$" + "%,d".format(data.totalSpent.toInt()),
                            accentHex = argbToHex(shareAccent)
                        ))
                    }
                }
                val shownSpend = if (expSpend) data.artistSpend else data.artistSpend.take(5)
                val maxSpend = data.artistSpend.maxOfOrNull { it.amount } ?: 0.0
                itemsIndexed(shownSpend) { i, sp ->
                    ArtistSpendRow(sp, rank = i + 1, maxAmount = maxSpend, onClick = { onOpenArtist(sp.name) })
                }
                if (data.artistSpend.size > 5) item { ExpandToggle(expSpend, data.artistSpend.size) { expSpend = !expSpend } }
            }

            if (showStat(statCat, STAT_CAT_PLACE) && data.venues.isNotEmpty()) {
                val venSame = data.venues.map { it.count }.distinct().size == 1
                val venTitle = "最常去的場館" + if (venSame) " · 各 ${data.venues.first().count} 場" else ""
                stickyHeader {
                    StatSectionHeader(venTitle, data.venues.size) {
                        shareCard(barsCard("最常去的場館", periodLabel, argbToHex(shareAccent),
                            data.venues.map { it.venue to it.count }, " 場",
                            "去過 ${data.venues.size} 個場館"))
                    }
                }
                // 場館名字長、數量多 → 橫條圖（名字有整行可放，長度直接比出多寡）。
                item {
                    LabeledBarCard(
                        title = "場館場次",
                        data = data.venues.map { it.venue to it.count },
                        onRow = { onOpenVenue(it) },
                        maxRows = if (expVenues) Int.MAX_VALUE else 5
                    )
                }
                if (data.venues.size > 5) item { ExpandToggle(expVenues, data.venues.size) { expVenues = !expVenues } }
            }

            if (!showStat(statCat, STAT_CAT_ARTIST)) Unit
            else if (data.companions.isEmpty()) {
                stickyHeader { StatSectionHeader("最常一起去的同行者", 0) }
                item { StatEmptyRow("還沒有記錄同行者") }
            } else {
                // 全同值（如全部 1 場）→ 隱藏每列數值，標題註明「各 N 場」（spec §6.9.3）。
                val compSame = data.companions.map { it.second }.distinct().size == 1
                val compTitle = "最常一起去的同行者" + if (compSame) " · 各 ${data.companions.first().second} 場" else ""
                stickyHeader {
                    StatSectionHeader(compTitle, data.companions.size) {
                        shareCard(barsCard("最常一起去的同行者", periodLabel, argbToHex(shareAccent),
                            data.companions, " 場"))
                    }
                }
                // 同行者＝人，用名字首字的圓形頭像卡呈現；本身是橫向捲動，不需要展開。
                item {
                    AvatarRankCard(
                        data = data.companions,
                        showCount = !compSame,
                        onClick = { onOpenFilter("companion", it) }
                    )
                }
            }

            if (!showStat(statCat, STAT_CAT_PLACE)) Unit
            else if (data.cities.isEmpty()) {
                stickyHeader { StatSectionHeader("最常去的縣市", 0) }
                item { StatEmptyRow("還沒有填縣市的場次") }
            } else {
                stickyHeader {
                    StatSectionHeader("最常去的縣市", data.cities.size) {
                        shareCard(com.bandary.presentation.ui.share.StatCardRenderer.Card(
                            title = "最常去的縣市", period = periodLabel,
                            kind = com.bandary.presentation.ui.share.StatCardRenderer.Kind.DONUT,
                            rows = data.cities.map {
                                com.bandary.presentation.ui.share.StatCardRenderer.Row(
                                    it.first, it.second.toDouble(), "${it.second} 場")
                            },
                            caption = "走過 ${data.cities.size} 個縣市",
                            accentHex = argbToHex(shareAccent)
                        ))
                    }
                }
                // 縣市類別少、重點是比例 → 甜甜圈圖。不用方格地圖：先前試過，示意不清楚。
                stickyHeader { StatSectionHeader("縣市分布", data.cities.size) }
                item { DonutChartCard(title = "", data = data.cities, onSlice = { lbl ->
                    chartDetail = chartDetailOf(lbl, data.cities, " 場") { onOpenCity(lbl) } }) }
            }

            if (!showStat(statCat, STAT_CAT_TICKET)) Unit
            else if (data.platforms.isEmpty()) {
                stickyHeader { StatSectionHeader("售票平台分布", 0) }
                item { StatEmptyRow("還沒有填售票平台的場次") }
            } else {
                stickyHeader {
                    StatSectionHeader("售票平台分布", data.platforms.size) {
                        shareCard(com.bandary.presentation.ui.share.StatCardRenderer.Card(
                            title = "售票平台分布", period = periodLabel,
                            kind = com.bandary.presentation.ui.share.StatCardRenderer.Kind.DONUT,
                            rows = data.platforms.map {
                                com.bandary.presentation.ui.share.StatCardRenderer.Row(
                                    it.first, it.second.toDouble(), "${it.second} 場")
                            },
                            accentHex = argbToHex(shareAccent)
                        ))
                    }
                }
                // 平台就那幾家，重點是佔比 → 甜甜圈圖。
                stickyHeader { StatSectionHeader("售票平台", data.platforms.size) }
                item { DonutChartCard(title = "", data = data.platforms, onSlice = { lbl ->
                    chartDetail = chartDetailOf(lbl, data.platforms, " 場") { onOpenPlatform(lbl) } }) }
            }

            // 空資料時的範例已經移到這個分頁最上面（見上方），這裡只留「資料還少」的可展開入口。
            if (data.totalShows in 1 until SAMPLE_THRESHOLD) {
                // 才記了幾場，圖表還很空；保留範例入口，讓人知道記多了會看到什麼。
                item { SampleToggle(expanded = showShowsSample, onToggle = { showShowsSample = !showShowsSample }) }
                if (showShowsSample) {
                    item { SamplePreviewHeader() }
                    sampleStatCards()
                }
            }
            } // ══ 看現場 end ══

            // ══ 選歌 ══
            if (statMode == 1) {
                if (songStats.totalSongs == 0) {
                    item { EmptyState(title = "這段期間還沒有選歌", subtitle = "去「行事曆 → 選歌」記錄每天在聽的歌") }
                    item { SamplePreviewHeader("以下為示意，記錄選歌後會換成你的真實數據") }
                    sampleSongCards()
                }
                // 搜尋列放在最上面：找某一首歌不該要先捲過所有圖表。
                // 搜尋範圍是「所有記過的歌」，不受上面的期間篩選影響——要找的通常是很久以前那一首。
                item {
                    OutlinedTextField(
                        value = songQuery,
                        onValueChange = { songQuery = it },
                        label = { Text("搜尋寫過的歌") },
                        placeholder = { Text("歌名、藝人或備註") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        trailingIcon = {
                            if (songQuery.isNotBlank()) {
                                IconButton(onClick = { songQuery = "" }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "清除")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                if (songQuery.isNotBlank()) {
                    stickyHeader { StatSectionHeader("搜尋結果（全部紀錄）", songHits.size) }
                    if (songHits.isEmpty()) {
                        item {
                            Text(
                                "找不到「${songQuery.trim()}」",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    }
                    items(songHits) { song -> SongSearchRow(song, onClick = { songSearchEditing = song }) }
                }
                item {
                    StatGrid(
                        listOf(
                            StatCell("選歌數", "${songStats.totalSongs}", "首"),
                            StatCell("紀錄天數", "${songStats.daysWithSongs}", "天"),
                            StatCell("選過藝人", "${songStats.distinctArtists}", "組"),
                            StatCell(
                                "最常選", songStats.topArtist ?: "\u2014",
                                if (songStats.topArtistCount > 0) "\u00d7${songStats.topArtistCount}" else ""
                            ),
                            StatCell("每天平均", if (songStats.avgPerActiveDay > 0) String.format("%.1f", songStats.avgPerActiveDay) else "\u2014", "首"),
                            StatCell("最長連續", "${songStats.longestStreak}", "天")
                        )
                    )
                }
                // 感受標籤分布（甜甜圈）
                if (songStats.reactions.isNotEmpty()) {
                    stickyHeader { StatSectionHeader("選歌感受分布", songStats.reactions.size) }
                    item { DonutChartCard(title = "", data = songStats.reactions,
                        insight = topShareInsight(songStats.reactions, " 首")) }
                }
                // 每月選歌數
                if (songStats.monthlySongs.any { it.second > 0 }) {
                    stickyHeader { StatSectionHeader("每月選歌數", songStats.monthlySongs.size) }
                    item {
                        // 每月選歌數的重點不是「最多的月份」，而是「有幾個月真的在記」——
                        // 這張圖常常是一兩根高、其他是 0，講尖峰會誤導成「平常也有在記」。
                        val active = songStats.monthlySongs.count { it.second > 0 }
                        val months = songStats.monthlySongs.size
                        val peak = songStats.monthlySongs.maxByOrNull { it.second }
                        ChartCard(
                            title = "", data = songStats.monthlySongs,
                            insight = peak?.takeIf { it.second > 0 && months > 1 }?.let {
                                "這 $months 個月裡有 $active 個月你有記歌，最多的是 ${it.first} 月的 ${it.second} 首"
                            }
                        )
                    }
                }
                // 現場記下 vs 平常在聽：選歌與看團的接點，用甜甜圈看比重。
                if (songStats.liveSongs > 0) {
                    item {
                        DonutChartCard(
                            title = "現場記下的歌",
                            data = listOf(
                                "現場記下" to songStats.liveSongs,
                                "平常在聽" to (songStats.totalSongs - songStats.liveSongs)
                            ).filter { it.second > 0 }
                        )
                    }
                }
                // 星期分布：看得出是週末才聽歌還是每天都聽。
                if (songStats.weekdaySongs.any { it.second > 0 }) {
                    stickyHeader { StatSectionHeader("一週哪幾天最常記歌", songStats.weekdaySongs.size) }
                    item { LabeledBarCard(title = "", data = songStats.weekdaySongs,
                        insight = topShareInsight(songStats.weekdaySongs, " 首")) }
                }
                // 最常選的專輯
                if (songStats.topAlbums.isNotEmpty()) {
                    stickyHeader { StatSectionHeader("最常選的專輯", songStats.topAlbums.size) }
                    item { LabeledBarCard(title = "", data = songStats.topAlbums,
                        insight = topShareInsight(songStats.topAlbums, " 首")) }
                }
                if (songStats.topArtists.isNotEmpty()) {
                    stickyHeader {
                        StatSectionHeader("喜歡的藝人 TOP", songStats.topArtists.size) {
                            shareCard(barsCard("喜歡的藝人", periodLabel, argbToHex(shareAccent),
                                songStats.topArtists.map { it.name to it.count }, " 首",
                                "選了 ${songStats.totalSongs} 首歌"))
                        }
                    }
                    run {
                        // 與「最愛樂手排行」同款頒獎台，兩邊的排行呈現方式一致。
                        if (songStats.topArtists.size >= 2) {
                            item {
                                PodiumCard(
                                    songStats.topArtists.take(3).map { it.name to "${it.count} 首" },
                                    onClick = { artistHistoryTarget = it }
                                )
                            }
                        }
                        items(songStats.topArtists.drop(3)) { a -> SongArtistRankRow(a, onClick = { artistHistoryTarget = a.name }) }
                    }
                }
                if (songStats.topSongs.isNotEmpty()) {
                    stickyHeader {
                        StatSectionHeader("最常選的歌 TOP", songStats.topSongs.size) {
                            pendingTopSongs = com.bandary.presentation.ui.share.TopSongsCardRenderer.Card(
                                songs = songStats.topSongs.map {
                                    com.bandary.presentation.ui.share.TopSongsCardRenderer.Song(
                                        it.title, it.artist, it.count
                                    )
                                },
                                period = selectedYear?.let { "$it 年" } ?: "全部時間",
                                totalSongs = songStats.totalSongs,
                                activeDays = songStats.daysWithSongs,
                                accentHex = String.format("#%06X", 0xFFFFFF and shareAccent)
                            )
                        }
                    }
                    itemsIndexed(songStats.topSongs) { i, s ->
                        SongTitleRankRow(
                            s,
                            rank = i,
                            maxCount = songStats.topSongs.firstOrNull()?.count ?: 1,
                            onClick = { songHistoryTarget = s }
                        )
                    }
                    item {
                        // 把分享做成一張看得見的卡，而不是只藏在標題列那顆小圖示裡。
                        // 這是整頁最有傳播價值的東西，藏起來等於沒做。
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            elevation = CardDefaults.cardElevation(0.dp),
                            onClick = {
                                pendingTopSongs = com.bandary.presentation.ui.share.TopSongsCardRenderer.Card(
                                    songs = songStats.topSongs.map {
                                        com.bandary.presentation.ui.share.TopSongsCardRenderer.Song(
                                            it.title, it.artist, it.count
                                        )
                                    },
                                    period = selectedYear?.let { "$it 年" } ?: "全部時間",
                                    totalSongs = songStats.totalSongs,
                                    activeDays = songStats.daysWithSongs,
                                    accentHex = String.format("#%06X", 0xFFFFFF and shareAccent)
                                )
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.IosShare, null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "做成「我最常聽的 ${songStats.topSongs.size} 首歌」",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        "直式分享圖，限時動態的尺寸",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }
                // ── 全部選歌紀錄 ──
                // 上面的排行榜只回答「最常選什麼」，回答不了「我到底選過哪些歌」。
                // 這裡把每一筆都列出來（不受上方期間篩選影響），預設收合避免洗掉其他區塊。
                if (allDailySongs.isNotEmpty() && songQuery.isBlank()) {
                    stickyHeader { StatSectionHeader("全部選歌紀錄", allDailySongs.size) }
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { songListExpanded = !songListExpanded },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(0.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        if (songListExpanded) "收起清單" else "列出全部 ${allDailySongs.size} 首",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        "從第一筆到最新一筆，不分期間；點任一首可以改感受與備註",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    if (songListExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if (songListExpanded) {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("日期" to 0, "藝人" to 1, "歌名" to 2).forEach { (label, mode) ->
                                    FilterChip(
                                        selected = songListSort == mode,
                                        onClick = { songListSort = mode },
                                        label = { Text(label, fontSize = 12.sp) },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }
                        }
                        items(fullSongList, key = { it.second.id }) { (header, song) ->
                            Column {
                                header?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                                    )
                                }
                                SongSearchRow(song, onClick = { songSearchEditing = song })
                            }
                        }
                    }
                }
                // 空資料時的範例已移到這個分頁最上面；這裡只留「資料還少」的可展開入口。
                if (songStats.totalSongs in 1 until SAMPLE_THRESHOLD) {
                    item { SampleToggle(expanded = showSongsSample, onToggle = { showSongsSample = !showSongsSample }) }
                    if (showSongsSample) {
                        item { SamplePreviewHeader("以下為示意，記錄選歌後會換成你的真實數據") }
                        sampleSongCards()
                    }
                }
            }

            item {
                Button(
                    onClick = { showShareSheet = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("製作分享圖", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        ListScrubber(
            state = listState,
            modifier = Modifier.align(Alignment.CenterEnd),
            topInset = padding.calculateTopPadding(),
            // 同上：FAB 高度已含在 calculateBottomPadding 裡。
            bottomInset = padding.calculateBottomPadding() + 16.dp
        )
      }
    }

    chartDetail?.let { d ->
        ChartDetailSheet(detail = d, onDismiss = { chartDetail = null })
    }

    // 算圖中：給一個明確的等待提示，不然按了分享之後畫面毫無反應，會以為沒吃到點擊。
    if (pendingCard != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { },
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text("製作分享圖…")
                }
            }
        )
    }
    previewBitmap?.let { bmp ->
        com.bandary.presentation.ui.components.SharePreviewDialog(
            bitmap = bmp,
            fileName = "bandary_stat",
            onDismiss = { previewBitmap = null }
        )
    }

    if (showShareSheet) {
        val isPro by viewModel.isPro.collectAsState()
        // 分享時間依據：選「全部時間」就抓第一場～最後一場的實際區間當標籤。
        val shareLabel = if (selectedYear == null && data.firstShow != null && data.lastShow != null) {
            val f = data.firstShow!!; val l = data.lastShow!!
            "${f.year}.${f.monthNumber.toString().padStart(2, '0')} – ${l.year}.${l.monthNumber.toString().padStart(2, '0')}"
        } else periodLabel
        ShareSheet(
            periodLabel = shareLabel,
            data = data,
            songStats = songStats,
            periodCovers = periodCovers,
            timeline = remember(periodConcerts, allDailySongs) { buildConcertSongTimeline(periodConcerts, allDailySongs) },
            isPro = isPro,
            onDismiss = { showShareSheet = false; onShareWrapped() }
        )
    }

    badgeTarget?.let { m ->
        MilestoneBadgeDialog(milestone = m, onDismiss = { badgeTarget = null })
    }

    // 搜尋結果點進去＝就地編輯，跟排行榜鑽取的那條路徑同一個對話框。
    songSearchEditing?.let { song ->
        SongEditDialog(
            song = song,
            onSave = { viewModel.updateSong(it); songSearchEditing = null },
            onDelete = { viewModel.deleteSong(song.id); songSearchEditing = null },
            onDismiss = { songSearchEditing = null }
        )
    }

    songHistoryTarget?.let { target ->
        val history = remember(target, allDailySongs) {
            allDailySongs.filter { it.songTitle == target.title && it.artistName == target.artist }
                .sortedByDescending { it.date }
        }
        // 點進來只能看不能改，看到寫錯的字也只好退出去自己翻月曆找那天。
        // 跟「喜歡的藝人」那條鑽取路徑一樣，這裡也直接開編輯。
        var editing by remember(target) { mutableStateOf<DailySong?>(null) }
        editing?.let { song ->
            SongEditDialog(
                song = song,
                onSave = { viewModel.updateSong(it); editing = null },
                onDelete = { viewModel.deleteSong(song.id); editing = null },
                onDismiss = { editing = null }
            )
        }
        AlertDialog(
            onDismissRequest = { songHistoryTarget = null },
            title = { Text(target.title) },
            text = {
                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    if (history.isNotEmpty()) {
                        item {
                            Text(
                                "點任一筆可以編輯感受與備註。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }
                    items(history) { song ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { editing = song }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("${song.date}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    song.reaction?.let {
                                        Text(it.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                if (!song.memo.isNullOrBlank()) {
                                    Text(song.memo!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Icon(
                                Icons.Outlined.ChevronRight, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (history.isEmpty()) {
                        item { Text("找不到紀錄", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { songHistoryTarget = null }) { Text("關閉") } }
        )
    }

    // 點「喜歡的藝人」→ 三層鑽取：藝人 → 喜歡過的歌（每首次數）→ 日期 → 預覽並就地編輯。
    artistHistoryTarget?.let { artist ->
        var selSong by remember(artist) { mutableStateOf<String?>(null) }
        var editing by remember(artist) { mutableStateOf<DailySong?>(null) }
        val artistSongs = remember(artist, allDailySongs) { allDailySongs.filter { it.artistName == artist } }
        // 大部分歌只選過一次：那種情況直接把日期顯示在第一層，不用多點一層只為了看一個日期。
        // 選過 ≥2 次才需要鑽進去分辨是哪幾次（onlySong 為 null）。
        val songsByCount = remember(artistSongs) {
            artistSongs.groupBy { it.songTitle }
                .entries.sortedByDescending { it.value.size }
                .map { (title, songs) -> Triple(title, songs.size, songs.singleOrNull()) }
        }
        val datesForSong = remember(selSong, artistSongs) {
            selSong?.let { s -> artistSongs.filter { it.songTitle == s }.sortedByDescending { it.date } } ?: emptyList()
        }

        editing?.let { song ->
            SongEditDialog(
                song = song,
                onSave = { viewModel.updateSong(it); editing = null },
                onDelete = { viewModel.deleteSong(song.id); editing = null },
                onDismiss = { editing = null }
            )
        }

        AlertDialog(
            onDismissRequest = { artistHistoryTarget = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selSong != null) {
                        IconButton(onClick = { selSong = null }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Outlined.ChevronLeft, contentDescription = "返回")
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(selSong ?: artist, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            text = {
                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    if (selSong == null) {
                        // 第一層：喜歡過的歌 + 次數
                        if (songsByCount.isEmpty()) item { Text("找不到紀錄", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        items(songsByCount) { (title, count, onlySong) ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { if (onlySong != null) editing = onlySong else selSong = title }
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (onlySong != null) {
                                    Text(
                                        "${onlySong.date}", style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 10.dp)
                                    )
                                } else {
                                    Text("×$count", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 10.dp))
                                }
                            }
                        }
                    } else {
                        // 第二層：這首歌的每次紀錄（日期）→ 點進去預覽 / 編輯
                        if (datesForSong.isEmpty()) item { Text("找不到紀錄", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        items(datesForSong) { song ->
                            Column(modifier = Modifier.fillMaxWidth().clickable { editing = song }.padding(vertical = 8.dp)) {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("${song.date}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    song.reaction?.let { Text(it.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                                }
                                if (!song.albumName.isNullOrBlank()) Text(song.albumName!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (!song.memo.isNullOrBlank()) Text(song.memo!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { artistHistoryTarget = null }) { Text("關閉") } }
        )
    }

    if (showBaseCityDialog) {
        val cities = listOf(
            "臺北", "新北", "桃園", "新竹", "苗栗", "臺中", "彰化", "南投", "雲林",
            "嘉義", "臺南", "高雄", "屏東", "宜蘭", "花蓮", "臺東", "基隆", "澎湖", "金門", "馬祖"
        )
        AlertDialog(
            onDismissRequest = { showBaseCityDialog = false },
            title = { Text("選擇距離基準地") },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = customBaseInput,
                            onValueChange = { customBaseInput = it },
                            placeholder = { Text("自訂輸入城市") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        TextButton(
                            onClick = {
                                val c = customBaseInput.trim()
                                if (c.isNotBlank()) { viewModel.setHomeBaseCity(c); customBaseInput = ""; showBaseCityDialog = false }
                            },
                            enabled = customBaseInput.isNotBlank()
                        ) { Text("使用") }
                    }
                    Spacer(Modifier.height(4.dp))
                    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                        items(cities) { city ->
                            Text(
                                city,
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { viewModel.setHomeBaseCity(city); showBaseCityDialog = false }
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (city == data.baseCity) FontWeight.Bold else FontWeight.Normal,
                                color = if (city == data.baseCity) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showBaseCityDialog = false }) { Text("關閉") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareSheet(
    periodLabel: String,
    data: StatsData,
    songStats: SongStats,
    periodCovers: List<String> = emptyList(),
    timeline: List<Pair<Concert, List<DailySong>>> = emptyList(),
    isPro: Boolean = false,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var template by remember { mutableStateOf(ShareTemplate.MIDNIGHT) }
    var layout by remember { mutableStateOf(StatsLayout.TOP) }
    var showColorPicker by remember { mutableStateOf(false) }
    // Batch D 新排版管線（PosterRenderer）：null = 沿用現有排版，非 null = 用新的 5 種 TypeStyle。
    // 先當額外選項加進來，不動現有的 renderStatsBitmap 管線。
    var advancedStyle by remember { mutableStateOf<com.bandary.presentation.ui.share.PosterRenderer.TypeStyle?>(null) }
    // 分享內容：四大類最多選兩項（預設總覽 + 藝人）。依選取數量改排版。
    var content by remember { mutableStateOf(setOf(ShareContent.OVERVIEW, ShareContent.ARTISTS)) }
    // 配色跟隨 App 主題：預設帶入目前主題主色（C-1 的四色 / 自訂邏輯）。
    val themePrimaryHex = String.format("#%06X", 0xFFFFFF and MaterialTheme.colorScheme.primary.toArgb())
    // customColor 現在代表「字體顏色」（黑 / 白 / 調色盤自訂），預設白字（預設底圖為深色）。
    var customColor by remember { mutableStateOf<String?>("#FFFFFF") }
    // 預設帶入內建演唱會背景圖（有放素材時），沒有則用漸層底。
    var bgUri by remember {
        mutableStateOf<String?>(
            com.bandary.presentation.ui.share.PosterBackgrounds.availableIndices(context).firstOrNull()
                ?.let { com.bandary.presentation.ui.share.PosterBackgrounds.key(it) }
        )
    }
    // 直接選圖（移除會閃退的裁切器）。渲染時本來就會 center-crop 進 9:16。
    val bgPickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            bgUri = uri.toString()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        // 依目前選項即時算出預覽圖（Canvas 很快）。選了新排版樣式就走 PosterRenderer，否則沿用原本管線。
        fun renderCurrent(): Bitmap {
            // 封面拼圖：把本期所有封面拼成底圖（用 PosterRenderer 疊字），選了拼圖就走海報管線。
            val collage = if (bgUri == COLLAGE_BG) buildCoverCollage(context, periodCovers, 1080, 1920) else null
            val style = advancedStyle ?: if (collage != null) com.bandary.presentation.ui.share.PosterRenderer.TypeStyle.MINIMAL else null
            return if (style != null) {
                var spec = statsToPosterSpec(periodLabel, data, songStats, content, style, customColor ?: "#6B9080",
                    if (bgUri == COLLAGE_BG) null else bgUri, timeline)
                if (collage != null) spec = spec.copy(background = com.bandary.presentation.ui.share.PosterRenderer.Background.Bitmap(collage))
                com.bandary.presentation.ui.share.PosterRenderer.render(context, spec)
            } else {
                renderStatsBitmap(context, periodLabel, data, songStats, content, template, layout, customColor, bgUri, timeline)
            }
        }
        // 算圖搬到背景執行緒。原本是 remember { renderCurrent() }，也就是**在組合階段同步**
        // 畫一張 1080×1920 的圖，而且每動一個選項就重畫一次；封面拼圖那條路徑還會解碼好幾張
        // 封面圖——主執行緒被卡住，操作起來就是一頓一頓的。
        // produceState 會在算完之前保留上一張預覽，不會閃白。
        val previewBmp by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(
            initialValue = null,
            content, template, layout, customColor, bgUri, data, songStats, advancedStyle, periodCovers, timeline
        ) {
            value = withContext(kotlinx.coroutines.Dispatchers.Default) {
                runCatching { renderCurrent().asImageBitmap() }.getOrNull()
            }
        }
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── 即時預覽 ──
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .height(220.dp)
                    .aspectRatio(9f / 16f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                previewBmp?.let { bmp ->
                    androidx.compose.foundation.Image(
                        bitmap = bmp,
                        contentDescription = "分享預覽",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } ?: androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }

            // ── 分享內容（四大類自由組合）。原本限制最多兩項，但每種排版樣式現在都會
            //     把資料塊畫出來、放不下會自動截止，所以不需要再硬性限制數量。
            Text("分享內容", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ShareContent.entries.forEach { sc ->
                    val selected = sc in content
                    FilterChip(
                        selected = selected,
                        // 已選則可取消；未選且未達 2 項才可加入（維持至少 1、至多 2 項）。
                        // 至少留一項，其餘自由加減。
                        onClick = {
                            content = if (selected) (if (content.size > 1) content - sc else content)
                            else content + sc
                        },
                        label = { Text(sc.label, fontSize = 11.sp) }, shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.secondary, selectedLabelColor = Color.White))
                }
            }

            // ── 排版樣式（新）：Batch D 的 PosterRenderer，5 種文字構圖，額外選項不影響原本排版 ──
            Text("排版樣式", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            // 用構圖縮圖取代文字代號（與票根分享共用同一個 StyleThumb）。
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 「預設」＝不走 PosterRenderer，維持原本的統計版面。用同尺寸方塊表示。
                val defSel = advancedStyle == null
                Box(
                    modifier = Modifier.size(width = 48.dp, height = 84.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            androidx.compose.foundation.BorderStroke(
                                if (defSel) 2.dp else 1.dp,
                                if (defSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ),
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { advancedStyle = null },
                    contentAlignment = Alignment.Center
                ) {
                    Text("預設", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                com.bandary.presentation.ui.share.PosterRenderer.TypeStyle.entries.forEach { ts ->
                    com.bandary.presentation.ui.share.StyleThumb(
                        styleItem = ts,
                        selected = advancedStyle == ts,
                        onClick = { advancedStyle = ts }
                    )
                }
            }

            // 字體一律白字（與票根統一），不再提供選色。

            // ── 演唱會背景圖（放在 assets/posters 才出現）。封面拼圖也是一種背景，所以放在同一排第一個 ──
            val posterIdxs = com.bandary.presentation.ui.share.PosterBackgrounds.availableIndices(context)
            if (posterIdxs.isNotEmpty() || periodCovers.isNotEmpty()) {
                Text("演唱會背景", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 封面拼圖：把本期每場演唱會的封面（一場一張）拼滿一張當底圖。
                    if (periodCovers.isNotEmpty()) {
                        val sel = bgUri == COLLAGE_BG
                        Box(
                            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(2.dp))
                                .border(androidx.compose.foundation.BorderStroke(if (sel) 3.dp else 1.dp,
                                    if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline), RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { bgUri = if (sel) null else COLLAGE_BG },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.GridView, "封面拼圖", modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    posterIdxs.forEach { idx ->
                        val key = com.bandary.presentation.ui.share.PosterBackgrounds.key(idx)
                        val thumb = remember(idx) { com.bandary.presentation.ui.share.PosterBackgrounds.load(context, idx, 120, 200) }
                        val sel = bgUri == key
                        Box(
                            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(2.dp))
                                .border(androidx.compose.foundation.BorderStroke(if (sel) 3.dp else 1.dp,
                                    if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline), RoundedCornerShape(2.dp))
                                .clickable { bgUri = key }
                        ) {
                            thumb?.let {
                                androidx.compose.foundation.Image(bitmap = it.asImageBitmap(), contentDescription = null,
                                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }

            // ── 本期各演唱會的封面（可直接當底圖）──
            if (periodCovers.isNotEmpty()) {
                Text("本期演唱會封面", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    periodCovers.forEach { uri ->
                        val sel = bgUri == uri
                        Box(
                            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(2.dp))
                                // 中性底：封面讀不到時是低調灰底，不會出現突兀的淺色（唇色）空框。
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(androidx.compose.foundation.BorderStroke(if (sel) 3.dp else 1.dp,
                                    if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline), RoundedCornerShape(2.dp))
                                .clickable { bgUri = uri }
                        ) {
                            AsyncImage(model = uri, contentDescription = null,
                                contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                        }
                    }
                }
            }

            // ── 底圖（排版改由上方「排版樣式」決定，不再有左上/左下對齊）──
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("底圖", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                if (bgUri != null) {
                    val pIdx = com.bandary.presentation.ui.share.PosterBackgrounds.indexOfKey(bgUri)
                    if (pIdx != null) {
                        val bmp = remember(pIdx) { com.bandary.presentation.ui.share.PosterBackgrounds.load(context, pIdx, 120, 200) }
                        bmp?.let {
                            androidx.compose.foundation.Image(bitmap = it.asImageBitmap(), contentDescription = null,
                                contentScale = ContentScale.Crop, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(2.dp)))
                        }
                    } else {
                        AsyncImage(model = bgUri, contentDescription = null,
                            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(2.dp)), contentScale = ContentScale.Crop)
                    }
                    TextButton(onClick = { bgUri = null }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("移除底圖", fontSize = 12.sp) }
                } else {
                    TextButton(onClick = { bgPickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        contentPadding = PaddingValues(horizontal = 6.dp)) { Text("選底圖", fontSize = 12.sp) }
                }
            }

            var downloadMsg by remember { mutableStateOf<String?>(null) }
            // 按鈕也不能在 onClick 裡同步算圖——那會在按下去的當下卡住主執行緒。
            val exportScope = rememberCoroutineScope()
            var exporting by remember { mutableStateOf(false) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        if (exporting) return@Button
                        exporting = true
                        exportScope.launch {
                            val bmp = withContext(kotlinx.coroutines.Dispatchers.Default) { renderCurrent() }
                            BitmapExporter.shareOnly(context, bmp, "bandary_wrapped")
                            exporting = false
                            onDismiss()
                        }
                    },
                    enabled = !exporting,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("分享", fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = {
                        // 下載圖片＝Pro：未解鎖時提示（分享仍免費，可從系統分享單自行儲存）。
                        if (!isPro) {
                            downloadMsg = "下載到相簿為 Pro 功能，可在「我的 → 付費功能」解鎖；分享不受限"
                            return@OutlinedButton
                        }
                        if (exporting) return@OutlinedButton
                        exporting = true
                        exportScope.launch {
                            val bmp = withContext(kotlinx.coroutines.Dispatchers.Default) { renderCurrent() }
                            val uri = BitmapExporter.downloadToGallery(context, bmp, "bandary_wrapped")
                            downloadMsg = if (uri != null) "已下載到相簿" else "下載失敗"
                            exporting = false
                        }
                    },
                    enabled = !exporting,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(if (isPro) Icons.Outlined.Download else Icons.Outlined.Lock, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("下載", fontWeight = FontWeight.SemiBold)
                }
            }
            downloadMsg?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun templateSwatch(t: ShareTemplate): Color = when (t) {
    ShareTemplate.MIDNIGHT -> Color(0xFF5A5650)
    ShareTemplate.SUNSET -> Color(0xFF6E5F52)
    ShareTemplate.FOREST -> Color(0xFF5E6B58)
}

/** 把統計資料轉成 PosterRenderer 的 Spec（Batch D 新排版管線，額外選項）。 */
private fun statsToPosterSpec(
    periodLabel: String,
    data: StatsData,
    songStats: SongStats,
    content: Set<ShareContent>,
    style: com.bandary.presentation.ui.share.PosterRenderer.TypeStyle,
    accentHex: String,
    bgUri: String?,
    timeline: List<Pair<Concert, List<DailySong>>> = emptyList()
): com.bandary.presentation.ui.share.PosterRenderer.Spec {
    val background = when {
        bgUri == null -> com.bandary.presentation.ui.share.PosterRenderer.Background.None
        com.bandary.presentation.ui.share.PosterBackgrounds.indexOfKey(bgUri) != null ->
            com.bandary.presentation.ui.share.PosterRenderer.Background.Preset(
                com.bandary.presentation.ui.share.PosterBackgrounds.indexOfKey(bgUri)!!
            )
        else -> com.bandary.presentation.ui.share.PosterRenderer.Background.Photo(bgUri)
    }
    val blocks = content.take(2).map { c ->
        when (c) {
            ShareContent.OVERVIEW -> com.bandary.presentation.ui.share.PosterRenderer.Block(
                "現場總覽",
                listOf("演出 ${data.totalShows} 場", "藝人 ${data.totalArtists} 組", "花費 NT$${"%,d".format(data.totalSpent.toInt())}")
            )
            ShareContent.ARTISTS -> com.bandary.presentation.ui.share.PosterRenderer.Block(
                "最愛藝人", data.bands.take(4).map { "${it.artistName} ×${it.count}" }
            )
            ShareContent.VENUES -> com.bandary.presentation.ui.share.PosterRenderer.Block(
                "常去場館", data.venues.take(4).map { "${it.venue} ×${it.count}" }
            )
            // 大部分歌都只選過一次：×1 沒有資訊量，秀出藝人比秀「×1」更看得出品味。
            // 選過 ≥2 次才值得秀次數（代表真的很愛聽）。
            ShareContent.SONGS -> com.bandary.presentation.ui.share.PosterRenderer.Block(
                "喜歡的歌",
                buildList {
                    add("選了 ${songStats.totalSongs} 首・認識 ${songStats.distinctArtists} 位藝人")
                    songStats.topSongs.take(3).forEach { s ->
                        add(if (s.count > 1) "${s.title} ×${s.count}" else "${s.title}・${s.artist}")
                    }
                }
            )
            ShareContent.TIMELINE -> com.bandary.presentation.ui.share.PosterRenderer.Block(
                "看團＋選歌時間軸", timeline.map { (c, songs) -> formatTimelineLine(c, songs) }
            )
        }
    }
    return com.bandary.presentation.ui.share.PosterRenderer.Spec(
        background = background,
        accentHex = accentHex,        // accentHex 現在＝使用者選的字體顏色（黑 / 白 / 自訂）
        style = style,
        title = periodLabel,
        subtitle = "現場回顧",
        day = data.totalShows.toString(),
        month = "SHOWS",
        fullDate = periodLabel,
        venue = data.topVenue ?: "",
        city = null,
        blocks = blocks,
        textColorHex = accentHex      // 內文也用同一個字體顏色
    )
}

// ── Canvas templates ──────────────────────────────────────────
private fun renderStatsBitmap(
    context: Context,
    periodLabel: String,
    data: StatsData,
    songStats: SongStats,
    content: Set<ShareContent>,
    template: ShareTemplate,
    layout: StatsLayout,
    customColorHex: String?,
    bgUri: String?,
    timeline: List<Pair<Concert, List<DailySong>>> = emptyList()
): Bitmap {
    val w = 1080; val h = 1920
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // 背景：演唱會背景圖 / 使用者底圖 > 自訂色漸層 > 範本漸層
    val bgBmp = bgUri?.let { key ->
        val pIdx = com.bandary.presentation.ui.share.PosterBackgrounds.indexOfKey(key)
        if (pIdx != null) com.bandary.presentation.ui.share.PosterBackgrounds.load(context, pIdx, w, h)
        else runCatching {
            context.contentResolver.openInputStream(Uri.parse(key))?.use { s -> BitmapFactory.decodeStream(s) }
        }.getOrNull()
    }
    if (bgBmp != null) {
        val scale = maxOf(w.toFloat() / bgBmp.width, h.toFloat() / bgBmp.height)
        val dw = bgBmp.width * scale; val dh = bgBmp.height * scale
        val left = (w - dw) / 2f; val top = (h - dh) / 2f
        canvas.drawBitmap(bgBmp, null, RectF(left, top, left + dw, top + dh), null)
        val scrim = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
                AndroidColor.parseColor("#66000000"), AndroidColor.parseColor("#DD000000"), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), scrim)
    } else {
        val (c1, c2) = if (customColorHex != null) {
            val base = AndroidColor.parseColor(customColorHex)
            base to ColorUtils.blendARGB(base, AndroidColor.BLACK, 0.55f)
        } else when (template) {
            ShareTemplate.MIDNIGHT -> AndroidColor.parseColor("#5A5650") to AndroidColor.parseColor("#36332E")
            ShareTemplate.SUNSET -> AndroidColor.parseColor("#6E5F52") to AndroidColor.parseColor("#473D34")
            ShareTemplate.FOREST -> AndroidColor.parseColor("#5E6B58") to AndroidColor.parseColor("#3A4336")
        }
        val bgPaint = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), c1, c2, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)
    }

    val white = AndroidColor.WHITE
    val dim = AndroidColor.parseColor("#CCFFFFFF")

    fun paint(sizePx: Float, bold: Boolean, color: Int = white, align: Paint.Align = Paint.Align.LEFT) = Paint().apply {
        this.color = color
        textSize = sizePx
        isAntiAlias = true
        textAlign = align
        typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    // 標題（共用）；副標依內容而定
    val subtitle = when {
        content == setOf(ShareContent.SONGS) -> "音樂品味"
        ShareContent.SONGS in content && ShareContent.OVERVIEW !in content -> "音樂品味"
        else -> "現場回顧"
    }
    // 靠左原則；左上對齊 = 標題區在上（dy=0），左下對齊 = 整體下移到下半部。
    val dy = if (layout == StatsLayout.BOTTOM) 640f else 0f
    val leftX = 80f
    val align = Paint.Align.LEFT
    canvas.drawText("BANDARY", 80f, 150f + dy, paint(40f, true, dim))
    canvas.drawText(periodLabel, 80f, 230f + dy, paint(56f, true))
    canvas.drawText(subtitle, 80f, 320f + dy, paint(96f, true))

    var y = 470f + dy

    val yLimit = 1680f  // 不畫到底部條碼區
    fun sectionHeader(text: String) {
        if (y > yLimit) return
        y += 20f
        canvas.drawText(text, leftX, y, paint(50f, true, dim, align)); y += 78f
    }
    fun line(label: String, value: String) {
        if (y > yLimit) return
        canvas.drawText(label, 80f, y, paint(44f, false, dim))
        val vp = paint(50f, true)
        val vw = Rect().also { vp.getTextBounds(value, 0, value.length, it) }.width()
        canvas.drawText(value, w - 80f - vw, y, vp)
        y += 88f
    }

    // ── OVERVIEW ──
    if (ShareContent.OVERVIEW in content) {
        canvas.drawText("${data.totalShows}", leftX, y + 150f, paint(200f, true, white, align));
        canvas.drawText("場演出", 100f, y + 235f, paint(52f, false, dim, align))
        y += 320f
        line("看了不同藝人", "${data.totalArtists} 組")
        line("去過不同場館", "${data.totalVenues} 個")
        line("總花費", "NT$${"%,d".format(data.totalSpent.toInt())}")
        line("估算交通距離", "${data.travelKm} km")
        data.farthestCity?.let { line("最遠演唱會", it) }
    }

    // ── ARTISTS ──
    if (ShareContent.ARTISTS in content && data.bands.isNotEmpty()) {
        sectionHeader("最常看的藝人")
        data.bands.take(5).forEachIndexed { i, b -> line("${i + 1}. ${b.artistName}", "${b.count} 場") }
    }

    // ── VENUES ──
    if (ShareContent.VENUES in content && data.venues.isNotEmpty()) {
        sectionHeader("常去的場館")
        data.venues.take(5).forEachIndexed { i, v -> line("${i + 1}. ${v.venue}", "${v.count} 場") }
    }

    // ── SONGS ──
    if (ShareContent.SONGS in content) {
        sectionHeader("喜歡的歌")
        line("選歌天數", "${songStats.daysWithSongs} 天")
        line("選過歌曲", "${songStats.totalSongs} 首")
        songStats.topArtists.firstOrNull()?.let { line("最愛藝人", "${it.name}・${it.pct}%") }
        // 大部分歌都只選過一次，「×1」沒有資訊量：只選過一次時改秀藝人名，選過 ≥2 次才秀次數。
        songStats.topSongs.take(3).forEachIndexed { i, s ->
            line("${i + 1}. ${s.title}", if (s.count > 1) "×${s.count}" else s.artist)
        }
    }

    // ── TIMELINE：看團之後選了哪些歌，取代脫離場次的排行榜 ──
    if (ShareContent.TIMELINE in content && timeline.isNotEmpty()) {
        sectionHeader("看團＋選歌時間軸")
        timeline.forEach { (c, songs) ->
            val date = "${c.dateTime.date.monthNumber}/${c.dateTime.date.dayOfMonth}"
            val titles = songs.map { it.songTitle }.distinct()
            val shown = titles.take(2).joinToString("、")
            val more = (titles.size - 2).let { if (it > 0) "（+$it）" else "" }
            line("$date ${c.artistName.ifBlank { c.name }}", "$shown$more")
        }
    }

    // 假條碼 + hashtag（共用）
    val barPaint = Paint().apply { color = white }
    var bx = 80f
    while (bx < w - 80f) {
        val bw = (1..5).random().toFloat() * 4f
        canvas.drawRect(bx, 1760f, bx + bw, 1840f, barPaint)
        bx += bw + 5f
    }
    canvas.drawText("#Bandary 聽團曆", 80f, 1900f, paint(34f, false, dim))

    return bitmap
}

// ── Reusable cards ────────────────────────────────────────────
@Composable
private fun WrappedHeroCard(
    period: String,
    shows: Int,
    spent: Int,
    mostWatched: List<StatsBand> = emptyList(),
    topSpender: List<ArtistSpend> = emptyList(),
    tiedWatchCount: Int = mostWatched.size,   // 並列第一的實際人數（mostWatched 已截斷）
    tiedSpendCount: Int = topSpender.size
) {
    // 頂部 KPI 摘要（spec §6.9.1）：總場數 / 總花費 + 最愛藝人，進頁 1 秒先看到結論。
    // 「最愛藝人」拆成看最多次 / 花最多錢兩種——同一個人時併成一行，避免重複佔版面。
    // 之前把藝人名塞進第三個 1/3 欄位，長名字會折成兩行、和旁邊的大數字對不齊，所以改成獨立整行。
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(cs.primaryContainer, cs.primaryContainer.copy(alpha = 0.35f))
                )
            )
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(period, style = MaterialTheme.typography.labelMedium, color = cs.onPrimaryContainer.copy(alpha = 0.8f), fontWeight = FontWeight.SemiBold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val showsStr = "$shows"
            val showsSize = if (showsStr.length >= 4) 30 else 38
            Column(modifier = Modifier.weight(1f)) {
                Text("看了", style = MaterialTheme.typography.labelSmall, color = cs.onPrimaryContainer.copy(alpha = 0.7f))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(showsStr, fontSize = showsSize.sp, fontWeight = FontWeight.Bold, color = cs.onPrimaryContainer, maxLines = 1, softWrap = false, lineHeight = (showsSize + 2).sp)
                    Text(" 場", style = MaterialTheme.typography.labelMedium, color = cs.onPrimaryContainer.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 5.dp))
                }
            }
            // 花費金額太大時自動縮字級，避免超出版面。
            val spentStr = "%,d".format(spent)
            val spentSize = when {
                spentStr.length >= 8 -> 18
                spentStr.length >= 6 -> 22
                else -> 28
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("花費", style = MaterialTheme.typography.labelSmall, color = cs.onPrimaryContainer.copy(alpha = 0.7f))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("NT$", style = MaterialTheme.typography.labelSmall, color = cs.onPrimaryContainer.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 3.dp))
                    Text(spentStr, fontSize = spentSize.sp, fontWeight = FontWeight.Bold, color = cs.onPrimaryContainer, maxLines = 1, softWrap = false, lineHeight = (spentSize + 2).sp)
                }
            }
        }

        // 最愛藝人：獨立整行，名字有整個寬度可用，不會被擠成兩行。
        // 同場次數 / 同金額並列第一時會有多位；最多列 3 位，再多就以「等 N 位」收尾，
        // 否則十幾位並列時這一行會爆掉。
        val watchNames = mostWatched.map { it.artistName }.filter { it.isNotBlank() }
        val spendNames = topSpender.map { it.name }.filter { it.isNotBlank() }
        fun joinCapped(names: List<String>, total: Int): String {
            val head = names.take(TIED_NAME_LIMIT).joinToString("、")
            return if (total > TIED_NAME_LIMIT) "$head 等 $total 位" else head
        }
        val watchName = watchNames.takeIf { it.isNotEmpty() }?.let { joinCapped(it, tiedWatchCount) }
        val spendName = spendNames.takeIf { it.isNotEmpty() }?.let { joinCapped(it, tiedSpendCount) }
        if (watchName != null || spendName != null) {
            @Composable
            fun favLine(label: String, name: String, value: String) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onPrimaryContainer.copy(alpha = 0.7f),
                        maxLines = 1, softWrap = false
                    )
                    Text(
                        name,
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                        color = cs.onPrimaryContainer,
                        // 並列第一有多位時允許折到第二行，不要把名字截掉只剩第一位。
                        maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        value,
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onPrimaryContainer.copy(alpha = 0.85f),
                        maxLines = 1, softWrap = false
                    )
                }
            }
            if (watchName != null && watchName == spendName) {
                // 完全同一組藝人：併成一行，值同時顯示場次與金額。
                favLine(
                    "最愛藝人", watchName,
                    "${mostWatched.first().count} 場 · NT$${"%,d".format(topSpender.first().amount.toInt())}"
                )
            } else {
                watchName?.let {
                    favLine(
                        "看最多次",
                        it, "${mostWatched.first().count} 場"
                    )
                }
                spendName?.let {
                    favLine(
                        "花最多",
                        it, "NT$${"%,d".format(topSpender.first().amount.toInt())}"
                    )
                }
            }
        }
    }
}

/** [StatGrid] 的一格：標籤、數值、單位（沒有單位就留空）。 */
private data class StatCell(val label: String, val value: String, val unit: String = "")

/**
 * 概覽數字格：**一張卡、細線分隔、單一顏色**。
 *
 * ## 原本是什麼樣子
 * 這些數字原本是 10 張 [StatCard]，兩兩一排、分成 5 個 `item{}`，
 * 每張都有自己的 12% 色底、4dp 色條和彩色數字，顏色從 5 色色盤輪替。
 *
 * ## 為什麼要改
 * **顏色沒有在表達任何事。**「藝人」是綠色、「場館」是霧藍、「去過縣市」是藕粉，
 * 這三個顏色不代表大小、不代表分類、不代表好壞——它們只是「第 1、2、3 張卡」。
 * 讀者會下意識去找顏色的意思，找不到，然後畫面已經先吵起來了。
 *
 * 而且這一頁上面就是沙綠色的 KPI 大卡和整塊沙綠的回顧入口。
 * 剛用兩張卡講完「這個 App 是綠色的」，下一秒就是粉紫棕藍——**自己打自己**。
 *
 * 版面上也不划算：5 排 × 18dp 內距的卡片 ≈ 兩個半螢幕高，
 * 使用者要滑過 10 個同等大小的數字才會看到第一張圖表。
 * 10 個單位互不相同、彼此也沒有關係的數字，本質是**一張對照表**，
 * 不是 10 個各自獨立的重點；表格就該長得像表格。
 *
 * ## 現在的作法
 * 一張描邊卡、2 欄、細線切格。顏色只剩「文字層級」：
 * 標籤是 onSurfaceVariant、數字是 onSurface、單位再淡一階。
 * 想強調的東西交給**字級**，不是顏色——需要顏色的時候（圖表要分系列）才給顏色。
 */
@Composable
private fun StatGrid(cells: List<StatCell>, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = Radius.Card,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, line)
    ) {
        Column {
            cells.chunked(2).forEachIndexed { r, row ->
                if (r > 0) androidx.compose.material3.HorizontalDivider(color = line)
                // 同一排的兩格要等高，分隔線才會從上切到下。中文字（如「屏東」）用的
                // CJK 備援字型行高比數字高，不鎖等高那一格會往下凸、線也跟著歪。
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    row.forEachIndexed { c, cell ->
                        if (c > 0) androidx.compose.material3.VerticalDivider(
                            color = line, modifier = Modifier.fillMaxHeight()
                        )
                        StatGridCell(cell, Modifier.weight(1f))
                    }
                    // 奇數格：補一個空格 + 分隔線，右半邊才不會整排空掉、看起來像壞了。
                    if (row.size == 1) {
                        androidx.compose.material3.VerticalDivider(
                            color = line, modifier = Modifier.fillMaxHeight()
                        )
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * 圖表底下的一句話重點。
 *
 * 刻意長得很輕（細線 + 小字 + 次要色）：它是圖的註腳，不是標題。
 * 做得太重會變成跟圖搶視線，兩個都讀不進去。
 */
@Composable
private fun ChartInsight(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatGridCell(cell: StatCell, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            cell.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                cell.value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (cell.unit.isNotEmpty()) {
                Text(
                    " ${cell.unit}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, softWrap = false,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    unit: String,
    highlight: Boolean = false,
    modifier: Modifier = Modifier,
    accent: Color = STAT_ACCENT
) {
    // 左側色條 + 柔和色底 + 彩色數字。
    //
    // 圓角從 0 改成 12：原本註解寫「直角方塊（不要圓弧）強調編輯/設計感」，
    // 但這一頁其他所有卡片（圖表卡、排行卡、本週回顧）都是 12～16 的圓角，
    // 只有數據卡是直角，看起來不像刻意的對比，像是漏改。
    // 要走編輯風就整頁一起走，混在一起的結果是兩邊都不像。
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.12f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(accent))
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        value,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (unit.isNotEmpty()) {
                        Text(
                            unit,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartCard(title: String, data: List<Pair<Int, Int>>, insight: String? = null) {
    val maxVal = (data.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    val barColor = MaterialTheme.colorScheme.primaryContainer
    val topColor = MaterialTheme.colorScheme.primary
    val numArgb = MaterialTheme.colorScheme.onSurface.toArgb()
    val lblArgb = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (title.isNotBlank()) Text(title, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            // Canvas 柱狀圖：所有柱子共用底線、數字在各自柱頂正上方、月份齊底一線，保證不歪。
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                val n = data.size.coerceAtLeast(1)
                val gap = 6.dp.toPx()
                val barW = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
                val labelH = 16.dp.toPx()
                val numH = 15.dp.toPx()
                val baseline = size.height - labelH
                val maxBarH = (baseline - numH).coerceAtLeast(1f)
                val numPaint = android.graphics.Paint().apply {
                    color = numArgb; textSize = 10.sp.toPx(); textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true; isFakeBoldText = true
                }
                val lblPaint = android.graphics.Paint().apply {
                    color = lblArgb; textSize = 9.sp.toPx(); textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true
                }
                data.forEachIndexed { i, pair ->
                    val (month, count) = pair
                    val x = i * (barW + gap)
                    val cx = x + barW / 2f
                    val h = if (count > 0) (count.toFloat() / maxVal * maxBarH).coerceAtLeast(3.dp.toPx()) else 2.dp.toPx()
                    val top = baseline - h
                    drawRoundRect(
                        color = if (count == maxVal && count > 0) topColor else barColor,
                        topLeft = androidx.compose.ui.geometry.Offset(x, top),
                        size = androidx.compose.ui.geometry.Size(barW, h),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
                    )
                    if (count > 0) {
                        drawContext.canvas.nativeCanvas.drawText("$count", cx, top - 4.dp.toPx(), numPaint)
                    }
                    drawContext.canvas.nativeCanvas.drawText("$month", cx, size.height - 3.dp.toPx(), lblPaint)
                }
            }
            insight?.let { ChartInsight(it) }
        }
    }
}

// 選歌鑽取的第三層：預覽某天的一首歌，並就地編輯感受 / 備註，或刪除。
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SongEditDialog(
    song: DailySong,
    onSave: (DailySong) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var reaction by remember(song.id) { mutableStateOf(song.reaction) }
    var memo by remember(song.id) { mutableStateOf(song.memo ?: "") }
    var confirmDelete by remember(song.id) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(song.songTitle.ifBlank { "這首歌" }, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            com.bandary.presentation.ui.components.DialogBody(spacing = 10.dp) {
                Text(
                    buildString {
                        append("${song.date}")
                        if (song.artistName.isNotBlank()) append("　·　${song.artistName}")
                        if (!song.albumName.isNullOrBlank()) append("　·　${song.albumName}")
                    },
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("感受", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.bandary.domain.model.SongReaction.entries.forEach { r ->
                        FilterChip(
                            selected = reaction == r,
                            onClick = { reaction = if (reaction == r) null else r },
                            label = { Text(r.label, fontSize = 13.sp) },
                            shape = RoundedCornerShape(8.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = { Text("備註") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (confirmDelete) {
                    Text("再按一次「刪除」以確認移除這筆紀錄。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(song.copy(reaction = reaction, memo = memo.trim().ifBlank { null })) }) {
                Text("儲存", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = { if (confirmDelete) onDelete() else confirmDelete = true }) {
                Text(if (confirmDelete) "確認刪除" else "刪除", color = MaterialTheme.colorScheme.error)
            }
        }
    )
}

// ── 空資料時的範例預覽：用假資料展示「累積後的統計長怎樣」──────────
/** 資料少於這個數時，統計頁保留「看範例」入口。 */
private const val SAMPLE_THRESHOLD = 5

@Composable
private fun SampleToggle(expanded: Boolean, onToggle: () -> Unit) {
    androidx.compose.material3.OutlinedButton(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(if (expanded) "收起範例" else "看看記多了之後的統計長怎樣（範例）")
    }
}

@Composable
private fun SamplePreviewHeader(caption: String = "以下為示意，記錄演出後會換成你的真實數據") {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    "範例預覽",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val SAMPLE_MONTHLY = listOf(
    "2025/07" to 1, "2025/08" to 0, "2025/09" to 2, "2025/10" to 1, "2025/11" to 3, "2025/12" to 2,
    "2026/01" to 1, "2026/02" to 4, "2026/03" to 2, "2026/04" to 3, "2026/05" to 5, "2026/06" to 3
)

/** 在 LazyColumn 中放入一組範例統計卡片（半透明，僅示意）。 */
private fun LazyListScope.sampleStatCards() {
    val cumulative = run {
        var acc = 0
        SAMPLE_MONTHLY.map { (k, v) -> acc += v; k to acc }
    }
    val types = listOf("演唱會" to 14, "音樂節" to 5, "見面會" to 3, "線上直播" to 2)
    val price = listOf("免費" to 5, "1–800" to 6, "800–1.5k" to 7, "1.5k–2.5k" to 4, "2.5k–4k" to 2, "4k+" to 1)
    val weekday = listOf(2, 1, 3, 2, 4, 8, 6)
    val heat = listOf(
        YearHeat(2025, listOf(0, 1, 0, 2, 1, 0, 1, 2, 1, 3, 2, 1)),
        YearHeat(2026, listOf(1, 2, 3, 2, 4, 3, 0, 0, 0, 0, 0, 0))
    )
    val artistMin = listOf("落日飛車" to 360, "老王樂隊" to 300, "告五人" to 260, "茄子蛋" to 210, "美秀集團" to 180)
    val sampleItems: List<@Composable () -> Unit> = listOf(
        { LineChartCard(title = "累積演唱會・每月", data = cumulative, monthly = SAMPLE_MONTHLY) },
        { DonutChartCard(title = "演出類型分布", data = types) },
        { PriceHistogramCard(buckets = price) },
        { WeekdayBarsCard(counts = weekday) },
        { MonthYearHeatmapCard(rows = heat) },
        { DurationRankCard(title = "藝人累積觀看時長", data = artistMin) }
    )
    items(sampleItems.size) { idx ->
        Box(modifier = Modifier.alpha(0.55f)) { sampleItems[idx]() }
    }
}

/** 選歌統計的範例卡片（半透明，僅示意）。 */
private fun LazyListScope.sampleSongCards() {
    val monthly = listOf(1 to 18, 2 to 22, 3 to 25, 4 to 19, 5 to 28, 6 to 31, 7 to 26, 8 to 30, 9 to 12)
    val weekday = listOf("一" to 14, "二" to 12, "三" to 15, "四" to 13, "五" to 20, "六" to 24, "日" to 22)
    val reactions = listOf("精彩" to 34, "收藏" to 21, "感動" to 17, "難忘" to 9)
    val podium = listOf("告五人" to "42 首", "落日飛車" to "35 首", "草東沒有派對" to "27 首")
    val sampleItems: List<@Composable () -> Unit> = listOf(
        { ChartCard(title = "每月選歌數", data = monthly) },
        { DonutChartCard(title = "選歌感受分布", data = reactions) },
        { LabeledBarCard(title = "一週哪幾天最常記歌", data = weekday) },
        { PodiumCard(podium, onClick = {}) }
    )
    items(sampleItems.size) { idx ->
        Box(modifier = Modifier.alpha(0.55f)) { sampleItems[idx]() }
    }
}

// 累積曲線圖（上）＋ 每月長條（下）：同一 x 軸；可拖曳看各月的累積與當月場數。
@Composable
private fun LineChartCard(
    title: String,
    data: List<Pair<String, Int>>,
    monthly: List<Pair<String, Int>> = emptyList(),
    formatValue: (Int) -> String = { "$it 場" },   // 累積值格式（場數 vs 金額）
    accent: Color? = null,       // 各圖表自己的主色，兩張累積圖才不會長得一模一樣
    insight: String? = null      // 只屬於這張圖的一句解讀
) {
    val maxVal = (data.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    val maxMonthly = (monthly.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    val lineColor = accent ?: MaterialTheme.colorScheme.primary
    val barColor = lineColor.copy(alpha = 0.28f)
    val indicatorColor = lineColor.copy(alpha = 0.4f)
    // 曲線下方的漸層填色：把「累積」這件事畫成一塊會長大的面積，比一條細線更讀得出成長。
    val areaBrush = androidx.compose.ui.graphics.Brush.verticalGradient(
        listOf(lineColor.copy(alpha = 0.26f), lineColor.copy(alpha = 0.02f))
    )
    val lblArgb = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val n = data.size
    var selected by remember(data) { mutableStateOf<Int?>(null) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // 標題與動態數值分兩行：花費的數值字串（累積 NT$… · 年月 (當月 …)）很長，
            // 與標題擠在同一行會撞在一起跑版；改成標題在上、數值獨佔一行（可換行）就不會擠。
            Column(modifier = Modifier.fillMaxWidth()) {
                if (title.isNotBlank()) Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                val sel = selected?.coerceIn(0, n - 1)
                Text(
                    if (sel != null) {
                        val m = monthly.getOrNull(sel)?.second
                        "累積 ${formatValue(data[sel].second)} · ${data[sel].first}" + (m?.let { " · 當月 ${formatValue(it)}" } ?: "")
                    } else "累積 ${formatValue(data.last().second)}",
                    style = MaterialTheme.typography.labelMedium, color = lineColor, fontWeight = FontWeight.Bold
                )
                if (insight != null) {
                    Text(insight, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // 圖例：長條=每月（PDF）、曲線=累積（CDF）
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(9.dp).background(barColor))
                    Text("每月", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(width = 12.dp, height = 2.dp).background(lineColor))
                    Text("累積", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("按住左右拖曳看各月", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.foundation.Canvas(
                modifier = Modifier.fillMaxWidth().height(168.dp)
                    .pointerInput(data) {
                        fun idxFrom(x: Float) = if (n <= 1) 0 else ((x / size.width) * (n - 1)).roundToInt().coerceIn(0, n - 1)
                        detectTapGestures(
                            onPress = { pos -> selected = idxFrom(pos.x); tryAwaitRelease(); selected = null },
                            onTap = { pos -> selected = idxFrom(pos.x) }
                        )
                    }
                    .pointerInput(data) {
                        fun idxFrom(x: Float) = if (n <= 1) 0 else ((x / size.width) * (n - 1)).roundToInt().coerceIn(0, n - 1)
                        detectDragGestures(onDragEnd = { selected = null }, onDragCancel = { selected = null }) { change, _ ->
                            selected = idxFrom(change.position.x); change.consume()
                        }
                    }
            ) {
                if (n < 2) return@Canvas
                val labelH = 16.dp.toPx()
                val topPad = 10.dp.toPx()
                // 單一繪圖區：長條(每月，PDF)與曲線(累積，CDF)共用同一組 x/y 軸、疊在一起。
                val plotBottom = size.height - labelH
                val plotTop = topPad
                val plotH = plotBottom - plotTop
                val stepX = size.width / (n - 1)
                // 淡橫向格線（統計圖表感）
                val gridColor = androidx.compose.ui.graphics.Color(lblArgb).copy(alpha = 0.18f)
                for (g in 0..4) {
                    val gy = plotTop + plotH * g / 4f
                    drawLine(gridColor, androidx.compose.ui.geometry.Offset(0f, gy), androidx.compose.ui.geometry.Offset(size.width, gy), strokeWidth = 1f)
                }
                // 每月長條（左軸尺度 maxMonthly）——先畫，半透明當底層。頂端圓角，不再是硬邊方塊。
                val barW = (stepX * 0.7f).coerceIn(2f, 26f)
                val barRadius = androidx.compose.ui.geometry.CornerRadius(minOf(barW / 2f, 3.dp.toPx()))
                monthly.forEachIndexed { i, p ->
                    val h = (p.second.toFloat() / maxMonthly * plotH).coerceAtLeast(if (p.second > 0) 2.dp.toPx() else 0f)
                    if (h > 0f) drawRoundRect(
                        color = barColor,
                        topLeft = androidx.compose.ui.geometry.Offset(i * stepX - barW / 2, plotBottom - h),
                        size = androidx.compose.ui.geometry.Size(barW, h),
                        cornerRadius = barRadius
                    )
                }
                // 累積曲線（右軸尺度 maxVal）——疊在長條上方。
                fun yForLine(v: Int) = plotTop + (1f - v.toFloat() / maxVal) * plotH
                val points = data.mapIndexed { i, p -> androidx.compose.ui.geometry.Offset(i * stepX, yForLine(p.second)) }
                // 曲線下方的漸層面積：封閉路徑（曲線 → 右下 → 左下）後填色。
                val area = androidx.compose.ui.graphics.Path().apply {
                    moveTo(points.first().x, plotBottom)
                    points.forEach { lineTo(it.x, it.y) }
                    lineTo(points.last().x, plotBottom)
                    close()
                }
                drawPath(area, brush = areaBrush)
                for (i in 0 until points.size - 1) drawLine(lineColor, points[i], points[i + 1], strokeWidth = 2.5.dp.toPx())
                points.forEach { drawCircle(lineColor, radius = 2.dp.toPx(), center = it) }
                // 選中：垂直指示線 + 放大點
                val sel = selected?.coerceIn(0, n - 1)
                if (sel != null) {
                    val p = points[sel]
                    drawLine(indicatorColor, androidx.compose.ui.geometry.Offset(p.x, plotTop), androidx.compose.ui.geometry.Offset(p.x, plotBottom), strokeWidth = 1.5.dp.toPx())
                    drawCircle(lineColor, radius = 5.dp.toPx(), center = p)
                }
                // 時間概要：x 軸約 4 個年月標籤
                val lblPaint = android.graphics.Paint().apply { color = lblArgb; textSize = 9.sp.toPx(); isAntiAlias = true }
                val ticks = 4
                for (t in 0 until ticks) {
                    val idx = (t * (n - 1) / (ticks - 1)).coerceIn(0, n - 1)
                    lblPaint.textAlign = when (t) {
                        0 -> android.graphics.Paint.Align.LEFT
                        ticks - 1 -> android.graphics.Paint.Align.RIGHT
                        else -> android.graphics.Paint.Align.CENTER
                    }
                    drawContext.canvas.nativeCanvas.drawText(data[idx].first, (idx * stepX), size.height - 3.dp.toPx(), lblPaint)
                }
            }
        }
    }
}

// 有趣數字：平均每幾天看一場、最狂的一個月。
@Composable
private fun FunFactsCard(firstShow: LocalDate, lastShow: LocalDate, totalShows: Int, monthly: List<Pair<String, Int>>) {
    val spanDays = firstShow.daysUntil(lastShow).coerceAtLeast(0)
    val avgGap = if (totalShows > 1) (spanDays.toFloat() / (totalShows - 1)) else 0f
    val busiest = monthly.maxByOrNull { it.second }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("整體摘要", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.Top) {
                SummaryCell(
                    modifier = Modifier.weight(1f),
                    value = if (avgGap > 0) "${avgGap.roundToInt()}" else "—",
                    unit = if (avgGap > 0) "天" else "",
                    label = "平均間隔", caption = "每場之間",
                    accent = STAT_ACCENT
                )
                SummaryDivider()
                SummaryCell(
                    modifier = Modifier.weight(1f),
                    value = busiest?.second?.toString() ?: "—",
                    unit = if (busiest != null) "場" else "",
                    label = "單月最高", caption = busiest?.first ?: "—",
                    accent = STAT_ACCENT
                )
                SummaryDivider()
                SummaryCell(
                    modifier = Modifier.weight(1f),
                    value = "$spanDays",
                    unit = "天",
                    label = "時間跨度", caption = "從第一場起",
                    accent = STAT_ACCENT
                )
            }
        }
    }
}

@Composable
private fun SummaryDivider() {
    Box(modifier = Modifier.width(1.dp).height(52.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)))
}

@Composable
private fun SummaryCell(modifier: Modifier, value: String, unit: String, label: String, caption: String, accent: Color) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = accent, maxLines = 1)
            if (unit.isNotEmpty()) Text(" $unit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// 年度回顧入口卡（E2-7）：點進去看逐頁 story。
@Composable
private fun WrappedEntryCard(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(0.dp),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("年度聽團回顧", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                Text("用一頁一頁的方式，重溫你的這一年", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f))
            }
            Text("開始 →", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

// 成就徽章卡（E2-8）：每個維度只顯示已達的最高階徽章，點擊生成分享圖。
@Composable
private fun AchievementsCard(milestones: List<Milestone>, onOpenBadge: (Milestone) -> Unit) {
    val earned = milestones.filter { it.earned }
    val topEarned = earned.groupBy { it.kind }.mapNotNull { (_, list) -> list.maxByOrNull { it.threshold } }
        .sortedByDescending { it.threshold }
    val next = milestones.filter { !it.earned }.minByOrNull { it.threshold - it.current }
    fun accentOf(kind: String): Color = when (kind) {
        "shows" -> STAT_SERIES[0]; "artists" -> STAT_SERIES[1]
        "venues" -> STAT_SERIES[2]; else -> STAT_SERIES[4]
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("成就徽章", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            topEarned.forEach { m ->
                val accent = accentOf(m.kind)
                Surface(
                    onClick = { onOpenBadge(m) },
                    shape = RoundedCornerShape(12.dp),
                    color = accent.copy(alpha = 0.14f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(m.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text(m.caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("分享 →", style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            next?.let {
                Text(
                    "下一個目標 ${it.label}，還差 ${(it.threshold - it.current).coerceAtLeast(0)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 里程碑徽章分享圖（9:16）：內建底圖（暗罩）＋大徽章數字＋標題＋一句說明＋達成日期＋品牌。 */
private fun buildMilestoneBadgeBitmap(context: Context, m: Milestone, accentArgb: Int): Bitmap {
    val w = 1080; val h = 1920
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    // 底圖：優先用內建海報圖（依徽章穩定挑一張），疊深色漸層讓白字清楚；沒有素材時用深色底。
    val idxs = com.bandary.presentation.ui.share.PosterBackgrounds.availableIndices(context)
    val bg = idxs.getOrNull(if (idxs.isNotEmpty()) Math.floorMod(m.kind.hashCode() + m.threshold, idxs.size) else 0)
        ?.let { com.bandary.presentation.ui.share.PosterBackgrounds.load(context, it, w, h) }
    if (bg != null) {
        val scale = maxOf(w.toFloat() / bg.width, h.toFloat() / bg.height)
        val dw = bg.width * scale; val dh = bg.height * scale
        canvas.drawBitmap(bg, null, RectF((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true })
        canvas.drawColor(AndroidColor.parseColor("#C0111214"))
    } else {
        canvas.drawColor(AndroidColor.parseColor("#141218"))
    }
    fun paint(size: Float, color: Int, bold: Boolean, align: Paint.Align = Paint.Align.CENTER) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; textSize = size; textAlign = align
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }
    val white = AndroidColor.WHITE
    val grey = AndroidColor.parseColor("#CFCFCF")
    val cx = w / 2f
    canvas.drawText("BANDARY・成就解鎖", cx, 300f, paint(46f, accentArgb, true))
    // 徽章圓環 + 大數字
    val ringR = 260f; val ringCy = 760f
    canvas.drawCircle(cx, ringCy, ringR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 12f; color = accentArgb
    })
    val numPaint = paint(300f, white, true)
    canvas.drawText(m.badge, cx, ringCy + 110f, numPaint)
    // 標題 + 說明
    canvas.drawText(m.label, cx, 1180f, paint(84f, white, true))
    canvas.drawText(m.caption, cx, 1270f, paint(46f, grey, false))
    val blurb = when (m.kind) {
        "shows" -> "每一場都在現場，累積成你的聽團足跡。"
        "artists" -> "認識越多藝人，世界越大聲。"
        "venues" -> "從南到北，把場館一座座收集起來。"
        else -> "為了現場，你走遍了台灣。"
    }
    canvas.drawText(blurb, cx, 1400f, paint(42f, grey, false))
    // 達成日期
    val today = Clock.System.now().toLocalDateTime(TimeZone.of("Asia/Taipei")).date
    canvas.drawText("達成於 ${today.year}/${today.monthNumber}/${today.dayOfMonth}", cx, 1720f, paint(40f, grey, false))
    canvas.drawText("聽團曆 · bandary", cx, (h - 90).toFloat(), paint(38f, accentArgb, true))
    return bmp
}

// 里程碑徽章分享圖：專屬設計（底圖 + 大徽章數字 + 標題 + 說明 + 品牌 / 日期），比原本 BLEED 樣式更完整。
@Composable
private fun MilestoneBadgeDialog(milestone: Milestone, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val primaryArgb = MaterialTheme.colorScheme.primary.toArgb()
    // 這張是 1080×1920 的分享圖。在 composition 裡直接畫＝主執行緒被佔住，
    // 按下里程碑到對話框出現之間會整個畫面停住。改成背景畫好再換上。
    val bitmap by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, milestone) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            buildMilestoneBadgeBitmap(context, milestone, primaryArgb)
        }
    }
    var downloadMsg by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(milestone.label, fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val badge = bitmap
                if (badge == null) {
                    Box(
                        modifier = Modifier.height(240.dp).aspectRatio(9f / 16f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                } else {
                    androidx.compose.foundation.Image(
                        bitmap = badge.asImageBitmap(),
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier.height(240.dp).aspectRatio(9f / 16f).clip(RoundedCornerShape(8.dp))
                    )
                }
                downloadMsg?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(enabled = bitmap != null, onClick = {
                    val b = bitmap ?: return@TextButton
                    val uri = com.bandary.data.util.BitmapExporter.downloadToGallery(context, b, "bandary_milestone_${milestone.kind}_${milestone.threshold}")
                    downloadMsg = if (uri != null) "已下載到相簿" else "下載失敗"
                }) { Text("下載") }
                TextButton(enabled = bitmap != null, onClick = {
                    val b = bitmap ?: return@TextButton
                    com.bandary.data.util.BitmapExporter.shareOnly(context, b, "bandary_milestone_${milestone.kind}_${milestone.threshold}")
                    onDismiss()
                }) { Text("分享") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("關閉") } }
    )
}

// 台灣縣市圖（示意方格地圖）：每個縣市一格，依場次數上色，北在上、離島在左、東岸在右。
private data class CountyTile(val label: String, val core: String, val row: Int, val col: Int)
private val TAIWAN_TILES = listOf(
    CountyTile("基隆", "基隆", 0, 1),
    CountyTile("台北", "台北", 1, 1), CountyTile("新北", "新北", 1, 2),
    CountyTile("金門", "金門", 2, 0), CountyTile("桃園", "桃園", 2, 1), CountyTile("宜蘭", "宜蘭", 2, 3),
    CountyTile("連江", "連江", 3, 0), CountyTile("新竹", "新竹", 3, 1),
    CountyTile("苗栗", "苗栗", 4, 1),
    CountyTile("台中", "台中", 5, 1), CountyTile("花蓮", "花蓮", 5, 3),
    CountyTile("彰化", "彰化", 6, 1), CountyTile("南投", "南投", 6, 2),
    CountyTile("雲林", "雲林", 7, 1),
    CountyTile("嘉義", "嘉義", 8, 1),
    CountyTile("台南", "台南", 9, 1), CountyTile("台東", "台東", 9, 3),
    CountyTile("高雄", "高雄", 10, 1),
    CountyTile("澎湖", "澎湖", 11, 0), CountyTile("屏東", "屏東", 11, 1)
)

@Composable
private fun TaiwanCityMapCard(cities: List<Pair<String, Int>>) {
    // 城市名正規化（臺→台），對應到縣市核心名再累加場次（新竹 / 嘉義市縣合併）。
    fun norm(s: String) = s.replace('臺', '台')
    val countByCore = TAIWAN_TILES.associate { tile ->
        tile.core to cities.filter { norm(it.first).contains(tile.core) }.sumOf { it.second }
    }
    val max = (countByCore.values.maxOrNull() ?: 1).coerceAtLeast(1)
    val base = STAT_ACCENT
    val rows = (TAIWAN_TILES.maxOf { it.row }) + 1
    val cols = 4
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("台灣縣市圖", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            for (r in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (col in 0 until cols) {
                        val tile = TAIWAN_TILES.firstOrNull { it.row == r && it.col == col }
                        if (tile == null) {
                            Spacer(Modifier.weight(1f))
                        } else {
                            val count = countByCore[tile.core] ?: 0
                            val alpha = if (count == 0) 0f else (0.22f + 0.78f * count / max)
                            Box(
                                modifier = Modifier.weight(1f).height(34.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (count == 0) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else base.copy(alpha = alpha)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(tile.label, fontSize = 9.sp, fontWeight = FontWeight.Medium,
                                        color = if (alpha > 0.6f) Color.White else MaterialTheme.colorScheme.onSurface)
                                    if (count > 0) Text("$count", fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                        color = if (alpha > 0.6f) Color.White else MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 甜甜圈圖：分類佔比一眼看出，中央顯示總數，右側圖例。
/** 某一區沒有資料時顯示的一行提示：保留區塊本身，讓人知道有這個統計、只是還沒累積到。 */
@Composable
private fun StatEmptyRow(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
    )
}

/**
 * 看過的藝人文字雲：看越多次字越大。排行榜只看得到前幾名，文字雲能一次呈現整體樣貌。
 * 字級依「該藝人場次 ÷ 最高場次」在 13–30sp 之間內插；顏色深淺同樣依場次，
 * 所以大小與濃度雙重編碼，不是只靠一種訊號。
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ArtistWordCloudCard(bands: List<StatsBand>, onClick: (String) -> Unit) {
    // 兩種檢視：「常看」只列看過 ≥2 場的（團多的時候才看得出重點），「全部」列出所有看過的藝人。
    // 全部模式不再截到 40 組——「看過的藝人」寫著 N 組卻只畫得出前 40 個，對不上就是錯的。
    var showAll by remember { mutableStateOf(true) }
    val repeatBands = remember(bands) { bands.filter { it.count > 1 } }
    val hasRepeat = repeatBands.isNotEmpty()
    val shown = if (showAll || !hasRepeat) bands else repeatBands
    val max = (shown.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
    val min = (shown.minOfOrNull { it.count } ?: 1)
    // 回訪過的（看過 2 次以上）用彩色，只看過一次的維持灰階。
    // 顏色因此有自己的意義——不是裝飾，是「這些是我會回頭再看的團」。
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "看過的藝人・${shown.size} 組",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                // 有回訪過的藝人才需要切換；只看過一次的話兩個選項內容一樣，不用佔版面。
                if (hasRepeat) {
                    listOf(false to "常看", true to "全部").forEach { (all, label) ->
                        val sel = showAll == all
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clickable { showAll = all }
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // 大小照場次，但排列刻意打散：名次相鄰的兩個不會並排，
                // 才不會從左上到右下排成一條遞減的階梯——那看起來就只是排行榜換個字級。
                val scattered = shown.withIndex().sortedBy { (i, _) -> (i * 7) % shown.size.coerceAtLeast(1) }
                scattered.forEach { (rank, b) ->
                    val t = if (max == min) 1f else (b.count - min).toFloat() / (max - min)
                    val size = (13 + t * 19).sp
                    val repeated = b.count > 1
                    val color = if (repeated) STAT_SERIES[rank % STAT_SERIES.size].copy(alpha = 0.55f + t * 0.45f)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f + t * 0.2f)
                    Text(
                        b.artistName,
                        fontSize = size,
                        // 明確給 lineHeight，否則不同字級混排時行距會被最大那個字撐開，行與行看起來鬆散。
                        lineHeight = size * 1.5f,
                        fontWeight = if (t > 0.6f) FontWeight.Bold else FontWeight.Medium,
                        color = color,
                        modifier = Modifier.clickable { onClick(b.artistName) }
                    )
                }
            }
        }
    }
}

/**
 * 同行者卡：名字首字的圓形頭像橫向排列。人的統計用「像人的東西」呈現，
 * 比一整排文字列更容易一眼看出「常跟誰去」。沒有真實頭像，所以用首字 + 依名字決定的色相。
 */
@Composable
private fun AvatarRankCard(
    data: List<Pair<String, Int>>,
    showCount: Boolean,
    onClick: (String) -> Unit
) {
    if (data.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(data) { (name, count) ->
                // 色相由名字雜湊決定 → 同一個人每次進來顏色一致。
                val hue = ((name.hashCode() % 360) + 360) % 360
                val bg = Color.hsl(hue.toFloat(), 0.45f, 0.72f)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(62.dp).clickable { onClick(name) }
                ) {
                    Box(
                        modifier = Modifier.size(46.dp).clip(CircleShape).background(bg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            name.trim().take(1).ifBlank { "?" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        name,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    if (showCount) {
                        Text(
                            "$count 場",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * 排行前三名的頒獎台：第一名在中間且最高，金銀銅用高度與顏色雙重編碼
 * （只靠顏色的話色弱使用者分不出名次）。
 */
@Composable
private fun PodiumCard(
    top: List<Pair<String, String>>,   // 名稱 to 數值文字
    onClick: (String) -> Unit
) {
    if (top.isEmpty()) return
    // 版面順序＝銀、金、銅；金在中間最高。
    val order = listOfNotNull(top.getOrNull(1), top.getOrNull(0), top.getOrNull(2))
    // 依名次（0=金）。先前誤寫成 0→74、1→96，變成第二名比第一名高。
    val heights = mapOf(0 to 96.dp, 1 to 74.dp, 2 to 58.dp)
    val medals = mapOf(0 to Color(0xFFD4AF37), 1 to Color(0xFFA8A9AD), 2 to Color(0xFFB87333))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            order.forEach { entry ->
                val rank = top.indexOf(entry)
                Column(
                    modifier = Modifier.weight(1f).clickable { onClick(entry.first) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        entry.first,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(heights[rank] ?: 58.dp)
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .background(medals[rank] ?: MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${rank + 1}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold, color = Color.White
                            )
                            Text(
                                entry.second,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DonutChartCard(
    title: String,
    data: List<Pair<String, Int>>,
    onSlice: ((String) -> Unit)? = null,
    insight: String? = null
) {
    val total = data.sumOf { it.second }.coerceAtLeast(1)
    val totalArgb = MaterialTheme.colorScheme.onSurface.toArgb()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (title.isNotBlank()) Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                androidx.compose.foundation.Canvas(modifier = Modifier.size(112.dp)) {
                    val stroke = 26.dp.toPx()
                    val d = size.minDimension - stroke
                    var start = -90f
                    data.forEachIndexed { i, p ->
                        val sweep = 360f * p.second / total
                        drawArc(
                            color = STAT_SERIES[i % STAT_SERIES.size],
                            startAngle = start, sweepAngle = sweep, useCenter = false,
                            topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                            size = androidx.compose.ui.geometry.Size(d, d),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
                        )
                        start += sweep
                    }
                    val tp = android.graphics.Paint().apply {
                        color = totalArgb; textSize = 20.sp.toPx(); textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true; isFakeBoldText = true
                    }
                    drawContext.canvas.nativeCanvas.drawText("$total", size.width / 2, size.height / 2 + 7.dp.toPx(), tp)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    data.forEachIndexed { i, (label, v) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = if (onSlice != null) Modifier.fillMaxWidth().clickable { onSlice(label) } else Modifier.fillMaxWidth()
                        ) {
                            Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(STAT_SERIES[i % STAT_SERIES.size]))
                            Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text("${v * 100 / total}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            if (onSlice != null) Icon(Icons.Outlined.ChevronRight, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            insight?.let { ChartInsight(it) }
        }
    }
}



/** 圖表細節面板。先講清楚這個數字的意義，要看實際場次才需要再點一次。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChartDetailSheet(detail: ChartDetail, onDismiss: () -> Unit) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            // inset 要在捲動之外，否則按鈕會被系統導覽列擋住（順序錯過一次了，見 v1.50.1）。
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(detail.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    detail.valueText,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            androidx.compose.material3.HorizontalDivider()
            detail.facts.forEach { (k, v) ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        k, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(72.dp)
                    )
                    Text(v, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
            }
            detail.onDrill?.let { drill ->
                Button(
                    onClick = { onDismiss(); drill() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("看這 ${detail.drillCount} 場", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

// 通用「標籤 + 橫向長條」卡：給各年份 / 類型 / 國家等字串維度用。
@Composable
private fun LabeledBarCard(
    title: String,
    data: List<Pair<String, Int>>,
    onRow: ((String) -> Unit)? = null,
    insight: String? = null,
    maxRows: Int = 8,
    formatValue: (Int) -> String = { "$it" },   // 金額類要帶 NT$ 與千分位
    restUnit: String = "場"
) {
    val accent = statAccent(title)
    // 長尾摺疊：場館這種「前幾名之後全是 1 場」的資料，全列出來會洗掉整個畫面。
    // 只列前 maxRows 名，其餘併成一行摘要。
    val shown = data.take(maxRows)
    val restCount = data.size - shown.size
    val restTotal = data.drop(maxRows).sumOf { it.second }
    val max = (shown.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    // 數值欄寬度原本寫死 32dp，只夠放兩位數；金額（NT$24,500）會被裁掉。
    // 改成依本圖最長的數值字串決定，各列共用同一寬度，長條才對得齊。
    val valueTexts = shown.map { formatValue(it.second) }
    val valueW = ((valueTexts.maxOfOrNull { it.length } ?: 2) * 8 + 6).dp.coerceIn(32.dp, 96.dp)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title.isNotBlank()) Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            shown.forEachIndexed { i, (label, count) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = if (onRow != null) Modifier.fillMaxWidth().clickable { onRow(label) } else Modifier.fillMaxWidth()
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(104.dp)
                    )
                    // 一律畫 bar：先前「占比 <10% 不畫」的規則會讓同一張圖有些列有條、有些列只有文字，
                    // 看起來像壞掉。長度改以「本圖最大值」為基準，最短保底 4%，短列也看得到。
                    Box(modifier = Modifier.weight(1f).height(14.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((count.toFloat() / max).coerceIn(0.04f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp))
                                .background(accent.copy(alpha = 0.85f))
                        )
                    }
                    Text(
                        valueTexts[i],
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                        maxLines = 1, softWrap = false,
                        modifier = Modifier.width(valueW), textAlign = TextAlign.End
                    )
                }
            }
            if (restCount > 0) {
                Text(
                    "其他 $restCount 項・共 ${formatValue(restTotal)}$restUnit",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            insight?.let { ChartInsight(it) }
        }
    }
}

// 觀看時長排行：值為分鐘，顯示成「Xh Ym」；長條依時長比例。
@Composable
private fun DurationRankCard(title: String, data: List<Pair<String, Int>>, onRow: ((String) -> Unit)? = null) {
    val max = (data.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    val accent = STAT_ACCENT
    fun fmt(min: Int): String {
        val h = min / 60; val m = min % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        var expanded by remember { mutableStateOf(false) }
        val shown = if (expanded) data else data.take(5)
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title.isNotBlank()) Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            if (onRow != null) {
                Text("點藝人看該藝人的所有場次", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            shown.forEach { (label, minutes) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = if (onRow != null) Modifier.clickable { onRow(label) } else Modifier
                ) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.width(84.dp))
                    Box(modifier = Modifier.weight(1f).height(14.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((minutes.toFloat() / max).coerceIn(0.02f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp))
                                .background(accent.copy(alpha = 0.85f))
                        )
                    }
                    Text(fmt(minutes), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(56.dp))
                }
            }
            if (data.size > 5) {
                Text(
                    if (expanded) "收合" else "展開看全部 ${data.size} 項",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { expanded = !expanded }.padding(top = 2.dp)
                )
            }
        }
    }
}

// 藝人回訪率：看過 ≥2 次的藝人占比（insight，不只是一個數字）。
@Composable
private fun RevisitRateCard(rate: Int, revisited: Int, total: Int) {
    val accent = STAT_ACCENT
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.12f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("藝人回訪率", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$rate", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = accent, lineHeight = 36.sp)
                Text("%", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
            }
            LinearProgressIndicator(
                progress = { (rate / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = accent,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
            Text("回訪的藝人・以前看過此期間又再看：$revisited / $total 組", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// 年 × 月 熱力圖：每年一列、12 個月一格，顏色越深看得越多。
@Composable
private fun MonthYearHeatmapCard(rows: List<YearHeat>, onYear: ((Int) -> Unit)? = null) {
    val max = rows.maxOf { r -> r.counts.maxOrNull() ?: 0 }.coerceAtLeast(1)
    val base = STAT_ACCENT
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("年 × 月 熱力圖", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            // 月份表頭
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(34.dp))
                (1..12).forEach { m ->
                    Text("$m", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            rows.forEach { row ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${row.year}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = (if (onYear != null) Modifier.clickable { onYear(row.year) } else Modifier).width(34.dp))
                    row.counts.forEach { c ->
                        val alpha = if (c == 0) 0f else (0.2f + 0.8f * c / max)
                        Box(
                            modifier = Modifier.weight(1f).height(22.dp).padding(1.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (c == 0) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else base.copy(alpha = alpha)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (c > 0) Text("$c", fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                color = if (alpha > 0.6f) Color.White else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}

// 星期分布：一週哪幾天最常看演出（直條）。
@Composable
private fun WeekdayBarsCard(counts: List<Int>, onDay: ((Int) -> Unit)? = null) {
    val labels = listOf("一", "二", "三", "四", "五", "六", "日")
    val max = (counts.maxOrNull() ?: 1).coerceAtLeast(1)
    val accent = STAT_ACCENT
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("星期分布", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            // 長條區：只有數字 + 柱子，固定高度；標籤另外一列，不會被高柱蓋住。
            Row(modifier = Modifier.fillMaxWidth().height(84.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                counts.forEachIndexed { day, c ->
                    Column(
                        modifier = (if (onDay != null && c > 0) Modifier.weight(1f).clickable { onDay(day) } else Modifier.weight(1f)),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom
                    ) {
                        Text(if (c > 0) "$c" else "", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .height((6f + 58f * c / max).dp)
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(accent.copy(alpha = if (c == 0) 0.2f else 0.85f))
                        )
                    }
                }
            }
            // 星期標籤列（獨立，永遠對齊在柱子下方）
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                labels.forEach { lbl ->
                    Text(lbl, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// 票價分布：各價格區間的場次數，橫向長條。
@Composable
private fun PriceHistogramCard(buckets: List<Pair<String, Int>>, onBucket: ((String) -> Unit)? = null) {
    val max = (buckets.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    val accent = STAT_ACCENT
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("票價分布", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            buckets.forEach { (label, count) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = if (onBucket != null && count > 0) Modifier.fillMaxWidth().clickable { onBucket(label) } else Modifier.fillMaxWidth()
                ) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false, modifier = Modifier.width(66.dp))
                    Box(modifier = Modifier.weight(1f).height(14.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((count.toFloat() / max).coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp))
                                .background(accent.copy(alpha = 0.85f))
                        )
                    }
                    Text("$count", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                }
            }
        }
    }
}

@Composable
private fun VenueRow(rank: Int, venue: String, count: Int, showCount: Boolean = true, onClick: () -> Unit = {}) {
    // 緊湊列（spec §6.9.2）：無卡片外框、單列 ≤56dp、排名｜名稱｜數值靠右，一屏可見更多。
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
            .heightIn(min = 44.dp).padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "$rank",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(26.dp),
            color = when (rank) {
                1 -> BandaryColors.Yellow
                2 -> Color(0xFF9E9E9E)
                3 -> Color(0xFFBF8060)
                else -> MaterialTheme.colorScheme.outline
            }
        )
        Text(
            venue,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        // 整個 section 全同值時隱藏每列數值（spec §6.9.3），改在區塊標題註明「各 N 場」。
        if (showCount) {
            Text(
                "$count 場",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ArtistSpendRow(sp: ArtistSpend, rank: Int, maxAmount: Double, onClick: () -> Unit = {}) {
    // 與「看最多次的藝人」用同一種列：名次 · 名稱 · 比例條 · 數值。
    // 原本只有名字和金額，沒有任何長度可比——第一名花 3 萬還是 3 千，得自己讀數字換算。
    val ratio = if (maxAmount > 0) (sp.amount / maxAmount).toFloat() else 0f
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "$rank",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(22.dp),
            color = when (rank) {
                1 -> BandaryColors.Yellow
                2 -> Color(0xFF9E9E9E)
                3 -> Color(0xFFBF8060)
                else -> MaterialTheme.colorScheme.outline
            }
        )
        Text(
            sp.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier.width(48.dp).height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(ratio.coerceIn(0.06f, 1f)).fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Text(
            "NT$${"%,d".format(sp.amount.toInt())}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1, softWrap = false,
            textAlign = TextAlign.End,
            modifier = Modifier.width(76.dp)
        )
    }
}

@Composable
private fun SongArtistRankRow(a: SongRank, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(a.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("${a.count} 首 · ${a.pct}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { (a.pct / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        }
    }
}

/**
 * 歌曲排行的一列。
 *
 * 原本每一首是一張 `elevation = 1dp` 的獨立白卡，而且**沒有名次**——
 * 十首歌就是十個一模一樣的浮起白方塊，看不出這是排行榜，也看不出第一名
 * 到底領先多少（實機回報「不好看」）。
 *
 * 改成真正的排行列：
 *   - **名次**。沒有編號的清單，讀者不會知道它是排序過的。
 *   - **長條**。數字 ×3 和 ×2 的差距要用算的；長條用看的就知道。
 *   - **前三名加重**。排行榜真正會被讀的就是前三名，其餘是長尾。
 *   - **不要卡片**。十張浮起的卡疊在一起，陰影本身就是噪音；
 *     改成平的列 + 細分隔線，整段才像「一個排行榜」而不是「十個東西」。
 */
@Composable
private fun SongTitleRankRow(
    s: SongTitleRank,
    rank: Int = 0,
    maxCount: Int = 1,
    onClick: () -> Unit = {}
) {
    val top = rank < 3
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(if (top) 24.dp else 20.dp)
                    .background(
                        if (rank == 0) scheme.primary
                        else scheme.primary.copy(alpha = if (top) 0.28f else 0.12f),
                        androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${rank + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = if (top) 12.sp else 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (rank == 0) scheme.onPrimary else scheme.primary
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    s.title,
                    style = if (top) MaterialTheme.typography.titleSmall
                    else MaterialTheme.typography.bodyMedium,
                    fontWeight = if (top) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (s.artist.isNotBlank()) {
                    Text(
                        s.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "×${s.count}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (top) scheme.primary else scheme.onSurfaceVariant
            )
        }
        // 長條縮排對齊歌名（名次寬 + 間距），看起來才是屬於這一列的。
        Spacer(Modifier.height(5.dp))
        Box(
            modifier = Modifier.padding(start = 34.dp).fillMaxWidth().height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(scheme.surfaceVariant.copy(alpha = 0.6f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((s.count.toFloat() / maxCount.coerceAtLeast(1)).coerceIn(0.04f, 1f))
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(scheme.primary.copy(alpha = if (top) 0.85f else 0.45f))
            )
        }
    }
}

/** 搜尋結果列：日期 + 歌名 / 藝人 + 備註，點一下就地編輯。 */
@Composable
private fun SongSearchRow(song: DailySong, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(song.songTitle, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${song.date}　${song.artistName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (!song.memo.isNullOrBlank()) {
                    Text(
                        song.memo!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BandAttendanceRow(rank: Int, band: StatsBand, onClick: () -> Unit = {}) {
    val progress = if (band.total > 0) band.count.toFloat() / band.total else 0f
    // 原本一位藝人佔三行（名稱列 + 進度條 + 「佔此區間演出 X%」），還放了每列都一樣的音符圖示，
    // 十位就洗掉整個畫面。壓成單列：名次 · 名稱 · 場數 · 一條細比例條；
    // 百分比就是那條的長度，不必再寫一次。
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "$rank",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(22.dp),
            color = when (rank) {
                1 -> BandaryColors.Yellow
                2 -> Color(0xFF9E9E9E)
                3 -> Color(0xFFBF8060)
                else -> MaterialTheme.colorScheme.outline
            }
        )
        Text(
            band.artistName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        // 比例條：長度＝佔此區間演出的比例，最短保底才看得到。
        Box(
            modifier = Modifier.width(58.dp).height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(progress.coerceIn(0.06f, 1f)).fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Text(
            "${band.count} 場",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1, softWrap = false,
            textAlign = TextAlign.End,
            modifier = Modifier.width(44.dp)
        )
    }
}
