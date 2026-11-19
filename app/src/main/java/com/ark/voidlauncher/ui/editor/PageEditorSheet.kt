package com.ark.voidlauncher.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ark.voidlauncher.domain.EdgeInfo
import com.ark.voidlauncher.domain.PageInfo
import com.ark.voidlauncher.domain.SwipeDirection

/**
 * Text-first page/edge editor. All state changes leave through callbacks; the caller
 * owns the page graph and decides when a proposed change is actually persisted.
 */
@Composable
fun PageEditorSheet(
    pages: List<PageInfo>,
    edges: List<EdgeInfo>,
    homePageId: Long,
    onAddPage: (coordX: Int, coordY: Int) -> Unit,
    onDeletePage: (pageId: Long) -> Unit,
    onSetEdge: (fromPageId: Long, direction: SwipeDirection, toPageId: Long) -> Unit,
    onRemoveEdge: (fromPageId: Long, direction: SwipeDirection) -> Unit,
    onSetHomePage: (pageId: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var coordX by remember { mutableStateOf("") }
    var coordY by remember { mutableStateOf("") }
    var addError by remember { mutableStateOf<String?>(null) }

    var sourceId by remember { mutableStateOf<Long?>(null) }
    var targetId by remember { mutableStateOf<Long?>(null) }
    var direction by remember { mutableStateOf(SwipeDirection.RIGHT) }

    fun submitAddPage() {
        val x = coordX.trim().toIntOrNull()
        val y = coordY.trim().toIntOrNull()
        if (x == null || y == null) {
            addError = "ENTER A NUMBER"
            return
        }
        if (x !in PageInfo.MIN_COORD..PageInfo.MAX_COORD ||
            y !in PageInfo.MIN_COORD..PageInfo.MAX_COORD
        ) {
            addError = "COORDINATES OUT OF BOUNDS (-10..10)"
            return
        }
        if (pages.any { it.coordX == x && it.coordY == y }) {
            addError = "PAGE ALREADY EXISTS AT THIS COORDINATE"
            return
        }
        onAddPage(x, y)
        coordX = ""
        coordY = ""
        addError = null
    }

    EditorSheetScaffold(title = "Page editor", onDismiss = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
        item { EditorSectionHeader("Add page") }
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                EditorCompactIntField(
                    value = coordX,
                    onValueChange = { coordX = it; addError = null },
                    label = "X",
                    modifier = Modifier.weight(1f),
                )
                EditorCompactIntField(
                    value = coordY,
                    onValueChange = { coordY = it; addError = null },
                    label = "Y",
                    modifier = Modifier.weight(1f),
                )
            }
            EditorTextAction(text = "ADD PAGE", onClick = { submitAddPage() })
            addError?.let { EditorHint(text = it, isError = true) }
        }

        item { EditorSectionHeader("Pages") }
        if (pages.isEmpty()) {
            item { EditorHint("NO PAGES YET") }
        }
        items(pages, key = { it.pageId }) { page ->
            Text(
                text = "${page.title.ifBlank { "PAGE ${page.pageId}" }}  (${page.coordX}, ${page.coordY})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                if (page.pageId == homePageId) {
                    EditorChip(text = "HOME", selected = true, onClick = {})
                } else {
                    EditorTextAction(
                        text = "SET HOME",
                        onClick = { onSetHomePage(page.pageId) },
                    )
                }
                EditorTextAction(
                    text = "DELETE",
                    onClick = { onDeletePage(page.pageId) },
                )
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                thickness = 0.5.dp,
            )
        }

        item { EditorSectionHeader("Set edge") }
        item {
            Text(
                text = "SOURCE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
            EditorChipRow {
                pages.forEach { page ->
                    EditorChip(
                        text = "${page.title.ifBlank { page.pageId.toString() }} (${page.pageId})",
                        selected = page.pageId == sourceId,
                        onClick = { sourceId = page.pageId },
                    )
                }
            }
            Text(
                text = "DIRECTION",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
            EditorChipRow {
                SwipeDirection.entries.forEach { option ->
                    EditorChip(
                        text = option.name,
                        selected = option == direction,
                        onClick = { direction = option },
                    )
                }
            }
            Text(
                text = "TARGET",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
            EditorChipRow {
                pages.forEach { page ->
                    EditorChip(
                        text = "${page.title.ifBlank { page.pageId.toString() }} (${page.pageId})",
                        selected = page.pageId == targetId,
                        onClick = { targetId = page.pageId },
                    )
                }
            }
        }
        item {
            val from = sourceId
            val to = targetId
            val setChange = if (from != null && to != null) {
                EdgeChange.SetEdge(EdgeInfo(from, direction, to))
            } else {
                null
            }
            val hasExistingEdge = edges.any {
                it.fromPageId == from && it.swipeDirection == direction
            }
            val removeChange = if (from != null && hasExistingEdge) {
                EdgeChange.RemoveEdge(from, direction)
            } else {
                null
            }
            val setSafe = setChange?.let {
                EditorValidation.edgeChangeIsSafe(pages, edges, homePageId, it)
            } ?: false
            val removeSafe = removeChange?.let {
                EditorValidation.edgeChangeIsSafe(pages, edges, homePageId, it)
            } ?: false

            Row(modifier = Modifier.fillMaxWidth()) {
                EditorTextAction(
                    text = "SET EDGE",
                    enabled = setSafe,
                    onClick = {
                        val source = sourceId
                        val target = targetId
                        if (source != null && target != null) {
                            onSetEdge(source, direction, target)
                        }
                    },
                )
                EditorTextAction(
                    text = "REMOVE",
                    enabled = removeSafe,
                    onClick = {
                        val source = sourceId
                        if (source != null) onRemoveEdge(source, direction)
                    },
                )
            }
            when {
                setChange != null && !setSafe ->
                    EditorHint("THIS EDGE WOULD DISCONNECT A PAGE", isError = true)

                removeChange != null && !removeSafe ->
                    EditorHint("REMOVING THIS EDGE WOULD DISCONNECT A PAGE", isError = true)

                sourceId == null || targetId == null ->
                    EditorHint("PICK A SOURCE AND TARGET PAGE")
            }
        }

        item { EditorSectionHeader("Existing edges") }
        if (edges.isEmpty()) {
            item { EditorHint("NO EDGES YET") }
        }
        items(edges) { edge ->
            Text(
                text = "${edge.fromPageId}  --${edge.swipeDirection.name}-->  ${edge.toPageId}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
            val safe = EditorValidation.edgeChangeIsSafe(
                pages = pages,
                edges = edges,
                homePageId = homePageId,
                change = EdgeChange.RemoveEdge(edge.fromPageId, edge.swipeDirection),
            )
            EditorTextAction(
                text = "REMOVE",
                enabled = safe,
                onClick = { onRemoveEdge(edge.fromPageId, edge.swipeDirection) },
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                thickness = 0.5.dp,
            )
        }
        }
    }
}
