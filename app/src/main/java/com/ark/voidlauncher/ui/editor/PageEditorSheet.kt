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
import com.ark.voidlauncher.domain.PageLinks
import com.ark.voidlauncher.domain.PageType
import com.ark.voidlauncher.domain.SwipeDirection
import com.ark.voidlauncher.domain.displayTitle

/**
 * Text-first page/edge editor. All state changes leave through callbacks; the caller
 * owns the page graph and decides when a proposed change is actually persisted.
 */
@Composable
fun PageEditorSheet(
    pages: List<PageInfo>,
    edges: List<EdgeInfo>,
    homePageId: Long,
    onDeletePage: (pageId: Long) -> Unit,
    onRename: (pageId: Long, title: String) -> Unit = { _, _ -> },
    onSetLink: (fromPageId: Long, direction: SwipeDirection, toPageId: Long) -> Unit,
    onRemoveLink: (fromPageId: Long, direction: SwipeDirection, toPageId: Long) -> Unit,
    onSetHomePage: (pageId: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var sourceId by remember { mutableStateOf<Long?>(null) }
    var targetId by remember { mutableStateOf<Long?>(null) }
    var direction by remember { mutableStateOf(SwipeDirection.RIGHT) }
    var renamePageId by remember { mutableStateOf<Long?>(null) }
    var renameText by remember { mutableStateOf("") }

    EditorSheetScaffold(title = "Page editor", onDismiss = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
        item { EditorSectionHeader("Pages") }
        if (pages.isEmpty()) {
            item { EditorHint("NO PAGES YET") }
        }
        items(pages, key = { it.pageId }) { page ->
            Text(
                text = page.displayTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "(${page.coordX}, ${page.coordY})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                when {
                    page.pageId == homePageId ->
                        EditorChip(text = "HOME", selected = true, onClick = {})

                    page.pageType == PageType.APP_DRAWER ->
                        EditorTextAction(
                            text = "HOME UNAVAILABLE",
                            enabled = false,
                            onClick = {},
                        )

                    else ->
                        EditorTextAction(
                            text = "SET HOME",
                            onClick = { onSetHomePage(page.pageId) },
                        )
                }
                EditorTextAction(
                    text = "RENAME",
                    onClick = {
                        renamePageId = page.pageId
                        renameText = page.title
                    },
                )
                EditorTextAction(
                    text = "DELETE",
                    onClick = { onDeletePage(page.pageId) },
                )
            }
            if (renamePageId == page.pageId) {
                EditorTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = "Title",
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    EditorTextAction(
                        text = "SAVE TITLE",
                        onClick = {
                            onRename(page.pageId, renameText)
                            renamePageId = null
                        },
                    )
                    EditorTextAction(
                        text = "CANCEL",
                        onClick = { renamePageId = null },
                    )
                }
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                thickness = 0.5.dp,
            )
        }

        item { EditorSectionHeader("Set link") }
        item {
            Text(
                text = "SOURCE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
            EditorChipRow {
                pages.forEach { page ->
                    EditorChip(
                        text = "${page.displayTitle} (${page.pageId})",
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
                        text = "${page.displayTitle} (${page.pageId})",
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
                LinkChange.SetLink(from, direction, to)
            } else {
                null
            }
            val removeChange = if (from != null && to != null &&
                PageLinks.isComplete(edges, from, direction, to)
            ) {
                LinkChange.RemoveLink(from, direction, to)
            } else {
                null
            }
            val setSafe = setChange?.let {
                !EditorValidation.linkChangeStrandsAdditionalPages(pages, edges, homePageId, it)
            } ?: false
            val removeSafe = removeChange?.let {
                !EditorValidation.linkChangeStrandsAdditionalPages(pages, edges, homePageId, it)
            } ?: false

            Row(modifier = Modifier.fillMaxWidth()) {
                EditorTextAction(
                    text = "SET LINK",
                    enabled = setSafe,
                    onClick = {
                        val source = sourceId
                        val target = targetId
                        if (source != null && target != null) {
                            onSetLink(source, direction, target)
                        }
                    },
                )
                EditorTextAction(
                    text = "REMOVE",
                    enabled = removeSafe,
                    onClick = {
                        val source = sourceId
                        val target = targetId
                        if (source != null && target != null) {
                            onRemoveLink(source, direction, target)
                        }
                    },
                )
            }
            when {
                setChange != null && !setSafe ->
                    EditorHint("THIS LINK WOULD DISCONNECT A PAGE", isError = true)

                removeChange != null && !removeSafe ->
                    EditorHint("REMOVING THIS LINK WOULD DISCONNECT A PAGE", isError = true)

                sourceId == null || targetId == null ->
                    EditorHint("PICK A SOURCE AND TARGET PAGE")
            }
        }

        item { EditorSectionHeader("Existing links") }
        if (edges.isEmpty()) {
            item { EditorHint("NO LINKS YET") }
        }
        items(edges) { edge ->
            Text(
                text = "${edge.fromPageId}  --${edge.swipeDirection.name}-->  ${edge.toPageId}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
            val safe = !EditorValidation.linkChangeStrandsAdditionalPages(
                pages = pages,
                edges = edges,
                homePageId = homePageId,
                change = LinkChange.RemoveLink(
                    edge.fromPageId, edge.swipeDirection, edge.toPageId,
                ),
            )
            EditorTextAction(
                text = "REMOVE LINK",
                enabled = safe,
                onClick = {
                    onRemoveLink(edge.fromPageId, edge.swipeDirection, edge.toPageId)
                },
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                thickness = 0.5.dp,
            )
        }
        }
    }
}
