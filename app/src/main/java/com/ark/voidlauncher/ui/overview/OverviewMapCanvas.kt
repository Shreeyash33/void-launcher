package com.ark.voidlauncher.ui.overview

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ark.voidlauncher.data.db.GridItemEntity
import com.ark.voidlauncher.domain.EdgeInfo
import com.ark.voidlauncher.domain.SwipeDirection
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Shared geometry and edge-rendering helpers for the bird's-eye page map.
 *
 * The map is laid out on an abstract grid: a page at `(coordX, coordY)` occupies
 * one [OverviewStride] slot, with X growing right and Y growing up (the caller
 * inverts screen Y when computing cell centres). All values
 * here are derived from theme tokens at the call site so dark/light/custom themes
 * need no changes in this file.
 */

/** Cell edge length at 1x zoom. */
internal val OverviewCellSize: Dp = 140.dp

/** Breathing room between neighbouring cells. */
internal val OverviewCellGap: Dp = 28.dp

/** Distance between adjacent cell centres at 1x zoom. */
internal val OverviewStride: Dp = OverviewCellSize + OverviewCellGap

internal const val OverviewMinScale: Float = 0.35f
internal const val OverviewMaxScale: Float = 2.5f

/** Empty slots kept around the used coordinate range so border pages are not cramped. */
internal const val OverviewMarginCells: Int = 2

internal fun SwipeDirection.overviewGlyph(): String = when (this) {
    SwipeDirection.UP -> "↑"
    SwipeDirection.DOWN -> "↓"
    SwipeDirection.LEFT -> "←"
    SwipeDirection.RIGHT -> "→"
}

/** Number of placed items per page, used for the "N items" cell caption. */
internal fun overviewItemCounts(items: List<GridItemEntity>): Map<Long, Int> =
    items.groupingBy { it.pageId }.eachCount()

/** Outgoing swipe directions per source page, used for the cell direction glyphs. */
internal fun overviewOutgoing(edges: List<EdgeInfo>): Map<Long, Set<SwipeDirection>> =
    edges.groupBy({ it.fromPageId }) { it.swipeDirection }.mapValues { it.value.toSet() }

/**
 * Draws every edge as a line from the source cell to the destination cell with an
 * arrowhead at the destination. Edges leaving [currentPageId] are drawn with the
 * [highlightColor] token and a heavier stroke; all others use [edgeColor].
 *
 * Both endpoints are looked up in [centers] (map-space centres in px); edges whose
 * page is no longer present are skipped rather than crashing the overview.
 */
internal fun DrawScope.drawOverviewEdges(
    edges: List<EdgeInfo>,
    centers: Map<Long, Offset>,
    currentPageId: Long,
    cellSizePx: Float,
    highlightColor: Color,
    edgeColor: Color,
) {
    val highlightWidth = 3.dp.toPx()
    val normalWidth = 1.5.dp.toPx()
    val arrowLength = 10.dp.toPx()
    val startInset = cellSizePx / 2f + 2.dp.toPx()
    val endInset = cellSizePx / 2f + 6.dp.toPx()
    val halfCell = cellSizePx / 2f

    edges.forEach { edge ->
        val from = centers[edge.fromPageId] ?: return@forEach
        val to = centers[edge.toPageId] ?: return@forEach

        val dx = to.x - from.x
        val dy = to.y - from.y
        val distance = sqrt(dx * dx + dy * dy)
        if (distance <= startInset + endInset) return@forEach

        val ux = dx / distance
        val uy = dy / distance
        val start = Offset(from.x + ux * startInset, from.y + uy * startInset)
        val tip = Offset(to.x - ux * endInset, to.y - uy * endInset)

        val highlighted = edge.fromPageId == currentPageId
        val color = if (highlighted) highlightColor else edgeColor
        val stroke = if (highlighted) highlightWidth else normalWidth

        drawLine(color = color, start = start, end = tip, strokeWidth = stroke)

        val angle = atan2(uy, ux)
        val back = angle + PI.toFloat()
        val spread = 0.5f
        val wing1 = Offset(
            x = tip.x + cos(back - spread) * arrowLength,
            y = tip.y + sin(back - spread) * arrowLength,
        )
        val wing2 = Offset(
            x = tip.x + cos(back + spread) * arrowLength,
            y = tip.y + sin(back + spread) * arrowLength,
        )
        drawLine(color = color, start = tip, end = wing1, strokeWidth = stroke)
        drawLine(color = color, start = tip, end = wing2, strokeWidth = stroke)

        // A small dot at the source makes the direction of travel unambiguous even
        // when the two cells are close together.
        drawCircle(color = color, radius = stroke * 1.6f, center = from + Offset(ux * halfCell, uy * halfCell))
    }
}
