package com.ark.voidlauncher.ui.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.offset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ark.voidlauncher.data.db.GridItemEntity
import com.ark.voidlauncher.domain.GridItemType

/**
 * Renders one page's items on an adaptive [cols] x [rows] lattice.
 *
 * Cells are derived from the available space, so the grid scales from phones
 * to tablets without changing any stored coordinates. Items are placed at
 * their column/row with their span determining the box size; later items are
 * drawn on top, which is what makes overlapping widgets stack predictably.
 * Conflict prevention lives in PlacementResolver - this composable simply
 * draws the items it is given.
 */
@Composable
fun PageGrid(
    cols: Int,
    rows: Int,
    items: List<GridItemEntity>,
    onAppClick: (packageName: String) -> Unit,
    widgetContent: @Composable (GridItemEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        if (cols <= 0 || rows <= 0) return@BoxWithConstraints

        val cellWidth = maxWidth / cols
        val cellHeight = maxHeight / rows
        val corner = RoundedCornerShape(8.dp)

        items.forEach { item ->
            val itemColumns = item.colSpan.coerceAtLeast(1)
            val itemRows = item.rowSpan.coerceAtLeast(1)
            val itemWidth = cellWidth * itemColumns
            val itemHeight = cellHeight * itemRows
            val x = cellWidth * item.col
            val y = cellHeight * item.row

            Box(
                modifier = Modifier
                    .offset { IntOffset(x.roundToPx(), y.roundToPx()) }
                    .width(itemWidth)
                    .height(itemHeight),
            ) {
                when (item.itemType) {
                    GridItemType.WIDGET -> widgetContent(item)

                    GridItemType.APP_SHORTCUT -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(corner)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                shape = corner,
                            )
                            .clickable { onAppClick(item.referenceId) }
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
            }
        }
    }
}
