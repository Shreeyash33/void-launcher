package com.ark.voidlauncher.ui.editor

import com.ark.voidlauncher.domain.EdgeInfo
import com.ark.voidlauncher.domain.PageInfo
import com.ark.voidlauncher.domain.PageLinks
import com.ark.voidlauncher.domain.SwipeDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorValidationLinkTest {

    private fun page(id: Long, x: Int, y: Int) = PageInfo(
        pageId = id,
        coordX = x,
        coordY = y,
        title = "Page $x,$y",
    )

    private fun bothRows(from: Long, direction: SwipeDirection, to: Long): List<EdgeInfo> =
        PageLinks.link(from, direction, to).toList()

    @Test
    fun `removing one link with both rows present keeps an alternative path`() {
        val pages = listOf(
            page(1, 0, 0), // home
            page(2, 1, 0),
            page(3, 0, 1),
            page(4, 1, 1),
        )
        val edges = bothRows(1, SwipeDirection.RIGHT, 2) +
            bothRows(1, SwipeDirection.DOWN, 3) +
            bothRows(2, SwipeDirection.DOWN, 4) +
            bothRows(3, SwipeDirection.RIGHT, 4)

        assertEquals(8, edges.size)
        // Both directed rows for the link about to be removed really exist.
        assertTrue(EdgeInfo(1, SwipeDirection.RIGHT, 2) in edges)
        assertTrue(EdgeInfo(2, SwipeDirection.LEFT, 1) in edges)

        // Page 2 still reaches home through 4 and 3, so nothing new is stranded.
        assertFalse(
            EditorValidation.linkChangeStrandsAdditionalPages(
                pages, edges, 1, LinkChange.RemoveLink(1, SwipeDirection.RIGHT, 2),
            ),
        )
    }

    @Test
    fun `removing the last link strands its previously connected page`() {
        val pages = listOf(page(1, 0, 0), page(2, 1, 0)) // home = 1
        val edges = bothRows(2, SwipeDirection.LEFT, 1)

        assertTrue(
            EditorValidation.linkChangeStrandsAdditionalPages(
                pages, edges, 1, LinkChange.RemoveLink(2, SwipeDirection.LEFT, 1),
            ),
        )
    }

    @Test
    fun `setting a link repairs an isolated page`() {
        val pages = listOf(page(1, 0, 0), page(2, 1, 0)) // home = 1

        assertFalse(
            EditorValidation.linkChangeStrandsAdditionalPages(
                pages, emptyList(), 1, LinkChange.SetLink(2, SwipeDirection.LEFT, 1),
            ),
        )
    }

    @Test
    fun `replacing a slot retires the old reverse before validating`() {
        val pages = listOf(page(1, 0, 0), page(2, 1, 0), page(3, 0, 1)) // home = 1
        val edges = bothRows(1, SwipeDirection.RIGHT, 2)

        // Page 2 loses its only link when the RIGHT slot is redirected to page 3.
        assertTrue(
            EditorValidation.linkChangeStrandsAdditionalPages(
                pages, edges, 1, LinkChange.SetLink(1, SwipeDirection.RIGHT, 3),
            ),
        )
    }

    @Test
    fun `link changes referencing unknown pages are rejected`() {
        val pages = listOf(page(1, 0, 0), page(2, 1, 0))
        val edges = bothRows(2, SwipeDirection.LEFT, 1)

        assertTrue(
            EditorValidation.linkChangeStrandsAdditionalPages(
                pages, edges, 1, LinkChange.SetLink(2, SwipeDirection.LEFT, 99),
            ),
        )
        assertTrue(
            EditorValidation.linkChangeStrandsAdditionalPages(
                pages, edges, 1, LinkChange.RemoveLink(99, SwipeDirection.LEFT, 1),
            ),
        )
    }
}
