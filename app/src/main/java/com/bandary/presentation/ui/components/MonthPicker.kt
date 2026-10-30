package com.bandary.presentation.ui.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 左右滑動切換分頁：向左滑＝下一頁、向右滑＝上一頁。門檻約 80px（比換月份高一些，避免誤觸換頁）。
 *
 * 給「內容是單一長清單、無法拆成 HorizontalPager」的頁面用（例如統計頁：兩個模式共用同一個
 * LazyColumn）。同樣 consume 手勢，圖表自己的水平拖曳因為也有 consume，不會互相干擾。
 */
fun Modifier.swipeHorizontalPage(onPrev: () -> Unit, onNext: () -> Unit): Modifier = this.pointerInput(Unit) {
    var total = 0f
    detectHorizontalDragGestures(
        onDragStart = { total = 0f },
        onDragEnd = { if (total < -80f) onNext() else if (total > 80f) onPrev() },
        onHorizontalDrag = { change, dx ->
            change.consume()
            total += dx
        }
    )
}

/**
 * 左右滑動切換月份：向左滑＝下個月、向右滑＝上個月。門檻約 60px。
 *
 * **會 consume 掉水平拖曳**：外層若有 HorizontalPager（清單↔月曆、演出↔選歌），
 * 不消耗的話同一個手勢會同時換月又換頁。消耗之後規則很單純——
 * 手指在月曆格子上＝換月份，在其他地方＝換頁。
 */
fun Modifier.swipeMonth(onPrev: () -> Unit, onNext: () -> Unit): Modifier = this.pointerInput(Unit) {
    var total = 0f
    detectHorizontalDragGestures(
        onDragStart = { total = 0f },
        onDragEnd = {
            if (total < -60f) onNext() else if (total > 60f) onPrev()
        },
        onHorizontalDrag = { change, dx ->
            change.consume()
            total += dx
        }
    )
}

/** 一次選 1–12 月的月份挑選器：年份可上下切換，月份用 3×4 格點選。 */
@Composable
fun MonthYearPickerDialog(
    initialYear: Int,
    initialMonth: Int,   // 1–12
    onPick: (year: Int, month: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var year by remember { mutableStateOf(initialYear) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { year-- }) { Icon(Icons.Outlined.ChevronLeft, "前一年") }
                Text("$year 年", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                IconButton(onClick = { year++ }) { Icon(Icons.Outlined.ChevronRight, "後一年") }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (row in 0 until 4) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (col in 0 until 3) {
                            val m = row * 3 + col + 1
                            val selected = year == initialYear && m == initialMonth
                            Surface(
                                onClick = { onPick(year, m) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    "$m 月",
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    textAlign = TextAlign.Center,
                                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("關閉") } }
    )
}
