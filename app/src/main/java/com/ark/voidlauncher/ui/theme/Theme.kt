package com.ark.voidlauncher.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ark.voidlauncher.ThemeMode
import com.ark.voidlauncher.domain.CustomPalette

/**
 * Warm-neutral dark base. The variant/secondary/outline tokens are deliberately
 * distinct from the flat neutrals so panels, chips and dividers have depth without
 * extra overlays. These two schemes are the only place literal colours appear;
 * everything else in this file is derived from a [ColorScheme] token.
 */
private val DarkColors = darkColorScheme(
    background = Color(0xFF0B0B0D),
    onBackground = Color(0xFFEDE9E3),
    surface = Color(0xFF161518),
    onSurface = Color(0xFFEDE9E3),
    surfaceVariant = Color(0xFF242227),
    onSurfaceVariant = Color(0xFFC2BBB2),
    surfaceTint = Color(0xFFE6DFD3),
    primary = Color(0xFFE6DFD3),
    onPrimary = Color(0xFF201D1A),
    primaryContainer = Color(0xFF37332E),
    onPrimaryContainer = Color(0xFFF0EAE0),
    secondary = Color(0xFFC2AE8E),
    onSecondary = Color(0xFF241E16),
    secondaryContainer = Color(0xFF3A3227),
    onSecondaryContainer = Color(0xFFF0E4CE),
    tertiary = Color(0xFFA9B7A6),
    onTertiary = Color(0xFF162017),
    tertiaryContainer = Color(0xFF2A352A),
    onTertiaryContainer = Color(0xFFDDE8DA),
    outline = Color(0xFF6E6862),
    outlineVariant = Color(0xFF3A3733),
    error = Color(0xFFF2B8AB),
    onError = Color(0xFF4A1B12),
    errorContainer = Color(0xFF57271D),
    onErrorContainer = Color(0xFFFFDAD3),
    inverseSurface = Color(0xFFEDE9E3),
    inverseOnSurface = Color(0xFF1A191C),
    inversePrimary = Color(0xFF6F685E),
    scrim = Color(0xFF000000),
)

/**
 * Soft, warm paper light scheme. No pure white and no pure black: the near-white
 * surface lifts off a slightly warmer background, and all `on*` inks keep a strong
 * contrast ratio while staying easy on the eyes in a dim room.
 */
private val LightColors = lightColorScheme(
    background = Color(0xFFF6F3ED),
    onBackground = Color(0xFF2A2724),
    surface = Color(0xFFFBF8F2),
    onSurface = Color(0xFF2A2724),
    surfaceVariant = Color(0xFFECE5DA),
    onSurfaceVariant = Color(0xFF5C554C),
    surfaceTint = Color(0xFF5E4B38),
    primary = Color(0xFF5E4B38),
    onPrimary = Color(0xFFFFF9F0),
    primaryContainer = Color(0xFFE8DCC9),
    onPrimaryContainer = Color(0xFF3D2E1F),
    secondary = Color(0xFF7A6A52),
    onSecondary = Color(0xFFFFF8EE),
    secondaryContainer = Color(0xFFEDE4D3),
    onSecondaryContainer = Color(0xFF4A3D2B),
    tertiary = Color(0xFF4F6657),
    onTertiary = Color(0xFFF3F8F2),
    tertiaryContainer = Color(0xFFDCE9DD),
    onTertiaryContainer = Color(0xFF2E4234),
    outline = Color(0xFF8B8175),
    outlineVariant = Color(0xFFD8D0C4),
    error = Color(0xFF9C3B2E),
    onError = Color(0xFFFFF7F3),
    errorContainer = Color(0xFFF9DED8),
    onErrorContainer = Color(0xFF41170F),
    inverseSurface = Color(0xFF2A2724),
    inverseOnSurface = Color(0xFFF6F3ED),
    inversePrimary = Color(0xFFDCCBB3),
    scrim = Color(0xFF1A1714),
)

/**
 * Shell-wide design tokens consumed by [GlassPanel] and later visual-polish work.
 * Colours are scheme-derived translucencies; geometry values keep the glass and
 * skeuomorphic treatment consistent across every surface.
 */
data class VoidStyle(
    val glassTint: Color,
    val glassBorder: Color,
    val glassBlurRadius: Dp,
    val panelShape: Shape,
    val raisedElevation: Dp,
    val skeuoHighlight: Color,
    val skeuoShadow: Color,
)

