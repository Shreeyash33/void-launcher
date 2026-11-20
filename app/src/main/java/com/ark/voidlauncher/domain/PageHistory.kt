package com.ark.voidlauncher.domain

/**
 * One step in the recent swipe history.
 *
 * [pageId] is the page that was entered. [entryDirection] is the *stored*
 * direction of the edge that led there, or null for an entry that was not
 * created by a swipe (kept for callers that seed history explicitly).
 */
data class HistoryEntry(
    val pageId: Long,
    val entryDirection: SwipeDirection?,
)

/**
 * Minimal, pure navigation history for one-finger page swipes.
 *
 * Each push records the page left behind as well as the page entered, so [pop]
 * can walk back exactly one step. [reverseTarget] only peeks: it never mutates
 * the stack, which lets the canvas prefer a reverse swipe over a normal edge
 * without consuming history until the navigation actually completes.
 */
class PageHistory {

    private data class Step(val fromPageId: Long, val entry: HistoryEntry)

    private val steps = mutableListOf<Step>()

    val size: Int
        get() = steps.size

    /**
     * Records an entry from [fromPageId] to [toPageId]. [direction] is the stored
     * swipe direction, or null for entries created by a button/map navigation.
     */
    fun push(fromPageId: Long, toPageId: Long, direction: SwipeDirection? = null) {
        steps.add(Step(fromPageId, HistoryEntry(toPageId, direction)))
    }

    /** Removes and returns the page that preceded the most recently entered page. */
    fun pop(): Long? = steps.removeLastOrNull()?.fromPageId

    /**
     * The page to visit when swiping [gestureDirection] while on [currentPageId].
     *
     * A swipe gesture is the mirror of the stored edge direction, so this only
     * matches when the top entry was entered in the stored-direction opposite
     * of the current gesture's stored direction. Returns null when the top
     * entry is not the current page or has no swipe direction.
     */
    fun reverseTarget(currentPageId: Long, gestureDirection: SwipeDirection): Long? {
        val top = steps.lastOrNull() ?: return null
        val entryDirection = top.entry.entryDirection ?: return null
        if (top.entry.pageId != currentPageId) return null
        if (PageLinks.opposite(entryDirection) != gestureDirection.edgeForGesture) return null
        return top.fromPageId
    }

    /** Drops every step that references [pageId], e.g. after a page is deleted. */
    fun dropPage(pageId: Long) {
        steps.removeAll { step ->
            step.fromPageId == pageId || step.entry.pageId == pageId
        }
    }

    fun clear() {
        steps.clear()
    }
}
