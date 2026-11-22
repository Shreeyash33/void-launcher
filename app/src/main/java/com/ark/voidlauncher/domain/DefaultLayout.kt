package com.ark.voidlauncher.domain

/** One page in the canonical fresh-install / RESET ALL layout. */
data class DefaultPage(
    val coordX: Int,
    val coordY: Int,
    val title: String,
    val pageType: PageType,
)

/** One directed swipe edge in the canonical fresh-install / RESET ALL layout. */
data class DefaultEdge(
    val fromX: Int,
    val fromY: Int,
    val direction: SwipeDirection,
    val toX: Int,
    val toY: Int,
)

/** One built-in widget placed by the canonical fresh-install / RESET ALL layout. */
data class DefaultBuiltInItem(
    val kind: BuiltInWidgetKind,
    val coordX: Int,
    val coordY: Int,
    val col: Int,
    val row: Int,
    val colSpan: Int,
    val rowSpan: Int,
)

/**
 * Single source of truth for the five-page starter map and its built-in widget.
 *
 * Both Room's fresh-install seed callback and [com.ark.voidlauncher.data.repository.LauncherRepository.resetToDefault]
 * build from this definition so a reset can never drift from a new install.
 * Coordinates use the same stored compass-style deltas as [neighbourCoord].
 */
object DefaultLayout {
    val pages: List<DefaultPage> = listOf(
        DefaultPage(0, 0, "Home", PageType.HOME),
        DefaultPage(1, 0, "Page 1,0", PageType.WIDGET),
        DefaultPage(-1, 0, "Page -1,0", PageType.WIDGET),
        DefaultPage(0, -1, "Page 0,-1", PageType.WIDGET),
        DefaultPage(0, 1, "Page 0,1", PageType.APP_DRAWER),
    )

    val edges: List<DefaultEdge> = listOf(
        // Home -> each neighbour.
        DefaultEdge(0, 0, SwipeDirection.RIGHT, 1, 0),
        DefaultEdge(0, 0, SwipeDirection.LEFT, -1, 0),
        DefaultEdge(0, 0, SwipeDirection.UP, 0, -1),
        DefaultEdge(0, 0, SwipeDirection.DOWN, 0, 1),
        // Each neighbour -> Home, so no page is stranded.
        DefaultEdge(1, 0, SwipeDirection.LEFT, 0, 0),
        DefaultEdge(-1, 0, SwipeDirection.RIGHT, 0, 0),
        DefaultEdge(0, -1, SwipeDirection.DOWN, 0, 0),
        DefaultEdge(0, 1, SwipeDirection.UP, 0, 0),
    )

    val home: DefaultPage get() = pages.first { it.pageType == PageType.HOME }

    /**
     * Built-ins seeded only for a fresh install or RESET ALL. Upgrades and a
     * silently missing origin page must never re-add an item the user deleted.
     */
    val items: List<DefaultBuiltInItem> = run {
        val kind = BuiltInWidgetKind.PAGE_INFO
        val span = BuiltInWidgets.defaultSpan(kind)
        listOf(
            DefaultBuiltInItem(
                kind = kind,
                coordX = home.coordX,
                coordY = home.coordY,
                col = 1,
                row = 1,
                colSpan = span.colSpan,
                rowSpan = span.rowSpan,
            ),
        )
    }
}
