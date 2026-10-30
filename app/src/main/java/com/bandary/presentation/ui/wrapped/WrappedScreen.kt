package com.bandary.presentation.ui.wrapped

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bandary.data.local.SettingsDataStore
import com.bandary.data.repository.ConcertRepository
import com.bandary.data.repository.DailySongRepository
import com.bandary.domain.model.Concert
import com.bandary.domain.model.ConcertType
import com.bandary.presentation.ui.stats.distanceFromTaipei
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import kotlin.math.abs

/** 年度回顧的一頁資料（E2-7）。 */
data class WrappedData(
    val year: Int = 0,
    val totalShows: Int = 0,
    val totalSpent: Int = 0,
    val distinctArtists: Int = 0,
    val distinctVenues: Int = 0,
    val topArtist: String? = null,
    val topArtistCount: Int = 0,
    val farthestCity: String? = null,
    val totalSongs: Int = 0,
    val topSong: String? = null,
    val topSongArtist: String? = null,
    val liveSongs: Int = 0,           // 其中從演唱會歌單記下來的（concertId 非 null）
    val topVenue: String? = null,
    val topVenueCount: Int = 0,
    val distinctCities: Int = 0,
    val busiestMonth: Int = 0,        // 1..12，0＝無
    val busiestMonthCount: Int = 0,
    val hasData: Boolean = false
)

@HiltViewModel
class WrappedViewModel @Inject constructor(
    private val concertRepo: ConcertRepository,
    private val dailySongRepo: DailySongRepository,
    private val settings: SettingsDataStore,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val tz = TimeZone.of("Asia/Taipei")
    private val requestedYear: Int = savedStateHandle.get<String>("year")?.toIntOrNull() ?: 0

    private fun artistNamesOf(c: Concert): List<String> {
        val fest = c.festivals.filter { it.isSelected && it.artistName.isNotBlank() }.map { it.artistName }
        return if (c.type == ConcertType.FESTIVAL) fest
        else (listOfNotNull(c.artistName.takeIf { it.isNotBlank() }) + fest).distinct()
    }

    val data: StateFlow<WrappedData?> = combine(
        concertRepo.getAllConcerts(),
        dailySongRepo.getAllSongs(),
        settings.homeBaseCity
    ) { concerts, songs, baseCity ->
        val nowYear = Clock.System.now().toLocalDateTime(tz).year
        // 決定要回顧的年份：指定 0 時挑最近有資料的年份（否則用今年）。
        val year = if (requestedYear > 0) requestedYear else {
            val years = (concerts.map { it.dateTime.date.year } + songs.map { it.date.year })
            years.filter { it <= nowYear }.maxOrNull() ?: nowYear
        }
        val yearShows = concerts.filter { it.dateTime.date.year == year }
        val yearSongs = songs.filter { it.date.year == year }
        if (yearShows.isEmpty() && yearSongs.isEmpty()) {
            return@combine WrappedData(year = year, hasData = false)
        }
        val artistNames = yearShows.flatMap { artistNamesOf(it) }
        val topArtist = artistNames.groupingBy { it }.eachCount().maxByOrNull { it.value }
        val baseDist = distanceFromTaipei(baseCity)
        val farthest = yearShows.filter { !it.city.isNullOrBlank() }
            .maxByOrNull { abs(distanceFromTaipei(it.city!!) - baseDist) }?.city
        val topSong = yearSongs.filter { it.songTitle.isNotBlank() }
            .groupingBy { it.songTitle to it.artistName }.eachCount().maxByOrNull { it.value }?.key
        val topVenue = yearShows.mapNotNull { it.venue.takeIf { v -> v.isNotBlank() } }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }
        val busiest = yearShows.groupingBy { it.dateTime.monthNumber }.eachCount().maxByOrNull { it.value }
        WrappedData(
            year = year,
            totalShows = yearShows.size,
            totalSpent = yearShows.sumOf { it.ticketPrice ?: 0.0 }.toInt(),
            distinctArtists = artistNames.toSet().size,
            distinctVenues = yearShows.mapNotNull { it.venue.takeIf { v -> v.isNotBlank() } }.toSet().size,
            topArtist = topArtist?.key,
            topArtistCount = topArtist?.value ?: 0,
            farthestCity = farthest,
            totalSongs = yearSongs.size,
            topSong = topSong?.first,
            topSongArtist = topSong?.second,
            liveSongs = yearSongs.count { it.concertId != null },
            topVenue = topVenue?.key,
            topVenueCount = topVenue?.value ?: 0,
            distinctCities = yearShows.mapNotNull { it.city?.trim()?.takeIf { c -> c.isNotBlank() } }.toSet().size,
            busiestMonth = busiest?.key ?: 0,
            busiestMonthCount = busiest?.value ?: 0,
            hasData = true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

// 每頁的背景色（依序輪替，做出 story 翻頁的節奏感）。
private val WRAPPED_COLORS = listOf(
    Color(0xFF1E2A24), Color(0xFF2A2440), Color(0xFF3A2436),
    Color(0xFF24303A), Color(0xFF3A3320), Color(0xFF203A32), Color(0xFF2E2438)
)

@Composable
fun WrappedScreen(
    onClose: () -> Unit,
    viewModel: WrappedViewModel = hiltViewModel()
) {
    val data by viewModel.data.collectAsState()
    val d = data
    if (d == null) {
        Box(Modifier.fillMaxSize().background(WRAPPED_COLORS[0]), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }
    if (!d.hasData) {
        Box(Modifier.fillMaxSize().background(WRAPPED_COLORS[0]), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("${d.year} 年還沒有足夠的紀錄", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = onClose) { Text("關閉", color = Color.White) }
            }
        }
        return
    }

    // 逐頁 story：組出這一年有內容可講的頁（沒資料的頁自動略過），最後一頁是分享。
    val pages = remember(d) { buildWrappedPages(d) }
    var page by remember { mutableIntStateOf(0) }

    Box(
        Modifier.fillMaxSize()
            .background(Color(0xFF0D1210))
            .pointerInput(pages.size) {
                detectTapGestures { offset ->
                    // 點右側往下一頁、左側回上一頁（Spotify Wrapped 式）。
                    if (offset.x > size.width * 0.35f) {
                        if (page < pages.size - 1) page++
                    } else if (page > 0) page--
                }
            }
    ) {
        // 每頁全螢幕滿版（自帶漸層底 + 裝飾），置中內容避開上方的進度條 / 關閉鈕。
        AnimatedContent(
            targetState = page,
            transitionSpec = { (fadeIn(tween(400))) togetherWith fadeOut(tween(200)) },
            label = "wrapped-page"
        ) { p ->
            pages[p]()
        }
        // 頂部進度條 + 關閉鈕 疊在最上層（避開系統狀態列）。
        Column(Modifier.fillMaxWidth().systemBarsPadding().padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                pages.indices.forEach { i ->
                    Box(
                        Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp))
                            .background(if (i <= page) Color.White else Color.White.copy(alpha = 0.3f))
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = "關閉", tint = Color.White)
                }
            }
        }
    }
}

