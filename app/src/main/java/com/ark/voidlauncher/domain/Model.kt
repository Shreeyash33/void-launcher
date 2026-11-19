package com.ark.voidlauncher.domain

/** Pure domain types shared by the data layer, validators, and later UI. */

enum class SwipeDirection { UP, DOWN, LEFT, RIGHT }

/**
 * Pages use carousel paging: dragging toward a screen edge reveals the neighbour
 * on the *opposite* side. Stored edges keep their compass-style names (LEFT means
 * the page at x-1), so a gesture must be inverted before looking one up.
 */
val SwipeDirection.opposite: SwipeDirection
    get() = when (this) {
        SwipeDirection.UP -> SwipeDirection.DOWN
        SwipeDirection.DOWN -> SwipeDirection.UP
        SwipeDirection.LEFT -> SwipeDirection.RIGHT
        SwipeDirection.RIGHT -> SwipeDirection.LEFT
    }

/**
 * Coordinate reached by swiping [gesture] from ([coordX], [coordY]).
 * Coordinates are X-right / Y-up, and paging reveals the opposite neighbour:
 * swiping right goes to x-1, swiping up goes to y+1.
 */
fun swipeDestination(coordX: Int, coordY: Int, gesture: SwipeDirection): Pair<Int, Int> =
    when (gesture) {
        SwipeDirection.UP -> coordX to coordY + 1
        SwipeDirection.DOWN -> coordX to coordY - 1
        SwipeDirection.LEFT -> coordX + 1 to coordY
        SwipeDirection.RIGHT -> coordX - 1 to coordY
    }

enum class GridItemType { APP_SHORTCUT, WIDGET }

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
) {
    companion object {
        const val DEFAULT_GRID_COLS = 10
        const val DEFAULT_GRID_ROWS = 20
        const val MIN_COORD = -10
        const val MAX_COORD = 10
    }
}

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
