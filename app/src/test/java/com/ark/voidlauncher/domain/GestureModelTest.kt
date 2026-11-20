package com.ark.voidlauncher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** JVM-only checks for scope precedence and many-to-one gesture bindings. */
class GestureModelTest {

    private val coord = 2 to -1

    @Test
    fun `page binding overrides global for the same trigger`() {
        val bindings = GestureBindings(
            global = setOf(
                GestureBinding(GestureTrigger.LONG_PRESS, GestureAction.OPEN_SETTINGS),
                GestureBinding(GestureTrigger.DOUBLE_TAP, GestureAction.OPEN_MAP),
            ),
            byPageCoord = mapOf(
                coord to setOf(
                    GestureBinding(GestureTrigger.LONG_PRESS, GestureAction.LOCK_SCREEN),
                ),
            ),
        )

        assertEquals(
            GestureAction.LOCK_SCREEN,
            bindings.actionFor(coord, GestureTrigger.LONG_PRESS),
        )
        // Other triggers on the same page still fall through to global.
        assertEquals(
            GestureAction.OPEN_MAP,
            bindings.actionFor(coord, GestureTrigger.DOUBLE_TAP),
        )
        // Other pages are unaffected by the override.
        assertEquals(
            GestureAction.OPEN_SETTINGS,
            bindings.actionFor(0 to 0, GestureTrigger.LONG_PRESS),
        )
    }

    @Test
    fun `unbound and NONE actions resolve to null`() {
        val empty = GestureBindings()
        assertNull(empty.actionFor(coord, GestureTrigger.LONG_PRESS))

        val withNone = GestureBindings(
            global = setOf(
                GestureBinding(GestureTrigger.DOUBLE_TAP, GestureAction.NONE),
                GestureBinding(GestureTrigger.LONG_PRESS, GestureAction.GO_HOME),
            ),
            byPageCoord = mapOf(
                coord to setOf(
                    GestureBinding(GestureTrigger.TRIPLE_TAP, GestureAction.NONE),
                    GestureBinding(GestureTrigger.LONG_PRESS, GestureAction.NONE),
                ),
            ),
        )
        assertNull(withNone.actionFor(coord, GestureTrigger.DOUBLE_TAP))
        assertNull(withNone.actionFor(coord, GestureTrigger.TRIPLE_TAP))
        assertNull(withNone.actionFor(coord, GestureTrigger.SWIPE_2_UP))
        // A page-level NONE also suppresses the global binding for that trigger.
        assertNull(withNone.actionFor(coord, GestureTrigger.LONG_PRESS))
    }

    @Test
    fun `many triggers can share one action and all resolve correctly`() {
        val homeTriggers = listOf(
            GestureTrigger.DOUBLE_TAP,
            GestureTrigger.SWIPE_2_UP,
            GestureTrigger.SWIPE_3_LEFT,
        )
        val bindings = GestureBindings(
            global = homeTriggers.map { GestureBinding(it, GestureAction.GO_HOME) }.toSet(),
            byPageCoord = mapOf(
                coord to setOf(
                    GestureBinding(GestureTrigger.LONG_PRESS, GestureAction.GO_HOME),
                    GestureBinding(GestureTrigger.PINCH_ZOOM_IN, GestureAction.GO_HOME),
                ),
            ),
        )

        homeTriggers.forEach { trigger ->
            assertEquals(GestureAction.GO_HOME, bindings.actionFor(coord, trigger))
        }
        assertEquals(
            GestureAction.GO_HOME,
            bindings.actionFor(coord, GestureTrigger.LONG_PRESS),
        )
        assertEquals(
            GestureAction.GO_HOME,
            bindings.actionFor(coord, GestureTrigger.PINCH_ZOOM_IN),
        )
    }
}
