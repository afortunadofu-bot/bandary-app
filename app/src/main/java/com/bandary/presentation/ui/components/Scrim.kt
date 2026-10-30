package com.bandary.presentation.ui.components

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * 全站統一的封面 scrim（壓在封面圖上，讓其上的文字可讀）。
 * 一處定義、各頁共用，確保總覽卡片 / 清單列 / 詳情頁的壓暗一致。
 *
 * - light  = 淺色主題：壓白（配深色字）。深色主題：壓黑（配白字）。
 * - solidBg = 底圖是純色漸層（非照片）時用較弱的壓暗，避免過黑。
 */
object Scrim {
    /** 由上而下漸深（文字置底時用）。 */
    fun bottom(light: Boolean = false, solidBg: Boolean = false): Brush = when {
        // 淺色：平滑漸層壓白（避免出現明顯的「白色半透明框」邊界），只在最底稍微加強。
        light -> Brush.verticalGradient(
            0f to Color.Transparent,
            0.6f to Color.White.copy(alpha = 0.12f),
            1f to Color.White.copy(alpha = 0.62f)
        )
        solidBg -> Brush.verticalGradient(
            0f to Color.Transparent,
            0.55f to Color.Black.copy(alpha = 0.05f),
            1f to Color.Black.copy(alpha = 0.36f)
        )
        else -> Brush.verticalGradient(
            0f to Color.Transparent,
            0.45f to Color.Black.copy(alpha = 0.15f),
            1f to Color.Black.copy(alpha = 0.78f)
        )
    }

    /** 由左而右漸淡（文字置左時用）。 */
    fun start(light: Boolean = false, solidBg: Boolean = false): Brush = when {
        light -> Brush.horizontalGradient(
            0f to Color.White.copy(alpha = 0.80f),
            0.6f to Color.White.copy(alpha = 0.30f),
            1f to Color.Transparent
        )
        solidBg -> Brush.horizontalGradient(
            0f to Color.Black.copy(alpha = 0.30f),
            1f to Color.Black.copy(alpha = 0.10f)
        )
        else -> Brush.horizontalGradient(
            0f to Color.Black.copy(alpha = 0.62f),
            1f to Color.Black.copy(alpha = 0.30f)
        )
    }
}
