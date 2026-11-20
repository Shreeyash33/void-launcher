package com.ark.voidlauncher.domain

/**
 * Empty, in-bounds cells where the next page could be placed.
 *
 * A page can only be reached from another page through a swipe edge, so the useful
 * candidates are the four orthogonal neighbours of the pages that already exist.
 * The result is ordered by the input pages so the map draws previews in a stable
 * place when pages are added or removed.
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
 * The page a `+` preview most likely spawned from, plus the stored swipe direction
 * that leads from [page] back to it.
 *
 * PageEntity has no parent column, so this is derived deterministically from the
 * current graph: the first orthogonal neighbour in [NEIGHBOR_STEPS] order. That is
 * stable across restarts and matches the order the map computes its previews in.
 */
fun spawnNeighbour(
    pages: List<PageInfo>,
    page: PageInfo,
): Pair<PageInfo, SwipeDirection>? {
    for (step in NEIGHBOR_STEPS) {
        val neighbour = pages.firstOrNull {
            it.pageId != page.pageId &&
                it.coordX == page.coordX + step.dx &&
                it.coordY == page.coordY + step.dy
        }
        if (neighbour != null) return neighbour to step.direction
    }
    return null
}

private data class NeighborStep(val dx: Int, val dy: Int, val direction: SwipeDirection)

/**
 * Stored direction deltas: LEFT=(-1,0), RIGHT=(+1,0), UP=(0,-1), DOWN=(0,+1).
 * Gestures mirror X in [edgeForGesture], but edge storage and map geometry use
 * these deltas directly.
 */
private val NEIGHBOR_STEPS = arrayOf(
    NeighborStep(0, -1, SwipeDirection.UP),
    NeighborStep(1, 0, SwipeDirection.RIGHT),
    NeighborStep(0, 1, SwipeDirection.DOWN),
    NeighborStep(-1, 0, SwipeDirection.LEFT),
)
