package com.ark.voidlauncher.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks the paging convention: dragging toward a screen edge reveals the neighbour
 * on the opposite side on both axes, and coordinates use X-right / Y-up.
 */
class SpatialNavigationTest {

    @Test
    fun `horizontal gesture maps to the mirrored stored edge`() {
        assertEquals(SwipeDirection.LEFT, SwipeDirection.RIGHT.edgeForGesture)
        assertEquals(SwipeDirection.RIGHT, SwipeDirection.LEFT.edgeForGesture)
    }

    @Test
    fun `vertical gesture maps to the same stored edge`() {
        assertEquals(SwipeDirection.UP, SwipeDirection.UP.edgeForGesture)
        assertEquals(SwipeDirection.DOWN, SwipeDirection.DOWN.edgeForGesture)
    }

    @Test
    fun `gesture mapping is symmetric`() {
        SwipeDirection.entries.forEach { direction ->
            assertEquals(direction, direction.edgeForGesture.edgeForGesture)
        }
    }

    @Test
    fun `swiping right reveals the page to the left`() {
        assertEquals(-1 to 0, swipeDestination(0, 0, SwipeDirection.RIGHT))
        assertEquals(1 to 0, swipeDestination(0, 0, SwipeDirection.LEFT))
    }

    @Test
    fun `swiping down reveals the page above with positive Y up`() {
        assertEquals(0 to 1, swipeDestination(0, 0, SwipeDirection.DOWN))
        assertEquals(0 to -1, swipeDestination(0, 0, SwipeDirection.UP))
    }

    @Test
    fun `destination coordinate agrees with the stored edge it selects`() {
        SwipeDirection.entries.forEach { gesture ->
            val (x, y) = swipeDestination(3, -2, gesture)
            val stored = gesture.edgeForGesture
            val storedTarget = when (stored) {
                SwipeDirection.LEFT -> 3 - 1 to -2
                SwipeDirection.RIGHT -> 3 + 1 to -2
                SwipeDirection.UP -> 3 to -2 - 1
                SwipeDirection.DOWN -> 3 to -2 + 1
            }
            assertEquals("gesture $gesture must match stored edge $stored", storedTarget, x to y)
        }
    }
}
