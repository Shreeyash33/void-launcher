package com.ark.voidlauncher.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
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
 * A light press settles the panel toward its surface and dims the sheen, giving a
 * small tactile response without a ripple or a click handler of its own. Children
 * that handle input keep priority, so buttons placed inside still work normally.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val style = LocalVoidStyle.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val elevation = if (isPressed) 0.dp else style.raisedElevation
    val pressSheen = if (isPressed) 0.45f else 1f
    val tint = style.glassTint.copy(alpha = style.glassTint.alpha * if (isPressed) 0.92f else 1f)

    Box(
        modifier = modifier
            // Track press state without consuming the tap: children remain interactive.
            .pointerInput(interactionSource) {
                detectTapGestures(onPress = { offset ->
                    val press = PressInteraction.Press(offset)
                    interactionSource.emit(press)
                    val released = tryAwaitRelease()
                    interactionSource.emit(
                        if (released) PressInteraction.Release(press) else PressInteraction.Cancel(press),
                    )
                })
            }
            .shadow(elevation = elevation, shape = style.panelShape, clip = false)
            .background(color = tint, shape = style.panelShape)
            .border(width = 1.dp, color = style.glassBorder, shape = style.panelShape)
            .clip(style.panelShape)
            .drawBehind {
                // Skeuo depth: highlight along the top edge, shadow along the bottom.
                // Transparent gradient stops are the same tokens at alpha = 0, so no
                // extra colours are introduced here.
                val sheen = style.glassBlurRadius.toPx().coerceAtMost(size.height / 2f)
                if (sheen <= 0f) return@drawBehind

                val highlight = style.skeuoHighlight.copy(
                    alpha = style.skeuoHighlight.alpha * pressSheen,
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(highlight, highlight.copy(alpha = 0f)),
                        startY = 0f,
                        endY = sheen,
                    ),
                    size = Size(size.width, sheen),
                )

                val shadowTop = size.height - sheen
                val shadow = style.skeuoShadow.copy(alpha = style.skeuoShadow.alpha * pressSheen)
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(shadow.copy(alpha = 0f), shadow),
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
