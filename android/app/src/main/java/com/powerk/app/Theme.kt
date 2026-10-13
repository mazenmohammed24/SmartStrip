package com.powerk.app

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import android.os.Build
import androidx.compose.ui.unit.dp

private val LightScheme = lightColorScheme(
    primary = Color(0xFF1B6B45),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA6F2C4),
    onPrimaryContainer = Color(0xFF00210F),
    secondary = Color(0xFF4E6355),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD0E8D7),
    onSecondaryContainer = Color(0xFF0B1F14),
    tertiary = Color(0xFF3C6373),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBFE9FC),
    onTertiaryContainer = Color(0xFF001F29),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF5FBF5),
    onBackground = Color(0xFF171D18),
    surface = Color(0xFFF5FBF5),
    onSurface = Color(0xFF171D18),
    surfaceVariant = Color(0xFFDCE5DA),
    onSurfaceVariant = Color(0xFF404943),
    outline = Color(0xFF707972),
    outlineVariant = Color(0xFFC0C9C0),
    inverseSurface = Color(0xFF2C322D),
    inverseOnSurface = Color(0xFFEDF3EC),
    inversePrimary = Color(0xFF8AD5A7),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF6EF),
    surfaceContainer = Color(0xFFE9F0E9),
    surfaceContainerHigh = Color(0xFFE3EAE3),
    surfaceContainerHighest = Color(0xFFDDE4DD),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8AD5A7),
    onPrimary = Color(0xFF00391E),
    primaryContainer = Color(0xFF00522E),
    onPrimaryContainer = Color(0xFFA6F2C4),
    secondary = Color(0xFFB4CCB9),
    onSecondary = Color(0xFF203528),
    secondaryContainer = Color(0xFF364B3E),
    onSecondaryContainer = Color(0xFFD0E8D7),
    tertiary = Color(0xFFA4CCDF),
    onTertiary = Color(0xFF063542),
    tertiaryContainer = Color(0xFF234C5B),
    onTertiaryContainer = Color(0xFFBFE9FC),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1511),
    onBackground = Color(0xFFDEE4DE),
    surface = Color(0xFF0F1511),
    onSurface = Color(0xFFDEE4DE),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFC0C9C0),
    outline = Color(0xFF8A938C),
    outlineVariant = Color(0xFF404943),
    inverseSurface = Color(0xFFDEE4DE),
    inverseOnSurface = Color(0xFF2C322D),
    inversePrimary = Color(0xFF1B6B45),
    surfaceContainerLowest = Color(0xFF0A0F0B),
    surfaceContainerLow = Color(0xFF171D19),
    surfaceContainer = Color(0xFF1B211C),
    surfaceContainerHigh = Color(0xFF252B26),
    surfaceContainerHighest = Color(0xFF303631),
)

private val PowerkShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

private fun parseColor(hex: String): Color {
    val value = hex.removePrefix("#").toLongOrNull(16) ?: 0x1B6B45
    return Color(0xFF000000 or value)
}

private fun Color.container(alpha: Float, dark: Boolean): Color = if (dark) {
    copy(red = red * alpha, green = green * alpha, blue = blue * alpha)
} else {
    copy(red = red + (1f - red) * alpha, green = green + (1f - green) * alpha, blue = blue + (1f - blue) * alpha)
}

fun powerkScheme(dark: Boolean, systemScheme: ColorScheme? = null, accentHex: String = AppTheme.accentColor): ColorScheme {
    val base = systemScheme ?: if (dark) DarkScheme else LightScheme
    val accent = parseColor(accentHex)
    return base.copy(
        primary = accent,
        primaryContainer = accent.container(if (dark) 0.34f else 0.74f, dark),
        inversePrimary = accent,
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
    val context = LocalContext.current
    val systemScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else null
    MaterialExpressiveTheme(
        colorScheme = powerkScheme(dark, systemScheme),
        motionScheme = MotionScheme.expressive(),
        shapes = PowerkShapes,
        content = content,
    )
}
