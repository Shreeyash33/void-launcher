package com.ark.voidlauncher.domain

/**
 * Empty, in-bounds cells where the next page could be placed.
 *
 * A page can only be reached from another page through a swipe edge, so the useful
 * candidates are the four orthogonal neighbours of the pages that already exist.
 * The result is ordered by the input pages (and then by [NEIGHBOR_STEPS]) so the
 * map draws previews in a stable place when pages are added or removed.
 */
fun nextPageCandidates(pages: List<PageInfo>): List<Pair<Int, Int>> {
    if (pages.isEmpty()) return listOf(0 to 0)

    val occupied = pages.map { it.coordX to it.coordY }.toSet()
    val candidates = linkedSetOf<Pair<Int, Int>>()
    for (page in pages) {
        for (step in NEIGHBOR_STEPS) {
            val x = page.coordX + step.dx
            val y = page.coordY + step.dy
            if (x !in PageInfo.MIN_COORD..PageInfo.MAX_COORD) continue
            if (y !in PageInfo.MIN_COORD..PageInfo.MAX_COORD) continue
            if ((x to y) in occupied) continue
            candidates += x to y
        }
    }
    return candidates.toList()
}

/**
 * Coordinate of the stored edge that leaves ([x], [y]) in [direction].
 *
 * Stored directions are compass-style: LEFT is x-1, RIGHT is x+1, UP is y-1 and
 * DOWN is y+1. Gestures mirror X in [edgeForGesture], but edge storage and map
 * geometry use these deltas directly.
 */
fun neighbourCoord(x: Int, y: Int, direction: SwipeDirection): Pair<Int, Int> =
    when (direction) {
        SwipeDirection.LEFT -> x - 1 to y
        SwipeDirection.RIGHT -> x + 1 to y
        SwipeDirection.UP -> x to y - 1
        SwipeDirection.DOWN -> x to y + 1
    }

/**
 * First existing orthogonal neighbour of ([x], [y]) in default priority order:
 * LEFT, RIGHT, UP, DOWN. The returned direction leads from the new/moved page to
 * that neighbour, so it can be stored as its default outgoing edge directly.
 *
 * Pure: callers moving a page should exclude the moving page from [pages] before
 * calling, otherwise its old coordinate can be picked as the new neighbour.
 */
fun primaryNeighbour(
    pages: List<PageInfo>,
    x: Int,
    y: Int,
): Pair<PageInfo, SwipeDirection>? {
    for (step in NEIGHBOR_STEPS) {
        val neighbour = pages.firstOrNull {
            it.coordX == x + step.dx && it.coordY == y + step.dy
        }
        if (neighbour != null) return neighbour to step.direction
    }
    return null
}

/**
 * Whether a page may occupy ([x], [y]): in bounds, unoccupied, and adjacent to at
 * least one other page. When [movingPageId] is supplied, that page is ignored for
 * both checks so a move can evaluate its own destination.
 */
fun isValidPageDestination(
    pages: List<PageInfo>,
    x: Int,
    y: Int,
    movingPageId: Long? = null,
): Boolean {
    if (x !in PageInfo.MIN_COORD..PageInfo.MAX_COORD) return false
    if (y !in PageInfo.MIN_COORD..PageInfo.MAX_COORD) return false
    val others = if (movingPageId == null) {
        pages
    } else {
        pages.filterNot { it.pageId == movingPageId }
    }
    if (others.any { it.coordX == x && it.coordY == y }) return false
    return primaryNeighbour(others, x, y) != null
}

/**
 * Effective role of a stored page row. APP_DRAWER is authoritative; the configured
 * home coordinate wins over a stale stored HOME; everything else is a widget page.
 */
fun effectivePageType(
    pageType: PageType,
    x: Int,
    y: Int,
    homeX: Int,
    homeY: Int,
): PageType = when {
    pageType == PageType.APP_DRAWER -> PageType.APP_DRAWER
    x == homeX && y == homeY -> PageType.HOME
    else -> PageType.WIDGET
}

/**
 * The page a `+` preview most likely spawned from, plus the stored swipe direction
 * that leads from [page] back to it.
 *
 * PageEntity has no parent column, so this is derived deterministically from the
 * current graph using the same priority as default edge creation. That is stable
 * across restarts and matches the order the map computes its previews in.
 */
fun spawnNeighbour(
    pages: List<PageInfo>,
    page: PageInfo,
): Pair<PageInfo, SwipeDirection>? = primaryNeighbour(pages, page.coordX, page.coordY)

private data class NeighborStep(val dx: Int, val dy: Int, val direction: SwipeDirection)

/**
 * Stored direction deltas in default priority order:
 * LEFT=(-1,0), RIGHT=(+1,0), UP=(0,-1), DOWN=(0,+1).
 */
private val NEIGHBOR_STEPS = arrayOf(
    NeighborStep(-1, 0, SwipeDirection.LEFT),
    NeighborStep(1, 0, SwipeDirection.RIGHT),
    NeighborStep(0, -1, SwipeDirection.UP),
    NeighborStep(0, 1, SwipeDirection.DOWN),
)
