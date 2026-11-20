package com.ark.voidlauncher.ui.overview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
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
 * One page as seen from above: coordinate, optional HOME/HERE tags and item count.
 * A tap selects the page; a long press selects it for dragging and a following drag
 * reports map-space deltas. The four navigation arrows live outside the tile in
 * [DirectionToggle] so they never trigger a tap or a drag.
 *
 * [center] is the cell centre in map-space pixels; the enclosing map layer applies
 * the shared pan/zoom transform, so this cell only positions itself.
 */
@Composable
internal fun PageCell(
    center: Offset,
    cellWidthPx: Float,
    cellHeightPx: Float,
    coordLabel: String,
    isHome: Boolean,
    isCurrent: Boolean,
    showCoordDetail: Boolean = true,
    itemCount: Int,
    outgoing: Set<SwipeDirection>,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    val currentClick = rememberUpdatedState(onClick)
    val currentLongPress = rememberUpdatedState(onLongPress)
    val currentDragStart = rememberUpdatedState(onDragStart)
    val currentDrag = rememberUpdatedState(onDrag)
    val currentDragEnd = rememberUpdatedState(onDragEnd)

    val shape = RoundedCornerShape(8.dp)
    val containerColor = when {
        isCurrent -> MaterialTheme.colorScheme.primaryContainer
        isSelected -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val contentColor = when {
        isCurrent -> MaterialTheme.colorScheme.onPrimaryContainer
        isSelected -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val markerColor = if (isCurrent || isSelected) {
        contentColor
    } else {
        MaterialTheme.colorScheme.primary
    }
    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.tertiary
        isCurrent -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (center.x - cellWidthPx / 2f).roundToInt(),
                    (center.y - cellHeightPx / 2f).roundToInt(),
                )
            }
            .size(OverviewCellWidth, OverviewCellHeight)
            .clip(shape)
            .background(containerColor)
            .border(
                width = if (isCurrent || isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = shape,
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { currentClick.value() },
                    onLongPress = { currentLongPress.value() },
                )
            }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { currentDragStart.value() },
                    onDrag = { change, amount ->
                        change.consume()
                        currentDrag.value(amount)
                    },
                    onDragEnd = { currentDragEnd.value() },
                    onDragCancel = { currentDragEnd.value() },
                )
            }
            .padding(6.dp),
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
                if (isSelected && !isCurrent) {
                    Text(
                        text = "HOLD",
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

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SwipeDirection.entries.forEach { direction ->
                    OverviewArrow(
                        direction = direction,
                        color = if (direction in outgoing) {
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

/** Page previews share the exact geometry of real page tiles. */
internal val PreviewHeight: Dp = OverviewCellHeight
internal val PreviewWidth: Dp = OverviewCellWidth

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
        Canvas(modifier = Modifier.fillMaxSize()) {
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
 * One of the four outgoing edge controls for a page. [available] is true only when
 * a page actually sits at that orthogonal coordinate: unavailable arrows are dim
 * and have no click handler at all. Bright means the edge exists, dim means the
 * slot is free to be toggled on. The control sits above the page tiles in the map
 * layer so its click never selects or drags the page.
 */
@Composable
internal fun DirectionToggle(
    center: Offset,
    cellWidthPx: Float,
    cellHeightPx: Float,
    direction: SwipeDirection,
    available: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    // Two neighbouring tiles contribute one arrow each to the same 28dp gap, so
    // 14dp controls placed flush against their own tile edge never overlap.
    val size = 14.dp
    val sizePx = with(LocalDensity.current) { size.toPx() }
    val inset = 0f
    val (x, y) = when (direction) {
        SwipeDirection.LEFT ->
            (center.x - cellWidthPx / 2f - sizePx - inset).roundToInt() to
                (center.y - sizePx / 2f).roundToInt()
        SwipeDirection.RIGHT ->
            (center.x + cellWidthPx / 2f + inset).roundToInt() to
                (center.y - sizePx / 2f).roundToInt()
        SwipeDirection.UP ->
            (center.x - sizePx / 2f).roundToInt() to
                (center.y - cellHeightPx / 2f - sizePx - inset).roundToInt()
        SwipeDirection.DOWN ->
            (center.x - sizePx / 2f).roundToInt() to
                (center.y + cellHeightPx / 2f + inset).roundToInt()
    }
    val arrowColor = when {
        !available -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
        enabled -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    }
    var controlModifier = Modifier
        .offset { IntOffset(x, y) }
        .size(size)
        .clip(RoundedCornerShape(9.dp))
        .background(
            MaterialTheme.colorScheme.surface.copy(
                alpha = if (available && enabled) 0.95f else 0.6f,
            ),
        )
    if (available) controlModifier = controlModifier.clickable(onClick = onClick)
    Box(modifier = controlModifier, contentAlignment = Alignment.Center) {
        OverviewArrow(direction = direction, color = arrowColor, diameter = 10.dp)
    }
}

/** Translucent dashed outline shown at the candidate cell while a page is dragged. */
@Composable
internal fun DragGhostCell(
    center: Offset,
    cellWidthPx: Float,
    cellHeightPx: Float,
    valid: Boolean,
) {
    val color = if (valid) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (center.x - cellWidthPx / 2f).roundToInt(),
                    (center.y - cellHeightPx / 2f).roundToInt(),
                )
            }
            .size(OverviewCellWidth, OverviewCellHeight)
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRoundRect(
                color = color.copy(alpha = 0.8f),
                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f),
                ),
            )
        }
        Text(
            text = "MOVE",
            style = MaterialTheme.typography.labelSmall,
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
internal fun OverviewArrow(
    direction: SwipeDirection,
    color: Color,
    diameter: Dp = 10.dp,
) {
    val rotation = when (direction) {
        SwipeDirection.UP -> 0f
        SwipeDirection.RIGHT -> 90f
        SwipeDirection.DOWN -> 180f
        SwipeDirection.LEFT -> 270f
    }
    Canvas(
        modifier = Modifier.size(diameter),
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

/** One of the bottom navigation entry points, including the conditional HOME toggle. */
@Composable
internal fun RowScope.NavButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(56.dp)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            // `background()` does not provide a content colour, so inherit from the
            // theme explicitly or the label renders black on a dark surface.
            color = MaterialTheme.colorScheme.onSurface.copy(
                alpha = if (enabled) 1f else 0.35f,
            ),
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}
