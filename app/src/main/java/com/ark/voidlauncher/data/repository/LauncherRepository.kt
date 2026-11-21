package com.ark.voidlauncher.data.repository

import androidx.room.withTransaction
import com.ark.voidlauncher.LauncherPrefs
import com.ark.voidlauncher.ThemeMode
import com.ark.voidlauncher.data.backup.BackupItemNormalizer
import com.ark.voidlauncher.data.backup.BackupPayload
import com.ark.voidlauncher.domain.BuiltInWidgetKind
import com.ark.voidlauncher.domain.BuiltInWidgets
import com.ark.voidlauncher.domain.DefaultLayout
import com.ark.voidlauncher.data.db.AppCustomizationEntity
import com.ark.voidlauncher.data.db.GridItemEntity
import com.ark.voidlauncher.data.db.PageEdgeEntity
import com.ark.voidlauncher.data.db.PageEntity
import com.ark.voidlauncher.data.db.VoidDatabase
import com.ark.voidlauncher.data.db.toEdgeInfo
import com.ark.voidlauncher.data.db.toPageInfo
import com.ark.voidlauncher.domain.EdgeInfo
import com.ark.voidlauncher.domain.GridItemType
import com.ark.voidlauncher.domain.PageGraphChanges
import com.ark.voidlauncher.domain.PageInfo
import com.ark.voidlauncher.domain.PageLinks
import com.ark.voidlauncher.domain.PageType
import com.ark.voidlauncher.domain.SwipeDirection
import com.ark.voidlauncher.domain.effectivePageType
import com.ark.voidlauncher.domain.isValidPageDestination
import com.ark.voidlauncher.domain.primaryNeighbour
import com.ark.voidlauncher.domain.ResetCleanup
import com.ark.voidlauncher.domain.ResetExecution
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Diagnostics from a backup restore. */
data class RestoreResult(
    val skippedEdges: Int = 0,
    val skippedItems: Int = 0,
    val synthesizedPages: Int = 0,
    val linkedPairs: Int = 0,
)

/** Result of a Room-only transaction whose preference write must happen after commit. */
private data class HomeUpdate(val ok: Boolean, val newCoord: Pair<Int, Int>?)

/** Negative sentinel used only to validate graph mutations before Room assigns an id. */
private const val TEMP_PAGE_ID = -1L

