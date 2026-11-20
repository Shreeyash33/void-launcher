package com.ark.voidlauncher.domain

/**
 * Pure helpers for bidirectional page links.
 *
 * A user-visible link between two pages is stored as two directed [EdgeInfo]
 * rows: the requested edge and its reverse. Both rows must be written and
 * removed together so a link never leaves a half-connected graph behind.
 */
object PageLinks {

    /** The direction that travels from a link's target back to its source. */
    fun opposite(direction: SwipeDirection): SwipeDirection = when (direction) {
        SwipeDirection.UP -> SwipeDirection.DOWN
        SwipeDirection.DOWN -> SwipeDirection.UP
        SwipeDirection.LEFT -> SwipeDirection.RIGHT
        SwipeDirection.RIGHT -> SwipeDirection.LEFT
    }

    /**
     * Returns the outgoing edge followed by its reverse edge. Ordering is
     * part of the contract: `first` is the requested direction from
     * [fromPageId], `second` goes back from [toPageId].
     */
    fun link(
        fromPageId: Long,
        direction: SwipeDirection,
        toPageId: Long,
    ): Pair<EdgeInfo, EdgeInfo> =
        EdgeInfo(fromPageId, direction, toPageId) to
            EdgeInfo(toPageId, opposite(direction), fromPageId)

    /**
     * Returns a copy of [edges] without either directed row of the link.
     * Unrelated edges (including other directions between the same pages)
     * are preserved, and the input list is never modified.
     */
    fun withoutLink(
        edges: List<EdgeInfo>,
        fromPageId: Long,
        direction: SwipeDirection,
        toPageId: Long,
    ): List<EdgeInfo> {
        val reverse = opposite(direction)
        return edges.filterNot { edge ->
            (edge.fromPageId == fromPageId &&
                edge.swipeDirection == direction &&
                edge.toPageId == toPageId) ||
                (edge.fromPageId == toPageId &&
                    edge.swipeDirection == reverse &&
                    edge.toPageId == fromPageId)
        }
    }
}
