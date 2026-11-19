package com.ark.voidlauncher.ui.spatial

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ark.voidlauncher.data.db.GridItemEntity
import com.ark.voidlauncher.domain.EdgeInfo
import com.ark.voidlauncher.domain.PageInfo
import com.ark.voidlauncher.domain.SwipeDirection
import com.ark.voidlauncher.ui.grid.PageGrid
import kotlin.math.abs

/**
 * Full-screen spatial surface for one launcher page.
 *
 * Swipes are read directly from pointer input (no nested Pagers). A drag past
 * [SWIPE_THRESHOLD] on its dominant axis looks up a single outgoing edge from
 * the current page. When an edge exists the page animates off-screen and
 * [onPageChanged] is invoked; otherwise the page springs back and the device
 * emits a long-press haptic. A double tap always calls [onHomeRequested].
 */
@Composable
fun SpatialPageCanvas(
    pages: List<PageInfo>,
    edges: List<EdgeInfo>,
    items: List<GridItemEntity>,
    currentPageId: Long,
    homePageId: Long,
    onPageChanged: (Long) -> Unit,
    onHomeRequested: () -> Unit,
    widgetContent: @Composable (GridItemEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentPage = remember(pages, currentPageId) {
        pages.firstOrNull { it.pageId == currentPageId }
    }
    val pageItems = remember(items, currentPageId) {
        items.filter { it.pageId == currentPageId }
    }

    val translation = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = translation.value.x
                    translationY = translation.value.y
                }
                .pointerInput(currentPageId, edges, onPageChanged, view) {
                    val thresholdPx = SWIPE_THRESHOLD.toPx()
                    var dragTotal = Offset.Zero

                    detectDragGestures(
                        onDragStart = {
                            dragTotal = Offset.Zero
                            scope.launch { translation.stop() }
                        },
                        onDragEnd = {
                            val absX = abs(dragTotal.x)
                            val absY = abs(dragTotal.y)
                            val direction: SwipeDirection? = when {
                                absX >= absY && absX >= thresholdPx ->
                                    if (dragTotal.x > 0f) SwipeDirection.RIGHT else SwipeDirection.LEFT

                                absY > absX && absY >= thresholdPx ->
                                    if (dragTotal.y > 0f) SwipeDirection.DOWN else SwipeDirection.UP

                                else -> null
                            }

                            if (direction == null) {
                                scope.launch { translation.animateTo(Offset.Zero, spring()) }
                            } else {
                                val edge = edges.firstOrNull {
                                    it.fromPageId == currentPageId && it.swipeDirection == direction
                                }
                                if (edge == null) {
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    scope.launch { translation.animateTo(Offset.Zero, spring()) }
                                } else {
                                    val exit = when (direction) {
                                        SwipeDirection.RIGHT -> Offset(size.width.toFloat(), 0f)
                                        SwipeDirection.LEFT -> Offset(-size.width.toFloat(), 0f)
                                        SwipeDirection.DOWN -> Offset(0f, size.height.toFloat())
                                        SwipeDirection.UP -> Offset(0f, -size.height.toFloat())
                                    }
                                    scope.launch {
                                        translation.animateTo(exit, tween(durationMillis = EXIT_DURATION_MS))
                                        onPageChanged(edge.toPageId)
                                        translation.snapTo(Offset.Zero)
                                    }
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch { translation.animateTo(Offset.Zero, spring()) }
                        },
                    ) { _, dragAmount ->
                        dragTotal += dragAmount
                        translation.snapTo(dragTotal)
                    }
                }
                .pointerInput(onHomeRequested) {
                    detectTapGestures(onDoubleTap = { onHomeRequested() })
                },
        ) {
            if (currentPage != null) {
                PageGrid(
                    cols = currentPage.gridCols,
                    rows = currentPage.gridRows,
                    items = pageItems,
                    onAppClick = onAppClick,
                    widgetContent = widgetContent,
                )
            }
        }
    }
}

private val SWIPE_THRESHOLD: Dp = 80.dp
private const val EXIT_DURATION_MS = 220
