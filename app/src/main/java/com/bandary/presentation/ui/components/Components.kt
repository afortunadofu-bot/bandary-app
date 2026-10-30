package com.bandary.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandary.domain.model.Concert
import com.bandary.domain.model.ConcertType
import com.bandary.domain.model.TicketStatus
import com.bandary.presentation.ui.theme.BandaryColors

// ── Chip base ─────────────────────────────────────────────────
@Composable
fun StatusChip(
    text: String,
    bg: Color,
    fg: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = bg
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun TicketStatusChip(status: TicketStatus, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg, label) = when (status) {
        TicketStatus.NONE -> Triple(scheme.surfaceContainer, scheme.outline, "未購票")
        TicketStatus.BOUGHT -> Triple(scheme.primaryContainer, scheme.onPrimaryContainer, "✓ 已購票")
        TicketStatus.PICKED_UP -> Triple(BandaryColors.TealBg, BandaryColors.Teal, "已取票")
        TicketStatus.WATCHED -> Triple(BandaryColors.PurpleBg, BandaryColors.Purple, "已看過")
    }
    StatusChip(label, bg, fg, modifier)
}

@Composable
fun PlatformChip(platform: String, modifier: Modifier = Modifier) {
    val (bg, fg) = when (platform) {
        "KKTIX" -> BandaryColors.PrimaryContainer to BandaryColors.OnPrimaryContainer
        "ibon", "IBON" -> BandaryColors.BlueBg to BandaryColors.Blue
        "拓元", "TIXCRAFT", "tixcraft" -> BandaryColors.RedBg to BandaryColors.Red
        "Ticket Plus", "遠大", "TICKETPLUS" -> BandaryColors.PurpleBg to BandaryColors.Purple
        else -> MaterialTheme.colorScheme.surfaceContainer to MaterialTheme.colorScheme.outline
    }
    StatusChip(platform, bg, fg, modifier)
}

@Composable
fun ConcertTypeChip(type: ConcertType, modifier: Modifier = Modifier) {
    StatusChip(
        text = type.label,
        bg = MaterialTheme.colorScheme.surfaceContainerHigh,
        fg = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

// ── Date Box ──────────────────────────────────────────────────
@Composable
fun DateBox(
    day: Int,
    month: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        modifier = modifier.size(52.dp),
        shape = RoundedCornerShape(10.dp),
        color = backgroundColor
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "$day",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                lineHeight = 22.sp
            )
            Text(
                month.uppercase(),
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor.copy(alpha = 0.7f)
            )
        }
    }
}

// ── Concert Card ──────────────────────────────────────────────
@Composable
fun ConcertCard(
    concert: Concert,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DateBox(
                day = concert.dateTime.dayOfMonth,
                month = concert.dateTime.month.name.take(3)
            )
            Column(
                modifier = Modifier.weight(1f, fill = true),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    concert.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        Icons.Outlined.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        buildString {
                            concert.city?.takeIf { it.isNotBlank() }?.let { append(it).append(" · ") }
                            append(concert.venue)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TicketStatusChip(concert.ticketStatus)
                    concert.ticketPlatform?.takeIf { it.isNotBlank() }?.let { PlatformChip(it) }
                }
            }
            trailing?.invoke()
        }
    }
}

// ── Section Header ────────────────────────────────────────────
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // 前導色條——深色主題下區塊之間也能一眼分清。
            Box(
                modifier = Modifier
                    .size(3.dp, 14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        }
        action?.invoke()
    }
}

// ── Avatar ────────────────────────────────────────────────────
@Composable
fun UserAvatar(
    initials: String,
    size: Dp = 44.dp,
    backgroundColor: Color = MaterialTheme.colorScheme.primaryContainer,
    textColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    hasActiveSong: Boolean = false,
    modifier: Modifier = Modifier
) {
    val outerMod = if (hasActiveSong) {
        modifier
            .size(size + 6.dp)
            .clip(CircleShape)
            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
            .padding(3.dp)
    } else {
        modifier.size(size)
    }
    Box(
        modifier = outerMod
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            initials,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.36).sp,
            color = textColor,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

// ── Empty State ───────────────────────────────────────────────
/**
 * [onClick] 有給時（E-2）整塊變成可點的「ghost card」——虛線框 + 引導文字，
 * 點下去直接做那件事（例如新增第一場），比純文字空狀態更像「示範卡片」。
 * 沒給就維持原本純資訊性的空狀態（其餘畫面沿用，行為不變）。
 */
@Composable
fun EmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    actionLabel: String? = null
) {
    val content = @Composable {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                if (onClick != null) Icons.Outlined.Add else Icons.Outlined.ConfirmationNumber,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (onClick != null && actionLabel != null) {
                Text(
                    actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
    if (onClick != null) {
        androidx.compose.material3.Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            color = androidx.compose.ui.graphics.Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )
        ) {
            Box(modifier = Modifier.padding(horizontal = 40.dp, vertical = 40.dp)) { content() }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) { content() }
    }
}

// ── Countdown unit ────────────────────────────────────────────
@Composable
fun CountdownUnit(
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    valueSize: androidx.compose.ui.unit.TextUnit = 28.sp
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            value,
            fontSize = valueSize,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            lineHeight = (valueSize.value + 4).sp
        )
        Text(
            unit,
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.75f),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun CountdownSeparator() {
    Text(
        ":",
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White.copy(alpha = 0.5f),
        modifier = Modifier.padding(top = 2.dp)
    )
}
