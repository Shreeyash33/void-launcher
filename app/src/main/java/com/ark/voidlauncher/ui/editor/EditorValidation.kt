package com.ark.voidlauncher.ui.editor

import com.ark.voidlauncher.domain.EdgeInfo
import com.ark.voidlauncher.domain.PageGraphChanges
import com.ark.voidlauncher.domain.PageGraphValidator
import com.ark.voidlauncher.domain.PageInfo
import com.ark.voidlauncher.domain.PageLinks
import com.ark.voidlauncher.domain.SwipeDirection

/** A proposed edge mutation. The caller owns the real graph; this is never applied in place. */
sealed interface EdgeChange {
    data class SetEdge(val edge: EdgeInfo) : EdgeChange
    data class RemoveEdge(val fromPageId: Long, val direction: SwipeDirection) : EdgeChange
}

/** A proposed change to a bidirectional page link. Never applied in place. */
sealed interface LinkChange {
    data class SetLink(
        val fromPageId: Long,
        val direction: SwipeDirection,
        val toPageId: Long,
    ) : LinkChange

    data class RemoveLink(
        val fromPageId: Long,
        val direction: SwipeDirection,
        val toPageId: Long,
    ) : LinkChange
}

/** Pure validation helpers used by the editor UI before it emits an intent. */
object EditorValidation {

    /**
     * Applies [change] to a copy of [edges] and reports whether the resulting graph keeps
     * every page connected (degree > 0) and able to reach [homePageId] by directed edges.
     *
     * Deterministic and non-mutating: [pages] and [edges] are never modified. A SET for an
     * existing (page, direction) slot replaces that slot, matching the one-edge-per-direction
     * model used by the launcher. Edges referring to unknown pages are rejected.
     */
    fun edgeChangeIsSafe(
        pages: List<PageInfo>,
        edges: List<EdgeInfo>,
        homePageId: Long,
        change: EdgeChange,
    ): Boolean {
        val pageIds = pages.asSequence().map { it.pageId }.toSet()

        val proposedEdges = when (change) {
            is EdgeChange.SetEdge -> {
                val edge = change.edge
                if (edge.fromPageId !in pageIds || edge.toPageId !in pageIds) return false
                edges.filterNot {
                    it.fromPageId == edge.fromPageId && it.swipeDirection == edge.swipeDirection
                } + edge
            }

            is EdgeChange.RemoveEdge -> edges.filterNot {
                it.fromPageId == change.fromPageId && it.swipeDirection == change.direction
            }
        }

        return PageGraphValidator.validate(pages, proposedEdges, homePageId).isValid
    }

    /**
     * True when [change] would strand at least one page that was not already
     * stranded. Unlike [edgeChangeIsSafe], this does not reject a graph that was
     * already incomplete, so the map can repair isolated pages one link at a time.
     * It still refuses any change that makes connectivity worse.
     */
    fun edgeChangeStrandsAdditionalPages(
        pages: List<PageInfo>,
        edges: List<EdgeInfo>,
        homePageId: Long,
        change: EdgeChange,
    ): Boolean {
        val pageIds = pages.asSequence().map { it.pageId }.toSet()
        val proposedEdges = when (change) {
            is EdgeChange.SetEdge -> {
                val edge = change.edge
                if (edge.fromPageId !in pageIds || edge.toPageId !in pageIds) return true
                edges.filterNot {
                    it.fromPageId == edge.fromPageId && it.swipeDirection == edge.swipeDirection
                } + edge
            }

            is EdgeChange.RemoveEdge -> edges.filterNot {
                it.fromPageId == change.fromPageId && it.swipeDirection == change.direction
            }
        }

        val before = PageGraphValidator.validate(pages, edges, homePageId)
        val after = PageGraphValidator.validate(pages, proposedEdges, homePageId)
        return after.pagesWithoutConnection.any { it !in before.pagesWithoutConnection } ||
            after.pagesWithoutPathToHome.any { it !in before.pagesWithoutPathToHome }
    }

    /**
     * True when [change] would strand at least one page that was not already
     * stranded. A link change touches both directed rows; a SET first clears any
     * existing link between the same pair in the same direction, then adds the
     * two fresh rows. Unknown page ids are always rejected.
     */
    fun linkChangeStrandsAdditionalPages(
        pages: List<PageInfo>,
        edges: List<EdgeInfo>,
        homePageId: Long,
        change: LinkChange,
    ): Boolean {
        val pageIds = pages.asSequence().map { it.pageId }.toSet()

        val proposedEdges = when (change) {
            is LinkChange.SetLink -> {
                if (change.fromPageId !in pageIds || change.toPageId !in pageIds) return true
                PageLinks.withoutLink(
                    edges, change.fromPageId, change.direction, change.toPageId,
                ) + PageLinks.link(change.fromPageId, change.direction, change.toPageId).toList()
            }

            is LinkChange.RemoveLink -> {
                if (change.fromPageId !in pageIds || change.toPageId !in pageIds) return true
                PageLinks.withoutLink(
                    edges, change.fromPageId, change.direction, change.toPageId,
                )
            }
        }

        return PageGraphChanges.strandsAdditionalPages(
            pages, edges, pages, proposedEdges, homePageId,
        )
    }
}