/** Light and dark token sets; only alpha is adjusted — no new literal colours. */
private fun voidStyleFor(scheme: ColorScheme, isDark: Boolean): VoidStyle = when {
    isDark -> VoidStyle(
        glassTint = scheme.surface.copy(alpha = 0.82f),
        glassBorder = scheme.outlineVariant.copy(alpha = 0.70f),
        glassBlurRadius = 18.dp,
        panelShape = RoundedCornerShape(20.dp),
        raisedElevation = 6.dp,
        skeuoHighlight = scheme.onSurface.copy(alpha = 0.10f),
        skeuoShadow = scheme.scrim.copy(alpha = 0.40f),
    )
    else -> VoidStyle(
        glassTint = scheme.surface.copy(alpha = 0.78f),
        glassBorder = scheme.outlineVariant.copy(alpha = 0.65f),
        glassBlurRadius = 20.dp,
        panelShape = RoundedCornerShape(20.dp),
        raisedElevation = 5.dp,
        skeuoHighlight = scheme.onPrimary.copy(alpha = 0.55f),
        skeuoShadow = scheme.onSurface.copy(alpha = 0.09f),
    )
}

/** Default keeps previews and any out-of-tree consumer safe before [VoidTheme] runs. */
val LocalVoidStyle: ProvidableCompositionLocal<VoidStyle> = staticCompositionLocalOf {
    voidStyleFor(DarkColors, isDark = true)
}

/**
 * Accepts `#RRGGBB`, `RRGGBB`, `#AARRGGBB` or `AARRGGBB`. Anything else yields
 * null so a malformed preference can never crash theme construction.
 */
private fun parseArgb(value: String): Color? = try {
    val hex = value.trim().removePrefix("#")
    when (hex.length) {
        6 -> Color(0xFF000000L or hex.toLong(16))
        8 -> Color(hex.toLong(16))
        else -> null
    }
} catch (e: NumberFormatException) {
    null
}

/**
 * Override the eight palette slots on top of the chosen base. It is all-or-nothing:
 * a single invalid field leaves the complete base scheme in effect.
 */
private fun CustomPalette.toSchemeOrNull(base: ColorScheme): ColorScheme? {
    val bg = parseArgb(background) ?: return null
    val sf = parseArgb(surface) ?: return null
    val sv = parseArgb(surfaceVariant) ?: return null
    val obg = parseArgb(onBackground) ?: return null
    val osf = parseArgb(onSurface) ?: return null
    val pri = parseArgb(primary) ?: return null
    val sec = parseArgb(secondary) ?: return null
    val out = parseArgb(outline) ?: return null
    return base.copy(
        background = bg,
        surface = sf,
        surfaceVariant = sv,
        onBackground = obg,
        onSurface = osf,
        primary = pri,
        secondary = sec,
        outline = out,
    )
}

/** Text-first typography. Sizes come from Material defaults; only the face changes. */
private val BaseTypography = Typography()
val VoidTypography = Typography(
    displayLarge = BaseTypography.displayLarge.copy(fontFamily = FontFamily.Monospace),
    displayMedium = BaseTypography.displayMedium.copy(fontFamily = FontFamily.Monospace),
    headlineSmall = BaseTypography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
    bodyLarge = BaseTypography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
    bodyMedium = BaseTypography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
    labelLarge = BaseTypography.labelLarge.copy(fontFamily = FontFamily.Monospace),
    labelMedium = BaseTypography.labelMedium.copy(fontFamily = FontFamily.Monospace),
    labelSmall = BaseTypography.labelSmall.copy(fontFamily = FontFamily.Monospace),
)

@Composable
fun VoidTheme(
    themeMode: ThemeMode,
    customPalette: CustomPalette? = null,
    content: @Composable () -> Unit,
) {
    // CUSTOM follows the system's light/dark preference for the *base* tokens, so
    // the eight overridden slots are surrounded by coherent secondary tokens.
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.CUSTOM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val base = if (useDark) DarkColors else LightColors
    val colorScheme = if (themeMode == ThemeMode.CUSTOM) {
        customPalette?.toSchemeOrNull(base) ?: base
    } else {
        base
    }

    CompositionLocalProvider(LocalVoidStyle provides voidStyleFor(colorScheme, useDark)) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = VoidTypography,
            content = content,
        )
    }
}
