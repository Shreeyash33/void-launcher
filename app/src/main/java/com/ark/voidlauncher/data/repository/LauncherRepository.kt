package com.ark.voidlauncher.data.repository

import com.ark.voidlauncher.LauncherPrefs
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
}
