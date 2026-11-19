package com.ark.voidlauncher.domain.placement

import com.ark.voidlauncher.data.db.GridItemEntity
import com.ark.voidlauncher.domain.GridItemType
import com.ark.voidlauncher.domain.SwipeDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacementResolverTest {

    private fun item(
        id: Long = 0,
        type: GridItemType = GridItemType.APP_SHORTCUT,
        col: Int = 0,
        row: Int = 0,
        colSpan: Int = 1,
        rowSpan: Int = 1,
        allowOverlap: Boolean = false,
        pageId: Long = 1,
    ) = GridItemEntity(
        itemId = id,
        pageId = pageId,
        itemType = type,
        col = col,
        row = row,
        colSpan = colSpan,
        rowSpan = rowSpan,
        referenceId = "ref$id",
        allowOverlap = allowOverlap,
    )

    @Test
    fun freePlacementIsAccepted() {
        val result = PlacementResolver.resolve(
            candidate = item(id = 10, col = 2, row = 3),
            existing = emptyList(),
            cols = 10,
            rows = 20,
        )

        assertEquals(PlacementResult.Ok, result)
    }

    @Test
    fun adjacentItemsDoNotConflict() {
        val existing = item(id = 1, col = 2, row = 3, colSpan = 2, rowSpan = 2)
        val candidate = item(id = 2, col = 4, row = 3) // touches the right edge, shares no cell

        assertEquals(
            PlacementResult.Ok,
            PlacementResolver.resolve(candidate, listOf(existing), 10, 20),
        )
    }

    @Test
    fun shortcutOverlappingShortcutIsRejected() {
        val existing = item(id = 1, col = 2, row = 3)
        val candidate = item(id = 2, col = 2, row = 3)

        assertTrue(PlacementResolver.resolve(candidate, listOf(existing), 10, 20) is PlacementResult.Rejected)
    }

    @Test
    fun shortcutOverlappingWidgetIsRejected() {
        val existing = item(id = 1, type = GridItemType.WIDGET, col = 2, row = 3, colSpan = 2, rowSpan = 2)
        val candidate = item(id = 2, col = 3, row = 3)

        assertTrue(PlacementResolver.resolve(candidate, listOf(existing), 10, 20) is PlacementResult.Rejected)
    }

    @Test
    fun widgetOverlappingWidgetIsAllowedWithFlag() {
        val existing = item(id = 1, type = GridItemType.WIDGET, col = 2, row = 3, colSpan = 2, rowSpan = 2)
        val candidate = item(
            id = 2,
            type = GridItemType.WIDGET,
            col = 3,
            row = 4,
            colSpan = 2,
            rowSpan = 2,
            allowOverlap = true,
        )

        assertEquals(
            PlacementResult.Ok,
            PlacementResolver.resolve(candidate, listOf(existing), 10, 20),
        )
    }

    @Test
    fun widgetOverlappingWidgetIsRejectedWithoutFlag() {
        val existing = item(id = 1, type = GridItemType.WIDGET, col = 2, row = 3, colSpan = 2, rowSpan = 2)
        val candidate = item(
            id = 2,
            type = GridItemType.WIDGET,
            col = 3,
            row = 4,
            colSpan = 2,
            rowSpan = 2,
            allowOverlap = false,
        )

        assertTrue(PlacementResolver.resolve(candidate, listOf(existing), 10, 20) is PlacementResult.Rejected)
    }

    @Test
    fun widgetOverlappingShortcutIsRejectedEvenWithFlag() {
        val existing = item(id = 1, type = GridItemType.APP_SHORTCUT, col = 2, row = 3)
        val candidate = item(
            id = 2,
            type = GridItemType.WIDGET,
            col = 2,
            row = 3,
            colSpan = 2,
            rowSpan = 2,
            allowOverlap = true,
        )

        assertTrue(PlacementResolver.resolve(candidate, listOf(existing), 10, 20) is PlacementResult.Rejected)
    }

    @Test
    fun overflowPastRightEdgeMovesRight() {
        val candidate = item(col = 9, row = 2, colSpan = 2, rowSpan = 1)

        assertEquals(
            PlacementResult.MoveToNeighbor(SwipeDirection.RIGHT),
            PlacementResolver.resolve(candidate, emptyList(), 10, 20),
        )
    }

    @Test
    fun overflowPastBottomEdgeMovesDown() {
        val candidate = item(col = 2, row = 19, colSpan = 1, rowSpan = 2)

        assertEquals(
            PlacementResult.MoveToNeighbor(SwipeDirection.DOWN),
            PlacementResolver.resolve(candidate, emptyList(), 10, 20),
        )
    }

    @Test
    fun negativeColumnMovesLeft() {
        val candidate = item(col = -1, row = 2)

        assertEquals(
            PlacementResult.MoveToNeighbor(SwipeDirection.LEFT),
            PlacementResolver.resolve(candidate, emptyList(), 10, 20),
        )
    }

    @Test
    fun negativeRowMovesUp() {
        val candidate = item(col = 2, row = -1)

        assertEquals(
            PlacementResult.MoveToNeighbor(SwipeDirection.UP),
            PlacementResolver.resolve(candidate, emptyList(), 10, 20),
        )
    }

    @Test
    fun overflowIsRejectedWithOneHopOnly() {
        // Even a wildly out-of-range candidate reports exactly one directed hop;
        // the resolver never chains into a second page resolution.
        val candidate = item(col = -25, row = 0)

        assertEquals(
            PlacementResult.MoveToNeighbor(SwipeDirection.LEFT),
            PlacementResolver.resolve(candidate, emptyList(), 10, 20),
        )
    }

    @Test
    fun spanLargerThanGridIsRejected() {
        val candidate = item(col = 0, row = 0, colSpan = 11, rowSpan = 1)

        assertTrue(PlacementResolver.resolve(candidate, emptyList(), 10, 20) is PlacementResult.Rejected)
    }

    @Test
    fun movingAnItemIgnoresItsOwnPreviousCell() {
        val previous = item(id = 7, col = 2, row = 3)
        val moved = item(id = 7, col = 2, row = 3) // same id, same cell

        assertEquals(
            PlacementResult.Ok,
            PlacementResolver.resolve(moved, listOf(previous), 10, 20),
        )
    }
}
