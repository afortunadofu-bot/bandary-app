package com.bandary.presentation.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider

/**
 * Widget 配色，沿用 App 的鼠尾草綠品牌色。Glance 不吃 MaterialTheme，故獨立定義。
 * 使用固定淺色卡片：widget 自帶白底，深色桌布上也清楚可讀。
 */
object WidgetColors {
    val accent = ColorProvider(Color(0xFF6B9080))
    val onAccent = ColorProvider(Color.White)
    val surface = ColorProvider(Color(0xFFFFFFFF))
    val surfaceVariant = ColorProvider(Color(0xFFEAF4F4))
    val onSurface = ColorProvider(Color(0xFF1E2A26))
    val onSurfaceVariant = ColorProvider(Color(0xFF4A5A54))
    val sale = ColorProvider(Color(0xFFC76A1A))
}
