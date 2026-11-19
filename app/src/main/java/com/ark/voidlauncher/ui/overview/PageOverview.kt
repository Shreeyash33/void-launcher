package com.ark.voidlauncher.ui.overview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.ark.voidlauncher.data.db.GridItemEntity
import com.ark.voidlauncher.domain.EdgeInfo
import com.ark.voidlauncher.domain.PageInfo

/**
 * Full-screen bird's-eye view of the page graph.
 *
 * Pages are placed on a 2D lattice from their `(coordX, coordY)` (X right, Y
 * up — positive Y renders above the origin) and the whole map can be dragged and pinched. Pan/zoom are the only local
 * state; everything else is derived from the supplied pages, edges and items. On
 * first composition the map is centred on [currentPageId] (or the first page when
 * the current id is not present). Empty and single-page maps are handled without
 * special cases in the layout: a fallback one-cell map is drawn with an empty-state
 * message on top.
 */
@Composable
fun PageOverview(
    pages: List<PageInfo>,
    edges: List<EdgeInfo>,
    items: List<GridItemEntity>,
    currentPageId: Long,
    homePageId: Long,
    onSelectPage: (Long) -> Unit,
    onClose: () -> Unit,
    onThemes: () -> Unit,
    onSettings: () -> Unit,
    onWidgets: () -> Unit,
    onPageManagement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val density = LocalDensity.current
        val stridePx = with(density) { OverviewStride.toPx() }
        val cellSizePx = with(density) { OverviewCellSize.toPx() }
        val viewportCenter = Offset(constraints.maxWidth * 0.5f, constraints.maxHeight * 0.5f)

        val minCoordX = pages.minOfOrNull { it.coordX } ?: 0
        val maxCoordX = pages.maxOfOrNull { it.coordX } ?: 0
        val minCoordY = pages.minOfOrNull { it.coordY } ?: 0
        val maxCoordY = pages.maxOfOrNull { it.coordY } ?: 0
        val mapCols = (maxCoordX - minCoordX) + 1 + 2 * OverviewMarginCells
        val mapRows = (maxCoordY - minCoordY) + 1 + 2 * OverviewMarginCells

        // Map-space centre (px) of each page. Shifting by the minimum coordinate
        // keeps every cell inside a Canvas with non-negative origin.
        val centers = remember(pages, minCoordX, minCoordY, stridePx) {
            pages.associate { page ->
                val mapX = page.coordX - minCoordX + OverviewMarginCells
                // Screen Y grows down; inverting the coordinate puts (0,1) above (0,0).
                val mapY = maxCoordY - page.coordY + OverviewMarginCells
                page.pageId to Offset((mapX + 0.5f) * stridePx, (mapY + 0.5f) * stridePx)
            }
        }

        val centerPage = pages.firstOrNull { it.pageId == currentPageId } ?: pages.firstOrNull()
        val initialCenter = centerPage?.let { centers[it.pageId] }

        var scale by remember { mutableStateOf(1f) }
        var pan by remember {
            mutableStateOf(if (initialCenter != null) viewportCenter - initialCenter else Offset.Zero)
        }

        val counts = remember(items) { overviewItemCounts(items) }
        val outgoing = remember(edges) { overviewOutgoing(edges) }
        val edgeHighlightColor = MaterialTheme.colorScheme.primary
        val edgeColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, panChange, zoomChange, _ ->
                        val oldScale = scale
                        val newScale = (oldScale * zoomChange).coerceIn(
                            OverviewMinScale,
                            OverviewMaxScale,
                        )
                        // Keep the point under the fingers anchored while zooming,
                        // then apply the drag delta from this gesture.
                        val ratio = if (oldScale == 0f) 1f else newScale / oldScale
                        pan = centroid + (pan - centroid) * ratio + panChange
                        scale = newScale
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .size(OverviewStride * mapCols, OverviewStride * mapRows)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = scale
                        scaleY = scale
                        translationX = pan.x
                        translationY = pan.y
                    },
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawOverviewEdges(
                        edges = edges,
                        centers = centers,
                        currentPageId = currentPageId,
                        cellSizePx = cellSizePx,
                        highlightColor = edgeHighlightColor,
                        edgeColor = edgeColor,
                    )
                }

                pages.forEach { page ->
                    PageCell(
                        center = centers.getValue(page.pageId),
                        cellSizePx = cellSizePx,
                        coordLabel = "${page.coordX},${page.coordY}",
                        isHome = page.pageId == homePageId,
                        isCurrent = page.pageId == currentPageId,
                        itemCount = counts[page.pageId] ?: 0,
                        outgoing = outgoing[page.pageId].orEmpty(),
                        onClick = { onSelectPage(page.pageId) },
                    )
                }
            }
        }

        if (pages.isEmpty()) {
            Text(
                text = "NO PAGES YET",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "BACK",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onClose),
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = "PAGE MAP · ${pages.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(56.dp)
                .background(MaterialTheme.colorScheme.surface),
        ) {
            NavButton("THEMES", onThemes)
            NavButton("SETTINGS", onSettings)
            NavButton("WIDGETS", onWidgets)
            NavButton("PAGES", onPageManagement)
        }
    }
}
