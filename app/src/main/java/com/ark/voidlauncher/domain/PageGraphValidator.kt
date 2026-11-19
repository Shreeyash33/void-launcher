package com.ark.voidlauncher.domain

/**
 * Pure-Kotlin graph checks over the page/swipe graph.
 *
 * The graph is directed: an [EdgeInfo] is a transition from [EdgeInfo.fromPageId]
 * to [EdgeInfo.toPageId]. Duplicate edges are harmless because all traversals
 * work with sets and visited markers.
 */
object PageGraphValidator {

    /**
     * Returns the ids of pages that have no incident edge at all — neither an
     * outgoing nor an incoming edge. Results are sorted by page id ascending.
     */
    fun pagesWithoutConnection(
        pages: List<PageInfo>,
        edges: List<EdgeInfo>
    ): List<Long> {
        val pageIds = pages.asSequence().map { it.pageId }.toSet()
        if (pageIds.isEmpty()) return emptyList()

        // Both endpoints count as "connected"; a set makes duplicates irrelevant.
        val connectedIds = HashSet<Long>(edges.size * 2 + 1)
        for (edge in edges) {
            connectedIds.add(edge.fromPageId)
            connectedIds.add(edge.toPageId)
        }

        return pageIds.filterNot { it in connectedIds }.sorted()
    }

    /**
     * Returns the ids of pages that cannot reach [homePageId] by following
     * directed edges. The home page itself is always reachable. If the home id
     * is not among [pages], every page is reported (without throwing).
     * Results are sorted by page id ascending.
     */
    fun pagesWithoutPathToHome(
        pages: List<PageInfo>,
        edges: List<EdgeInfo>,
        homePageId: Long
    ): List<Long> {
        val pageIds = pages.asSequence().map { it.pageId }.toSet()
        if (pageIds.isEmpty()) return emptyList()

        // The home page must be an actual page for the notion of "path to home" to apply.
        if (homePageId !in pageIds) return pageIds.sorted()

        // Walk the reversed graph outwards from home: every node reached this way
        // has a directed path to home. A visited set makes cycles terminate safely.
        val predecessors = HashMap<Long, MutableList<Long>>()
        for (edge in edges) {
            predecessors.getOrPut(edge.toPageId) { ArrayList() }.add(edge.fromPageId)
        }

        val canReachHome = HashSet<Long>()
        val queue = ArrayDeque<Long>()
        canReachHome.add(homePageId)
        queue.addLast(homePageId)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val incoming = predecessors[current] ?: continue
            for (fromId in incoming) {
                if (canReachHome.add(fromId)) {
                    queue.addLast(fromId)
                }
            }
        }

        return pageIds.filterNot { it in canReachHome }.sorted()
    }

    /** Combines both checks into a single [GraphValidationResult]. */
    fun validate(
        pages: List<PageInfo>,
        edges: List<EdgeInfo>,
        homePageId: Long
    ): GraphValidationResult = GraphValidationResult(
        pagesWithoutConnection = pagesWithoutConnection(pages, edges),
        pagesWithoutPathToHome = pagesWithoutPathToHome(pages, edges, homePageId),
    )
}
