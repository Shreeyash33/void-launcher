package com.ark.voidlauncher.ui.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ark.voidlauncher.data.db.GridItemEntity
import com.ark.voidlauncher.domain.BuiltInWidgetKind
import com.ark.voidlauncher.domain.BuiltInWidgets
import com.ark.voidlauncher.domain.GridItemType
import kotlin.math.roundToInt

/**
 * Renders one page's items on an adaptive [cols] x [rows] lattice.
 *
 * Cells are derived from the available space, so the grid scales from phones
 * to tablets without changing any stored coordinates. Items are placed at
 * their column/row with their span determining the box size; later items are
 * drawn on top, which is what makes overlapping widgets stack predictably.
 * Conflict prevention lives in PlacementResolver - this composable simply
 * draws the items it is given and reports candidate moves to its host.
 */
@Composable
fun PageGrid(
    cols: Int,
    rows: Int,
    items: List<GridItemEntity>,
    onAppClick: (packageName: String) -> Unit,
    widgetContent: @Composable (GridItemEntity) -> Unit,
    modifier: Modifier = Modifier,
    editing: Boolean = false,
    selectedItemId: Long? = null,
    onSelectItem: (GridItemEntity) -> Unit = {},
    onMoveItem: (GridItemEntity, Int, Int) -> Boolean = { _, _, _ -> false },
    onResizeItem: (GridGeometry) -> Boolean = { false },
    onWidgetAction: (GridItemEntity) -> Unit = {},
    onItemTouched: () -> Unit = {},
    onEmptyTap: () -> Unit = {},
    floatingKind: BuiltInWidgetKind? = null,
    onFloatingDrop: (Int, Int) -> Boolean = { _, _ -> false },
) {
    // Items mark every interaction; an empty tap that follows one is a tap on
    // an item that bubbled through, not a request to leave the editor.
    var lastItemTouch by remember { mutableStateOf(0L) }

    fun markTouch() {
        lastItemTouch = System.nanoTime()
        onItemTouched()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(editing, onEmptyTap, floatingKind) {
                if (!editing || floatingKind != null) return@pointerInput
                detectTapGestures(
                    onTap = {
                        if (System.nanoTime() - lastItemTouch > ITEM_TOUCH_WINDOW_NS) {
                            onEmptyTap()
                        }
                    },
                )
            },
    ) {
        if (cols <= 0 || rows <= 0) return@BoxWithConstraints

        val cellWidth = maxWidth / cols
        val cellHeight = maxHeight / rows
        val density = LocalDensity.current
        val cellWidthPx = with(density) { cellWidth.roundToPx() }.coerceAtLeast(1)
        val cellHeightPx = with(density) { cellHeight.roundToPx() }.coerceAtLeast(1)
        val corner = RoundedCornerShape(8.dp)
        val showGuide = editing || floatingKind != null

        if (showGuide) {
            GridGuide(cols = cols, rows = rows, modifier = Modifier.fillMaxSize())
        }

        items.forEach { item ->
            EditableGridItem(
                item = item,
                cellWidth = cellWidth,
                cellHeight = cellHeight,
                cellWidthPx = cellWidthPx,
                cellHeightPx = cellHeightPx,
                corner = corner,
                density = density,
                maxCols = cols,
                maxRows = rows,
                editing = editing,
                selected = editing && selectedItemId == item.itemId,
                onTap = {
                    markTouch()
                    if (editing) {
                        onSelectItem(item)
                    } else if (item.itemType == GridItemType.APP_SHORTCUT) {
                        onAppClick(item.referenceId)
                    }
                },
                onLongPress = {
                    markTouch()
                    onSelectItem(item)
                },
                onItemTouched = ::markTouch,
                onMove = { col, row ->
                    markTouch()
                    onMoveItem(item, col, row)
                },
                onResize = { geometry ->
                    markTouch()
                    onResizeItem(geometry)
                },
                onWidgetAction = {
                    markTouch()
                    onWidgetAction(item)
                },
                widgetContent = widgetContent,
            )
        }

        if (floatingKind != null) {
            FloatingWidget(
                kind = floatingKind,
                cellWidth = cellWidth,
                cellHeight = cellHeight,
                cellWidthPx = cellWidthPx,
                cellHeightPx = cellHeightPx,
                corner = corner,
                onTouched = ::markTouch,
                onDrop = onFloatingDrop,
            )
        }
    }
}

