package com.ark.voidlauncher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureDispatchTest {

    private val supportedMappings = listOf(
        GestureAction.GO_HOME to GestureTarget.HOME,
        GestureAction.OPEN_MAP to GestureTarget.MAP,
        GestureAction.OPEN_SETTINGS to GestureTarget.SETTINGS,
        GestureAction.OPEN_APP_DRAWER to GestureTarget.APP_DRAWER,
        GestureAction.OPEN_WIDGET_PICKER to GestureTarget.WIDGET_PICKER,
        GestureAction.OPEN_EDITOR to GestureTarget.EDITOR,
        GestureAction.TOGGLE_COORDINATES to GestureTarget.TOGGLE_COORDINATES,
        GestureAction.SYSTEM_BACK to GestureTarget.SYSTEM_BACK,
        GestureAction.SYSTEM_RECENTS to GestureTarget.SYSTEM_RECENTS,
        GestureAction.SYSTEM_NOTIFICATIONS to GestureTarget.SYSTEM_NOTIFICATIONS,
        GestureAction.LOCK_SCREEN to GestureTarget.LOCK_SCREEN,
    )

    private val unsupportedActions = listOf(
        GestureAction.ZOOM_IN,
        GestureAction.ZOOM_OUT,
        GestureAction.LOCK_LAUNCHER,
        GestureAction.NONE,
    )

    @Test
    fun supportedActionsMapToExpectedTargets() {
        supportedMappings.forEach { (action, expected) ->
            assertEquals("Mapping for $action", expected, GestureDispatch.targetFor(action))
        }
    }

    @Test
    fun unsupportedActionsMapToUnsupported() {
        unsupportedActions.forEach { action ->
            assertEquals(
                "Mapping for $action",
                GestureTarget.UNSUPPORTED,
                GestureDispatch.targetFor(action),
            )
        }
    }

    @Test
    fun systemTargetsRequireAccessibility() {
        listOf(
            GestureTarget.SYSTEM_BACK,
            GestureTarget.SYSTEM_RECENTS,
            GestureTarget.SYSTEM_NOTIFICATIONS,
            GestureTarget.LOCK_SCREEN,
        ).forEach { target ->
            assertTrue("$target should require accessibility", GestureDispatch.requiresAccessibility(target))
        }
    }

    @Test
    fun nonSystemTargetsDoNotRequireAccessibility() {
        enumValues<GestureTarget>().filterNot { it in setOf(
            GestureTarget.SYSTEM_BACK,
            GestureTarget.SYSTEM_RECENTS,
            GestureTarget.SYSTEM_NOTIFICATIONS,
            GestureTarget.LOCK_SCREEN,
        ) }.forEach { target ->
            assertFalse("$target should not require accessibility", GestureDispatch.requiresAccessibility(target))
        }
    }
}
