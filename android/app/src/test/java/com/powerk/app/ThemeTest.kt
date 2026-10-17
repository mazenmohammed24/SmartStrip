package com.powerk.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Locks the design-system policy: dynamic colour gating, accent handling, semantic contrast, density. */
class ThemeTest {

    @Test
    fun dynamicColorRequiresApi31() {
        assertFalse(useDynamicScheme(requested = true, sdkInt = 26))
        assertFalse(useDynamicScheme(requested = true, sdkInt = 30))
        assertTrue(useDynamicScheme(requested = true, sdkInt = 31))
        assertTrue(useDynamicScheme(requested = true, sdkInt = 36))
    }

    @Test
    fun dynamicColorIgnoredWhenNotRequested() {
        assertFalse(useDynamicScheme(requested = false, sdkInt = 34))
    }

    @Test
    fun lightSchemeFollowsAccentExactly() {
        assertEquals(Color(0xFF7C3AED), powerkScheme(dark = false, accentHex = "#7C3AED").primary)
    }

    @Test
    fun darkSchemeLiftsAccentForContrast() {
        val accent = Color(0xFF7C3AED)
        val dark = powerkScheme(dark = true, accentHex = "#7C3AED").primary
        assertTrue(dark != accent)
        assertTrue(dark.luminance() > accent.luminance())
    }

    @Test
    fun invalidAccentFallsBackToBrandDefault() {
        assertEquals(Color(0xFF1AA34A), powerkScheme(dark = false, accentHex = "not-a-color").primary)
    }

    @Test
    fun semanticColorsStayDistinctFromEachOtherAndFromErrors() {
        assertTrue(LightSemanticColors.success != LightSemanticColors.warning)
        assertTrue(DarkSemanticColors.success != DarkSemanticColors.warning)
        assertTrue(LightSemanticColors.success != powerkScheme(dark = false, accentHex = "#1AA34A").error)
        assertTrue(DarkSemanticColors.success != powerkScheme(dark = true, accentHex = "#1AA34A").error)
    }

    @Test
    fun semanticContainersKeepReadableContrast() {
        // Light mode: pale containers with dark text; dark mode: the inverse.
        assertTrue(LightSemanticColors.successContainer.luminance() > LightSemanticColors.onSuccessContainer.luminance())
        assertTrue(LightSemanticColors.warningContainer.luminance() > LightSemanticColors.onWarningContainer.luminance())
        assertTrue(DarkSemanticColors.successContainer.luminance() < DarkSemanticColors.onSuccessContainer.luminance())
        assertTrue(DarkSemanticColors.warningContainer.luminance() < DarkSemanticColors.onWarningContainer.luminance())
    }

    @Test
    fun densityTokensMapToSpacing() {
        assertEquals(CompactSpacing, spacingFor(AppTheme.COMPACT))
        assertEquals(ComfortableSpacing, spacingFor(AppTheme.COMFORTABLE))
        assertEquals(ComfortableSpacing, spacingFor("unexpected"))
        assertTrue(CompactSpacing.listGap < ComfortableSpacing.listGap)
        assertTrue(CompactSpacing.sectionGap < ComfortableSpacing.sectionGap)
    }
}
