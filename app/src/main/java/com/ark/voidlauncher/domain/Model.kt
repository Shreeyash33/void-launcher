package com.ark.voidlauncher.domain

/** Pure domain types shared by the data layer, validators, and later UI. */

enum class SwipeDirection { UP, DOWN, LEFT, RIGHT }

/**
 * Maps a swipe gesture to the stored edge that should be followed.
 *
 * Paging is a carousel on both axes: dragging toward a screen edge reveals the
 * neighbour on the opposite side. Stored edges keep compass-style names (LEFT
 * means the page at x-1, UP the page at y-1), so X needs inverting in the lookup,
 * while Y already matches: a downward swipe uses the DOWN edge at y+1.
 */
val SwipeDirection.edgeForGesture: SwipeDirection
    get() = when (this) {
        SwipeDirection.UP -> SwipeDirection.UP
        SwipeDirection.DOWN -> SwipeDirection.DOWN
        SwipeDirection.LEFT -> SwipeDirection.RIGHT
        SwipeDirection.RIGHT -> SwipeDirection.LEFT
    }

/**
 * Coordinate reached by swiping [gesture] from ([coordX], [coordY]).
 * Coordinates are X-right / Y-up; paging reveals the opposite neighbour, so
 * right goes to x-1 and down goes to y+1 (which renders above on the map).
 */
fun swipeDestination(coordX: Int, coordY: Int, gesture: SwipeDirection): Pair<Int, Int> =
    when (gesture) {
        SwipeDirection.UP -> coordX to coordY - 1
        SwipeDirection.DOWN -> coordX to coordY + 1
        SwipeDirection.LEFT -> coordX + 1 to coordY
        SwipeDirection.RIGHT -> coordX - 1 to coordY
    }

enum class GridItemType { APP_SHORTCUT, WIDGET }

/**
 * Page role. [HOME] is the configured home coordinate, [APP_DRAWER] hosts the
 * app drawer, and every other page is a [WIDGET] page. Pages may only be
 * navigated to by the user; the role is data, not a UI overlay.
 */
enum class PageType { HOME, WIDGET, APP_DRAWER }

enum class FontFamily { MONOSPACE, SANS, SERIF, DISPLAY }

enum class AppFontWeight { LIGHT, NORMAL, MEDIUM, BOLD }

enum class TextAlign { START, CENTER, END }

enum class TextTransform { NONE, LOWERCASE, UPPERCASE }

data class PageInfo(
    val pageId: Long,
    val coordX: Int,
    val coordY: Int,
    val title: String,
    val gridCols: Int = DEFAULT_GRID_COLS,
    val gridRows: Int = DEFAULT_GRID_ROWS,
    val isSecret: Boolean = false,
    val pageType: PageType = PageType.WIDGET,
) {
    companion object {
        const val DEFAULT_GRID_COLS = 10
        const val DEFAULT_GRID_ROWS = 20
        const val MIN_COORD = -10
        const val MAX_COORD = 10
    }
}

/** User-facing page name with one shared fallback for blank titles. */
val PageInfo.displayTitle: String
    get() = title.ifBlank { "Page $coordX,$coordY" }

data class EdgeInfo(
    val fromPageId: Long,
    val swipeDirection: SwipeDirection,
    val toPageId: Long,
)

data class GraphValidationResult(
    val pagesWithoutConnection: List<Long>,
    val pagesWithoutPathToHome: List<Long>,
) {
    val isValid: Boolean
        get() = pagesWithoutConnection.isEmpty() && pagesWithoutPathToHome.isEmpty()
}
