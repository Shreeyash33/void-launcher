package com.ark.voidlauncher.ui.background

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.ark.voidlauncher.domain.background.BackgroundKind
import com.ark.voidlauncher.domain.background.PageBackground
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Extent used only when a parent cannot supply bounded constraints (e.g. a loose preview). */
private const val FallbackExtent = 1_000f

/**
 * Draws a resolved [PageBackground] as the first, stationary layer of a launcher page.
 *
 * Every colour is either a `MaterialTheme.colorScheme` token or a safely parsed
 * user-supplied hex value; malformed hex always falls back to the theme background
 * instead of throwing. The image scrims are alpha-adjusted scheme colours so HUD
 * text stays legible without introducing any literal colours.
 */
@Composable
fun PageBackgroundRenderer(
    background: PageBackground?,
    image: ImageBitmap?, // loaded by lead from imageToken; null while loading
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val fill = modifier.fillMaxSize()
    val bg = background

    if (bg == null) {
        Box(fill.background(scheme.background))
        return
    }

    when (bg.kind) {
        BackgroundKind.COLOR -> {
            val color = bg.colorHex?.let(::parseBackgroundHex) ?: scheme.background
            Box(fill.background(color))
        }

        BackgroundKind.GRADIENT -> {
            val stops = bg.gradientStops
            val colors = stops.mapNotNull { parseBackgroundHex(it.colorHex) }
            if (colors.size < 2 || colors.size != stops.size) {
                Box(fill.background(scheme.background))
            } else {
                BoxWithConstraints(fill) {
                    val width = if (constraints.hasBoundedWidth) {
                        constraints.maxWidth.toFloat()
                    } else {
                        FallbackExtent
                    }
                    val height = if (constraints.hasBoundedHeight) {
                        constraints.maxHeight.toFloat()
                    } else {
                        FallbackExtent
                    }

                    // 0 degrees = left-to-right; 90 degrees = top-to-bottom.
                    val radians = Math.toRadians(bg.gradientAngle.toDouble())
                    val dx = cos(radians).toFloat()
                    val dy = sin(radians).toFloat()
                    val span = (abs(dx) * width + abs(dy) * height).coerceAtLeast(1f)
                    val cx = width / 2f
                    val cy = height / 2f

                    val brush = Brush.linearGradient(
                        *colors.toTypedArray(),
                        start = Offset(cx - dx * span / 2f, cy - dy * span / 2f),
                        end = Offset(cx + dx * span / 2f, cy + dy * span / 2f),
                    )
                    Box(Modifier.fillMaxSize().background(brush))
                }
            }
        }

        BackgroundKind.IMAGE -> {
            if (image == null) {
                // Gentle theme-coloured placeholder rather than a black void.
                Box(fill.background(scheme.surface))
            } else {
                Box(fill) {
                    Image(
                        bitmap = image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )

                    val scrimHeight = 96.dp
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(scrimHeight)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    scheme.background.copy(alpha = 0.55f),
                                    scheme.background.copy(alpha = 0f),
                                ),
                            ),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(scrimHeight)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    scheme.surface.copy(alpha = 0f),
                                    scheme.surface.copy(alpha = 0.55f),
                                ),
                            ),
                    )
                }
            }
        }
    }
}

/**
 * Parses #RRGGBB / #AARRGGBB (with optional 0x prefix) into a Compose [Color].
 * Returns null for anything else; it never throws on user input.
 */
private fun parseBackgroundHex(input: String?): Color? {
    if (input == null) return null
    val hex = input.trim()
        .removePrefix("#")
        .removePrefix("0x")
        .removePrefix("0X")
    if (hex.length != 6 && hex.length != 8) return null
    if (hex.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null

    fun channel(from: Int) = hex.substring(from, from + 2).toInt(16) / 255f

    return if (hex.length == 8) {
        Color(alpha = channel(0), red = channel(2), green = channel(4), blue = channel(6))
    } else {
        Color(alpha = 1f, red = channel(0), green = channel(2), blue = channel(4))
    }
}
