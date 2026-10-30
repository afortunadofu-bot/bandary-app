package com.bandary.presentation.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 清單是否正在往上捲（或停在頂部）。浮動按鈕用它決定要不要讓位：
 * 往下捲（正在看內容）時藏起來，才不會蓋住卡片上的日期、地點與按鈕；往上捲或回到頂部再出現。
 */
@Composable
fun LazyListState.isScrollingUp(): Boolean {
    var previousIndex by remember(this) { mutableIntStateOf(firstVisibleItemIndex) }
    var previousOffset by remember(this) { mutableIntStateOf(firstVisibleItemScrollOffset) }
    return remember(this) {
        derivedStateOf {
            val up = if (previousIndex != firstVisibleItemIndex) {
                previousIndex > firstVisibleItemIndex
            } else {
                previousOffset >= firstVisibleItemScrollOffset
            }
            previousIndex = firstVisibleItemIndex
            previousOffset = firstVisibleItemScrollOffset
            up
        }
    }.value
}

/**
 * 「回到頂部」小按鈕：當清單捲動超過幾個項目、而且使用者正在往上捲時才出現。各頁共用，行為一致。
 * 放在 Scaffold 的 floatingActionButton 槽，或任一 Box 內對齊角落。
 */
@Composable
fun ScrollTopFab(
    listState: LazyListState,
    scope: CoroutineScope,
    modifier: Modifier = Modifier
) {
    val scrolledDown by remember { derivedStateOf { listState.firstVisibleItemIndex > 1 } }
    val scrollingUp = listState.isScrollingUp()
    if (scrolledDown && scrollingUp) {
        SmallFloatingActionButton(
            onClick = { scope.launch { listState.animateScrollToItem(0) } },
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = modifier
        ) {
            Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "回到頂部")
        }
    }
}
