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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.awaitEachGesture
import androidx.compose.ui.input.pointer.awaitFirstDown
import androidx.compose.ui.input.pointer.awaitPointerEvent
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
 *
 * While editing, faint outlines show every item's stored extents above the
 * widget content, then the selected item's resize handles are drawn on top.
 * A floating preview may span [floatingSpanX] by [floatingSpanY] cells.
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
    floatingPoint: Offset? = null,
    floatingSpanX: Int = 1,
    floatingSpanY: Int = 1,
) {
    // Items mark every interaction; an empty tap that follows one is a tap on
    // an item that bubbled through, not a request to leave the editor.
    var lastItemTouch by remember { mutableStateOf(0L) }

    // The long press that opened the editor must not be read as an empty tap
    // when the finger is released.
    LaunchedEffect(editing) {
        if (editing) lastItemTouch = System.nanoTime()
    }

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

        // Shared live geometry keeps the item, its bounds and its handles in sync.
        val itemStates = remember { mutableMapOf<Long, ItemVisualState>() }

        items.forEach { item ->
            val state = itemStates.getOrPut(item.itemId) {
                ItemVisualState(GridGeometry(item.col, item.row, item.colSpan, item.rowSpan))
            }
            EditableGridItem(
                item = item,
                state = state,
                cellWidth = cellWidth,
                cellHeight = cellHeight,
                cellWidthPx = cellWidthPx,
                cellHeightPx = cellHeightPx,
                corner = corner,
                density = density,
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
                onWidgetAction = {
                    markTouch()
                    onWidgetAction(item)
                },
                widgetContent = widgetContent,
            )
        }

        // Stored extents sit above all widget content; this Canvas has no pointer
        // input, so it never steals a tap from an app row underneath it.
        if (editing) {
            ItemBoundsOverlay(
                items = items,
                cols = cols,
                rows = rows,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Handles come last so the faint bounds never hide their touch targets.
        if (editing) {
            val selectedItem = items.firstOrNull { it.itemId == selectedItemId }
            val selectedState = selectedItem?.let { itemStates[it.itemId] }
            if (selectedItem != null && selectedState != null) {
                val base = GridGeometry(
                    selectedItem.col,
                    selectedItem.row,
                    selectedItem.colSpan,
                    selectedItem.rowSpan,
                )
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (cellWidth * selectedState.geometry.col).roundToPx() +
                                    selectedState.dragOffset.x.roundToInt(),
                                (cellHeight * selectedState.geometry.row).roundToPx() +
                                    selectedState.dragOffset.y.roundToInt(),
                            )
                        }
                        .width(cellWidth * selectedState.geometry.spanX.coerceAtLeast(1))
                        .height(cellHeight * selectedState.geometry.spanY.coerceAtLeast(1)),
                ) {
                    ResizeHandles(
                        key = selectedItem.itemId,
                        base = base,
                        cellWidthPx = cellWidthPx,
                        cellHeightPx = cellHeightPx,
                        maxCols = cols,
                        maxRows = rows,
                        onPreview = { selectedState.geometry = it },
                        onCommit = { target ->
                            markTouch()
                            if (!onResizeItem(target)) selectedState.geometry = base
                        },
                        onTouched = ::markTouch,
                    )
                }
            }
        }

        if (floatingKind != null && floatingPoint != null) {
            FloatingWidget(
                kind = floatingKind,
                point = floatingPoint,
                cellWidth = cellWidth,
                cellHeight = cellHeight,
                cellWidthPx = cellWidthPx,
                cellHeightPx = cellHeightPx,
                corner = corner,
                spanX = floatingSpanX,
                spanY = floatingSpanY,
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

/** Live drag/resize geometry shared with the boundary overlay and resize handles. */
private class ItemVisualState(initial: GridGeometry) {
    var dragOffset by mutableStateOf(Offset.Zero)
    var geometry by mutableStateOf(initial)
}

/**
 * One item plus its transparent gesture layer. The drag delta lives in [state]
 * so the widget content, frame, bounds and selection outline move together.
 */
@Composable
private fun EditableGridItem(
    item: GridItemEntity,
    state: ItemVisualState,
    cellWidth: Dp,
    cellHeight: Dp,
    cellWidthPx: Int,
    cellHeightPx: Int,
    corner: Shape,
    density: Density,
    editing: Boolean,
    selected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onItemTouched: () -> Unit,
    onMove: (Int, Int) -> Boolean,
    onWidgetAction: () -> Unit,
    widgetContent: @Composable (GridItemEntity) -> Unit,
) {
    val base = GridGeometry(item.col, item.row, item.colSpan, item.rowSpan)
    val hasAction = item.widgetKind == BuiltInWidgetKind.APP_LIST ||
        BuiltInWidgets.fromReference(item.referenceId) == BuiltInWidgetKind.APP_LIST
    val boxWidth = cellWidth * state.geometry.spanX.coerceAtLeast(1)

    LaunchedEffect(item.itemId, item.col, item.row, item.colSpan, item.rowSpan) {
        state.dragOffset = Offset.Zero
        state.geometry = base
    }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (cellWidth * state.geometry.col).roundToPx() +
                        state.dragOffset.x.roundToInt(),
                    (cellHeight * state.geometry.row).roundToPx() +
                        state.dragOffset.y.roundToInt(),
                )
            }
            .width(cellWidth * state.geometry.spanX.coerceAtLeast(1))
            .height(cellHeight * state.geometry.spanY.coerceAtLeast(1))
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
                .pointerInput(item.itemId, hasAction, boxWidth, onTap, onLongPress, editing) {
                    if (!editing && hasAction) {
                        // APP_LIST rows in widgetContent own plain taps and the
                        // action zone. Only long press is handled here, without
                        // consuming the down/up of ordinary taps.
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val pointerId = down.id
                            val slop = viewConfiguration.touchSlop
                            // The scope's timeout is safe inside awaitEachGesture;
                            // null means the press survived to the long-press delay.
                            val longPressed = withTimeoutOrNull(
                                viewConfiguration.longPressTimeoutMillis,
                            ) {
                                var waiting = true
                                while (waiting) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val change = event.changes.firstOrNull { it.id == pointerId }
                                    waiting = change != null && change.pressed &&
                                        (change.position - down.position)
                                            .getDistanceSquared() <= slop * slop
                                }
                                false // Released or moved before the timeout.
                            } ?: true
                            if (longPressed) {
                                onLongPress()
                                down.consume()
                                var pressing = true
                                while (pressing) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val change = event.changes.firstOrNull { it.id == pointerId }
                                    change?.consume()
                                    pressing = change?.pressed == true
                                }
                            }
                        }
                    } else {
                        detectTapGestures(
                            onTap = { at ->
                                val xDp = with(density) { at.x.toDp() }
                                val yDp = with(density) { at.y.toDp() }
                                if (hasAction &&
                                    xDp >= boxWidth - ACTION_ZONE && yDp <= ACTION_ZONE
                                ) {
                                    onWidgetAction()
                                } else {
                                    onTap()
                                }
                            },
                            onLongPress = { onLongPress() },
                        )
                    }
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
                            state.dragOffset = Offset.Zero
                        },
                        onDragCancel = {
                            state.dragOffset = Offset.Zero
                        },
                    ) { _, amount ->
                        total += amount
                        state.dragOffset = total
                    }
                },
        )
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
    val edge = -HANDLE_TOUCH_SIZE / 2
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
            .size(HANDLE_TOUCH_SIZE)
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
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(HANDLE_VISUAL_SIZE)
                .background(
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(2.dp),
                ),
        )
    }
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
 * Preview for a widget picked up in the widget list. Pointer tracking lives at
 * the activity root so the same finger that long-pressed the picker entry can
 * keep moving the preview after the picker is replaced by the page.
 */
@Composable
private fun BoxScope.FloatingWidget(
    kind: BuiltInWidgetKind,
    point: Offset,
    cellWidth: Dp,
    cellHeight: Dp,
    cellWidthPx: Int,
    cellHeightPx: Int,
    corner: Shape,
    spanX: Int = 1,
    spanY: Int = 1,
) {
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    point.x.roundToInt() - cellWidthPx / 2,
                    point.y.roundToInt() - cellHeightPx / 2,
                )
            }
            .width(cellWidth * spanX.coerceAtLeast(1))
            .height(cellHeight * spanY.coerceAtLeast(1))
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

/** Touch target stays large enough to grab; the visible dot is much smaller. */
private val HANDLE_TOUCH_SIZE = 18.dp
private val HANDLE_VISUAL_SIZE = 10.dp
private val ACTION_ZONE = 32.dp
private const val ITEM_TOUCH_WINDOW_NS = 300_000_000L
