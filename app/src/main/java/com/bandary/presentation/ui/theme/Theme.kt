package com.bandary.presentation.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandary.data.local.ThemeMode
import com.bandary.data.local.ThemePalette

// ── Brand palette (legacy access) ─────────────────────────────
object BandaryColors {
    // 馬卡龍鼠尾草綠色階：6B9080 / A4C3B2 / CCE3DE / EAF4F4 / F6FFF8
    val Primary = Color(0xFF6B9080)
    val PrimaryContainer = Color(0xFFCCE3DE)
    val OnPrimaryContainer = Color(0xFF223A31)

    val Secondary = Color(0xFF52776A)
    val SecondaryContainer = Color(0xFFD6E8E1)

    val Tertiary = Color(0xFF5E8C84)
    val TertiaryContainer = Color(0xFFCFE6E2)
    val OnTertiaryContainer = Color(0xFF18302B)

    val Background = Color(0xFFF6FFF8)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceVariant = Color(0xFFE3EEEA)
    val SurfaceContainer = Color(0xFFEAF4F4)
    val SurfaceContainerHigh = Color(0xFFDDEBE6)

    val OnSurface = Color(0xFF1E2A26)
    val OnSurfaceVariant = Color(0xFF4A5A54)
    val Outline = Color(0xFF8AA39A)
    val OutlineVariant = Color(0xFFC4D6CF)

    val Error = Color(0xFFBA1A1A)
    val ErrorContainer = Color(0xFFFFDAD6)

    val DarkBackground = Color(0xFF0F1512)
    val DarkSurface = Color(0xFF171E1B)
    val DarkSurfaceVariant = Color(0xFF3D4A45)
    val DarkOnSurface = Color(0xFFDCE6E1)

    val Orange = Color(0xFFC76A1A)
    val OrangeBg = Color(0xFFFDF0E3)
    val Blue = Color(0xFF1A5FA8)
    val BlueBg = Color(0xFFE6EEFA)
    val Red = Color(0xFFB8382B)
    val RedBg = Color(0xFFFCEAE7)
    val Yellow = Color(0xFFB8940E)
    val YellowBg = Color(0xFFFDF5D9)
    val Purple = Color(0xFF6B3FA0)
    val PurpleBg = Color(0xFFF0EAF8)
    val Teal = Color(0xFF0F7A6E)
    val TealBg = Color(0xFFDCF2EF)
}

// ── Palette definitions ──────────────────────────────────────
data class BrandPalette(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val tertiary: Color,
    val darkPrimary: Color,
    val darkPrimaryContainer: Color,
    val darkOnPrimary: Color,
    val darkOnPrimaryContainer: Color
)

private val GREEN_PALETTE = BrandPalette(
    primary = Color(0xFF6B9080),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCE3DE),
    onPrimaryContainer = Color(0xFF223A31),
    secondary = Color(0xFF52776A),
    tertiary = Color(0xFF5E8C84),
    darkPrimary = Color(0xFFA4C3B2),
    darkPrimaryContainer = Color(0xFF3A574C),
    darkOnPrimary = Color(0xFF15281F),
    darkOnPrimaryContainer = Color(0xFFCCE3DE)
)

// 四款預設主題色。每個選項是一個柔和的基底色，整套配色（含背景 / 表面 / 文字）
// 由該色推導，所以選什麼色 app 主視覺就跟著和諧變化。CUSTOM 由 customColorHex 決定，
// 這裡給綠色 fallback（實際自訂色在 BandaryTheme 以 brandFromHex 套用）。
fun ThemePalette.toBrand(): BrandPalette = when (this) {
    ThemePalette.GRAY -> brandFromHex("#8C8B85")     // 莫蘭迪灰
    ThemePalette.PINK -> brandFromHex("#CE8BA6")     // 粉
    ThemePalette.GREEN -> brandFromHex("#6B9080")    // 莫蘭迪綠（預設）
    ThemePalette.BLUE -> brandFromHex("#6F9BC4")     // 藍
    ThemePalette.CUSTOM -> GREEN_PALETTE             // fallback；實際色見 BandaryTheme
}

// Build a full brand palette from a single user-picked colour.
fun brandFromHex(hex: String): BrandPalette {
    val base = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
        ?: GREEN_PALETTE.primary
    val onBase = if (base.luminance() > 0.5f) Color.Black else Color.White
    return BrandPalette(
        primary = base,
        onPrimary = onBase,
        primaryContainer = androidx.compose.ui.graphics.lerp(base, Color.White, 0.75f),
        onPrimaryContainer = androidx.compose.ui.graphics.lerp(base, Color.Black, 0.6f),
        secondary = androidx.compose.ui.graphics.lerp(base, Color.Gray, 0.4f),
        tertiary = androidx.compose.ui.graphics.lerp(base, Color.Gray, 0.5f),
        darkPrimary = androidx.compose.ui.graphics.lerp(base, Color.White, 0.4f),
        darkPrimaryContainer = androidx.compose.ui.graphics.lerp(base, Color.Black, 0.4f),
        darkOnPrimary = Color.Black,
        darkOnPrimaryContainer = androidx.compose.ui.graphics.lerp(base, Color.White, 0.8f)
    )
}

