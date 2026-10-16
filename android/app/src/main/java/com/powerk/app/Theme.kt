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

private val PowerkTypography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(fontFamily = PlexSans),
        displayMedium = t.displayMedium.copy(fontFamily = PlexSans),
        displaySmall = t.displaySmall.copy(fontFamily = PlexSans),
        headlineLarge = t.headlineLarge.copy(fontFamily = PlexSans),
        headlineMedium = t.headlineMedium.copy(fontFamily = PlexSans),
        headlineSmall = t.headlineSmall.copy(fontFamily = PlexSans),
        titleLarge = t.titleLarge.copy(fontFamily = PlexSans),
        titleMedium = t.titleMedium.copy(fontFamily = PlexSans),
        titleSmall = t.titleSmall.copy(fontFamily = PlexSans),
        bodyLarge = t.bodyLarge.copy(fontFamily = PlexSans),
        bodyMedium = t.bodyMedium.copy(fontFamily = PlexSans),
        bodySmall = t.bodySmall.copy(fontFamily = PlexSans),
        labelLarge = t.labelLarge.copy(fontFamily = PlexSans),
        labelMedium = t.labelMedium.copy(fontFamily = PlexSans),
        labelSmall = t.labelSmall.copy(fontFamily = PlexSans),
    )
}

private val PowerkShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
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
        primary = mix(accent, Color.White, 0.12f),
        onPrimary = Color(0xFF04210F),
        primaryContainer = mix(Color(0xFF121B2E), accent, 0.28f),
        onPrimaryContainer = mix(accent, Color.White, 0.65f),
        secondary = Color(0xFF94A3B8),
        secondaryContainer = Color(0xFF1E2A42),
        onSecondaryContainer = Color(0xFFE5EAF3),
        tertiary = Color(0xFFFBBF24),
        tertiaryContainer = Color(0xFF3B2F0B),
        onTertiaryContainer = Color(0xFFFDE68A),
        error = Color(0xFFF87171),
        onError = Color(0xFF450A0A),
        errorContainer = Color(0xFF4C1D1D),
        onErrorContainer = Color(0xFFFECACA),
        background = Color(0xFF0B1220),
        onBackground = Color(0xFFE5EAF3),
        surface = Color(0xFF121B2E),
        onSurface = Color(0xFFE5EAF3),
        surfaceVariant = Color(0xFF1A2540),
        onSurfaceVariant = Color(0xFF94A3B8),
        outline = Color(0xFF64748B),
        outlineVariant = Color(0xFF243047),
        surfaceContainerLowest = Color(0xFF0B1220),
        surfaceContainerLow = Color(0xFF0F182A),
        surfaceContainer = Color(0xFF16213A),
        surfaceContainerHigh = Color(0xFF1C2844),
        surfaceContainerHighest = Color(0xFF243256),
    ) else lightColorScheme(
        primary = accent,
        onPrimary = Color.White,
        primaryContainer = mix(Color.White, accent, 0.14f),
        onPrimaryContainer = mix(accent, Color.Black, 0.45f),
        secondary = Color(0xFF64748B),
        secondaryContainer = Color(0xFFE2E8F0),
        onSecondaryContainer = Color(0xFF0F172A),
        tertiary = Color(0xFFD97706),
        tertiaryContainer = Color(0xFFFEF3C7),
        onTertiaryContainer = Color(0xFF78350F),
        error = Color(0xFFDC2626),
        onError = Color.White,
        errorContainer = Color(0xFFFEE2E2),
        onErrorContainer = Color(0xFF7F1D1D),
        background = Color(0xFFF3F7FA),
        onBackground = Color(0xFF0F172A),
        surface = Color.White,
        onSurface = Color(0xFF0F172A),
        surfaceVariant = Color(0xFFF1F5F9),
        onSurfaceVariant = Color(0xFF64748B),
        outline = Color(0xFF94A3B8),
        outlineVariant = Color(0xFFE1E8EF),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF8FAFC),
        surfaceContainer = Color(0xFFF1F5F9),
        surfaceContainerHigh = Color(0xFFE8EDF4),
        surfaceContainerHighest = Color(0xFFE2E8F0),
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
    content: @Composable () -> Unit,
) {
    MaterialExpressiveTheme(
        colorScheme = powerkScheme(dark),
        motionScheme = MotionScheme.expressive(),
        shapes = PowerkShapes,
        typography = PowerkTypography,
        content = content,
    )
}
