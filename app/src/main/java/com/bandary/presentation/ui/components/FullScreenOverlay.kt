package com.bandary.presentation.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 全螢幕覆蓋層（滿版 Dialog）的共用外框。
 *
 * 為什麼需要這個元件：App 用 `enableEdgeToEdge()`，而根 Scaffold 把 contentWindowInsets
 * 設為 0，把 inset 處理下放給每個畫面自己負責。有自己 Scaffold 的畫面靠 Scaffold 的
 * 預設行為會處理掉，但**直接用 `Dialog(usePlatformDefaultWidth = false)` 的滿版覆蓋層
 * 沒有這層保護**——少寫一行 inset，標題列就躲到狀態列底下、按鈕就被手勢列蓋住。
 * 這個 bug 已經真的發生過兩次（v1.50.3 的看圖關閉鈕與頁數指示、
 * v1.58.1 的設定全螢幕頁標題列）。
 *
 * 所以這裡把「讓開系統列」變成**預設值**而不是每次都要記得寫的約定。
 * 之後所有滿版覆蓋層都應該用這個，而不是自己開 Dialog。
 *
 * @param insetContent 內容要不要自動讓開系統列。
 *   true（預設）＝一般頁面型覆蓋層，整塊內容都讓開。
 *   false＝內容本身要滿版出血（例如看圖、影片），此時**呼叫端必須自己替控制項
 *   加上 [systemBarsPadding]**，否則關閉鈕之類的東西會躲到系統列底下。
 */
@Composable
fun FullScreenOverlay(
    onDismiss: () -> Unit,
    background: Color = MaterialTheme.colorScheme.background,
    insetContent: Boolean = true,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = dismissOnBackPress,
            dismissOnClickOutside = dismissOnClickOutside
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = background) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .then(if (insetContent) Modifier.systemBarsPadding() else Modifier),
                content = content
            )
        }
    }
}
