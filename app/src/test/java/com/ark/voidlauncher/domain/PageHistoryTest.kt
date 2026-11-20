package com.ark.voidlauncher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageHistoryTest {

    @Test
    fun `pop walks back in reverse push order`() {
        val history = PageHistory()
        history.push(1L, 2L, SwipeDirection.RIGHT)
        history.push(2L, 3L, SwipeDirection.DOWN)

        assertEquals(2, history.size)
        assertEquals(2L, history.pop())
        assertEquals(1L, history.pop())
        assertNull(history.pop())
        assertEquals(0, history.size)
    }

    @Test
    fun `reverse target matches the opposite stored direction of a swipe`() {
        val history = PageHistory()
        // Stored RIGHT means the gesture was LEFT; going back needs a RIGHT
        // gesture, whose stored direction (LEFT) is RIGHT's opposite.
        history.push(1L, 2L, SwipeDirection.RIGHT)

        assertEquals(1L, history.reverseTarget(2L, SwipeDirection.RIGHT))
        assertNull(history.reverseTarget(2L, SwipeDirection.LEFT))
        assertNull(history.reverseTarget(1L, SwipeDirection.RIGHT))
    }

    @Test
    fun `reverse target works for vertical swipes too`() {
        val history = PageHistory()
        history.push(10L, 11L, SwipeDirection.UP)

        assertEquals(10L, history.reverseTarget(11L, SwipeDirection.DOWN))
        assertNull(history.reverseTarget(11L, SwipeDirection.UP))
    }

    @Test
    fun `reverse target peeks without mutating history`() {
        val history = PageHistory()
        history.push(1L, 2L, SwipeDirection.LEFT)

        assertEquals(1L, history.reverseTarget(2L, SwipeDirection.LEFT))
        assertEquals(1, history.size)
        assertEquals(1L, history.pop())
    }

    @Test
    fun `dropPage removes every step referencing the deleted page`() {
        val history = PageHistory()
        history.push(1L, 2L, SwipeDirection.RIGHT)
        history.push(2L, 3L, SwipeDirection.DOWN)

        history.dropPage(2L)
        assertEquals(0, history.size)
        assertNull(history.reverseTarget(3L, SwipeDirection.DOWN))
        assertNull(history.pop())
    }

    @Test
    fun `clear removes all history`() {
        val history = PageHistory()
        history.push(1L, 2L, SwipeDirection.RIGHT)
        history.push(2L, 3L, SwipeDirection.DOWN)

        history.clear()
        assertEquals(0, history.size)
        assertNull(history.pop())
    }

    @Test
    fun `dropPage leaves unrelated navigation intact`() {
        val history = PageHistory()
        history.push(1L, 2L, SwipeDirection.RIGHT)
        history.push(2L, 3L, SwipeDirection.UP)

        history.dropPage(99L)
        assertEquals(2, history.size)
        assertEquals(2L, history.reverseTarget(3L, SwipeDirection.DOWN))
        assertEquals(2L, history.pop())
    }

    @Test
    fun `opposite helper agrees with the history reverse rule`() {
        SwipeDirection.entries.forEach { direction ->
            val history = PageHistory()
            history.push(100L, 200L, direction)
            val reverseGesture = PageLinks.opposite(direction).edgeForGesture
            assertEquals(100L, history.reverseTarget(200L, reverseGesture))
        }
    }
}
