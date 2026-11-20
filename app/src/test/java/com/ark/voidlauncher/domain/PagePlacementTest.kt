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
        assert(1 to 1 !in candidates)
        assert(-1 to 0 in candidates)
        assert(2 to 0 in candidates)
        assert(0 to 2 in candidates)
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
}
