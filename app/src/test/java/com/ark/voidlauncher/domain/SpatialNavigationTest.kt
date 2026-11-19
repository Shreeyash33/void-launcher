package com.ark.voidlauncher.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks the paging convention: swiping toward a screen edge reveals the neighbour
 * on the opposite side, and coordinates use X-right / Y-up.
 */
class SpatialNavigationTest {

    @Test
    fun `gesture maps to the opposite stored edge direction`() {
        assertEquals(SwipeDirection.LEFT, SwipeDirection.RIGHT.opposite)
        assertEquals(SwipeDirection.RIGHT, SwipeDirection.LEFT.opposite)
        assertEquals(SwipeDirection.DOWN, SwipeDirection.UP.opposite)
        assertEquals(SwipeDirection.UP, SwipeDirection.DOWN.opposite)
    }

    @Test
    fun `opposite is symmetric`() {
        SwipeDirection.entries.forEach { direction ->
            assertEquals(direction, direction.opposite.opposite)
        }
    }

    @Test
    fun `swiping right reveals the page to the left`() {
        assertEquals(-1 to 0, swipeDestination(0, 0, SwipeDirection.RIGHT))
        assertEquals(1 to 0, swipeDestination(0, 0, SwipeDirection.LEFT))
    }

    @Test
    fun `swiping up reveals the page above and positive Y is up`() {
        assertEquals(0 to 1, swipeDestination(0, 0, SwipeDirection.UP))
        assertEquals(0 to -1, swipeDestination(0, 0, SwipeDirection.DOWN))
    }

    @Test
    fun `destination coordinate agrees with the stored edge it selects`() {
        SwipeDirection.entries.forEach { gesture ->
            val (x, y) = swipeDestination(3, -2, gesture)
            val stored = gesture.opposite
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
