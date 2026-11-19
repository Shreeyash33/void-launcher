package com.ark.voidlauncher.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

/**
 * Reusable frosted shell surface.
 *
 * All visual values come from [LocalVoidStyle], which [VoidTheme] derives from the
 * active `ColorScheme`. There is deliberately no `RenderEffect` dependency: the
 * frosted look is produced by a translucent tint plus a top highlight / bottom
 * shadow, so the panel behaves identically (and never crashes) on API 24 through
 * 34, including devices that have no blur support, and needs no opt-in APIs.
 *
 * The panel is purely visual. It installs no pointer input of its own, so any
 * clickable child receives touches normally — a press-tracking gesture detector
 * here would consume the down event and silently break buttons inside the panel.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val style = LocalVoidStyle.current

    Box(
        modifier = modifier
            .shadow(elevation = style.raisedElevation, shape = style.panelShape, clip = false)
            .background(color = style.glassTint, shape = style.panelShape)
            .border(width = 1.dp, color = style.glassBorder, shape = style.panelShape)
            .clip(style.panelShape)
            .drawBehind {
                // Skeuo depth: highlight along the top edge, shadow along the bottom.
                // Transparent gradient stops reuse the same tokens at alpha = 0.
                val sheen = style.glassBlurRadius.toPx().coerceAtMost(size.height / 2f)
                if (sheen <= 0f) return@drawBehind

                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(style.skeuoHighlight, style.skeuoHighlight.copy(alpha = 0f)),
                        startY = 0f,
                        endY = sheen,
                    ),
                    size = Size(size.width, sheen),
                )

                val shadowTop = size.height - sheen
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(style.skeuoShadow.copy(alpha = 0f), style.skeuoShadow),
                        startY = shadowTop,
                        endY = size.height,
                    ),
                    topLeft = Offset(0f, shadowTop),
                    size = Size(size.width, sheen),
                )
            },
    ) {
        content()
    }
}