// Wrapped 用的暖金色點綴。
private val WrappedAccent = Color(0xFFC9A96A)

// 每頁一套配色（漸層底 + 角落裝飾圓 + 大浮水印色），讓逐頁翻起來有變化、不單調。
private data class WrapTheme(val top: Color, val bottom: Color, val ring: Color)
private val WRAP_THEMES = listOf(
    WrapTheme(Color(0xFF20302A), Color(0xFF0D1210), Color(0xFF6B9080)),  // 藍綠
    WrapTheme(Color(0xFF2A2440), Color(0xFF120F1C), Color(0xFF8A7CC9)),  // 紫
    WrapTheme(Color(0xFF3A2436), Color(0xFF190F16), Color(0xFFC97CA0)),  // 玫瑰
    WrapTheme(Color(0xFF23303A), Color(0xFF0E141A), Color(0xFF6AA0C9)),  // 藍
    WrapTheme(Color(0xFF33301F), Color(0xFF15130D), Color(0xFFC9A96A)),  // 暖金
)

/** 把年度資料組成「頁」（把能一起看的整併成一頁，避免拆太多頁）。最後一頁固定為分享。 */
private fun buildWrappedPages(d: WrappedData): List<@Composable () -> Unit> {
    val pages = mutableListOf<@Composable () -> Unit>()
    pages += { WrappedIntroPage(d.year) }
    // 一頁看完的現場數字（場次 / 藝人 / 場館 / 花費）。
    pages += { WrappedNumbersPage(d) }
    // 年度藝人（單獨一頁，主角要夠大）。
    d.topArtist?.let { pages += { WrappedArtistPage(d) } }
    // 最常出沒的場館。
    d.topVenue?.let { pages += { WrappedVenuePage(d) } }
    // 最狂的月份。
    if (d.busiestMonth > 0 && d.busiestMonthCount > 1) pages += { WrappedMonthPage(d) }
    // 最遠抵達（走過的地圖）。
    d.farthestCity?.let { pages += { WrappedPlacePage(d) } }
    // 選歌。
    if (d.totalSongs > 0) pages += { WrappedSongsPage(d) }
    pages += { WrappedSharePage(d) }
    return pages
}

