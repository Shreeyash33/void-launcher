package com.ark.voidlauncher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageLinksTest {

    @Test
    fun oppositeSwapsBothAxisPairs() {
        assertEquals(SwipeDirection.DOWN, PageLinks.opposite(SwipeDirection.UP))
        assertEquals(SwipeDirection.UP, PageLinks.opposite(SwipeDirection.DOWN))
        assertEquals(SwipeDirection.RIGHT, PageLinks.opposite(SwipeDirection.LEFT))
        assertEquals(SwipeDirection.LEFT, PageLinks.opposite(SwipeDirection.RIGHT))
    }

    @Test
    fun linkReturnsOutgoingEdgeAndItsReverse() {
        assertEquals(
            EdgeInfo(10L, SwipeDirection.RIGHT, 20L) to
                EdgeInfo(20L, SwipeDirection.LEFT, 10L),
            PageLinks.link(10L, SwipeDirection.RIGHT, 20L),
        )
        assertEquals(
            EdgeInfo(3L, SwipeDirection.UP, 4L) to
                EdgeInfo(4L, SwipeDirection.DOWN, 3L),
            PageLinks.link(3L, SwipeDirection.UP, 4L),
        )
    }

    @Test
    fun withoutLinkRemovesBothDirectedRowsButKeepsUnrelatedEdges() {
        val outgoing = EdgeInfo(10L, SwipeDirection.RIGHT, 20L)
        val reverse = EdgeInfo(20L, SwipeDirection.LEFT, 10L)
        val unrelated = listOf(
            EdgeInfo(10L, SwipeDirection.LEFT, 20L),
            EdgeInfo(20L, SwipeDirection.RIGHT, 10L),
            EdgeInfo(20L, SwipeDirection.UP, 30L),
        )
        val edges = listOf(outgoing) + unrelated + listOf(reverse)

        assertEquals(
            unrelated,
            PageLinks.withoutLink(edges, 10L, SwipeDirection.RIGHT, 20L),
        )
        // The input is never mutated.
        assertEquals(5, edges.size)
    }

    @Test
    fun withoutLinkLeavesTheListUntouchedWhenNoRowMatches() {
        val edges = listOf(EdgeInfo(10L, SwipeDirection.UP, 20L))

        assertEquals(
            edges,
            PageLinks.withoutLink(edges, 10L, SwipeDirection.DOWN, 20L),
        )
    }

    @Test
    fun isCompleteIsTrueWhenBothDirectedRowsExist() {
        val outgoing = EdgeInfo(10L, SwipeDirection.RIGHT, 20L)
        val reverse = EdgeInfo(20L, SwipeDirection.LEFT, 10L)

        assertTrue(
            PageLinks.isComplete(
                listOf(outgoing, reverse),
                10L,
                SwipeDirection.RIGHT,
                20L,
            ),
        )
    }

    @Test
    fun isCompleteIsFalseForOnlyTheForwardRow() {
        val outgoing = EdgeInfo(10L, SwipeDirection.RIGHT, 20L)

        assertFalse(
            PageLinks.isComplete(
                listOf(outgoing),
                10L,
                SwipeDirection.RIGHT,
                20L,
            ),
        )
    }

    @Test
    fun isCompleteIsFalseForOnlyTheReverseRow() {
        val reverse = EdgeInfo(20L, SwipeDirection.LEFT, 10L)

        assertFalse(
            PageLinks.isComplete(
                listOf(reverse),
                10L,
                SwipeDirection.RIGHT,
                20L,
            ),
        )
    }

    @Test
    fun isCompleteIsFalseForFullPairPointingToADifferentNeighbour() {
        val edges = listOf(
            EdgeInfo(10L, SwipeDirection.RIGHT, 30L),
            EdgeInfo(30L, SwipeDirection.LEFT, 10L),
        )

        assertFalse(
            PageLinks.isComplete(
                edges,
                10L,
                SwipeDirection.RIGHT,
                20L,
            ),
        )
    }
}
