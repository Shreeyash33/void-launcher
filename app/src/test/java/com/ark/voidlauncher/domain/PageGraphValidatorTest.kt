package com.ark.voidlauncher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageGraphValidatorTest {

    private fun page(id: Long): PageInfo = PageInfo(
        pageId = id,
        coordX = 0,
        coordY = 0,
        title = "Page $id",
    )

    private fun edge(from: Long, to: Long): EdgeInfo = EdgeInfo(
        fromPageId = from,
        swipeDirection = SwipeDirection.RIGHT,
        toPageId = to,
    )

    @Test
    fun isolatedPageWithNoIncidentEdgesIsReportedAsHavingNoConnection() {
        val pages = listOf(page(1), page(2), page(3))
        val edges = listOf(edge(1, 2)) // 3 is isolated; 1 and 2 are incident.

        assertEquals(listOf(3L), PageGraphValidator.pagesWithoutConnection(pages, edges))
    }

    @Test
    fun oneWayDirectedPathToHomeIsConsideredReachableAndValid() {
        val pages = listOf(page(1), page(2), page(3))
        val edges = listOf(edge(1, 2), edge(2, 3)) // home = 3

        assertTrue(PageGraphValidator.pagesWithoutPathToHome(pages, edges, 3).isEmpty())
        assertTrue(PageGraphValidator.pagesWithoutConnection(pages, edges).isEmpty())

        val result = PageGraphValidator.validate(pages, edges, 3)
        assertTrue(result.isValid)
        assertEquals(emptyList<Long>(), result.pagesWithoutPathToHome)
    }

    @Test
    fun edgePointingAwayFromHomeLeavesPageWithoutPathToHome() {
        val pages = listOf(page(1), page(2)) // home = 1, edge goes 1 -> 2 only
        val edges = listOf(edge(1, 2))

        // Page 2 has an incoming edge, so it is "connected", but cannot get back home.
        assertTrue(PageGraphValidator.pagesWithoutConnection(pages, edges).isEmpty())
        assertEquals(listOf(2L), PageGraphValidator.pagesWithoutPathToHome(pages, edges, 1))
        assertFalse(PageGraphValidator.validate(pages, edges, 1).isValid)
    }

    @Test
    fun cycleWithAnExitToHomeIsFullyReachableAndValid() {
        val pages = listOf(page(1), page(2), page(3))
        val edges = listOf(
            edge(1, 2),
            edge(2, 1), // cycle between 1 and 2
            edge(2, 3), // exit from the cycle to home (3)
        )

        assertTrue(PageGraphValidator.pagesWithoutPathToHome(pages, edges, 3).isEmpty())
        assertTrue(PageGraphValidator.validate(pages, edges, 3).isValid)
    }

    @Test
    fun cycleWithoutAnExitToHomeLeavesItsPagesUnreachable() {
        val pages = listOf(page(1), page(2), page(3)) // home = 3, isolated from the cycle
        val edges = listOf(edge(1, 2), edge(2, 1))

        assertEquals(listOf(1L, 2L), PageGraphValidator.pagesWithoutPathToHome(pages, edges, 3))
        assertFalse(PageGraphValidator.validate(pages, edges, 3).isValid)
    }

    @Test
    fun duplicateEdgesDoNotChangeResultsAndOutputsAreSortedAscending() {
        // Deliberately unordered ids; 30 <-> 40 is a cycle with no exit.
        val pages = listOf(page(40), page(10), page(30), page(20), page(5))
        val edges = listOf(
            edge(10, 5),
            edge(10, 5), // duplicate of the line above
            edge(30, 40),
            edge(40, 30),
            edge(40, 30), // duplicate
        ) // home = 5

        val expectedNoConnection = listOf(20L)
        val expectedNoPath = listOf(20L, 30L, 40L)

        assertEquals(expectedNoConnection, PageGraphValidator.pagesWithoutConnection(pages, edges))
        assertEquals(expectedNoPath, PageGraphValidator.pagesWithoutPathToHome(pages, edges, 5))

        // Removing the duplicates yields identical results.
        val uniqueEdges = listOf(edge(10, 5), edge(30, 40), edge(40, 30))
        assertEquals(
            expectedNoConnection,
            PageGraphValidator.pagesWithoutConnection(pages, uniqueEdges),
        )
        assertEquals(
            expectedNoPath,
            PageGraphValidator.pagesWithoutPathToHome(pages, uniqueEdges, 5),
        )

        val result = PageGraphValidator.validate(pages, edges, 5)
        assertEquals(expectedNoConnection, result.pagesWithoutConnection)
        assertEquals(expectedNoPath, result.pagesWithoutPathToHome)
        assertFalse(result.isValid)
    }

    @Test
    fun emptyPageListReturnsEmptyResultsRegardlessOfEdges() {
        val strayEdges = listOf(edge(99, 100), edge(100, 99))

        assertEquals(emptyList<Long>(), PageGraphValidator.pagesWithoutConnection(emptyList(), strayEdges))
        assertEquals(
            emptyList<Long>(),
            PageGraphValidator.pagesWithoutPathToHome(emptyList(), strayEdges, 100),
        )

        val result = PageGraphValidator.validate(emptyList(), strayEdges, 100)
        assertEquals(emptyList<Long>(), result.pagesWithoutConnection)
        assertEquals(emptyList<Long>(), result.pagesWithoutPathToHome)
        assertTrue(result.isValid)
    }

    @Test
    fun homeIdMissingFromPagesReportsAllPagesWithoutPathToHome() {
        val pages = listOf(page(2), page(1))
        val edges = listOf(edge(1, 999)) // 999 is referenced but is not a page

        assertEquals(listOf(1L, 2L), PageGraphValidator.pagesWithoutPathToHome(pages, edges, 999))
        assertEquals(listOf(2L), PageGraphValidator.pagesWithoutConnection(pages, edges))
        assertFalse(PageGraphValidator.validate(pages, edges, 999).isValid)
    }

    @Test
    fun homePageItselfIsAlwaysReachableEvenWhenFullyIsolated() {
        val pages = listOf(page(7))
        val edges = emptyList<EdgeInfo>()

        // Home is isolated, so it is reported as "no connection", but never as
        // "no path to home".
        assertEquals(listOf(7L), PageGraphValidator.pagesWithoutConnection(pages, edges))
        assertTrue(PageGraphValidator.pagesWithoutPathToHome(pages, edges, 7).isEmpty())
    }
}
