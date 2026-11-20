package com.ark.voidlauncher.ui.overview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import com.ark.voidlauncher.domain.PageType
import com.ark.voidlauncher.domain.SwipeDirection
import com.ark.voidlauncher.domain.isValidPageDestination
import com.ark.voidlauncher.domain.neighbourCoord
import com.ark.voidlauncher.domain.nextPageCandidates
import kotlin.math.roundToInt

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
 *
 * A `+` preview asks for a WIDGET or APP DRAWER role before creating a page. Long
 * pressing a tile selects it for dragging (and reveals the HOME toggle); a normal
 * tap still opens the page. Dragging uses the same pan/zoom transform as the map.
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
    onAddPage: (Int, Int, PageType) -> Unit = { _, _, _ -> },
    onMovePage: (Long, Int, Int) -> Unit = { _, _, _ -> },
    onSetHomePage: (Long) -> Unit = {},
    onToggleEdge: (Long, SwipeDirection, Long, Boolean) -> Unit = { _, _, _, _ -> },
    showCoordinateDetail: Boolean = true,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val density = LocalDensity.current
        val strideXPx = with(density) { OverviewStrideX.toPx() }
        val strideYPx = with(density) { OverviewStrideY.toPx() }
        val cellWidthPx = with(density) { OverviewCellWidth.toPx() }
        val cellHeightPx = with(density) { OverviewCellHeight.toPx() }
        val previewWidthPx = with(density) { PreviewWidth.toPx() }
        val previewHeightPx = with(density) { PreviewHeight.toPx() }
        val viewportCenter = Offset(constraints.maxWidth * 0.5f, constraints.maxHeight * 0.5f)

        val minCoordX = pages.minOfOrNull { it.coordX } ?: 0
        val maxCoordX = pages.maxOfOrNull { it.coordX } ?: 0
        val minCoordY = pages.minOfOrNull { it.coordY } ?: 0
        val maxCoordY = pages.maxOfOrNull { it.coordY } ?: 0
        val mapCols = (maxCoordX - minCoordX) + 1 + 2 * OverviewMarginCells
        val mapRows = (maxCoordY - minCoordY) + 1 + 2 * OverviewMarginCells

        // Map-space centre (px) of each page. Shifting by the minimum coordinate
        // keeps every cell inside a Canvas with non-negative origin.
        val centers = remember(pages, minCoordX, maxCoordY, strideXPx, strideYPx) {
            pages.associate { page ->
                val mapX = page.coordX - minCoordX + OverviewMarginCells
                // Screen Y grows down; inverting the coordinate puts (0,1) above (0,0).
                val mapY = maxCoordY - page.coordY + OverviewMarginCells
                page.pageId to Offset(
                    (mapX + 0.5f) * strideXPx,
                    (mapY + 0.5f) * strideYPx,
                )
            }
        }

        // Ghost tiles use the same lattice maths as real pages so they line up with
        // the dotted grid and never drift when the map is panned or zoomed.
        val previewCenters = remember(pages, minCoordX, maxCoordY, strideXPx, strideYPx) {
            nextPageCandidates(pages).map { (x, y) ->
                val mapX = x - minCoordX + OverviewMarginCells
                val mapY = maxCoordY - y + OverviewMarginCells
                (x to y) to Offset(
                    (mapX + 0.5f) * strideXPx,
                    (mapY + 0.5f) * strideYPx,
                )
            }
        }

        val centerPage = pages.firstOrNull { it.pageId == currentPageId } ?: pages.firstOrNull()
        val initialCenter = centerPage?.let { centers[it.pageId] }

        var scale by remember { mutableStateOf(1f) }
        var pan by remember {
            mutableStateOf(if (initialCenter != null) viewportCenter - initialCenter else Offset.Zero)
        }
        val currentScale = rememberUpdatedState(scale)

        var pendingAddCoord by remember { mutableStateOf<Pair<Int, Int>?>(null) }
        var selectedPageId by remember { mutableStateOf<Long?>(null) }
        var drag by remember { mutableStateOf<PageDrag?>(null) }

        val counts = remember(items) { overviewItemCounts(items) }
        val outgoing = remember(edges) { overviewOutgoing(edges) }
        val pageByCoord = remember(pages) { pages.associateBy { it.coordX to it.coordY } }

        // All four directions are rendered for every page. Only slots with an actual
        // orthogonal neighbour are clickable; the others are purely visual walls.
        val toggles = remember(pages, edges, pageByCoord) {
            pages.flatMap { page ->
                SwipeDirection.entries.map { direction ->
                    val (neighbourX, neighbourY) = neighbourCoord(
                        page.coordX, page.coordY, direction,
                    )
                    val neighbourId = pageByCoord[neighbourX to neighbourY]?.pageId
                    EdgeToggle(
                        pageId = page.pageId,
                        direction = direction,
                        toPageId = neighbourId,
                        enabled = neighbourId != null && edges.any {
                            it.fromPageId == page.pageId &&
                                it.swipeDirection == direction &&
                                it.toPageId == neighbourId
                        },
                    )
                }
            }
        }
        val edgeHighlightColor = MaterialTheme.colorScheme.primary
        val edgeColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
        val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)

        // Drag deltas arrive in screen pixels; convert to map-space before applying
        // them to the tile offset so pan/zoom does not distort the drag distance.
        fun updateDrag(amount: Offset) {
            val current = drag ?: return
            val factor = currentScale.value
            drag = current.copy(
                offset = current.offset + Offset(amount.x / factor, amount.y / factor),
            )
        }

        fun finishDrag() {
            val current = drag ?: return
            val dx = (current.offset.x / strideXPx).roundToInt()
            // Map Y is inverted relative to stored coordinates.
            val dy = (-current.offset.y / strideYPx).roundToInt()
            val x = current.startX + dx
            val y = current.startY + dy
            drag = null
            if (x != current.startX || y != current.startY) {
                onMovePage(current.pageId, x, y)
            }
        }

        val dragPreview = drag?.let { current ->
            val base = centers[current.pageId] ?: return@let null
            val dx = (current.offset.x / strideXPx).roundToInt()
            val dy = (-current.offset.y / strideYPx).roundToInt()
            val x = current.startX + dx
            val y = current.startY + dy
            DragPreview(
                x = x,
                y = y,
                valid = isValidPageDestination(pages, x, y, current.pageId),
                center = base + Offset(dx * strideXPx, -dy * strideYPx),
            )
        }

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
                    .size(OverviewStrideX * mapCols, OverviewStrideY * mapRows)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = scale
                        scaleY = scale
                        translationX = pan.x
                        translationY = pan.y
                    },
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawOverviewGrid(
                        strideXPx = strideXPx,
                        strideYPx = strideYPx,
                        cols = mapCols,
                        rows = mapRows,
                        color = gridColor,
                    )
                    drawOverviewEdges(
                        edges = edges,
                        centers = centers,
                        currentPageId = currentPageId,
                        cellWidthPx = cellWidthPx,
                        cellHeightPx = cellHeightPx,
                        highlightColor = edgeHighlightColor,
                        edgeColor = edgeColor,
                    )
                }

                pages.forEach { page ->
                    val dragging = drag
                    val center = centers.getValue(page.pageId).let { base ->
                        if (dragging != null && dragging.pageId == page.pageId) {
                            base + dragging.offset
                        } else {
                            base
                        }
                    }
                    PageCell(
                        center = center,
                        cellWidthPx = cellWidthPx,
                        cellHeightPx = cellHeightPx,
                        coordLabel = "${page.coordX},${page.coordY}",
                        isHome = page.pageId == homePageId,
                        isCurrent = page.pageId == currentPageId,
                        isSelected = page.pageId == selectedPageId,
                        showCoordDetail = showCoordinateDetail,
                        itemCount = counts[page.pageId] ?: 0,
                        outgoing = outgoing[page.pageId].orEmpty(),
                        onClick = { onSelectPage(page.pageId) },
                        onLongPress = { selectedPageId = page.pageId },
                        onDragStart = {
                            selectedPageId = page.pageId
                            if (drag?.pageId != page.pageId) {
                                drag = PageDrag(page.pageId, page.coordX, page.coordY, Offset.Zero)
                            }
                        },
                        onDrag = ::updateDrag,
                        onDragEnd = ::finishDrag,
                    )
                }

                previewCenters.forEach { (coord, center) ->
                    PlacementPreviewCell(
                        center = center,
                        previewWidthPx = previewWidthPx,
                        previewHeightPx = previewHeightPx,
                        onClick = { pendingAddCoord = coord },
                    )
                }

                // Drawn after the `+` ghosts so the candidate slot is always visible.
                dragPreview?.let { preview ->
                    DragGhostCell(
                        center = preview.center,
                        cellWidthPx = cellWidthPx,
                        cellHeightPx = cellHeightPx,
                        valid = preview.valid,
                    )
                }

                toggles.forEach { toggle ->
                    val center = centers[toggle.pageId] ?: return@forEach
                    val dragging = drag
                    val toggleCenter = if (dragging != null && dragging.pageId == toggle.pageId) {
                        center + dragging.offset
                    } else {
                        center
                    }
                    DirectionToggle(
                        center = toggleCenter,
                        cellWidthPx = cellWidthPx,
                        cellHeightPx = cellHeightPx,
                        direction = toggle.direction,
                        available = toggle.toPageId != null,
                        enabled = toggle.enabled,
                        onClick = {
                            toggle.toPageId?.let { targetId ->
                                onToggleEdge(
                                    toggle.pageId,
                                    toggle.direction,
                                    targetId,
                                    !toggle.enabled,
                                )
                            }
                        },
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
            val selectedPage = selectedPageId?.let { id -> pages.firstOrNull { it.pageId == id } }
            if (selectedPage != null) {
                val isHome = selectedPage.pageId == homePageId
                val canSetHome = selectedPage.pageType != PageType.APP_DRAWER
                NavButton(
                    label = if (isHome) "HOME" else "SET HOME",
                    onClick = { onSetHomePage(selectedPage.pageId) },
                    enabled = canSetHome,
                )
            }
        }

        val addCoord = pendingAddCoord
        if (addCoord != null) {
            AlertDialog(
                onDismissRequest = { pendingAddCoord = null },
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurface,
                title = {
                    Text(text = "ADD PAGE", style = MaterialTheme.typography.titleMedium)
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "WIDGET PAGE",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onAddPage(addCoord.first, addCoord.second, PageType.WIDGET)
                                    pendingAddCoord = null
                                }
                                .padding(vertical = 14.dp),
                        )
                        Text(
                            text = "APP DRAWER PAGE",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onAddPage(addCoord.first, addCoord.second, PageType.APP_DRAWER)
                                    pendingAddCoord = null
                                }
                                .padding(vertical = 14.dp),
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { pendingAddCoord = null }) {
                        Text(
                            text = "CANCEL",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
            )
        }
    }
}

/** Active drag in map-space pixels, anchored to the page's original coordinate. */
private data class PageDrag(
    val pageId: Long,
    val startX: Int,
    val startY: Int,
    val offset: Offset,
)

/** Candidate cell and snapped ghost centre derived from an active drag. */
private data class DragPreview(
    val x: Int,
    val y: Int,
    val valid: Boolean,
    val center: Offset,
)

/** One outgoing edge control derived from a page and its orthogonal neighbour. */
private data class EdgeToggle(
    val pageId: Long,
    val direction: SwipeDirection,
    val toPageId: Long?,
    val enabled: Boolean,
)