private fun mix(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)

// 由品牌主色推導整套淺色配色 —— 背景 / 表面 / 文字都帶一點主色，整體和諧。
private fun lightFor(brand: BrandPalette): ColorScheme {
    val p = brand.primary
    fun tint(f: Float) = mix(p, Color.White, f)   // 越接近 1 越白
    val onSurf = mix(p, Color.Black, 0.80f)
    return lightColorScheme(
        primary = p,
        onPrimary = brand.onPrimary,
        primaryContainer = brand.primaryContainer,
        onPrimaryContainer = brand.onPrimaryContainer,
        secondary = brand.secondary,
        onSecondary = Color.White,
        secondaryContainer = tint(0.82f),
        onSecondaryContainer = mix(p, Color.Black, 0.6f),
        tertiary = brand.tertiary,
        onTertiary = Color.White,
        tertiaryContainer = tint(0.78f),
        onTertiaryContainer = mix(p, Color.Black, 0.6f),
        background = tint(0.93f),
        onBackground = onSurf,
        surface = tint(0.965f),
        onSurface = onSurf,
        surfaceVariant = tint(0.85f),
        onSurfaceVariant = mix(p, Color.Black, 0.52f),
        surfaceContainer = tint(0.91f),
        surfaceContainerHigh = tint(0.86f),
        surfaceContainerHighest = tint(0.82f),
        surfaceContainerLow = tint(0.95f),
        outline = mix(p, Color.Gray, 0.5f),
        outlineVariant = tint(0.70f),
        error = BandaryColors.Error,
        errorContainer = BandaryColors.ErrorContainer,
        onErrorContainer = Color(0xFF410002)
    )
}

// 深色：由 darkPrimary 推導，背景往黑、表面帶主色。
private fun darkFor(brand: BrandPalette): ColorScheme {
    val p = brand.darkPrimary
    fun shade(f: Float) = mix(p, Color.Black, f)  // 越接近 1 越黑
    return darkColorScheme(
        primary = p,
        onPrimary = brand.darkOnPrimary,
        primaryContainer = brand.darkPrimaryContainer,
        onPrimaryContainer = brand.darkOnPrimaryContainer,
        secondary = mix(p, Color.White, 0.2f),
        secondaryContainer = shade(0.7f),
        // 黑底：背景近純黑，表面 / 卡片是極深帶一點主色的灰，層次靠明度而非圓角
        background = mix(p, Color.Black, 0.975f),
        onBackground = mix(p, Color.White, 0.80f),
        surface = mix(p, Color.Black, 0.94f),
        onSurface = mix(p, Color.White, 0.82f),
        surfaceVariant = shade(0.80f),
        onSurfaceVariant = mix(p, Color.White, 0.58f),
        surfaceContainer = mix(p, Color.Black, 0.90f),
        surfaceContainerHigh = shade(0.84f),
        surfaceContainerHighest = shade(0.78f),
        outline = mix(p, Color.Gray, 0.4f),
        outlineVariant = shade(0.62f)
    )
}

// ── Typography ────────────────────────────────────────────────
// 雜誌 / 編輯風：大標題用襯線（Serif）帶個性；資訊 / 標籤用單線 sans 並拉開字級差距。
private val Display = androidx.compose.ui.text.font.FontFamily.Serif
private val Sans = androidx.compose.ui.text.font.FontFamily.SansSerif
val BandaryTypography = Typography(
    displayLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.W700, fontSize = 57.sp, lineHeight = 60.sp, letterSpacing = (-1).sp),
    displayMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.W700, fontSize = 45.sp, lineHeight = 50.sp, letterSpacing = (-0.5).sp),
    displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.W700, fontSize = 36.sp, lineHeight = 42.sp),
    headlineLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.W700, fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.W700, fontSize = 27.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.W700, fontSize = 23.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.W700, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.W600, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.W600, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.W400, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.W400, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.W400, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.W600, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.4.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.W500, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.6.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.W500, fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.8.sp)
)

// 方塊化：整體轉向直角 / 極小圓角（元件用 MaterialTheme.shapes 者跟著變）。
val BandaryShapes = androidx.compose.material3.Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
)

object Spacing {
    val xs = 4
    val sm = 8
    val md = 12
    val lg = 16
    val xl = 20
    val xxl = 24
    val huge = 32
}

@Composable
fun BandaryTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    palette: ThemePalette = ThemePalette.GREEN,
    customColorHex: String? = null,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val brand = if (palette == ThemePalette.CUSTOM && !customColorHex.isNullOrBlank())
        brandFromHex(customColorHex) else palette.toBrand()
    MaterialTheme(
        colorScheme = if (useDark) darkFor(brand) else lightFor(brand),
        typography = BandaryTypography,
        shapes = BandaryShapes,
        content = content
    )
}
