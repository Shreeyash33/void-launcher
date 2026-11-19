package com.ark.voidlauncher.ui.hud

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.ark.voidlauncher.domain.EdgeInfo
import com.ark.voidlauncher.domain.PageInfo
import com.ark.voidlauncher.domain.SwipeDirection
import kotlinx.coroutines.delay

/**
 * Non-interactive spatial-awareness overlay for the home canvas.
 *
 * The whole surface is transparent to touch: only the small MAP text is
 * clickable, so swipes, double taps and long presses still reach the canvas
 * underneath. The coordinate badge is the primary "where am I" cue; the four
 * edge arrows show which swipes lead somewhere and which are walls.
 *
 * Pass a size-providing [modifier] (for example `Modifier.matchParentSize()`
 * from the owning BoxScope, or `Modifier.fillMaxSize()`).
 */
@Composable
fun NavigationHud(
    page: PageInfo,
    edges: List<EdgeInfo>,
    wallHit: SwipeDirection?,
    onWallHitCleared: () -> Unit,
    onOpenOverview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val outgoing = remember(edges, page.pageId) {
        edges.filter { it.fromPageId == page.pageId }
    }
    // The origin is the default home coordinate; the host also renders this HUD
    // only on the home page, so this tag reflects home in the standard setup.
    val isHome = page.coordX == HOME_COORD_X && page.coordY == HOME_COORD_Y

    // Keep the last side long enough to fade the bar out after the host clears
    // the wall hit.
    var flashDirection by remember { mutableStateOf<SwipeDirection?>(null) }
    val flashAlpha by animateFloatAsState(
        targetValue = if (wallHit == null) 0f else 1f,
        animationSpec = tween(
            durationMillis = if (wallHit == null) FLASH_FADE_OUT_MS else FLASH_FADE_IN_MS,
        ),
        label = "wallFlashAlpha",
    )

    LaunchedEffect(wallHit) {
        if (wallHit != null) {
            flashDirection = wallHit
            delay(WALL_FEEDBACK_MS)
            onWallHitCleared()
        }
    }

    val visibleFlash = wallHit ?: flashDirection
    Box(modifier = modifier) {
        Box(modifier = Modifier.matchParentSize()) {
            if (visibleFlash != null && flashAlpha > 0f) {
                WallFlash(direction = visibleFlash, flashAlpha = flashAlpha)
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            shape = MaterialTheme.shapes.small,
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "PAGE ${page.coordX},${page.coordY}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (isHome) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "HOME",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = MaterialTheme.shapes.small,
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            DirectionIndicator(
                direction = SwipeDirection.UP,
                edge = outgoing.firstOrNull { it.swipeDirection == SwipeDirection.UP },
                page = page,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp),
            )
            DirectionIndicator(
                direction = SwipeDirection.DOWN,
                edge = outgoing.firstOrNull { it.swipeDirection == SwipeDirection.DOWN },
                page = page,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 88.dp),
            )
            DirectionIndicator(
                direction = SwipeDirection.LEFT,
                edge = outgoing.firstOrNull { it.swipeDirection == SwipeDirection.LEFT },
                page = page,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp),
            )
            DirectionIndicator(
                direction = SwipeDirection.RIGHT,
                edge = outgoing.firstOrNull { it.swipeDirection == SwipeDirection.RIGHT },
                page = page,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp),
            )

            Text(
                text = "MAP",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 12.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        shape = MaterialTheme.shapes.small,
                    )
                    .clickable(onClick = onOpenOverview)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun DirectionIndicator(
    direction: SwipeDirection,
    edge: EdgeInfo?,
    page: PageInfo,
    modifier: Modifier = Modifier,
) {
    val arrow = when (direction) {
        SwipeDirection.UP -> "↑"
        SwipeDirection.DOWN -> "↓"
        SwipeDirection.LEFT -> "←"
        SwipeDirection.RIGHT -> "→"
    }
    val available = edge != null
    val arrowColor = if (available) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onBackground.copy(alpha = DIM_ALPHA)
    }
    val neighbour = if (available) neighbourLabel(page, direction) else null

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = arrow,
            style = MaterialTheme.typography.headlineSmall,
            color = arrowColor,
        )
        if (neighbour != null) {
            Text(
                text = neighbour,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = LABEL_ALPHA),
            )
        }
    }
}

@Composable
private fun BoxScope.WallFlash(direction: SwipeDirection, flashAlpha: Float) {
    val barModifier = when (direction) {
        SwipeDirection.UP -> Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(WALL_BAR_THICKNESS)

        SwipeDirection.DOWN -> Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(WALL_BAR_THICKNESS)

        SwipeDirection.LEFT -> Modifier
            .align(Alignment.CenterStart)
            .fillMaxHeight()
            .width(WALL_BAR_THICKNESS)

        SwipeDirection.RIGHT -> Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(WALL_BAR_THICKNESS)
    }
    Box(
        modifier = barModifier
            .background(MaterialTheme.colorScheme.error)
            .graphicsLayer { alpha = flashAlpha },
    )
}

/**
 * The HUD receives no page list, so a target is shown as the adjacent coordinate
 * implied by the swipe direction (grid X right, Y down).
 */
private fun neighbourLabel(page: PageInfo, direction: SwipeDirection): String {
    val x = page.coordX
    val y = page.coordY
    return when (direction) {
        SwipeDirection.UP -> "${x},${y - 1}"
        SwipeDirection.DOWN -> "${x},${y + 1}"
        SwipeDirection.LEFT -> "${x - 1},${y}"
        SwipeDirection.RIGHT -> "${x + 1},${y}"
    }
}

private const val HOME_COORD_X = 0
private const val HOME_COORD_Y = 0
private const val WALL_FEEDBACK_MS = 450L
private const val FLASH_FADE_IN_MS = 80
private const val FLASH_FADE_OUT_MS = 220
private val WALL_BAR_THICKNESS = 10.dp
private const val DIM_ALPHA = 0.3f
private const val LABEL_ALPHA = 0.7f
