package com.bandary.presentation.ui.shows.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * 票根 Hero（spec §1、§7）：倒數大字為主角 + 封面縮圖（可點）+ 名稱 + 日期·場地 + 存根聯。
 * 收合動畫（高度 280→0、MiniTitle）由外層 shell 控制；本元件只負責畫「完整」內容。
 */
@Composable
fun TicketHero(
    countdownDays: Int?,       // null = 已結束 / 無日期；<=0 = 今天
    coverUri: String?,
    title: String,
    dateVenue: String,
    seat: String?,
    price: String?,
    statusLabel: String,
    onCoverClick: () -> Unit,
    modifier: Modifier = Modifier,
    collapsed: Boolean = false,
    onToggleCollapse: () -> Unit = {},
) {
    val countdownText = when {
        countdownDays == null -> "已結束"
        countdownDays <= 0 -> "就是今天"
        else -> "倒數 $countdownDays 天"
    }
    if (collapsed) {
        // 往下捲時的壓縮列：只留「標題 + 演出時間」，同一行。整條可點，點了捲回頂端就會展開。
        // 有封面時同樣用封面當底圖（與展開狀態一致），加暗色遮罩讓白字讀得到。
        val barHasCover = !coverUri.isNullOrBlank()
        Box(modifier.fillMaxWidth().clickable { onToggleCollapse() }) {
            if (barHasCover) {
                AsyncImage(
                    model = coverUri,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.5f)))
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title.ifBlank { "未命名演出" },
                    fontSize = EditTokens.bodyLarge, fontWeight = FontWeight.W700,
                    color = if (barHasCover) Color.White else EditTokens.Ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                // 日期時間取 dateVenue 的前段（場地在 " · " 之後，壓縮列不顯示）。
                Text(
                    dateVenue.substringBefore(" · "),
                    fontSize = 12.sp,
                    color = if (barHasCover) Color.White.copy(alpha = 0.85f) else EditTokens.InkSoft,
                    maxLines = 1, softWrap = false
                )
            }
        }
        return
    }
    // 有封面時整張票根用封面當底圖，文字改白字 + 暗色遮罩（scrim）確保可讀；
    // 沒封面時退回原本的淺色卡片。封面已經在這裡看得到，表單裡就不必再放一次大預覽。
    val hasCover = !coverUri.isNullOrBlank()
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(EditTokens.heroRadius))
            .background(EditTokens.Card)
            .clickable { onToggleCollapse() }   // 點 Hero 收合成單行
    ) {
        if (hasCover) {
            AsyncImage(
                model = coverUri,
                contentDescription = "封面",
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
            // 由下往上加深的遮罩：上緣保留圖片、下緣壓暗讓存根聯的小字讀得到。
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.30f), Color.Black.copy(alpha = 0.68f))
                    )
                )
            )
        }
        val onCover = if (hasCover) Color.White else EditTokens.Ink
        val onCoverSoft = if (hasCover) Color.White.copy(alpha = 0.82f) else EditTokens.InkSoft
        val onCoverFaint = if (hasCover) Color.White.copy(alpha = 0.65f) else EditTokens.InkFaint
        val accent = if (hasCover) Color.White else EditTokens.SageDeep

        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // ── 名稱 + 日期（主體）──
            Column(Modifier.weight(1f)) {
                Text(
                    title.ifBlank { "未命名演出" },
                    fontSize = 16.sp, fontWeight = FontWeight.W800, color = onCover,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(dateVenue, fontSize = 12.sp, color = onCoverSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                // 倒數：低調小字（依使用者回饋，不要大字占版面）
                Text(
                    countdownText,
                    fontSize = 13.sp, fontWeight = FontWeight.W700,
                    color = if (countdownDays == null) onCoverFaint else accent
                )
            }
            Spacer(Modifier.width(12.dp))
            // 沒封面時才留縮圖入口（點→跳照片區）；有封面時整張就是封面，不再重複放縮圖。
            if (!hasCover) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))
                        .background(EditTokens.SageTint).clickable { onCoverClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Image, null, tint = EditTokens.Sage, modifier = Modifier.size(20.dp))
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        // ── 打孔虛線（存根聯撕線）──
        PerforationLine(if (hasCover) Color.White.copy(alpha = 0.45f) else EditTokens.Line)
        Spacer(Modifier.height(6.dp))
        // ── 存根聯：座位 / 票價 / 狀態 單行小字（不再兩行一格，把高度壓到最低）──
        Row(Modifier.fillMaxWidth()) {
            InlineStub("座位", seat?.takeIf { it.isNotBlank() } ?: "—", Modifier.weight(1.2f), onCover, onCoverFaint)
            InlineStub("票價", price?.takeIf { it.isNotBlank() } ?: "—", Modifier.weight(1f), onCover, onCoverFaint)
            InlineStub("狀態", statusLabel, Modifier.weight(1f), onCover, onCoverFaint)
        }
        }
    }
}

@Composable
private fun InlineStub(
    label: String,
    value: String,
    modifier: Modifier,
    valueColor: Color,
    labelColor: Color
) {
    Row(modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$label ", fontSize = 11.sp, color = labelColor)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.W700, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PerforationLine(color: Color) {
    Canvas(Modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
        )
    }
}
