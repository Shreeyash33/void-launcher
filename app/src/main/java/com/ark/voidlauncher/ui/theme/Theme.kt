package com.ark.voidlauncher.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ark.voidlauncher.ThemeMode

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

@Composable
fun VoidTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    MaterialTheme(
        colorScheme = if (useDark) DarkColors else LightColors,
        content = content,
    )
}
