package com.ark.voidlauncher.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

/**
 * Keeps the system status and navigation bars in step with the launcher theme.
 *
 * Without this the bars keep the platform default (dark on most devices), which
 * clashes badly with the light and custom light palettes. The bars are painted
 * with the active background token and their icon contrast follows the same
 * light/dark decision [VoidTheme] uses, so SYSTEM and CUSTOM stay consistent.
 */
@Composable
fun ThemedSystemBars(darkTheme: Boolean) {
    val context = LocalContext.current
    val background = MaterialTheme.colorScheme.background

    DisposableEffect(context, background, darkTheme) {
        val activity = context.findActivity() ?: return@DisposableEffect onDispose { }
        val window = activity.window
        val color = background.toArgb()
        window.statusBarColor = color
        window.navigationBarColor = color
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = !darkTheme
        controller.isAppearanceLightNavigationBars = !darkTheme
        onDispose { }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
