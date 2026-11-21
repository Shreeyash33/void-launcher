package com.ark.voidlauncher.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageGraphChangesTest {

    private fun page(id: Long, coordX: Int = 0, coordY: Int = 0): PageInfo = PageInfo(
        pageId = id,
        coordX = coordX,
        coordY = coordY,
        title = "Page $id",
    )

    private fun edge(from: Long, to: Long): EdgeInfo = EdgeInfo(
        fromPageId = from,
        swipeDirection = SwipeDirection.RIGHT,
        toPageId = to,
    )

    /** A complete bidirectional link: both directed rows are present. */
    private fun link(from: Long, to: Long): List<EdgeInfo> = listOf(
        edge(from, to),
        edge(to, from),
    )

    @Test
    fun addingPageWithCompleteLinkToValidGraphIsSafe() {
        // Before: home 1 <-> 2 is fully connected with a path to home.
        val beforePages = listOf(page(1), page(2))
        val beforeEdges = link(1, 2)

        // After: new page 3 is added with a complete bidirectional link to home.
        val afterPages = beforePages + page(3)
        val afterEdges = beforeEdges + link(1, 3)

        assertFalse(
            PageGraphChanges.strandsAdditionalPages(
                beforePages,
                beforeEdges,
                afterPages,
                afterEdges,
                1L,
            ),
        )
    }

    @Test
    fun addingIsolatedPageStrandsIt() {
        val beforePages = listOf(page(1), page(2))
        val beforeEdges = link(1, 2)

        // Page 3 is added with no incident edges at all.
        val afterPages = beforePages + page(3)
        val afterEdges = beforeEdges

        assertTrue(
            PageGraphChanges.strandsAdditionalPages(
                beforePages,
                beforeEdges,
                afterPages,
                afterEdges,
                1L,
            ),
        )
    }

    @Test
    fun movingPageThatPreservesConnectivityAndPathToHomeIsSafe() {
        // Before: 3 reaches home 1 through 2 via 3 -> 2 -> 1.
        val beforePages = listOf(page(1), page(2), page(3, coordX = 0, coordY = 1))
        val beforeEdges = link(1, 2) + link(2, 3)

        // After: page 3 is moved (new coordinate) and relinked straight to home,
        // so it still has an incident edge and a directed path to home.
        val afterPages = listOf(page(1), page(2), page(3, coordX = 2, coordY = 0))
        val afterEdges = link(1, 2) + link(1, 3)

        assertFalse(
            PageGraphChanges.strandsAdditionalPages(
                beforePages,
                beforeEdges,
                afterPages,
                afterEdges,
                1L,
            ),
        )
    }

    @Test
    fun movingPageThatRemovesOnlyPathToHomeIsUnsafe() {
        // Before: 3's only route to home 1 is 3 -> 2 -> 1.
        val beforePages = listOf(page(1), page(2, coordX = 0, coordY = 1), page(3))
        val beforeEdges = link(1, 2) + link(2, 3)

        // After: page 2 is moved away. Page 3 still has an incoming edge (so it
        // is not isolated), but the reverse row 3 -> 2 is gone, leaving 3 with
        // no directed path to home any more.
        val afterPages = listOf(page(1), page(2, coordX = 4, coordY = 4), page(3))
        val afterEdges = link(1, 2) + listOf(edge(2, 3))

        assertTrue(
            PageGraphChanges.strandsAdditionalPages(
                beforePages,
                beforeEdges,
                afterPages,
                afterEdges,
                1L,
            ),
        )
    }

    @Test
    fun alreadyInvalidGraphIsNotRejectedWhenNoAdditionalPageIsStranded() {
        // Before: page 4 is already stranded (no incident edge, no path to home).
        val beforePages = listOf(page(1), page(2), page(3), page(4))
        val beforeEdges = link(1, 2) + link(1, 3)

        // After: an extra page 5 is added with a complete link to home. Page 4
        // remains stranded exactly as before, so no *additional* page is stranded.
        val afterPages = beforePages + page(5)
        val afterEdges = beforeEdges + link(1, 5)

        assertFalse(
            PageGraphChanges.strandsAdditionalPages(
                beforePages,
                beforeEdges,
                afterPages,
                afterEdges,
                1L,
            ),
        )
    }
}
