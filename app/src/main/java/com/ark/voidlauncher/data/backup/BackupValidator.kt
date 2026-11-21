package com.ark.voidlauncher.data.backup

import com.ark.voidlauncher.ThemeMode
import com.ark.voidlauncher.domain.GridItemType
import com.ark.voidlauncher.domain.OrientationMode
import com.ark.voidlauncher.domain.PageGraphValidator
import com.ark.voidlauncher.domain.PageInfo
import com.ark.voidlauncher.domain.PageLinks
import com.ark.voidlauncher.domain.PageType
import com.ark.voidlauncher.domain.SwipeDirection
import com.ark.voidlauncher.domain.background.BackgroundKind
import com.ark.voidlauncher.domain.background.BackgroundScope

/** Outcome of a pre-import check. [errors] is deterministic and may contain many rows. */
data class BackupValidationResult(val errors: List<String>) {
    val isValid: Boolean get() = errors.isEmpty()
}

/**
 * Pure-JVM validation of a [BackupPayload] before it reaches the repository.
 *
 * No Android or Compose API is used: every check is a plain Kotlin comparison,
 * enum parse, or call into the pure domain helpers. Nothing here throws; an
 * unexpected failure is reported as a single error row.
 */
object BackupValidator {

    private val IMAGE_TOKEN = Regex("^[A-Za-z0-9._-]+$")

    fun validate(payload: BackupPayload): BackupValidationResult =
        BackupValidationResult(
            runCatching { collectErrors(payload) }
                .getOrDefault(listOf("VALIDATION FAILED")),
        )