/** Single write surface for editor/canvas callbacks. Reads stay reactive. */
class LauncherRepository(
    private val database: VoidDatabase,
    private val prefs: LauncherPrefs,
    private val releaseAppWidget: (Int) -> Unit = {},
    private val deleteBackgroundImage: suspend (String) -> Unit = {},
    private val readAppWidgetIds: suspend () -> List<Int> = { emptyList() },
) {
    /** Raw rows shared by derived pages and HOME resolution, independent of prefs. */
    private val pageRows: Flow<List<PageEntity>> = database.pageDao().observeAll()

    /**
     * Page roles are derived, not trusted blindly: APP_DRAWER is authoritative,
     * the configured home coordinate is HOME, and everything else is a widget
     * page. This keeps old rows (stored as WIDGET) correct after a home change
     * without needing a rewrite on every preference update.
     */
    val pages: Flow<List<PageInfo>> = combine(
        pageRows,
        prefs.homeCoord,
    ) { rows, (homeX, homeY) ->
        rows.map { row ->
            val effective = effectivePageType(
                row.pageType, row.coordX, row.coordY, homeX, homeY,
            )
            row.toPageInfo().copy(pageType = effective)
        }
    }

    val appDrawerPageId: Flow<Long?> = pages.map { list ->
        list.firstOrNull { it.pageType == PageType.APP_DRAWER }?.pageId
    }

    val edges: Flow<List<EdgeInfo>> = database.pageEdgeDao().observeAll().map { rows ->
        rows.map { it.toEdgeInfo() }
    }

    val items: Flow<List<GridItemEntity>> = database.gridItemDao().observeAll()

    val customizations: Flow<List<AppCustomizationEntity>> =
        database.appCustomizationDao().observeAll()

    val homeCoord: Flow<Pair<Int, Int>> = prefs.homeCoord

    /**
     * Resolve HOME from raw rows so a stale coordinate cannot be reinforced by the
     * derived roles. The generated id from a reset is never assumed here.
     */
    val homePageId: Flow<Long> = combine(homeCoord, pageRows) { (x, y), rows ->
        rows.firstOrNull { it.coordX == x && it.coordY == y }?.pageId
            ?: rows.firstOrNull { it.pageType == PageType.HOME }?.pageId
            ?: rows.firstOrNull {
                it.coordX == DefaultLayout.home.coordX &&
                    it.coordY == DefaultLayout.home.coordY
            }?.pageId
            ?: rows.firstOrNull()?.pageId
            ?: VoidDatabase.ORIGIN_PAGE_ID
    }

    /**
     * Creates a page with an explicit stored role and a bidirectional default link
     * to the first existing orthogonal neighbour (LEFT, RIGHT, UP, DOWN priority).
     * The first page may be isolated; every later page must be adjacent and may not
     * strand an existing page. Page and both directed edge rows commit together.
     */
    suspend fun addPage(
        x: Int,
        y: Int,
        pageType: PageType = PageType.WIDGET,
    ): Long? {
        if (x !in PageInfo.MIN_COORD..PageInfo.MAX_COORD) return null
        if (y !in PageInfo.MIN_COORD..PageInfo.MAX_COORD) return null
        val homeId = homePageId.first()
        return database.withTransaction {
            val currentPages = database.pageDao().getAllOnce().map { it.toPageInfo() }
            if (currentPages.any { it.coordX == x && it.coordY == y }) {
                return@withTransaction null
            }
            if (currentPages.isNotEmpty() && !isValidPageDestination(currentPages, x, y)) {
                return@withTransaction null
            }

            val currentEdges = database.pageEdgeDao().getAllOnce().map { it.toEdgeInfo() }
            val defaultLink = primaryNeighbour(currentPages, x, y)
                ?.let { (neighbour, direction) -> PageLinks.link(TEMP_PAGE_ID, direction, neighbour.pageId) }
            val tempPage = PageInfo(
                pageId = TEMP_PAGE_ID,
                coordX = x,
                coordY = y,
                title = "Page $x,$y",
                pageType = pageType,
            )
            val afterPages = currentPages + tempPage
            val afterEdges = currentEdges + defaultLink?.toList().orEmpty()
            if (currentPages.isNotEmpty() &&
                PageGraphChanges.strandsAdditionalPages(
                    currentPages, currentEdges, afterPages, afterEdges, homeId,
                )
            ) {
                return@withTransaction null
            }

            val id = database.pageDao().insert(
                PageEntity(
                    coordX = x,
                    coordY = y,
                    title = "Page $x,$y",
                    pageType = pageType,
                ),
            )
            defaultLink?.let { (forward, _) ->
                database.pageEdgeDao().insert(
                    PageEdgeEntity(id, forward.swipeDirection, forward.toPageId),
                )
                database.pageEdgeDao().insert(
                    PageEdgeEntity(
                        forward.toPageId,
                        PageLinks.opposite(forward.swipeDirection),
                        id,
                    ),
                )
            }
            id
        }
    }

    /**
     * Relocates a page while preserving its role. All old edges are dropped and one
     * bidirectional default link is derived at the destination. The move is rejected
     * if it would strand an additional page; the effective HOME follows the moved
     * page if it was home.
     */
    suspend fun movePage(pageId: Long, x: Int, y: Int): Boolean {
        val (homeX, homeY) = prefs.homeCoord.first()
        val homeId = homePageId.first()
        val result = database.withTransaction {
            val page = database.pageDao().getByIdOnce(pageId)
                ?: return@withTransaction HomeUpdate(false, null)
            val rows = database.pageDao().getAllOnce().map { it.toPageInfo() }
            if (!isValidPageDestination(rows, x, y, pageId)) {
                return@withTransaction HomeUpdate(false, null)
            }

            val edgesBefore = database.pageEdgeDao().getAllOnce().map { it.toEdgeInfo() }
            val others = rows.filterNot { it.pageId == pageId }
            val defaultLink = primaryNeighbour(others, x, y)
                ?.let { (neighbour, direction) -> PageLinks.link(pageId, direction, neighbour.pageId) }
            val afterPages = others + page.toPageInfo().copy(coordX = x, coordY = y)
            val afterEdges = edgesBefore.filterNot {
                it.fromPageId == pageId || it.toPageId == pageId
            } + defaultLink?.toList().orEmpty()
            if (PageGraphChanges.strandsAdditionalPages(
                    rows, edgesBefore, afterPages, afterEdges, homeId,
                )
            ) {
                return@withTransaction HomeUpdate(false, null)
            }

            val wasHome = page.coordX == homeX && page.coordY == homeY
            database.pageEdgeDao().deleteInvolving(pageId)
            database.pageDao().update(page.copy(coordX = x, coordY = y))
            defaultLink?.let { (forward, _) ->
                database.pageEdgeDao().insert(
                    PageEdgeEntity(pageId, forward.swipeDirection, forward.toPageId),
                )
                database.pageEdgeDao().insert(
                    PageEdgeEntity(
                        forward.toPageId,
                        PageLinks.opposite(forward.swipeDirection),
                        pageId,
                    ),
                )
            }
            HomeUpdate(ok = true, newCoord = if (wasHome) x to y else null)
        }
        result.newCoord?.let { (newX, newY) -> prefs.setHomeCoord(newX, newY) }
        return result.ok
    }

    /**
     * Promotes a widget page to HOME. The previous HOME is found through the stored
     * home coordinate (roles are derived, not authoritative) and demoted to WIDGET.
     * Drawer pages can never become HOME.
     */
    suspend fun setHomePage(pageId: Long): Boolean {
        val (homeX, homeY) = prefs.homeCoord.first()
        val result = database.withTransaction {
            val target = database.pageDao().getByIdOnce(pageId)
                ?: return@withTransaction HomeUpdate(false, null)
            if (target.pageType == PageType.APP_DRAWER) {
                return@withTransaction HomeUpdate(false, null)
            }

            val previous = database.pageDao().getByCoordOnce(homeX, homeY)
            if (previous != null && previous.pageId != target.pageId &&
                previous.pageType != PageType.APP_DRAWER
            ) {
                database.pageDao().update(previous.copy(pageType = PageType.WIDGET))
            }
            database.pageDao().update(target.copy(pageType = PageType.HOME))
            HomeUpdate(true, target.coordX to target.coordY)
        }
        result.newCoord?.let { (newX, newY) -> prefs.setHomeCoord(newX, newY) }
        return result.ok
    }

    suspend fun deletePage(pageId: Long) {
        val page = database.pageDao().getByIdOnce(pageId) ?: return
        // The seeded origin is the safe fallback when a configured home page is removed.
        if (page.coordX == VoidDatabase.ORIGIN_COORD_X &&
            page.coordY == VoidDatabase.ORIGIN_COORD_Y
        ) return
        database.pageDao().delete(page)
    }

    /**
     * Writes a user-visible link as its two directed rows in one transaction.
     * Returns false when either endpoint is not an existing page.
     */
    suspend fun setLink(fromPageId: Long, direction: SwipeDirection, toPageId: Long): Boolean {
        val (outgoing, reverse) = PageLinks.link(fromPageId, direction, toPageId)
        return database.withTransaction {
            if (database.pageDao().getByIdOnce(fromPageId) == null ||
                database.pageDao().getByIdOnce(toPageId) == null
            ) {
                return@withTransaction false
            }
            // Replacing a slot must retire both the old outgoing row and every
            // reverse-direction row pointing back into this page. Without the
            // second pass, legacy half-links from a different neighbour survive.
            database.pageEdgeDao().getEdgeOnce(fromPageId, direction)?.let { previous ->
                database.pageEdgeDao().delete(previous)
            }
            database.pageEdgeDao()
                .getIncomingByDirectionOnce(fromPageId, PageLinks.opposite(direction))
                .filter { it.fromPageId != toPageId }
                .forEach { stale -> database.pageEdgeDao().delete(stale) }
            listOf(outgoing, reverse).forEach { edge ->
                database.pageEdgeDao().insert(
                    PageEdgeEntity(edge.fromPageId, edge.swipeDirection, edge.toPageId),
                )
            }
            true
        }
    }

    /** Deletes both directed rows of a link atomically. Missing rows are harmless. */
    suspend fun removeLink(fromPageId: Long, direction: SwipeDirection, toPageId: Long): Boolean {
        val (outgoing, reverse) = PageLinks.link(fromPageId, direction, toPageId)
        database.withTransaction {
            listOf(outgoing, reverse).forEach { edge ->
                database.pageEdgeDao().delete(
                    PageEdgeEntity(edge.fromPageId, edge.swipeDirection, edge.toPageId),
                )
            }
        }
        return true
    }

    /** Renames a page, falling back to its coordinate when the supplied title is blank. */
    suspend fun renamePage(pageId: Long, title: String): Boolean = database.withTransaction {
        val page = database.pageDao().getByIdOnce(pageId) ?: return@withTransaction false
        val trimmed = title.trim()
        database.pageDao().update(
            page.copy(title = trimmed.ifBlank { "Page ${page.coordX},${page.coordY}" }),
        )
        true
    }

    suspend fun setHomeCoord(x: Int, y: Int) {
        if (x !in PageInfo.MIN_COORD..PageInfo.MAX_COORD) return
        if (y !in PageInfo.MIN_COORD..PageInfo.MAX_COORD) return
        // The drawer is a dedicated destination; it must never become HOME.
        if (database.pageDao().getByCoordOnce(x, y)?.pageType == PageType.APP_DRAWER) return
        prefs.setHomeCoord(x, y)
    }

    suspend fun addShortcut(packageName: String, pageId: Long, col: Int, row: Int): Long =
        database.gridItemDao().insert(
            GridItemEntity(
                pageId = pageId,
                itemType = GridItemType.APP_SHORTCUT,
                col = col,
                row = row,
                referenceId = packageName,
            ),
        )

    suspend fun addWidget(
        appWidgetId: Int,
        pageId: Long,
        col: Int,
        row: Int,
        colSpan: Int,
        rowSpan: Int,
    ): Long = database.gridItemDao().insert(
        GridItemEntity(
            pageId = pageId,
            itemType = GridItemType.WIDGET,
            col = col,
            row = row,
            colSpan = colSpan,
            rowSpan = rowSpan,
            referenceId = appWidgetId.toString(),
            allowOverlap = true,
        ),
    )

    /** Creates a 1×1 built-in widget. Placement is resolved by the caller. */
    suspend fun addBuiltInWidget(
        kind: BuiltInWidgetKind,
        pageId: Long,
        col: Int,
        row: Int,
    ): Long = database.gridItemDao().insert(
        GridItemEntity(
            pageId = pageId,
            itemType = GridItemType.WIDGET,
            col = col,
            row = row,
            referenceId = BuiltInWidgets.reference(kind),
            allowOverlap = true,
            widgetKind = kind,
        ),
    )

    suspend fun setAppListPackages(itemId: Long, packages: List<String>) {
        val item = database.gridItemDao().observeAll().first()
            .firstOrNull { it.itemId == itemId } ?: return
        if (item.widgetKind != BuiltInWidgetKind.APP_LIST) return
        database.gridItemDao().update(
            item.copy(appListPackages = BuiltInWidgets.encodePackages(packages)),
        )
    }

    suspend fun moveItem(itemId: Long, col: Int, row: Int) {
        val item = database.gridItemDao().observeAll().first()
            .firstOrNull { it.itemId == itemId } ?: return
        database.gridItemDao().update(item.copy(col = col, row = row))
    }

    /** Full placement update used by direct canvas editing, including one-hop page changes. */
    suspend fun placeItem(
        itemId: Long,
        pageId: Long,
        col: Int,
        row: Int,
        colSpan: Int,
        rowSpan: Int,
    ) {
        val item = database.gridItemDao().observeAll().first()
            .firstOrNull { it.itemId == itemId } ?: return
        database.gridItemDao().update(
            item.copy(
                pageId = pageId,
                col = col,
                row = row,
                colSpan = colSpan,
                rowSpan = rowSpan,
            ),
        )
    }

    suspend fun resizeItem(itemId: Long, colSpan: Int, rowSpan: Int) {
        val item = database.gridItemDao().observeAll().first()
            .firstOrNull { it.itemId == itemId } ?: return
        database.gridItemDao().update(item.copy(colSpan = colSpan, rowSpan = rowSpan))
    }

    suspend fun removeItem(itemId: Long) {
        val item = database.gridItemDao().observeAll().first()
            .firstOrNull { it.itemId == itemId } ?: return
        database.gridItemDao().delete(item)
    }

    suspend fun removeWidget(appWidgetId: Int) {
        val id = appWidgetId.toString()
        database.gridItemDao().observeByReference(GridItemType.WIDGET, id).first()
            .forEach { database.gridItemDao().delete(it) }
    }

    suspend fun customizeApp(customization: AppCustomizationEntity) {
        database.appCustomizationDao().insert(customization)
    }

    /**
     * Replaces all user state with the canonical fresh-install layout. [ResetExecution]
     * captures host/file cleanup data before Room, then releases resources and clears
     * preferences only after the single Room transaction commits.
     */
    suspend fun resetToDefault(): Long {
        val execution = ResetExecution(
            readCleanup = {
                val hostIds = readAppWidgetIds()
                val referencedIds = database.gridItemDao().getAllOnce()
                    .filter { it.itemType == GridItemType.WIDGET && it.widgetKind == null }
                    .mapNotNull { it.referenceId.removePrefix("placeholder:").toIntOrNull() }
                ResetCleanup(
                    appWidgetIds = (hostIds + referencedIds).distinct(),
                    backgroundImageTokens = database.backgroundDao().getAllOnce()
                        .mapNotNull { it.imageToken },
                )
            },
            commitRoom = {
                database.withTransaction {
                    database.pageEdgeDao().deleteAll()
                    database.gridItemDao().deleteAll()
                    database.appCustomizationDao().deleteAll()
                    database.backgroundDao().deleteAll()
                    database.pageDao().deleteAll()

                    val ids = mutableMapOf<Pair<Int, Int>, Long>()
                    DefaultLayout.pages.forEach { page ->
                        ids[page.coordX to page.coordY] = database.pageDao().insert(
                            PageEntity(
                                coordX = page.coordX,
                                coordY = page.coordY,
                                title = page.title,
                                pageType = page.pageType,
                            ),
                        )
                    }
                    DefaultLayout.edges.forEach { edge ->
                        database.pageEdgeDao().insert(
                            PageEdgeEntity(
                                fromPageId = ids.getValue(edge.fromX to edge.fromY),
                                swipeDirection = edge.direction,
                                toPageId = ids.getValue(edge.toX to edge.toY),
                            ),
                        )
                    }
                    ids.getValue(DefaultLayout.home.coordX to DefaultLayout.home.coordY)
                }
            },
            releaseAppWidget = { appWidgetId -> releaseAppWidget(appWidgetId) },
            deleteBackgroundImage = { token -> deleteBackgroundImage(token) },
            writeHomeCoord = {
                prefs.setHomeCoord(DefaultLayout.home.coordX, DefaultLayout.home.coordY)
            },
            clearPreferences = { prefs.reset() },
        )
        return execution.execute()
    }

    /**
     * Replaces current state with a backup. Page ids are regenerated by Room, so
     * edges are remapped through the old-to-new id table and items are addressed
     * by stable page coordinates. Widgets become placeholders needing rebinding.
     */
    suspend fun restore(payload: BackupPayload): RestoreResult {
        val result = database.withTransaction {
            database.pageDao().deleteAll()
            database.appCustomizationDao().deleteAll()

            var synthesizedPages = 0
            var skippedEdges = 0
            var skippedItems = 0

            val idMap = mutableMapOf<Long, Long>()
            payload.pages.forEach { page ->
                val newId = database.pageDao().insert(
                    PageEntity(
                        coordX = page.coordX,
                        coordY = page.coordY,
                        title = page.title,
                        gridCols = page.gridCols,
                        gridRows = page.gridRows,
                        isSecret = page.isSecret,
                        pageType = page.pageType,
                    ),
                )
                idMap[page.pageId] = newId
            }
            if (database.pageDao().getByCoordOnce(
                    VoidDatabase.ORIGIN_COORD_X, VoidDatabase.ORIGIN_COORD_Y,
                ) == null
            ) {
                val originId = database.pageDao().insert(
                    PageEntity(
                        coordX = VoidDatabase.ORIGIN_COORD_X,
                        coordY = VoidDatabase.ORIGIN_COORD_Y,
                        title = PageEntity.ORIGIN_TITLE,
                    ),
                )
                idMap.putIfAbsent(VoidDatabase.ORIGIN_PAGE_ID, originId)
                synthesizedPages++
            }

            // Every logical link contributes two directed rows. Collect them once so
            // a payload that already contains the reverse row is not written twice.
            val restoredEdges = linkedSetOf<EdgeInfo>()

            // Old backups predate page roles. Guarantee a drawer so APPS always
            // has a destination, reusing the seeded DOWN neighbour when free.
            if (database.pageDao().getAllOnce()
                .none { it.pageType == PageType.APP_DRAWER }
            ) {
                val x = VoidDatabase.APP_DRAWER_COORD_X
                val y = VoidDatabase.APP_DRAWER_COORD_Y
                val occupant = database.pageDao().getByCoordOnce(x, y)
                val drawerId = when {
                    occupant == null -> {
                        synthesizedPages++
                        database.pageDao().insert(
                            PageEntity(
                                coordX = x, coordY = y, title = "App drawer",
                                pageType = PageType.APP_DRAWER,
                            ),
                        )
                    }
                    occupant.pageType != PageType.HOME -> {
                        database.pageDao().update(
                            occupant.copy(pageType = PageType.APP_DRAWER),
                        )
                        occupant.pageId
                    }
                    else -> null
                }
                if (drawerId != null) {
                    // A failed HOME lookup (or a HOME that became the drawer) must
                    // not create a drawer-to-drawer self-link; the origin page is a
                    // distinct fallback so the drawer stays reachable.
                    val sourceId = database.pageDao().getByCoordOnce(
                        payload.homeCoordX, payload.homeCoordY,
                    )?.pageId
                        ?.takeIf { it != drawerId }
                        ?: database.pageDao().getByCoordOnce(
                            VoidDatabase.ORIGIN_COORD_X, VoidDatabase.ORIGIN_COORD_Y,
                        )?.pageId
                            ?.takeIf { it != drawerId }
                    if (sourceId != null) {
                        PageLinks.link(sourceId, SwipeDirection.DOWN, drawerId)
                            .toList()
                            .forEach { restoredEdges.add(it) }
                    }
                }
            }

            payload.edges.forEach { edge ->
                val from = idMap[edge.fromPageId]
                val to = idMap[edge.toPageId]
                if (from == null || to == null || from == to) {
                    skippedEdges++
                    return@forEach
                }
                PageLinks.link(from, edge.swipeDirection, to).toList()
                    .forEach { restoredEdges.add(it) }
            }
            restoredEdges.forEach { edge ->
                database.pageEdgeDao().insert(
                    PageEdgeEntity(edge.fromPageId, edge.swipeDirection, edge.toPageId),
                )
            }

            payload.items.forEach { rawItem ->
                val item = BackupItemNormalizer.normalize(rawItem)
                val pageId = database.pageDao().getByCoordOnce(
                    item.pageCoordX, item.pageCoordY,
                )?.pageId
                if (pageId == null) {
                    skippedItems++
                    return@forEach
                }
                val referenceId = when {
                    // Built-ins carry a stable sentinel and need no rebind.
                    item.widgetKind != null -> item.referenceId
                    item.itemType == GridItemType.WIDGET ->
                        "placeholder:${item.referenceId.removePrefix("placeholder:")}"
                    else -> item.referenceId
                }
                database.gridItemDao().insert(
                    GridItemEntity(
                        pageId = pageId,
                        itemType = item.itemType,
                        col = item.col,
                        row = item.row,
                        colSpan = item.colSpan,
                        rowSpan = item.rowSpan,
                        referenceId = referenceId,
                        allowOverlap = item.allowOverlap,
                        widgetKind = item.widgetKind,
                        appListPackages = item.appListPackages,
                    ),
                )
            }

            payload.customizations.forEach { database.appCustomizationDao().insert(it) }

            RestoreResult(
                skippedEdges = skippedEdges,
                skippedItems = skippedItems,
                synthesizedPages = synthesizedPages,
                linkedPairs = restoredEdges.size / 2,
            )
        }
        setHomeCoord(payload.homeCoordX, payload.homeCoordY)
        runCatching { prefs.setTheme(ThemeMode.valueOf(payload.theme)) }
        return result
    }
}