/** Full mutable geometry of an item during a live resize. */
data class GridGeometry(
    val col: Int,
    val row: Int,
    val spanX: Int,
    val spanY: Int,
)

/**
 * One item plus its transparent gesture layer. The drag delta lives here so
 * the widget content, frame and selection outline all move together.
 */
@Composable
private fun EditableGridItem(
    item: GridItemEntity,
    cellWidth: Dp,
    cellHeight: Dp,
    cellWidthPx: Int,
    cellHeightPx: Int,
    corner: Shape,
    density: Density,
    maxCols: Int,
    maxRows: Int,
    editing: Boolean,
    selected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onItemTouched: () -> Unit,
    onMove: (Int, Int) -> Boolean,
    onResize: (GridGeometry) -> Boolean,
    onWidgetAction: () -> Unit,
    widgetContent: @Composable (GridItemEntity) -> Unit,
) {
    var dragOffset by remember(item.itemId) { mutableStateOf(Offset.Zero) }
    val base = GridGeometry(item.col, item.row, item.colSpan, item.rowSpan)
    var geometry by remember(item.itemId) { mutableStateOf(base) }
    val hasAction = item.widgetKind == BuiltInWidgetKind.APP_LIST ||
        BuiltInWidgets.fromReference(item.referenceId) == BuiltInWidgetKind.APP_LIST
    val boxWidth = cellWidth * geometry.spanX.coerceAtLeast(1)

    LaunchedEffect(item.itemId, item.col, item.row, item.colSpan, item.rowSpan) {
        dragOffset = Offset.Zero
        geometry = base
    }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (cellWidth * geometry.col).roundToPx() + dragOffset.x.roundToInt(),
                    (cellHeight * geometry.row).roundToPx() + dragOffset.y.roundToInt(),
                )
            }
            .width(cellWidth * geometry.spanX.coerceAtLeast(1))
            .height(cellHeight * geometry.spanY.coerceAtLeast(1))
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, corner)
                } else {
                    Modifier
                },
            ),
    ) {
        when (item.itemType) {
            GridItemType.WIDGET -> widgetContent(item)

            GridItemType.APP_SHORTCUT -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(corner)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                        shape = corner,
                    )
                    .padding(6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = item.referenceId.substringAfterLast('.'),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "GRID ITEM ${item.itemType.name} " +
                        "${item.referenceId} AT ${item.col},${item.row}"
                }
                .pointerInput(item.itemId, hasAction, boxWidth, onTap, onLongPress) {
                    detectTapGestures(
                        onTap = { at ->
                            val xDp = with(density) { at.x.toDp() }
                            val yDp = with(density) { at.y.toDp() }
                            if (hasAction && xDp >= boxWidth - ACTION_ZONE && yDp <= ACTION_ZONE) {
                                onWidgetAction()
                            } else {
                                onTap()
                            }
                        },
                        onLongPress = { onLongPress() },
                    )
                }
                .pointerInput(
                    item.itemId,
                    editing,
                    cellWidthPx,
                    cellHeightPx,
                    item.col,
                    item.row,
                    onMove,
                ) {
                    if (!editing) return@pointerInput
                    var total = Offset.Zero
                    detectDragGestures(
                        onDragStart = {
                            total = Offset.Zero
                            onItemTouched()
                        },
                        onDragEnd = {
                            val col = item.col + (total.x / cellWidthPx.toFloat()).roundToInt()
                            val row = item.row + (total.y / cellHeightPx.toFloat()).roundToInt()
                            onMove(col, row)
                            dragOffset = Offset.Zero
                        },
                        onDragCancel = {
                            dragOffset = Offset.Zero
                        },
                    ) { _, amount ->
                        total += amount
                        dragOffset = total
                    }
                },
        )

        if (selected && editing) {
            ResizeHandles(
                key = item.itemId,
                base = base,
                cellWidthPx = cellWidthPx,
                cellHeightPx = cellHeightPx,
                maxCols = maxCols,
                maxRows = maxRows,
                onPreview = { geometry = it },
                onCommit = { target ->
                    if (!onResize(target)) geometry = base
                },
                onTouched = onItemTouched,
            )
        }
    }
}