/**
 * 每頁共用骨架：漸層底 + 兩個角落裝飾圓（低透明度、壓在上下角，不碰置中內容）＋可選浮水印大字
 * （放在最上緣、字級控制在內容之上，不重疊）。內容置中。
 */
@Composable
private fun WrappedPageScaffold(theme: WrapTheme, watermark: String = "", content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(
            androidx.compose.ui.graphics.Brush.verticalGradient(listOf(theme.top, theme.bottom))
        )
    ) {
        // 右上、左下兩個柔和裝飾圓（大半在畫面外），做出層次但不擋內容。
        Box(
            Modifier.align(Alignment.TopEnd).offset(x = 90.dp, y = (-70).dp)
                .size(260.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .background(theme.ring.copy(alpha = 0.16f))
        )
        Box(
            Modifier.align(Alignment.BottomStart).offset(x = (-80).dp, y = 80.dp)
                .size(220.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .background(theme.ring.copy(alpha = 0.10f))
        )
        // 浮水印：貼齊最上緣、字級 110sp（約螢幕高 1/5），明顯在置中內容之上，不重疊。
        if (watermark.isNotBlank()) {
            Text(
                watermark,
                color = Color.White.copy(alpha = 0.06f),
                fontSize = 110.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                modifier = Modifier.align(Alignment.TopStart).padding(top = 12.dp)
            )
        }
        Column(
            Modifier.fillMaxSize().padding(start = 28.dp, end = 24.dp, top = 96.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.Center,
            content = content
        )
    }
}

@Composable
private fun WrappedIntroPage(year: Int) {
    WrappedPageScaffold(theme = WRAP_THEMES[0]) {
        Text("BANDARY", color = WrappedAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
        Spacer(Modifier.height(12.dp))
        Text("$year", color = Color.White, fontSize = 96.sp, fontWeight = FontWeight.Black)
        Text("你的年度聽團回顧", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        Box(Modifier.width(48.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(WrappedAccent))
        Spacer(Modifier.height(20.dp))
        Text("點畫面右邊繼續 →", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
    }
}

@Composable
private fun WrappedNumbersPage(d: WrappedData) {
    WrappedPageScaffold(theme = WRAP_THEMES[1], watermark = "${d.totalShows}") {
        Text("今年的現場", color = WrappedAccent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        WrappedBigLine("${d.totalShows}", "場演出")
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            WrappedMiniStat("${d.distinctArtists}", "組藝人")
            WrappedMiniStat("${d.distinctVenues}", "個場館")
            if (d.totalSpent > 0) WrappedMiniStat("NT$${"%,d".format(d.totalSpent)}", "現場投入")
        }
    }
}

// 年度藝人：主角獨佔一頁（左對齊大字）。
@Composable
private fun WrappedArtistPage(d: WrappedData) {
    WrappedPageScaffold(theme = WRAP_THEMES[2], watermark = "★") {
        Text("今年陪你最多的是", color = Color.White.copy(alpha = 0.85f), fontSize = 18.sp)
        Spacer(Modifier.height(10.dp))
        WrappedHeadline(d.topArtist ?: "")
        Spacer(Modifier.height(10.dp))
        Box(Modifier.width(40.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(WrappedAccent))
        Spacer(Modifier.height(10.dp))
        Text(if (d.topArtistCount > 1) "一起見面 ${d.topArtistCount} 次" else "你們的第一次相遇",
            color = WrappedAccent, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

// 最常出沒的場館：置中排版，和其他頁的左對齊做出變化。
@Composable
private fun WrappedVenuePage(d: WrappedData) {
    WrappedPageScaffold(theme = WRAP_THEMES[4]) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("最常出沒的場館", color = WrappedAccent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Text(
                d.topVenue ?: "",
                color = Color.White,
                fontSize = if ((d.topVenue?.length ?: 0) <= 8) 40.sp else 28.sp,
                lineHeight = if ((d.topVenue?.length ?: 0) <= 8) 50.sp else 36.sp,
                fontWeight = FontWeight.Black,
                maxLines = 3,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(12.dp))
            Text("去了 ${d.topVenueCount} 次", color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp)
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                WrappedMiniStat("${d.distinctVenues}", "個場館")
                WrappedMiniStat("${d.distinctCities}", "個縣市")
            }
        }
    }
}

// 最狂的月份：超大月份數字 + 單位，走「數字主視覺」路線。
@Composable
private fun WrappedMonthPage(d: WrappedData) {
    WrappedPageScaffold(theme = WRAP_THEMES[0], watermark = "${d.busiestMonth}") {
        Text("你最瘋的一個月", color = Color.White.copy(alpha = 0.85f), fontSize = 18.sp)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${d.busiestMonth}", color = Color.White, fontSize = 96.sp, lineHeight = 104.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(8.dp))
            Text("月", color = WrappedAccent, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 18.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text("那個月你就看了 ${d.busiestMonthCount} 場", color = WrappedAccent, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

// 最遠抵達：獨立一頁。
@Composable
private fun WrappedPlacePage(d: WrappedData) {
    WrappedPageScaffold(theme = WRAP_THEMES[3]) {
        Text("你為了現場，最遠去到", color = Color.White.copy(alpha = 0.85f), fontSize = 18.sp)
        Spacer(Modifier.height(10.dp))
        WrappedHeadline(d.farthestCity ?: "", maxLines = 2)
        Spacer(Modifier.height(12.dp))
        Text("把喜歡的演出，走成地圖上的一個點", color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
    }
}

@Composable
private fun WrappedSongsPage(d: WrappedData) {
    WrappedPageScaffold(theme = WRAP_THEMES[3], watermark = "${d.totalSongs}") {
        Text("今年你選了", color = Color.White.copy(alpha = 0.85f), fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        WrappedBigLine("${d.totalSongs}", "首歌")
        // 選歌與看團的接點：有幾首是站在現場當下記下來的。
        if (d.liveSongs > 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                "其中 ${d.liveSongs} 首，是在現場記下的",
                color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp
            )
        }
        d.topSong?.let {
            Spacer(Modifier.height(24.dp))
            Text("最常回放", color = WrappedAccent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            WrappedHeadline(it + (d.topSongArtist?.takeIf { a -> a.isNotBlank() }?.let { a -> " · $a" } ?: ""), maxLines = 2)
        }
    }
}

/**
 * 大標（藝人 / 縣市 / 歌名）：依文字長度自動縮字級，並**明確指定行高**——只設 fontSize 不設 lineHeight
 * 會沿用預設樣式的小行高，多行時整個疊在一起（多藝人名單就會跑版）。
 */
@Composable
private fun WrappedHeadline(text: String, maxLines: Int = 3) {
    val size = when {
        text.length <= 6 -> 46.sp
        text.length <= 12 -> 34.sp
        text.length <= 24 -> 26.sp
        else -> 21.sp
    }
    Text(
        text,
        color = Color.White,
        fontSize = size,
        lineHeight = size * 1.28f,
        fontWeight = FontWeight.Black,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun WrappedBigLine(big: String, unit: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(big, color = Color.White, fontSize = 72.sp, fontWeight = FontWeight.Black)
        if (unit.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text(unit, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 14.dp))
        }
    }
}

@Composable
private fun WrappedMiniStat(value: String, label: String) {
    Column {
        Text(value, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
    }
}

@Composable
private fun WrappedSharePage(d: WrappedData) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // 1080×1920 的總結圖，改在背景執行緒畫：翻到最後一頁不該整個卡住。
    val bitmap by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, d) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { buildWrappedShareBitmap(d) }
    }
    var msg by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${d.year} 年，謝謝你的每一場", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        val shareBmp = bitmap
        if (shareBmp == null) {
            Box(
                modifier = Modifier.fillMaxWidth(0.62f).aspectRatio(9f / 16f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp)) }
        } else {
            androidx.compose.foundation.Image(
                bitmap = shareBmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(0.62f).clip(RoundedCornerShape(12.dp))
            )
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(enabled = bitmap != null, onClick = {
                val b = bitmap ?: return@OutlinedButton
                val uri = com.bandary.data.util.BitmapExporter.downloadToGallery(context, b, "bandary_wrapped_${d.year}")
                msg = if (uri != null) "已下載到相簿" else "下載失敗"
            }) {
                Icon(Icons.Outlined.Download, null, modifier = Modifier.size(18.dp), tint = Color.White)
                Spacer(Modifier.width(6.dp)); Text("下載", color = Color.White)
            }
            Button(
                enabled = bitmap != null,
                onClick = {
                    val b = bitmap ?: return@Button
                    com.bandary.data.util.BitmapExporter.shareOnly(context, b, "bandary_wrapped_${d.year}")
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
            ) {
                Icon(Icons.Outlined.Share, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp)); Text("分享")
            }
        }
        msg?.let { Spacer(Modifier.height(10.dp)); Text(it, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp) }
    }
}

/** 年度回顧總結圖（9:16），把重點條列成一張可分享圖。 */
private fun buildWrappedShareBitmap(d: WrappedData): android.graphics.Bitmap {
    val w = 1080; val h = 1920
    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    canvas.drawColor(android.graphics.Color.parseColor("#161418"))
    val accent = android.graphics.Color.parseColor("#C9A96A")
    val white = android.graphics.Color.WHITE
    val grey = android.graphics.Color.parseColor("#AAAAAA")
    fun paint(size: Float, color: Int, bold: Boolean, align: android.graphics.Paint.Align = android.graphics.Paint.Align.LEFT) =
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; textSize = size; textAlign = align
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }
    val marginL = 90f
    canvas.drawText("BANDARY", marginL, 180f, paint(38f, accent, true))
    canvas.drawText("${d.year}", marginL, 400f, paint(200f, white, true))
    canvas.drawText("年度聽團回顧", marginL, 480f, paint(56f, white, true))

    // 只列有資料的欄位，並依「剩餘高度 ÷ 列數」平均分配 y，確保不會蓋到底部品牌列。
    val rows = buildList {
        add("演出場次" to "${d.totalShows}")
        add("不同藝人" to "${d.distinctArtists}")
        add("走過場館" to "${d.distinctVenues}")
        if (d.totalSpent > 0) add("現場投入" to "NT$${"%,d".format(d.totalSpent)}")
        d.topArtist?.let { add("年度藝人" to it) }
        d.farthestCity?.let { add("最遠抵達" to it) }
        if (d.totalSongs > 0) add("年度選歌" to "${d.totalSongs} 首")
        d.topSong?.let { add("最常回放" to it) }
    }
    val top = 620f
    val bottom = (h - 200).toFloat()               // 底部保留品牌列空間
    val step = ((bottom - top) / rows.size).coerceAtMost(150f)
    rows.forEachIndexed { i, (label, value) ->
        val y = top + step * (i + 0.7f)
        val isSong = label == "最常回放"
        val labelPaint = paint(38f, grey, false)
        canvas.drawText(label, marginL, y, labelPaint)

        // 數值靠右對齊往左長，長字串（多藝人的「五月天、告五人、茄子蛋…」、長歌名）會蓋掉左邊
        // 的標籤甚至衝出畫面——drawText 不換行也不縮字。所以先量可用寬度，逐級縮字，
        // 縮到下限仍放不下才截斷。
        val gap = 28f
        val avail = (w - marginL) - (marginL + labelPaint.measureText(label) + gap)
        val valuePaint = android.text.TextPaint(paint(if (isSong) 44f else 50f, if (isSong) accent else white, true, android.graphics.Paint.Align.RIGHT))
        while (valuePaint.measureText(value) > avail && valuePaint.textSize > 26f) {
            valuePaint.textSize -= 2f
        }
        val shown = if (valuePaint.measureText(value) > avail) {
            android.text.TextUtils.ellipsize(value, valuePaint, avail, android.text.TextUtils.TruncateAt.END).toString()
        } else value
        canvas.drawText(shown, w - marginL, y, valuePaint)
    }
    canvas.drawText("聽團曆 · bandary", marginL, (h - 90).toFloat(), paint(34f, grey, false))
    return bmp
}
