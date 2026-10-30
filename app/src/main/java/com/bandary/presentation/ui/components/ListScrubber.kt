package com.bandary.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 通用快捲把手：右側一條可上下拖的握把，依比例跳到清單的任一位置。
 *
 * 用在很長的頁面（統計頁）。與「我的演唱會」的 TimeScrubber 差別：
 * 那個是按「月份分組」跳，這個沒有分組概念，純粹按比例跳。
 *
 * [topInset] / [bottomInset] 一定要給：這個元件通常疊在滿版的 Box 上，
 * 不讓開 TopAppBar 與底部導覽的話，握把最上／最下段會被蓋住、摸不到。
 */
@Composable
fun ListScrubber(
    state: LazyListState,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp,
    minItems: Int = 12          // 內容太短就不顯示，免得多一條沒用的東西
) {
    val total = state.layoutInfo.totalItemsCount
    if (total < minItems) return
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var dragFrac by remember { mutableFloatStateOf(0f) }

    val frac = if (dragging) dragFrac
    else (state.firstVisibleItemIndex.toFloat() / (total - 1).coerceAtLeast(1))
    val thumbFrac = frac.coerceIn(0.02f, 0.98f)

    fun jumpTo(f: Float) {
        val target = (f * (total - 1)).roundToInt().coerceIn(0, total - 1)
        scope.launch { state.scrollToItem(target) }
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(top = topInset, bottom = bottomInset)
            // 44dp 觸控區（Material 觸控下限），比細細一條好抓很多。
            .width(44.dp)
            .pointerInput(total) {
                detectVerticalDragGestures(
                    onDragStart = { off ->
                        dragging = true
                        dragFrac = (off.y / size.height).coerceIn(0f, 1f)
                        jumpTo(dragFrac)
                    },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        dragFrac = (change.position.y / size.height).coerceIn(0f, 1f)
                        jumpTo(dragFrac)
                    }
                )
            }
    ) {
        Column(
            Modifier.fillMaxHeight().width(10.dp).align(Alignment.CenterEnd).padding(vertical = 8.dp)
        ) {
            Spacer(Modifier.weight(thumbFrac))
            Box(
                Modifier.width(10.dp).height(56.dp).clip(RoundedCornerShape(5.dp))
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = if (dragging) 1f else 0.6f)
                    )
            )
            Spacer(Modifier.weight(1f - thumbFrac))
        }
    }
}