@Composable
private fun BoxScope.ResizeHandles(
    key: Long,
    base: GridGeometry,
    cellWidthPx: Int,
    cellHeightPx: Int,
    maxCols: Int,
    maxRows: Int,
    onPreview: (GridGeometry) -> Unit,
    onCommit: (GridGeometry) -> Unit,
    onTouched: () -> Unit,
) {
    val edge = -HANDLE_SIZE / 2
    ResizeHandleBox(
        key, ResizeHandle.TOP_LEFT, base,
        Modifier.align(Alignment.TopStart).offset(edge, edge),
        cellWidthPx, cellHeightPx, maxCols, maxRows, onPreview, onCommit, onTouched,
    )
    ResizeHandleBox(
        key, ResizeHandle.TOP, base,
        Modifier.align(Alignment.TopCenter).offset(y = edge),
        cellWidthPx, cellHeightPx, maxCols, maxRows, onPreview, onCommit, onTouched,
    )
    ResizeHandleBox(
        key, ResizeHandle.TOP_RIGHT, base,
        Modifier.align(Alignment.TopEnd).offset(x = -edge, y = edge),
        cellWidthPx, cellHeightPx, maxCols, maxRows, onPreview, onCommit, onTouched,
    )
    ResizeHandleBox(
        key, ResizeHandle.RIGHT, base,
        Modifier.align(Alignment.CenterEnd).offset(x = -edge),
        cellWidthPx, cellHeightPx, maxCols, maxRows, onPreview, onCommit, onTouched,
    )
    ResizeHandleBox(
        key, ResizeHandle.BOTTOM_RIGHT, base,
        Modifier.align(Alignment.BottomEnd).offset(x = -edge, y = -edge),
        cellWidthPx, cellHeightPx, maxCols, maxRows, onPreview, onCommit, onTouched,
    )
    ResizeHandleBox(
        key, ResizeHandle.BOTTOM, base,
        Modifier.align(Alignment.BottomCenter).offset(y = -edge),
        cellWidthPx, cellHeightPx, maxCols, maxRows, onPreview, onCommit, onTouched,
    )
    ResizeHandleBox(
        key, ResizeHandle.BOTTOM_LEFT, base,
        Modifier.align(Alignment.BottomStart).offset(x = edge, y = -edge),
        cellWidthPx, cellHeightPx, maxCols, maxRows, onPreview, onCommit, onTouched,
    )
    ResizeHandleBox(
        key, ResizeHandle.LEFT, base,
        Modifier.align(Alignment.CenterStart).offset(x = edge),
        cellWidthPx, cellHeightPx, maxCols, maxRows, onPreview, onCommit, onTouched,
    )
}

@Composable
private fun ResizeHandleBox(
    key: Long,
    handle: ResizeHandle,
    base: GridGeometry,
    modifier: Modifier,
    cellWidthPx: Int,
    cellHeightPx: Int,
    maxCols: Int,
    maxRows: Int,
    onPreview: (GridGeometry) -> Unit,
    onCommit: (GridGeometry) -> Unit,
    onTouched: () -> Unit,
) {
    var start by remember(key, handle) { mutableStateOf(base) }
    var total by remember(key, handle) { mutableStateOf(Offset.Zero) }
    Box(
        modifier = modifier
            .size(HANDLE_SIZE)
            .background(
                MaterialTheme.colorScheme.primary,
                RoundedCornerShape(3.dp),
            )
            .pointerInput(
                key, handle, cellWidthPx, cellHeightPx, maxCols, maxRows,
                base,
            ) {
                detectDragGestures(
                    onDragStart = {
                        start = base
                        total = Offset.Zero
                        onTouched()
                    },
                    onDragEnd = {
                        onCommit(resized(start, total, handle, cellWidthPx, cellHeightPx, maxCols, maxRows))
                        total = Offset.Zero
                    },
                    onDragCancel = { total = Offset.Zero },
                ) { _, amount ->
                    total += amount
                    onPreview(resized(start, total, handle, cellWidthPx, cellHeightPx, maxCols, maxRows))
                }
            },
    )
}

