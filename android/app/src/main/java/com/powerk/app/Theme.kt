package com.powerk.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography

val PlexSans = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_bold, FontWeight.Bold),
)

val PlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.Bold),
)

/** Tightened, semi-bold first type scale: reads like a modern control app instead of stock Material. */
private val PowerkTypography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.5).sp),
        displayMedium = t.displayMedium.copy(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.2).sp),
        displaySmall = t.displaySmall.copy(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.9).sp),
        headlineLarge = t.headlineLarge.copy(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.7).sp),
        headlineMedium = t.headlineMedium.copy(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineSmall = t.headlineSmall.copy(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
        titleLarge = t.titleLarge.copy(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
        titleMedium = t.titleMedium.copy(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleSmall = t.titleSmall.copy(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold),
        bodyLarge = t.bodyLarge.copy(fontFamily = PlexSans),
        bodyMedium = t.bodyMedium.copy(fontFamily = PlexSans),
        bodySmall = t.bodySmall.copy(fontFamily = PlexSans),
        labelLarge = t.labelLarge.copy(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
        labelMedium = t.labelMedium.copy(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
        labelSmall = t.labelSmall.copy(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp),
    )
}

private val PowerkShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private fun parseColor(hex: String): Color {
    val value = hex.removePrefix("#").toLongOrNull(16) ?: 0x1AA34A
    return Color(0xFF000000 or value)
}

private fun mix(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
)

fun powerkScheme(dark: Boolean, accentHex: String = AppTheme.accentColor): ColorScheme {
    val accent = parseColor(accentHex)
    return if (dark) darkColorScheme(
        primary = mix(accent, Color.White, 0.14f),
        onPrimary = Color(0xFF04210F),
        primaryContainer = mix(Color(0xFF0F1A2E), accent, 0.34f),
        onPrimaryContainer = mix(accent, Color.White, 0.68f),
        secondary = Color(0xFF95A5BC),
        secondaryContainer = Color(0xFF1C2942),
        onSecondaryContainer = Color(0xFFE3E9F3),
        tertiary = Color(0xFFFBBF24),
        tertiaryContainer = Color(0xFF3A2E0C),
        onTertiaryContainer = Color(0xFFFDE68A),
        error = Color(0xFFF87171),
        onError = Color(0xFF450A0A),
        errorContainer = Color(0xFF4C1D1D),
        onErrorContainer = Color(0xFFFECACA),
        background = Color(0xFF080D17),
        onBackground = Color(0xFFE7ECF5),
        surface = Color(0xFF0E1626),
        onSurface = Color(0xFFE7ECF5),
        surfaceVariant = Color(0xFF16203A),
        onSurfaceVariant = Color(0xFF8E9CB3),
        outline = Color(0xFF5A6A85),
        outlineVariant = Color(0xFF212D45),
        surfaceContainerLowest = Color(0xFF070B13),
        surfaceContainerLow = Color(0xFF0C1322),
        surfaceContainer = Color(0xFF121C30),
        surfaceContainerHigh = Color(0xFF182338),
        surfaceContainerHighest = Color(0xFF1F2B44),
    ) else lightColorScheme(
        primary = accent,
        onPrimary = Color.White,
        primaryContainer = mix(Color.White, accent, 0.15f),
        onPrimaryContainer = mix(accent, Color.Black, 0.48f),
        secondary = Color(0xFF5C6B80),
        secondaryContainer = Color(0xFFE2E9F2),
        onSecondaryContainer = Color(0xFF0E1726),
        tertiary = Color(0xFFD97706),
        tertiaryContainer = Color(0xFFFEF3C7),
        onTertiaryContainer = Color(0xFF78350F),
        error = Color(0xFFDC2626),
        onError = Color.White,
        errorContainer = Color(0xFFFEE2E2),
        onErrorContainer = Color(0xFF7F1D1D),
        background = Color(0xFFF2F6FB),
        onBackground = Color(0xFF0E1726),
        surface = Color.White,
        onSurface = Color(0xFF0E1726),
        surfaceVariant = Color(0xFFEDF2F8),
        onSurfaceVariant = Color(0xFF5C6B80),
        outline = Color(0xFF8C9AAD),
        outlineVariant = Color(0xFFDFE7F0),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF7FAFD),
        surfaceContainer = Color(0xFFEDF2F8),
        surfaceContainerHigh = Color(0xFFE5ECF4),
        surfaceContainerHighest = Color(0xFFDCE5F0),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PowerkTheme(
    dark: Boolean = when (AppTheme.mode) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        else -> isSystemInDarkTheme()
    },
    accentHex: String = AppTheme.accentColor,
    content: @Composable () -> Unit,
) {
    MaterialExpressiveTheme(
        colorScheme = powerkScheme(dark, accentHex),
        motionScheme = MotionScheme.expressive(),
        shapes = PowerkShapes,
        typography = PowerkTypography,
        content = content,
    )
}
