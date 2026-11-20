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
        for ((dx, dy) in NEIGHBOR_DELTAS) {
            val x = page.coordX + dx
            val y = page.coordY + dy
            if (x !in PageInfo.MIN_COORD..PageInfo.MAX_COORD) continue
            if (y !in PageInfo.MIN_COORD..PageInfo.MAX_COORD) continue
            if (x to y in occupied) continue
            candidates += x to y
        }
    }
    return candidates.toList()
}

private val NEIGHBOR_DELTAS = arrayOf(0 to -1, 1 to 0, 0 to 1, -1 to 0)
