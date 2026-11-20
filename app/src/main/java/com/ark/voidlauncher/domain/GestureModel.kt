package com.ark.voidlauncher.domain

/**
 * Pure gesture configuration model. Recognition and dispatch are deliberately
 * absent here: this only describes which action a trigger is bound to, at two
 * possible scopes. Persistence lives in LauncherPrefs; no Android or UI types
 * leak into this file so it can be tested on the JVM.
 */

enum class GestureScope { GLOBAL, PAGE }

enum class GestureAction {
    NONE,
    ZOOM_IN, ZOOM_OUT,
    LOCK_SCREEN, LOCK_LAUNCHER,
    OPEN_EDITOR, OPEN_MAP, GO_HOME,
    OPEN_SETTINGS, OPEN_APP_DRAWER, OPEN_WIDGET_PICKER,
    TOGGLE_COORDINATES,
    SYSTEM_BACK, SYSTEM_RECENTS, SYSTEM_NOTIFICATIONS,
}

enum class GestureTrigger {
    LONG_PRESS, DOUBLE_TAP, TRIPLE_TAP,
    PINCH_ZOOM_IN, PINCH_ZOOM_OUT,
    SWIPE_2_UP, SWIPE_2_DOWN, SWIPE_2_LEFT, SWIPE_2_RIGHT,
    SWIPE_3_UP, SWIPE_3_DOWN, SWIPE_3_LEFT, SWIPE_3_RIGHT,
}

/** One trigger/action pair. Distinct pairs make many-to-one bindings natural. */
data class GestureBinding(
    val trigger: GestureTrigger,
    val action: GestureAction,
)

/**
 * Global bindings apply to every page; page bindings are keyed by (x, y).
 *
 * A page binding for a trigger wins over the global one, including when it is
 * [GestureAction.NONE] (an explicit page-level "disable"). NONE is never
 * returned as an action; it resolves to null, as does a missing binding.
 */
data class GestureBindings(
    val global: Set<GestureBinding> = emptySet(),
    val byPageCoord: Map<Pair<Int, Int>, Set<GestureBinding>> = emptyMap(),
) {
    fun actionFor(coord: Pair<Int, Int>, trigger: GestureTrigger): GestureAction? {
        val pageBinding = byPageCoord[coord]?.firstOrNull { it.trigger == trigger }
        if (pageBinding != null) {
            return pageBinding.action.takeIf { it != GestureAction.NONE }
        }

        return global
            .firstOrNull { it.trigger == trigger }
            ?.action
            ?.takeIf { it != GestureAction.NONE }
    }
}
