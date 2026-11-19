package com.ark.voidlauncher.ui.grid

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ark.voidlauncher.data.db.GridItemEntity
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
    onItemTouched: () -> Unit = {},
    onEmptyTap: () -> Unit = {},
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
            .pointerInput(editing, onEmptyTap) {
                if (!editing) return@pointerInput
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

        if (editing) {
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
                editing = editing,
                selected = editing && selectedItemId == item.itemId,
                onTap = {
                    markTouch()
                    if (editing) onSelectItem(item) else onAppClick(item.referenceId)
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
                widgetContent = widgetContent,
            )
        }
    }
}

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
    editing: Boolean,
    selected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onItemTouched: () -> Unit,
    onMove: (Int, Int) -> Boolean,
    widgetContent: @Composable (GridItemEntity) -> Unit,
) {
    var dragOffset by remember(item.itemId) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(item.itemId, item.col, item.row) {
        dragOffset = Offset.Zero
    }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (cellWidth * item.col).roundToPx() + dragOffset.x.roundToInt(),
                    (cellHeight * item.row).roundToPx() + dragOffset.y.roundToInt(),
                )
            }
            .width(cellWidth * item.colSpan.coerceAtLeast(1))
            .height(cellHeight * item.rowSpan.coerceAtLeast(1))
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
                .pointerInput(item.itemId, editing, onTap, onLongPress) {
                    detectTapGestures(
                        onTap = { onTap() },
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
    }
}

private const val ITEM_TOUCH_WINDOW_NS = 300_000_000L
