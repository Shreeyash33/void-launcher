package com.ark.voidlauncher.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.ark.voidlauncher.ThemeMode

/**
 * User-defined palette. This is the extension point for the future custom theme
 * editor: no editor is built yet, but all UI reads colors through MaterialTheme
 * tokens, so a palette can be dropped in without touching any screen.
 */
data class CustomPalette(
    val background: Color,
    val surface: Color,
    val onBackground: Color,
    val onSurface: Color,
    val primary: Color,
) {
    internal fun toColorScheme(preferDark: Boolean) =
        (if (preferDark) DarkColors else LightColors).copy(
            background = background,
            surface = surface,
            onBackground = onBackground,
            onSurface = onSurface,
            primary = primary,
        )
}

private val DarkColors = darkColorScheme(
    background = Color(0xFF080808),
    surface = Color(0xFF121212),
    onBackground = Color(0xFFEDEDED),
    onSurface = Color(0xFFEDEDED),
    primary = Color(0xFFEDEDED),
)

private val LightColors = lightColorScheme(
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF5F5F5),
    onBackground = Color(0xFF111111),
    onSurface = Color(0xFF111111),
    primary = Color(0xFF111111),
)

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
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    MaterialTheme(
        colorScheme = customPalette?.toColorScheme(useDark) ?: if (useDark) DarkColors else LightColors,
        typography = VoidTypography,
        content = content,
    )
}
