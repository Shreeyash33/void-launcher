package com.ark.voidlauncher.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PagePlacementTest {
    private fun page(x: Int, y: Int, id: Long = 0L) = PageInfo(
        pageId = id,
        coordX = x,
        coordY = y,
        title = "Page $x,$y",
    )

    @Test
    fun `empty graph offers the origin`() {
        assertEquals(listOf(0 to 0), nextPageCandidates(emptyList()))
    }

    @Test
    fun `single page offers its four orthogonal neighbours`() {
        assertEquals(
            setOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1),
            nextPageCandidates(listOf(page(0, 0))).toSet(),
        )
    }

    @Test
    fun `occupied cells and diagonal cells are excluded`() {
        val pages = listOf(page(0, 0, 1), page(1, 0, 2), page(0, 1, 3))
        val candidates = nextPageCandidates(pages)

        assert(1 to 0 !in candidates)
        assert(0 to 1 !in candidates)
        assert(-1 to 0 in candidates)
        assert(2 to 0 in candidates)
        assert(0 to 2 in candidates)
    }

    @Test
    fun `diagonal cells are excluded unless orthogonally adjacent`() {
        val pages = listOf(page(0, 0, 1), page(2, 0, 2))
        val candidates = nextPageCandidates(pages)

        assert(1 to 1 !in candidates)
        assert(1 to 0 in candidates)
    }

    @Test
    fun `candidates stay inside the coordinate bounds`() {
        val corners = listOf(
            page(PageInfo.MIN_COORD, PageInfo.MIN_COORD, 1),
            page(PageInfo.MAX_COORD, PageInfo.MAX_COORD, 2),
        )
        val candidates = nextPageCandidates(corners)

        assert(candidates.all { (x, y) ->
            x in PageInfo.MIN_COORD..PageInfo.MAX_COORD &&
                y in PageInfo.MIN_COORD..PageInfo.MAX_COORD
        })
        assert(PageInfo.MIN_COORD - 1 to PageInfo.MIN_COORD !in candidates)
        assert(PageInfo.MAX_COORD + 1 to PageInfo.MAX_COORD !in candidates)
    }

    @Test
    fun `duplicates are removed`() {
        val pages = listOf(page(0, 0, 1), page(2, 0, 2))
        val candidates = nextPageCandidates(pages)

        assertEquals(candidates.size, candidates.toSet().size)
        assertEquals(1 to 0, candidates.first { it.first == 1 })
    }

    @Test
    fun `primary neighbour uses stored deltas despite inverted map Y`() {
        val origin = page(0, 0, 1)
        val storedUp = page(0, -1, 2) // renders visually below the origin on the map

        val (neighbour, direction) =
            primaryNeighbour(listOf(origin, storedUp), storedUp.coordX, storedUp.coordY)!!
        assertEquals(origin.pageId, neighbour.pageId)
        assertEquals(SwipeDirection.DOWN, direction)
    }

    @Test
    fun `primary neighbour follows left right up down priority`() {
        val left = page(4, 5, 2)
        val right = page(6, 5, 3)
        val storedUp = page(5, 4, 4) // visually below the target
        val storedDown = page(5, 6, 5) // visually above the target

        assertEquals(
            SwipeDirection.LEFT,
            primaryNeighbour(listOf(left, right, storedUp, storedDown), 5, 5)!!.second,
        )
        assertEquals(
            SwipeDirection.RIGHT,
            primaryNeighbour(listOf(right, storedUp, storedDown), 5, 5)!!.second,
        )
        assertEquals(
            SwipeDirection.UP,
            primaryNeighbour(listOf(storedUp, storedDown), 5, 5)!!.second,
        )
        assertEquals(
            SwipeDirection.DOWN,
            primaryNeighbour(listOf(storedDown), 5, 5)!!.second,
        )
        assertEquals(storedDown.pageId, primaryNeighbour(listOf(storedDown), 5, 5)!!.first.pageId)
        assert(primaryNeighbour(emptyList(), 5, 5) == null)
    }

    @Test
    fun `stored direction deltas match neighbour coordinates`() {
        assertEquals(4 to 5, neighbourCoord(5, 5, SwipeDirection.LEFT))
        assertEquals(6 to 5, neighbourCoord(5, 5, SwipeDirection.RIGHT))
        assertEquals(5 to 4, neighbourCoord(5, 5, SwipeDirection.UP))
        assertEquals(5 to 6, neighbourCoord(5, 5, SwipeDirection.DOWN))
    }

    @Test
    fun `destination rejects bounds collisions and isolated cells`() {
        val anchor = page(0, 0, 1)
        val occupant = page(1, 0, 2)

        assert(isValidPageDestination(listOf(anchor), 1, 0))
        assert(!isValidPageDestination(listOf(anchor, occupant), 1, 0))
        assert(!isValidPageDestination(listOf(anchor), 11, 0))
        assert(!isValidPageDestination(listOf(anchor), 0, -11))
        assert(!isValidPageDestination(listOf(anchor), 5, 5))

        // A moving page is ignored both for occupancy and as a neighbour: its old
        // coordinate must not make the new destination look connected.
        assert(isValidPageDestination(listOf(anchor, occupant), 1, 0, movingPageId = 2))
        assert(!isValidPageDestination(listOf(anchor, page(5, 5, 2)), 1, 0, movingPageId = 1))
    }

    @Test
    fun `effective page type keeps drawer authoritative and home coordinate derived`() {
        assertEquals(PageType.APP_DRAWER, effectivePageType(PageType.APP_DRAWER, 0, 0, 0, 0))
        assertEquals(PageType.HOME, effectivePageType(PageType.WIDGET, 0, 0, 0, 0))
        assertEquals(PageType.WIDGET, effectivePageType(PageType.HOME, 2, 3, 0, 0))
        assertEquals(PageType.WIDGET, effectivePageType(PageType.WIDGET, 2, 3, 0, 0))
    }
}