private enum class ResizeHandle(
    val left: Boolean,
    val right: Boolean,
    val top: Boolean,
    val bottom: Boolean,
) {
    TOP_LEFT(true, false, true, false),
    TOP(false, false, true, false),
    TOP_RIGHT(false, true, true, false),
    RIGHT(false, true, false, false),
    BOTTOM_RIGHT(false, true, false, true),
    BOTTOM(false, false, false, true),
    BOTTOM_LEFT(true, false, false, true),
    LEFT(true, false, false, false),
}

private fun resized(
    start: GridGeometry,
    delta: Offset,
    handle: ResizeHandle,
    cellWidthPx: Int,
    cellHeightPx: Int,
    maxCols: Int,
    maxRows: Int,
): GridGeometry {
    val dx = (delta.x / cellWidthPx.toFloat()).roundToInt()
    val dy = (delta.y / cellHeightPx.toFloat()).roundToInt()
    var col = start.col
    var row = start.row
    var spanX = start.spanX
    var spanY = start.spanY
    if (handle.left) {
        val shift = dx.coerceIn(-start.col, start.spanX - 1)
        col = start.col + shift
        spanX = start.spanX - shift
    }
    if (handle.right) {
        spanX = (start.spanX + dx).coerceIn(1, (maxCols - col).coerceAtLeast(1))
    }
    if (handle.top) {
        val shift = dy.coerceIn(-start.row, start.spanY - 1)
        row = start.row + shift
        spanY = start.spanY - shift
    }
    if (handle.bottom) {
        spanY = (start.spanY + dy).coerceIn(1, (maxRows - row).coerceAtLeast(1))
    }
    return GridGeometry(col, row, spanX, spanY)
}

/**
 * A just-picked widget follows the finger over the dotted grid. A touch-down
 * starts tracking, movement updates the preview, and release asks the host to
 * commit; a rejected drop leaves the preview floating for another attempt.
 */
@Composable
private fun BoxScope.FloatingWidget(
    kind: BuiltInWidgetKind,
    cellWidth: Dp,
    cellHeight: Dp,
    cellWidthPx: Int,
    cellHeightPx: Int,
    corner: Shape,
    onTouched: () -> Unit,
    onDrop: (Int, Int) -> Boolean,
) {
    var at by remember(kind) { mutableStateOf<Offset?>(null) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(kind, cellWidthPx, cellHeightPx, onDrop) {
                // Simplified: drag on the grid to position, release to drop.
                // TODO: catch the in-flight long-press pointer handed off from picker.
                detectDragGestures(
                    onDragStart = { offset ->
                        onTouched()
                        at = offset
                    },
                    onDragEnd = {
                        val point = at ?: return@detectDragGestures
                        val col = (point.x / cellWidthPx - 0.5f).roundToInt()
                        val row = (point.y / cellHeightPx - 0.5f).roundToInt()
                        onDrop(col, row)
                    },
                    onDragCancel = { at = null },
                ) { change, _ ->
                    at = change.position
                }
            },
    ) {
        at?.let { point ->
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            point.x.roundToInt() - cellWidthPx / 2,
                            point.y.roundToInt() - cellHeightPx / 2,
                        )
                    }
                    .width(cellWidth)
                    .height(cellHeight)
                    .border(2.dp, MaterialTheme.colorScheme.primary, corner)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        corner,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = BuiltInWidgets.label(kind),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private val HANDLE_SIZE = 18.dp
private val ACTION_ZONE = 32.dp
private const val ITEM_TOUCH_WINDOW_NS = 300_000_000L
