package com.ark.voidlauncher.ui.overview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ark.voidlauncher.domain.SwipeDirection
import kotlin.math.roundToInt

/**
 * One page as seen from above: coordinate, optional HOME/HERE tags, item count and
 * the four swipe arrows (bright when an outgoing edge exists, dim when it is a
 * wall). [center] is the cell centre in map-space pixels; the enclosing map layer
 * applies the shared pan/zoom transform, so this cell only positions itself.
 */
@Composable
internal fun PageCell(
    center: Offset,
    cellSizePx: Float,
    coordLabel: String,
    isHome: Boolean,
    isCurrent: Boolean,
    showCoordDetail: Boolean = true,
    itemCount: Int,
    outgoing: Set<SwipeDirection>,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val containerColor = if (isCurrent) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isCurrent) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val markerColor = if (isCurrent) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.primary
    }
    val borderColor = if (isCurrent) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (center.x - cellSizePx / 2f).roundToInt(),
                    (center.y - cellSizePx / 2f).roundToInt(),
                )
            }
            .size(OverviewCellSize)
            .clip(shape)
            .background(containerColor)
            .border(
                width = if (isCurrent) 2.dp else 1.dp,
                color = borderColor,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (showCoordDetail) {
                Text(
                    text = coordLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
            Row {
                if (isHome) {
                    Text(
                        text = "HOME",
                        style = MaterialTheme.typography.labelSmall,
                        color = markerColor,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
                if (isCurrent) {
                    Text(
                        text = "HERE",
                        style = MaterialTheme.typography.labelSmall,
                        color = markerColor,
                    )
                }
            }
            Text(
                text = "$itemCount items",
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )

            Spacer(Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SwipeDirection.entries.forEach { direction ->
                    val available = direction in outgoing
                    OverviewArrow(
                        direction = direction,
                        color = if (available) {
                            markerColor
                        } else {
                            contentColor.copy(alpha = 0.25f)
                        },
                    )
                }
            }
        }
    }
}

/** Height of a next-page preview. Portrait 16:9 height:width, like a phone. */
internal val PreviewHeight: Dp = OverviewCellSize

/** Width of a next-page preview: 9 units wide for every 16 units tall. */
internal val PreviewWidth: Dp = OverviewCellSize * 9f / 16f

/**
 * A tappable ghost page at a coordinate where a new page could be created.
 * The dashed outline and `+` make it read as an empty slot rather than a page.
 */
@Composable
internal fun PlacementPreviewCell(
    center: Offset,
    previewWidthPx: Float,
    previewHeightPx: Float,
    onClick: () -> Unit,
) {
    val color = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (center.x - previewWidthPx / 2f).roundToInt(),
                    (center.y - previewHeightPx / 2f).roundToInt(),
                )
            }
            .size(PreviewWidth, PreviewHeight)
            .background(color.copy(alpha = 0.06f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawRoundRect(
                color = color.copy(alpha = 0.75f),
                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f),
                ),
            )
        }
        Text(
            text = "+",
            style = MaterialTheme.typography.headlineMedium,
            color = color,
        )
    }
}

/**
 * Small direction cue drawn as geometry. Unicode arrows render as unrelated
 * characters on devices whose monospace face lacks them, so the map uses the
 * same font-independent approach as the home HUD.
 */
@Composable
private fun OverviewArrow(direction: SwipeDirection, color: Color) {
    val rotation = when (direction) {
        SwipeDirection.UP -> 0f
        SwipeDirection.RIGHT -> 90f
        SwipeDirection.DOWN -> 180f
        SwipeDirection.LEFT -> 270f
    }
    Canvas(
        modifier = Modifier.size(14.dp),
        contentDescription = "SWIPE ${direction.name}",
    ) {
        val stroke = 1.5.dp.toPx()
        val centerX = size.width / 2f
        val tipY = size.height * 0.10f
        val tailY = size.height * 0.90f
        val headWidth = size.width * 0.32f
        val headDepth = size.height * 0.30f
        rotate(rotation) {
            drawLine(color, Offset(centerX, tailY), Offset(centerX, tipY), stroke, StrokeCap.Round)
            drawLine(
                color,
                Offset(centerX - headWidth, tipY + headDepth),
                Offset(centerX, tipY),
                stroke,
                StrokeCap.Round,
            )
            drawLine(
                color,
                Offset(centerX + headWidth, tipY + headDepth),
                Offset(centerX, tipY),
                stroke,
                StrokeCap.Round,
            )
        }
    }
}

/** One of the four bottom navigation entry points. */
@Composable
internal fun RowScope.NavButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(56.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            // `background()` does not provide a content colour, so inherit from the
            // theme explicitly or the label renders black on a dark surface.
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}
