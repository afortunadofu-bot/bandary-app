package com.bandary.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.bandary.domain.model.Concert
import com.bandary.domain.model.isFromCalendarImport
import com.bandary.domain.model.occurrenceDates
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.daysUntil

// 莫蘭迪色系漸層對（低飽和、帶灰，高級感）。沒自訂色時依場次 id 取一組，畫面有變化又和諧。
private val MORANDI_GRADIENTS = listOf(
    listOf(Color(0xFFB8A9A0), Color(0xFF8C7A72)), // 灰粉棕
    listOf(Color(0xFFA7B0A0), Color(0xFF7C8A78)), // 灰綠
    listOf(Color(0xFFA3AEB8), Color(0xFF73818F)), // 霧藍
    listOf(Color(0xFFC2B6A8), Color(0xFF94856F)), // 米駝
    listOf(Color(0xFFB6A8B0), Color(0xFF877785)), // 灰紫
    listOf(Color(0xFFAFB39E), Color(0xFF7E8268))  // 橄欖灰
)

// 對應 MORANDI_GRADIENTS 每組的代表色（較深那個），給票根等處取一致的底色。
private val MORANDI_BASE_HEX = listOf("#8C7A72", "#7C8A78", "#73818F", "#94856F", "#877785", "#7E8268")

/**
 * 這場「預設底圖」：沒有真正封面、也沒選過自訂色的活動，從內建 5 張底圖挑一張（依 id 穩定分配）。
 * 匯入的、以及既有尚未修改的活動都自動套用。沒放底圖素材（availableIndices 空）時回 null，呼叫端沿用漸層底。
 */
fun defaultPosterCoverUri(context: android.content.Context, concert: Concert): String? {
    val idxs = com.bandary.presentation.ui.share.PosterBackgrounds.availableIndices(context)
    if (idxs.isEmpty()) return null
    val pick = idxs[kotlin.math.abs(concert.id.hashCode()) % idxs.size]
    return com.bandary.presentation.ui.share.PosterBackgrounds.coverUri(context, pick)
}

/**
 * 全 App 統一的「有效封面」——我的演唱會卡片、詳情頁 hero、編輯頁預覽、票根分享都以此為準，確保是同一張圖：
 * 指定當天封面(多日) > 使用者上傳封面 > 其他照片第一張 > 依 id 分配的內建底圖。
 * 使用者選過自訂底色(coverColorHex)時回 null，代表該用漸層底色而非圖片。
 */
fun effectiveCoverUri(context: android.content.Context, concert: Concert, overridePoster: String? = null): String? =
    overridePoster?.takeIf { it.isNotBlank() }
        ?: concert.photoUri?.takeIf { it.isNotBlank() }
        ?: concert.photoUris.firstOrNull { it.isNotBlank() }
        ?: if (concert.coverColorHex.isNullOrBlank()) defaultPosterCoverUri(context, concert) else null

/** 這場「有效底色」的 hex：有自訂 coverColorHex 就用它，否則用卡片依 id 取到的莫蘭迪色。
 *  票根用它當預設色，才會跟卡片顏色一致。 */
fun concertBaseColorHex(concert: Concert): String =
    concert.coverColorHex?.takeIf { it.isNotBlank() }
        ?.let { com.bandary.presentation.ui.share.ShareColors.morandi(it) }   // 海報主色常高飽和，統一莫蘭迪化
        ?: MORANDI_BASE_HEX[kotlin.math.abs(concert.id.hashCode()) % MORANDI_BASE_HEX.size]

/** 這場所有藝人（主藝人 + 選取的其他歌手）合成一行，音樂節可同時顯示多位。 */
fun concertArtistsLabel(concert: Concert): String =
    (listOfNotNull(concert.artistName.takeIf { it.isNotBlank() }) +
        concert.festivals.filter { it.isSelected }.map { it.artistName })
        .filter { it.isNotBlank() }.distinct()
        .joinToString(" / ").ifBlank { concert.artistName }

/** 卡片底圖漸層：有自訂 coverColorHex 就用它推導莫蘭迪漸層，否則依 id 取一組莫蘭迪色。 */
fun concertCoverBrush(concert: Concert): Brush {
    val custom = concert.coverColorHex
        ?.let { com.bandary.presentation.ui.share.ShareColors.morandi(it) }   // 卡片漸層底也莫蘭迪化，與票根一致
        ?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
    return if (custom != null) {
        Brush.linearGradient(
            listOf(lerp(custom, Color.White, 0.30f), custom, lerp(custom, Color(0xFF55514B), 0.32f))
        )
    } else {
        val pair = MORANDI_GRADIENTS[kotlin.math.abs(concert.id.hashCode()) % MORANDI_GRADIENTS.size]
        Brush.linearGradient(pair)
    }
}

