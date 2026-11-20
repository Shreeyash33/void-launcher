package com.ark.voidlauncher.data.repository

import androidx.room.withTransaction
import com.ark.voidlauncher.LauncherPrefs
import com.ark.voidlauncher.ThemeMode
import com.ark.voidlauncher.data.backup.BackupPayload
import com.ark.voidlauncher.domain.BuiltInWidgetKind
import com.ark.voidlauncher.domain.BuiltInWidgets
import com.ark.voidlauncher.data.db.AppCustomizationEntity
import com.ark.voidlauncher.data.db.GridItemEntity
import com.ark.voidlauncher.data.db.PageEdgeEntity
import com.ark.voidlauncher.data.db.PageEntity
import com.ark.voidlauncher.data.db.VoidDatabase
import com.ark.voidlauncher.data.db.toEdgeInfo
import com.ark.voidlauncher.data.db.toPageInfo
import com.ark.voidlauncher.domain.EdgeInfo
import com.ark.voidlauncher.domain.GridItemType
import com.ark.voidlauncher.domain.PageInfo
import com.ark.voidlauncher.domain.PageType
import com.ark.voidlauncher.domain.SwipeDirection
import com.ark.voidlauncher.domain.effectivePageType
import com.ark.voidlauncher.domain.isValidPageDestination
import com.ark.voidlauncher.domain.primaryNeighbour
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Result of a Room-only transaction whose preference write must happen after commit. */
private data class HomeUpdate(val ok: Boolean, val newCoord: Pair<Int, Int>?)

/** Single write surface for editor/canvas callbacks. Reads stay reactive. */
class LauncherRepository(
    private val database: VoidDatabase,
    private val prefs: LauncherPrefs,
) {
    /**
     * Page roles are derived, not trusted blindly: APP_DRAWER is authoritative,
     * the configured home coordinate is HOME, and everything else is a widget
     * page. This keeps old rows (stored as WIDGET) correct after a home change
     * without needing a rewrite on every preference update.
     */
    val pages: Flow<List<PageInfo>> = combine(
        database.pageDao().observeAll(),
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

    val homePageId: Flow<Long> = combine(homeCoord, pages) { (x, y), pages ->
        pages.firstOrNull { it.coordX == x && it.coordY == y }?.pageId
            ?: VoidDatabase.ORIGIN_PAGE_ID
    }

    /**
     * Creates a page with an explicit stored role and links it to the first existing
     * orthogonal neighbour by priority (LEFT, RIGHT, UP, DOWN). The link and the page
     * are written together so a new page is never left isolated by a partial insert.
     */
    suspend fun addPage(
        x: Int,
        y: Int,
        pageType: PageType = PageType.WIDGET,
    ): Long? {
        if (x !in PageInfo.MIN_COORD..PageInfo.MAX_COORD) return null
        if (y !in PageInfo.MIN_COORD..PageInfo.MAX_COORD) return null
        return database.withTransaction {
            if (database.pageDao().getByCoordOnce(x, y) != null) {
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
            val pages = database.pageDao().getAllOnce().map { it.toPageInfo() }
            primaryNeighbour(pages, x, y)?.let { (neighbour, direction) ->
                database.pageEdgeDao().insert(
                    PageEdgeEntity(id, direction, neighbour.pageId),
                )
            }
            id
        }
    }

    /**
     * Relocates a page while preserving its role. All old edges (incoming and
     * outgoing) are dropped and exactly one default outgoing edge is derived at the
     * destination; the effective HOME follows the moved page if it was home.
     */
    suspend fun movePage(pageId: Long, x: Int, y: Int): Boolean {
        val (homeX, homeY) = prefs.homeCoord.first()
        val result = database.withTransaction {
            val page = database.pageDao().getByIdOnce(pageId)
                ?: return@withTransaction HomeUpdate(false, null)
            val rows = database.pageDao().getAllOnce().map { it.toPageInfo() }
            if (!isValidPageDestination(rows, x, y, pageId)) {
                return@withTransaction HomeUpdate(false, null)
            }

            val wasHome = page.coordX == homeX && page.coordY == homeY
            database.pageEdgeDao().deleteInvolving(pageId)
            database.pageDao().update(page.copy(coordX = x, coordY = y))

            val others = rows.filterNot { it.pageId == pageId }
            primaryNeighbour(others, x, y)?.let { (neighbour, direction) ->
                database.pageEdgeDao().insert(
                    PageEdgeEntity(pageId, direction, neighbour.pageId),
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

    suspend fun setEdge(fromPageId: Long, direction: SwipeDirection, toPageId: Long) {
        database.pageEdgeDao().insert(
            PageEdgeEntity(
                fromPageId = fromPageId,
                swipeDirection = direction,
                toPageId = toPageId,
            ),
        )
    }

    suspend fun removeEdge(fromPageId: Long, direction: SwipeDirection) {
        val edge = database.pageEdgeDao().getEdgeOnce(fromPageId, direction) ?: return
        database.pageEdgeDao().delete(edge)
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
     * Replaces current state with a backup. Page ids are regenerated by Room, so
     * edges are remapped through the old-to-new id table and items are addressed
     * by stable page coordinates. Widgets become placeholders needing rebinding.
     */
    suspend fun restore(payload: BackupPayload) {
        database.withTransaction {
            database.pageDao().deleteAll()
            database.appCustomizationDao().deleteAll()

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
            }

            // Old backups predate page roles. Guarantee a drawer so APPS always
            // has a destination, reusing the seeded DOWN neighbour when free.
            if (database.pageDao().getAllOnce()
                .none { it.pageType == PageType.APP_DRAWER }
            ) {
                val x = VoidDatabase.APP_DRAWER_COORD_X
                val y = VoidDatabase.APP_DRAWER_COORD_Y
                val occupant = database.pageDao().getByCoordOnce(x, y)
                val drawerId = when {
                    occupant == null -> database.pageDao().insert(
                        PageEntity(
                            coordX = x, coordY = y, title = "App drawer",
                            pageType = PageType.APP_DRAWER,
                        ),
                    )
                    occupant.pageType != PageType.HOME -> {
                        database.pageDao().update(
                            occupant.copy(pageType = PageType.APP_DRAWER),
                        )
                        occupant.pageId
                    }
                    else -> null
                }
                if (drawerId != null) {
                    database.pageEdgeDao().insert(
                        PageEdgeEntity(
                            fromPageId = database.pageDao().getByCoordOnce(
                                payload.homeCoordX, payload.homeCoordY,
                            )?.pageId ?: drawerId,
                            swipeDirection = SwipeDirection.DOWN,
                            toPageId = drawerId,
                        ),
                    )
                }
            }

            payload.edges.forEach { edge ->
                val from = idMap[edge.fromPageId] ?: return@forEach
                val to = idMap[edge.toPageId] ?: return@forEach
                database.pageEdgeDao().insert(
                    PageEdgeEntity(from, edge.swipeDirection, to),
                )
            }

            payload.items.forEach { item ->
                val pageId = database.pageDao().getByCoordOnce(
                    item.pageCoordX, item.pageCoordY,
                )?.pageId ?: return@forEach
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
        }
        setHomeCoord(payload.homeCoordX, payload.homeCoordY)
        runCatching { prefs.setTheme(ThemeMode.valueOf(payload.theme)) }
    }
}
