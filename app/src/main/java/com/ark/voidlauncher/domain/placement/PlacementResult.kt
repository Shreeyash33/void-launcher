package com.ark.voidlauncher.domain.placement

import com.ark.voidlauncher.domain.SwipeDirection

/**
 * Outcome of validating a candidate item placement against the current page.
 *
 * [Ok] means the item fits and conflicts with nothing. [Rejected] means the
 * placement is invalid for this page and must not be committed. [MoveToNeighbor]
 * means the item overflows a page boundary; the caller should resolve the
 * directed edge for [direction] and re-run placement on that neighbor page.
 */
sealed interface PlacementResult {
    data object Ok : PlacementResult

    data class Rejected(val reason: String) : PlacementResult

    data class MoveToNeighbor(val direction: SwipeDirection) : PlacementResult
}
