package com.ark.voidlauncher.ui.editor

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ark.voidlauncher.AppInfo
import com.ark.voidlauncher.data.db.GridItemEntity
import com.ark.voidlauncher.domain.PageInfo

/** Editor for the items placed on one page. Placement validation stays with the caller. */
@Composable
fun ItemEditorSheet(
    page: PageInfo,
    items: List<GridItemEntity>,
    apps: List<AppInfo>,
    onAddShortcut: (packageName: String, col: Int, row: Int) -> Unit,
    onAddWidget: () -> Unit,
    onMove: (itemId: Long, col: Int, row: Int) -> Unit,
    onResize: (itemId: Long, colSpan: Int, rowSpan: Int) -> Unit,
    onRemove: (itemId: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var addCol by remember { mutableStateOf("0") }
    var addRow by remember { mutableStateOf("0") }
    var addError by remember { mutableStateOf<String?>(null) }

    fun addShortcut(packageName: String) {
        val col = addCol.trim().toIntOrNull()
        val row = addRow.trim().toIntOrNull()
        if (col == null || row == null) {
            addError = "ENTER A NUMBER"
            return
        }
        onAddShortcut(packageName, col, row)
        addError = null
    }

    EditorSheetScaffold(title = "Items - ${page.title}", onDismiss = onDismiss) {
        item { EditorSectionHeader("Widget") }
        item {
            EditorHint("WIDGET PLACEMENT IS REQUESTED FROM THE SYSTEM PICKER")
            EditorTextAction(text = "REQUEST WIDGET", onClick = onAddWidget)
        }

        item { EditorSectionHeader("Shortcut target cell") }
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                EditorCompactIntField(
                    value = addCol,
                    onValueChange = { addCol = it; addError = null },
                    label = "COL",
                    modifier = Modifier.weight(1f),
                )
                EditorCompactIntField(
                    value = addRow,
                    onValueChange = { addRow = it; addError = null },
                    label = "ROW",
                    modifier = Modifier.weight(1f),
                )
            }
            addError?.let { EditorHint(text = it, isError = true) }
            EditorHint("TAP AN APP BELOW TO ADD IT AT THIS CELL")
        }

        item { EditorSectionHeader("Apps") }
        if (apps.isEmpty()) {
            item { EditorHint("NO APPS AVAILABLE") }
        }
        lazyItems(apps, key = { it.packageName }) { app ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                EditorTextAction(text = "ADD", onClick = { addShortcut(app.packageName) })
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                thickness = 0.5.dp,
            )
        }

        item { EditorSectionHeader("Items on this page") }
        if (items.isEmpty()) {
            item { EditorHint("NO ITEMS ON THIS PAGE") }
        }
        lazyItems(items, key = { it.itemId }) { gridItem ->
            ItemEditorRow(
                item = gridItem,
                onMove = onMove,
                onResize = onResize,
                onRemove = onRemove,
            )
        }
    }
}

@Composable
private fun ItemEditorRow(
    item: GridItemEntity,
    onMove: (itemId: Long, col: Int, row: Int) -> Unit,
    onResize: (itemId: Long, colSpan: Int, rowSpan: Int) -> Unit,
    onRemove: (itemId: Long) -> Unit,
) {
    var col by remember(item.itemId) { mutableStateOf(item.col.toString()) }
    var row by remember(item.itemId) { mutableStateOf(item.row.toString()) }
    var colSpan by remember(item.itemId) { mutableStateOf(item.colSpan.toString()) }
    var rowSpan by remember(item.itemId) { mutableStateOf(item.rowSpan.toString()) }
    var error by remember(item.itemId) { mutableStateOf<String?>(null) }

    Text(
        text = "${item.itemType.name}  ${item.referenceId}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = "COL ${item.col}  ROW ${item.row}  SPAN ${item.colSpan}x${item.rowSpan}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
    )
    Row(modifier = Modifier.fillMaxWidth()) {
        EditorCompactIntField(
            value = col,
            onValueChange = { col = it; error = null },
            label = "COL",
            modifier = Modifier.weight(1f),
        )
        EditorCompactIntField(
            value = row,
            onValueChange = { row = it; error = null },
            label = "ROW",
            modifier = Modifier.weight(1f),
        )
        EditorCompactIntField(
            value = colSpan,
            onValueChange = { colSpan = it; error = null },
            label = "W",
            modifier = Modifier.weight(1f),
        )
        EditorCompactIntField(
            value = rowSpan,
            onValueChange = { rowSpan = it; error = null },
            label = "H",
            modifier = Modifier.weight(1f),
        )
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        EditorTextAction(
            text = "APPLY",
            onClick = {
                val newCol = col.trim().toIntOrNull()
                val newRow = row.trim().toIntOrNull()
                val newColSpan = colSpan.trim().toIntOrNull()
                val newRowSpan = rowSpan.trim().toIntOrNull()
                if (newCol == null || newRow == null || newColSpan == null || newRowSpan == null) {
                    error = "ENTER A NUMBER"
                } else {
                    if (newCol != item.col || newRow != item.row) {
                        onMove(item.itemId, newCol, newRow)
                    }
                    val width = newColSpan.coerceAtLeast(1)
                    val height = newRowSpan.coerceAtLeast(1)
                    if (width != item.colSpan || height != item.rowSpan) {
                        onResize(item.itemId, width, height)
                    }
                    error = null
                }
            },
        )
        EditorTextAction(text = "REMOVE", onClick = { onRemove(item.itemId) })
    }
    error?.let { EditorHint(text = it, isError = true) }
    HorizontalDivider(
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        thickness = 0.5.dp,
    )
}
