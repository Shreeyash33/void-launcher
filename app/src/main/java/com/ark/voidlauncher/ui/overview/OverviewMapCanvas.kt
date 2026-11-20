package com.ark.voidlauncher.ui.overview

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ark.voidlauncher.data.db.GridItemEntity
import com.ark.voidlauncher.domain.EdgeInfo
import com.ark.voidlauncher.domain.SwipeDirection
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Shared geometry and edge-rendering helpers for the bird's-eye page map.
 *
 * The map is laid out on an abstract grid: a page at `(coordX, coordY)` occupies
 * one portrait slot, with X growing right and Y growing up (the caller inverts
 * screen Y when computing cell centres). Page tiles and `+` previews share the
 * same 16:9 height:width ratio so the map reads like a collection of phones.
 * All values here are derived from theme tokens at the call site so dark/light/
 * custom themes need no changes in this file.
 */

/** Page tile height at 1x zoom. */
internal val OverviewCellHeight: Dp = 140.dp

/** Page tile width at 1x zoom: 9 units wide for every 16 units tall. */
internal val OverviewCellWidth: Dp = OverviewCellHeight * 9f / 16f

/** Breathing room between neighbouring cells. */
internal val OverviewCellGap: Dp = 28.dp

/** Distance between adjacent cell centres at 1x zoom. */
internal val OverviewStrideX: Dp = OverviewCellWidth + OverviewCellGap
internal val OverviewStrideY: Dp = OverviewCellHeight + OverviewCellGap

internal const val OverviewMinScale: Float = 0.35f
internal const val OverviewMaxScale: Float = 2.5f

/** Empty slots kept around the used coordinate range so border pages are not cramped. */
internal const val OverviewMarginCells: Int = 2

/** Number of placed items per page, used for the "N items" cell caption. */
internal fun overviewItemCounts(items: List<GridItemEntity>): Map<Long, Int> =
    items.groupingBy { it.pageId }.eachCount()

/** Outgoing swipe directions per source page, used for the cell direction glyphs. */
internal fun overviewOutgoing(edges: List<EdgeInfo>): Map<Long, Set<SwipeDirection>> =
    edges.groupBy({ it.fromPageId }) { it.swipeDirection }.mapValues { it.value.toSet() }

/**
 * Faint dotted lattice behind the pages. The boundary rectangle makes the extent
 * of the map visible while panning and zooming, so empty space reads as "no page
 * here" rather than as a missing render.
 */
internal fun DrawScope.drawOverviewGrid(
    strideXPx: Float,
    strideYPx: Float,
    cols: Int,
    rows: Int,
    color: Color,
) {
    val stroke = 1.dp.toPx()
    val dashes = PathEffect.dashPathEffect(floatArrayOf(8f, 10f), 0f)
    val width = cols * strideXPx
    val height = rows * strideYPx

    for (col in 0..cols) {
        val x = col * strideXPx
        drawLine(color, Offset(x, 0f), Offset(x, height), stroke, pathEffect = dashes)
    }
    for (row in 0..rows) {
        val y = row * strideYPx
        drawLine(color, Offset(0f, y), Offset(width, y), stroke, pathEffect = dashes)
    }
    drawRect(
        color = color,
        size = size,
        style = Stroke(width = stroke, pathEffect = dashes),
    )
}

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
    cellWidthPx: Float,
    cellHeightPx: Float,
    highlightColor: Color,
    edgeColor: Color,
) {
    val highlightWidth = 3.dp.toPx()
    val normalWidth = 1.5.dp.toPx()
    val arrowLength = 10.dp.toPx()
    val halfWidth = cellWidthPx / 2f
    val halfHeight = cellHeightPx / 2f

    edges.forEach { edge ->
        val from = centers[edge.fromPageId] ?: return@forEach
        val to = centers[edge.toPageId] ?: return@forEach

        val dx = to.x - from.x
        val dy = to.y - from.y
        val distance = sqrt(dx * dx + dy * dy)
        if (distance == 0f) return@forEach

        val ux = dx / distance
        val uy = dy / distance
        // Distance from the cell centre to its border along this ray. Portrait
        // tiles need a different inset horizontally and vertically.
        val borderDistance = when {
            abs(ux) < 0.0001f -> halfHeight
            abs(uy) < 0.0001f -> halfWidth
            else -> min(halfWidth / abs(ux), halfHeight / abs(uy))
        }
        val startInset = borderDistance + 2.dp.toPx()
        val endInset = borderDistance + 6.dp.toPx()
        if (distance <= startInset + endInset) return@forEach

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
        drawCircle(
            color = color,
            radius = stroke * 1.6f,
            center = from + Offset(ux * borderDistance, uy * borderDistance),
        )
    }
}