    private fun collectErrors(payload: BackupPayload): List<String> {
        val errors = ArrayList<String>()
        val pages = payload.pages

        // 1. Version.
        if (payload.version != BackupManager.FORMAT_VERSION) {
            errors += "UNSUPPORTED VERSION ${payload.version}"
        }

        // 2. Pages.
        if (pages.isEmpty()) errors += "NO PAGES"
        val seenPageIds = HashSet<Long>()
        val seenCoords = HashSet<Pair<Int, Int>>()
        for (page in pages) {
            if (!seenPageIds.add(page.pageId)) {
                errors += "DUPLICATE PAGE ID ${page.pageId}"
            }
            if (!seenCoords.add(page.coordX to page.coordY)) {
                errors += "DUPLICATE PAGE COORD ${page.coordX},${page.coordY}"
            }
            if (page.coordX !in PageInfo.MIN_COORD..PageInfo.MAX_COORD ||
                page.coordY !in PageInfo.MIN_COORD..PageInfo.MAX_COORD
            ) {
                errors += "PAGE COORD OUT OF RANGE ${page.pageId} ${page.coordX},${page.coordY}"
            }
            if (page.gridCols <= 0) errors += "INVALID GRID COLS ${page.pageId}"
            if (page.gridRows <= 0) errors += "INVALID GRID ROWS ${page.pageId}"
        }

        // 3. Home.
        val homePage = pages.firstOrNull {
            it.coordX == payload.homeCoordX && it.coordY == payload.homeCoordY
        }
        if (homePage == null) {
            errors += "MISSING HOME PAGE ${payload.homeCoordX},${payload.homeCoordY}"
        } else if (homePage.pageType == PageType.APP_DRAWER) {
            errors += "HOME PAGE IS APP DRAWER ${homePage.pageId}"
        }

        // 4. Edges. Half-links are legal; restore expands them into pairs.
        val pageIds = pages.asSequence().map { it.pageId }.toSet()
        val slotTargets = HashMap<Pair<Long, SwipeDirection>, Long>()
        for (edge in payload.edges) {
            if (edge.fromPageId !in pageIds || edge.toPageId !in pageIds) {
                errors += "DANGLING EDGE ${edge.fromPageId} ${edge.swipeDirection.name} ${edge.toPageId}"
            }
            if (edge.fromPageId == edge.toPageId) {
                errors += "SELF EDGE ${edge.fromPageId}"
            }
            val slot = edge.fromPageId to edge.swipeDirection
            val firstTarget = slotTargets[slot]
            if (firstTarget == null) {
                slotTargets[slot] = edge.toPageId
            } else if (firstTarget != edge.toPageId) {
                errors += "CONFLICTING EDGE SLOT ${edge.fromPageId} ${edge.swipeDirection.name}"
            }
        }

        // 5. Graph, over the same bidirectional expansion restore performs.
        val normalizedEdges = payload.edges.flatMap { edge ->
            PageLinks.link(edge.fromPageId, edge.swipeDirection, edge.toPageId).toList()
        }
        val graph = PageGraphValidator.validate(
            pages,
            normalizedEdges,
            homePage?.pageId ?: -1L,
        )
        for (id in graph.pagesWithoutConnection) {
            errors += "PAGE WITHOUT CONNECTION $id"
        }
        for (id in graph.pagesWithoutPathToHome) {
            errors += "PAGE WITHOUT PATH TO HOME $id"
        }

        // 6. Items.
        val pageByCoord = pages.associateBy { it.coordX to it.coordY }
        for (item in payload.items) {
            val page = pageByCoord[item.pageCoordX to item.pageCoordY]
            if (page == null) {
                errors += "ITEM PAGE NOT FOUND ${item.pageCoordX},${item.pageCoordY}"
                continue
            }
            if (item.itemType != GridItemType.APP_SHORTCUT &&
                item.itemType != GridItemType.WIDGET
            ) {
                errors += "INVALID ITEM TYPE ${item.itemType.name}"
            }
            if (item.col <= 0) errors += "INVALID ITEM COL ${item.col}"
            if (item.row <= 0) errors += "INVALID ITEM ROW ${item.row}"
            if (item.colSpan <= 0) errors += "INVALID ITEM COL SPAN ${item.colSpan}"
            if (item.rowSpan <= 0) errors += "INVALID ITEM ROW SPAN ${item.rowSpan}"
            if (item.col.toLong() + item.colSpan > page.gridCols.toLong()) {
                errors += "ITEM EXCEEDS GRID COLS ${item.pageCoordX},${item.pageCoordY}"
            }
            if (item.row.toLong() + item.rowSpan > page.gridRows.toLong()) {
                errors += "ITEM EXCEEDS GRID ROWS ${item.pageCoordX},${item.pageCoordY}"
            }
            if (item.referenceId.isBlank()) {
                errors += "BLANK ITEM REFERENCE ${item.pageCoordX},${item.pageCoordY}"
            }
        }

        // 7. Customizations.
        for (customization in payload.customizations) {
            if (customization.packageName.isBlank()) {
                errors += "BLANK CUSTOMIZATION PACKAGE"
            }
        }

        // 8. Backgrounds.
        for (background in payload.backgrounds) {
            val scope = runCatching {
                enumValueOf<BackgroundScope>(background.scope)
            }.getOrNull()
            if (scope == null) errors += "INVALID BACKGROUND SCOPE ${background.scope}"

            val kind = runCatching {
                enumValueOf<BackgroundKind>(background.kind)
            }.getOrNull()
            if (kind == null) errors += "INVALID BACKGROUND KIND ${background.kind}"

            when (scope) {
                BackgroundScope.PAGE -> {
                    val x = background.pageCoordX
                    val y = background.pageCoordY
                    if (x == null || y == null) {
                        errors += "PAGE BACKGROUND MISSING COORDS"
                    } else if (pageByCoord[x to y] == null) {
                        errors += "PAGE BACKGROUND COORD NOT FOUND $x,$y"
                    }
                }
                BackgroundScope.GLOBAL -> {
                    if (background.pageCoordX != null || background.pageCoordY != null) {
                        errors += "GLOBAL BACKGROUND HAS PAGE COORDS"
                    }
                }
                null -> Unit
            }

            val token = background.imageToken
            if (token != null && (!IMAGE_TOKEN.matches(token) || token == "." || token == "..")) {
                errors += "INVALID IMAGE TOKEN $token"
            }
            val encoded = background.imageBase64
            if (encoded != null && encoded.isBlank()) {
                errors += "BLANK IMAGE BASE64"
            }
        }

        // 9. Preferences.
        if (runCatching { enumValueOf<ThemeMode>(payload.theme) }.getOrNull() == null) {
            errors += "INVALID THEME ${payload.theme}"
        }
        if (runCatching { enumValueOf<OrientationMode>(payload.orientation) }.getOrNull() == null) {
            errors += "INVALID ORIENTATION ${payload.orientation}"
        }

        // 10. Palette.
        payload.customPalette?.let { palette ->
            val colors = listOf(
                "BACKGROUND" to palette.background,
                "SURFACE" to palette.surface,
                "SURFACE VARIANT" to palette.surfaceVariant,
                "ON BACKGROUND" to palette.onBackground,
                "ON SURFACE" to palette.onSurface,
                "PRIMARY" to palette.primary,
                "SECONDARY" to palette.secondary,
                "OUTLINE" to palette.outline,
            )
            for ((name, value) in colors) {
                if (value.isBlank()) errors += "BLANK PALETTE COLOR $name"
            }
        }

        return errors
    }
}
