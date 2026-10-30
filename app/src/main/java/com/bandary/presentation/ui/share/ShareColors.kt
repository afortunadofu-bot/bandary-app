package com.bandary.presentation.ui.share

/** 分享（票根 / 統計）共用的配色。全部走莫蘭迪色系（低飽和、帶灰、高級感），與封面卡片一致。 */
object ShareColors {
    // 預設調色盤：一組協調的莫蘭迪色（綠 / 橄欖灰 / 駝 / 藕粉 / 霧藍）。另有調色盤按鈕可自訂。
    val THEME = listOf("#6B9080", "#8C927A", "#A88F82", "#B593A0", "#8398AC")

    /**
     * 把任意顏色「莫蘭迪化」：大幅降飽和、拉到中等明度、再往暖灰靠一點。
     * 票根純色預設會從海報主色取，海報常是高飽和的橘 / 紫，直接當底色太搶；經此轉換後統一成低飽和莫蘭迪調。
     */
    fun morandi(hex: String): String {
        val col = runCatching { android.graphics.Color.parseColor(hex) }.getOrElse { return hex }
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(col, hsv)
        hsv[1] = (hsv[1] * 0.5f).coerceAtMost(0.30f)   // 飽和度砍半並封頂
        hsv[2] = hsv[2].coerceIn(0.50f, 0.68f)          // 中等明度，避免死黑或死白
        val muted = android.graphics.Color.HSVToColor(hsv)
        // 再和暖灰混一點，強化莫蘭迪的「灰」。
        val gray = android.graphics.Color.rgb(150, 142, 133)
        fun mix(a: Int, b: Int) = (a * 0.85f + b * 0.15f).toInt().coerceIn(0, 255)
        val r = mix(android.graphics.Color.red(muted), android.graphics.Color.red(gray))
        val g = mix(android.graphics.Color.green(muted), android.graphics.Color.green(gray))
        val b = mix(android.graphics.Color.blue(muted), android.graphics.Color.blue(gray))
        return String.format("#%02X%02X%02X", r, g, b)
    }
}
