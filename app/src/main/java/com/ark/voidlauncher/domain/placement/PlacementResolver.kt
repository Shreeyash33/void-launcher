package com.ark.voidlauncher.domain.placement

import com.ark.voidlauncher.data.db.GridItemEntity
import com.ark.voidlauncher.domain.GridItemType
import com.ark.voidlauncher.domain.SwipeDirection

/**
 * Pure-Kotlin placement validation. No Android dependency, no side effects;
 * the same inputs always produce the same [PlacementResult].
 *
 * The resolver is deliberately single-hop: when a candidate crosses a page
 * boundary it reports the direction to hop and stops. Resolving which concrete
 * page that edge points at is the caller's responsibility.
 */
object PlacementResolver {

    fun resolve(
        candidate: GridItemEntity,
        existing: List<GridItemEntity>,
        cols: Int,
        rows: Int,
    ): PlacementResult {
        if (cols <= 0 || rows <= 0) {
            return PlacementResult.Rejected("Grid dimensions must be positive")
        }
        if (candidate.colSpan <= 0 || candidate.rowSpan <= 0) {
            return PlacementResult.Rejected("Item spans must be positive")
        }
        if (candidate.colSpan > cols || candidate.rowSpan > rows) {
            return PlacementResult.Rejected(
                "Item span ${candidate.colSpan}x${candidate.rowSpan} does not fit in a ${cols}x${rows} grid",
            )
        }

        // A boundary crossing is a single-hop rejection. Priority is fixed so
        // the result is deterministic when two boundaries are crossed at once.
        val overflow = when {
            candidate.col < 0 -> SwipeDirection.LEFT
            candidate.row < 0 -> SwipeDirection.UP
            candidate.col + candidate.colSpan > cols -> SwipeDirection.RIGHT
            candidate.row + candidate.rowSpan > rows -> SwipeDirection.DOWN
            else -> null
        }
        if (overflow != null) return PlacementResult.MoveToNeighbor(overflow)

        for (other in existing) {
            // A moved item does not conflict with its own previous placement.
            if (candidate.itemId != 0L && other.itemId == candidate.itemId) continue
            if (!overlaps(candidate, other)) continue

            val candidateIsShortcut = candidate.itemType == GridItemType.APP_SHORTCUT
            val otherIsShortcut = other.itemType == GridItemType.APP_SHORTCUT
            if (candidateIsShortcut || otherIsShortcut) {
                return PlacementResult.Rejected("APP_SHORTCUT cannot overlap another item")
            }
            if (!candidate.allowOverlap) {
                return PlacementResult.Rejected("WIDGET overlap requires allowOverlap = true")
            }
        }

        return PlacementResult.Ok
    }

    /** True when the two half-open grid rectangles share at least one cell. */
    private fun overlaps(a: GridItemEntity, b: GridItemEntity): Boolean =
        a.col < b.col + b.colSpan &&
            a.col + a.colSpan > b.col &&
            a.row < b.row + b.rowSpan &&
            a.row + a.rowSpan > b.row
}
