package com.ark.voidlauncher.domain

/**
 * UI-free gesture dispatch helpers. These have no Android or Compose dependencies so they can be
 * exercised directly in JVM unit tests.
 */
enum class GestureTarget {
    HOME, MAP, SETTINGS, APP_DRAWER, WIDGET_PICKER, EDITOR,
    TOGGLE_COORDINATES, SYSTEM_BACK, SYSTEM_RECENTS, SYSTEM_NOTIFICATIONS,
    LOCK_SCREEN, UNSUPPORTED,
}

/**
 * UI-free gesture dispatch helpers. These have no Android or Compose dependencies so they can be
 * exercised directly in JVM unit tests.
 */
object GestureDispatch {
    fun targetFor(action: GestureAction): GestureTarget = when (action) {
        GestureAction.GO_HOME -> GestureTarget.HOME
        GestureAction.OPEN_MAP -> GestureTarget.MAP
        GestureAction.OPEN_SETTINGS -> GestureTarget.SETTINGS
        GestureAction.OPEN_APP_DRAWER -> GestureTarget.APP_DRAWER
        GestureAction.OPEN_WIDGET_PICKER -> GestureTarget.WIDGET_PICKER
        GestureAction.OPEN_EDITOR -> GestureTarget.EDITOR
        GestureAction.TOGGLE_COORDINATES -> GestureTarget.TOGGLE_COORDINATES
        GestureAction.SYSTEM_BACK -> GestureTarget.SYSTEM_BACK
        GestureAction.SYSTEM_RECENTS -> GestureTarget.SYSTEM_RECENTS
        GestureAction.SYSTEM_NOTIFICATIONS -> GestureTarget.SYSTEM_NOTIFICATIONS
        GestureAction.LOCK_SCREEN -> GestureTarget.LOCK_SCREEN
        GestureAction.ZOOM_IN,
        GestureAction.ZOOM_OUT,
        GestureAction.LOCK_LAUNCHER,
        GestureAction.NONE,
        -> GestureTarget.UNSUPPORTED
    }

    fun requiresAccessibility(target: GestureTarget): Boolean = when (target) {
        GestureTarget.SYSTEM_BACK,
        GestureTarget.SYSTEM_RECENTS,
        GestureTarget.SYSTEM_NOTIFICATIONS,
        GestureTarget.LOCK_SCREEN,
        -> true
        else -> false
    }
}
