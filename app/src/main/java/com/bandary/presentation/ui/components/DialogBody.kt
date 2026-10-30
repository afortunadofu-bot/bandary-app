package com.bandary.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * `AlertDialog` 的 `text = { }` 內容外框。可捲動，而且高度不會超過螢幕。
 *
 * ## 為什麼需要
 * `AlertDialog` 的 text 槽有高度上限，內容超過就被裁掉——而且**被裁掉的是下面，
 * 連帶把「確定」按鈕推出畫面**。使用者看得到對話框卻按不到確定，只能按返回取消，
 * 而且完全不知道為什麼。
 *
 * 這在開發機上幾乎看不到，因為開發機螢幕大、字體預設大小。
 * 真正會踩到的是：小螢幕、系統字體放大（無障礙設定）、橫向、分割畫面。
 * 也就是說，**最需要這個保護的使用者，最不可能是你自己**。
 *
 * ## 為什麼上限是螢幕高度的一半
 * 對話框還要放標題與按鈕，而且上下要留白才不會貼齊螢幕邊緣。
 * 實際量下來 55% 是「內容盡量多、但按鈕一定看得到」的平衡點。
 * 比例而不是固定 dp：固定值在平板上會浪費一大片，在小機上又還是會擠。
 */
@Composable
fun DialogBody(
    modifier: Modifier = Modifier,
    spacing: Dp = 10.dp,
    /** 內容很少時不需要捲動容器；傳 false 可以省掉它（例如只有一兩行字的確認框）。 */
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val maxH = LocalConfiguration.current.screenHeightDp.dp * 0.55f
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (scrollable) Modifier.heightIn(max = maxH) else Modifier)
            .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content
    )
}
