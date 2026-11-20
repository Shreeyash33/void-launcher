package com.ark.voidlauncher.ui.editor

import com.ark.voidlauncher.domain.EdgeInfo
import com.ark.voidlauncher.domain.PageInfo
import com.ark.voidlauncher.domain.SwipeDirection
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorValidationTest {
    private fun page(id: Long, x: Int, y: Int) = PageInfo(
        pageId = id,
        coordX = x,
        coordY = y,
        title = "Page $x,$y",
    )

    @Test
    fun `enabling a link from an isolated page is allowed`() {
        val pages = listOf(page(1, 0, 0), page(2, 1, 0))
        val edge = EdgeInfo(2, SwipeDirection.LEFT, 1)

        assertTrue(EditorValidation.edgeChangeIsSafe(pages, emptyList(), 1, EdgeChange.SetEdge(edge)))
        assertFalse(
            EditorValidation.edgeChangeStrandsAdditionalPages(
                pages, emptyList(), 1, EdgeChange.SetEdge(edge),
            ),
        )
    }

    @Test
    fun `repairing one of several isolated pages is allowed`() {
        val pages = listOf(page(1, 0, 0), page(2, 1, 0), page(3, -1, 0))
        val edge = EdgeInfo(2, SwipeDirection.LEFT, 1)

        assertFalse(EditorValidation.edgeChangeIsSafe(pages, emptyList(), 1, EdgeChange.SetEdge(edge)))
        assertFalse(
            EditorValidation.edgeChangeStrandsAdditionalPages(
                pages, emptyList(), 1, EdgeChange.SetEdge(edge),
            ),
        )
    }

    @Test
    fun `removing the only path home is still rejected`() {
        val pages = listOf(page(1, 0, 0), page(2, 1, 0))
        val edges = listOf(EdgeInfo(2, SwipeDirection.LEFT, 1))

        assertTrue(
            EditorValidation.edgeChangeStrandsAdditionalPages(
                pages, edges, 1, EdgeChange.RemoveEdge(2, SwipeDirection.LEFT),
            ),
        )
    }
}
