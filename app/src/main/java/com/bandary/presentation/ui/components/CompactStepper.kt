package com.bandary.presentation.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 精簡的期間步進器：一顆小膠囊，內含 ‹ 標籤 ›。取代原本佔滿整列、chevron 過大的設計。
 * 標籤可點（例如叫出年月選擇器）。
 */
@Composable
fun CompactStepper(
    label: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    onLabelClick: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev, modifier = Modifier.size(26.dp)) {
                Icon(Icons.Outlined.ChevronLeft, "上一個", modifier = Modifier.size(16.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = (if (onLabelClick != null) Modifier.clickable { onLabelClick() } else Modifier)
                    .padding(horizontal = 2.dp)
            )
            IconButton(onClick = onNext, modifier = Modifier.size(26.dp)) {
                Icon(Icons.Outlined.ChevronRight, "下一個", modifier = Modifier.size(16.dp))
            }
        }
    }
}
