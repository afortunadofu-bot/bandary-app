package com.bandary.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage

/** 全螢幕看圖：點背景或右上角關閉；可雙指縮放、拖曳。 */
@Composable
fun ImageZoomDialog(model: Any?, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.96f))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f; offsetY = 0f
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = model,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale, scaleY = scale,
                        translationX = offsetX, translationY = offsetY
                    ),
                contentScale = ContentScale.Fit
            )
            // 圖片維持滿版（去背景才好看），但控制項一定要讓開系統列——
            // 這個 Dialog 是 usePlatformDefaultWidth=false 的全螢幕，少了 systemBarsPadding
            // 關閉鈕會直接躲到狀態列底下按不到。
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).systemBarsPadding().padding(12.dp)
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "關閉", tint = Color.White)
            }
        }
    }
}

/**
 * 全螢幕看圖（多張，免費活動用）：左右滑切換、上下捲看長海報。
 *
 * 跟 [SwipeableImageViewer] 分開的原因：活動圖存在 Firestore 文件裡是「不含 data: 前綴的
 * 純 Base64」，Coil 的 AsyncImage 吃不下，一定要走 [EventImage] 自己解碼。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SwipeableEventImageViewer(images: List<String>, startIndex: Int = 0, onDismiss: () -> Unit) {
    if (images.isEmpty()) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val pagerState = androidx.compose.foundation.pager.rememberPagerState(
            initialPage = startIndex.coerceIn(0, images.lastIndex),
            pageCount = { images.size }
        )
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.94f)),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                // 活動海報常常是很長的時間表，用 Fit 會縮到看不見字；
                // 改成滿寬 + 垂直捲動，字才讀得到。
                val pageScroll = rememberScrollState()
                Box(
                    modifier = Modifier.fillMaxSize().verticalScroll(pageScroll),
                    contentAlignment = Alignment.Center
                ) {
                    EventImage(
                        data = images[page],
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (images.size > 1) {
                androidx.compose.material3.Surface(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                    modifier = Modifier.align(Alignment.BottomCenter).systemBarsPadding().padding(24.dp)
                ) {
                    androidx.compose.material3.Text(
                        "${pagerState.currentPage + 1} / ${images.size}",
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).systemBarsPadding().padding(12.dp)
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "關閉", tint = Color.White)
            }
        }
    }
}

/** 全螢幕看圖（多張）：左右滑動切換、雙指縮放。用於演唱會照片 / 場位圖一起瀏覽。 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SwipeableImageViewer(images: List<String>, startIndex: Int = 0, onDismiss: () -> Unit) {
    if (images.isEmpty()) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val pagerState = androidx.compose.foundation.pager.rememberPagerState(
            initialPage = startIndex.coerceIn(0, images.lastIndex),
            pageCount = { images.size }
        )
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.96f)),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                var scale by remember { mutableFloatStateOf(1f) }
                var offsetX by remember { mutableFloatStateOf(0f) }
                var offsetY by remember { mutableFloatStateOf(0f) }
                val zoomed = scale > 1f
                Box(
                    modifier = Modifier.fillMaxSize()
                        // 雙擊縮放：在 1x 時不攔截水平拖曳，讓 HorizontalPager 能左右滑切換圖片（修正多圖滑不動的 bug）。
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = {
                                if (scale > 1f) { scale = 1f; offsetX = 0f; offsetY = 0f } else scale = 2.5f
                            })
                        }
                        // 只有放大後才接管拖曳 / 縮放手勢；1x 時交給 pager 處理左右滑。
                        .then(
                            if (zoomed) Modifier.pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 5f)
                                    if (scale > 1f) { offsetX += pan.x; offsetY += pan.y } else { offsetX = 0f; offsetY = 0f }
                                }
                            } else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = images[page],
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().graphicsLayer(
                            scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY
                        ),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            // 頁數指示
            if (images.size > 1) {
                androidx.compose.material3.Surface(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                    modifier = Modifier.align(Alignment.BottomCenter).systemBarsPadding().padding(24.dp)
                ) {
                    androidx.compose.material3.Text(
                        "${pagerState.currentPage + 1} / ${images.size}",
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).systemBarsPadding().padding(12.dp)
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "關閉", tint = Color.White)
            }
        }
    }
}
