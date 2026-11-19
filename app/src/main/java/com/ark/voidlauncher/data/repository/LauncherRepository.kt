package com.ark.voidlauncher.data.repository

import androidx.room.withTransaction
import com.ark.voidlauncher.LauncherPrefs
import com.ark.voidlauncher.ThemeMode
import com.ark.voidlauncher.data.backup.BackupPayload
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
import com.ark.voidlauncher.domain.SwipeDirection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Single write surface for editor/canvas callbacks. Reads stay reactive. */
class LauncherRepository(
    private val database: VoidDatabase,
    private val prefs: LauncherPrefs,
) {
    val pages: Flow<List<PageInfo>> = database.pageDao().observeAll().map { rows ->
        rows.map { it.toPageInfo() }
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

    suspend fun addPage(x: Int, y: Int): Long? {
        if (x !in PageInfo.MIN_COORD..PageInfo.MAX_COORD) return null
        if (y !in PageInfo.MIN_COORD..PageInfo.MAX_COORD) return null
        if (database.pageDao().getByCoordOnce(x, y) != null) return null
        return database.pageDao().insert(
            PageEntity(coordX = x, coordY = y, title = "Page $x,$y"),
        )
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

    suspend fun moveItem(itemId: Long, col: Int, row: Int) {
        val item = database.gridItemDao().observeAll().first()
            .firstOrNull { it.itemId == itemId } ?: return
        database.gridItemDao().update(item.copy(col = col, row = row))
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
                val referenceId = if (item.itemType == GridItemType.WIDGET) {
                    "placeholder:${item.referenceId.removePrefix("placeholder:")}"
                } else {
                    item.referenceId
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
                    ),
                )
            }

            payload.customizations.forEach { database.appCustomizationDao().insert(it) }
        }
        setHomeCoord(payload.homeCoordX, payload.homeCoordY)
        runCatching { prefs.setTheme(ThemeMode.valueOf(payload.theme)) }
    }
}