/** 全幅美圖卡：海報底圖（或自訂漸層）+ Scrim + 文字疊加。演唱會列表 / 月曆共用。 */
/** 卡片顯示資訊層級：0=只留圖片、1=名稱+日期、2=名稱+日期+藝人+地點（完整）。 */
@Composable
fun ConcertCoverCard(
    concert: Concert,
    now: LocalDateTime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    heightDp: Int = 220,
    infoLevel: Int = 2,
    overridePoster: String? = null   // 多日活動：指定當天封面
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // 全 App 統一的封面來源（見 effectiveCoverUri）：指定當天封面 > 使用者封面 > 其他照片 > 依 id 內建底圖。
    val effectivePoster = androidx.compose.runtime.remember(concert.id, overridePoster, concert.photoUri, concert.photoUris, concert.coverColorHex) {
        effectiveCoverUri(context, concert, overridePoster)
    }
    val isUpcoming = concert.dateTime >= now
    // 逐場「淺色漸層」只套用在有圖的封面（純色漸層底維持白字較保險）。
    val useLight = concert.coverGradientLight && effectivePoster != null
    val fg = if (useLight) Color(0xFF1E1E1E) else Color.White
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(2.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(heightDp.dp)) {
            if (effectivePoster != null) {
                AsyncImage(
                    model = effectivePoster,
                    contentDescription = concert.artistName,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(modifier = Modifier.matchParentSize().background(concertCoverBrush(concert)))
            }
            // Scrim（全站統一，見 components/Scrim）：只在有疊字（infoLevel >= 1）時才壓，
            // 「只留圖片」模式不壓，避免底部出現沒有作用的白色/半透明框。
            if (infoLevel >= 1) {
                Box(modifier = Modifier.matchParentSize().background(Scrim.bottom(light = useLight, solidBg = effectivePoster == null)))
            }
            // 從 Google 日曆匯入的活動加標記，方便和手動新增的區分。
            if (concert.isFromCalendarImport()) {
                Surface(
                    color = if (useLight) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Icon(Icons.Outlined.CalendarMonth, null, modifier = Modifier.size(12.dp), tint = fg)
                        Text("日曆匯入", color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (isUpcoming) {
                val days = now.date.daysUntil(concert.dateTime.date)
                Surface(
                    color = if (useLight) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                ) {
                    Text(
                        if (days <= 0) "今天" else "倒數 $days 天",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                    )
                }
            }
            // 還沒買票的即將到來場次：左上角標「還沒買票」（購票狀況＝無＝尚未購票）。
            if (isUpcoming && concert.ticketStatus == com.bandary.domain.model.TicketStatus.NONE) {
                androidx.compose.material3.Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        "還沒買票",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }
            // infoLevel 0 = 只留圖片，不疊任何文字。
            if (infoLevel >= 1) {
                Column(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(14.dp)) {
                    Text(
                        buildString {
                            val d = concert.dateTime
                            append("${d.year}.${d.monthNumber.toString().padStart(2, '0')}.${d.dayOfMonth.toString().padStart(2, '0')}")
                            val occ = concert.occurrenceDates()
                            when {
                                concert.endDate?.let { it > d.date } == true && concert.extraDates.isEmpty() ->
                                    concert.endDate?.let { ed ->
                                        append(" – ${ed.monthNumber.toString().padStart(2, '0')}.${ed.dayOfMonth.toString().padStart(2, '0')}")
                                    }
                                occ.size > 1 -> append("　共 ${occ.size} 天")
                            }
                        },
                        color = fg.copy(alpha = 0.75f), fontSize = 12.sp, fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                    // 場次名稱為主。完整資訊（infoLevel 2）用大標；「名稱＋日期」層級（拿掉場地/藝人）
                    // 改用較秀氣的字級與字重，長標題換行時排版更乾淨、不會一大塊擠在圖片下緣。
                    val fullInfo = infoLevel >= 2
                    val titleSize = if (fullInfo) 22 else 19
                    Text(
                        concert.name.ifBlank { concertArtistsLabel(concert) },
                        color = fg,
                        fontSize = titleSize.sp,
                        fontWeight = if (fullInfo) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = (titleSize + 5).sp
                    )
                    // 藝人：字比名稱小（infoLevel 2 才顯示）。
                    if (infoLevel >= 2 && concert.name.isNotBlank() && concertArtistsLabel(concert).isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            concertArtistsLabel(concert),
                            // 單行即可：短卡片（橫式封面約 170dp）時，2 行藝人會把下方地點擠出卡片而被裁掉。
                            color = fg.copy(alpha = 0.9f), fontSize = 14.sp, fontWeight = FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (infoLevel >= 2) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Outlined.LocationOn, null, modifier = Modifier.size(13.dp), tint = fg.copy(alpha = 0.7f))
                            Text(
                                buildString {
                                    concert.city?.takeIf { it.isNotBlank() }?.let { append(it).append(" · ") }
                                    append(concert.venue)
                                },
                                color = fg.copy(alpha = 0.8f), fontSize = 13.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    // 第 3 層：票價 + 售票平台。放最後一層，因為這是「買票時才在意」的資訊，
                    // 不該和名稱、場地搶第一眼。
                    if (infoLevel >= 3) {
                        val priceText = concert.ticketPrice?.let {
                            if (it > 0) "NT$${it.toInt()}" else "免費"
                        }
                        val parts = listOfNotNull(priceText, concert.ticketPlatform?.takeIf { it.isNotBlank() })
                        if (parts.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Outlined.ConfirmationNumber, null, modifier = Modifier.size(13.dp), tint = fg.copy(alpha = 0.7f))
                                Text(
                                    parts.joinToString(" · "),
                                    color = fg.copy(alpha = 0.8f), fontSize = 13.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
