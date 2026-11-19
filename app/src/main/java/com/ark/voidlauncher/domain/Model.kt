package com.ark.voidlauncher.domain

/** Pure domain types shared by the data layer, validators, and later UI. */

enum class SwipeDirection { UP, DOWN, LEFT, RIGHT }

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
