package com.ark.voidlauncher.domain

/**
 * Pure graph-safety comparison for page mutations.
 *
 * A change is unsafe when it strands at least one page that was not already
 * stranded. This mirrors the link editor's rule but works on complete before/after
 * snapshots, so repository transactions can validate add/move mutations before
 * committing them.
 */
object PageGraphChanges {

    fun strandsAdditionalPages(
        beforePages: List<PageInfo>,
        beforeEdges: List<EdgeInfo>,
        afterPages: List<PageInfo>,
        afterEdges: List<EdgeInfo>,
        homePageId: Long,
    ): Boolean {
        val before = PageGraphValidator.validate(beforePages, beforeEdges, homePageId)
        val after = PageGraphValidator.validate(afterPages, afterEdges, homePageId)
        return after.pagesWithoutConnection.any { it !in before.pagesWithoutConnection } ||
            after.pagesWithoutPathToHome.any { it !in before.pagesWithoutPathToHome }
    }
}
