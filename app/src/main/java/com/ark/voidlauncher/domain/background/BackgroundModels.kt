package com.ark.voidlauncher.domain.background

/** Pure JVM background domain types. No Android/Compose imports by design. */

enum class BackgroundKind { COLOR, GRADIENT, IMAGE }

enum class BackgroundScope { GLOBAL, PAGE }

/** A single gradient colour stop. [position] is normalised to 0f..1f. */
data class GradientStop(val colorHex: String, val position: Float)

/**
 * A saved background. [pageId] is non-null iff [scope] is [BackgroundScope.PAGE].
 * [imageToken] is an app-internal filename only — never a content:// URI.
 */
data class PageBackground(
    val id: Long = 0L,
    val scope: BackgroundScope,
    val pageId: Long? = null,
    val kind: BackgroundKind,
    val colorHex: String? = null,
    val gradientStops: List<GradientStop> = emptyList(),
    val gradientAngle: Float = 0f,
    val imageToken: String? = null,
)

/** Inclusive bounds for the number of stops in a valid gradient. */
const val MIN_GRADIENT_STOPS = 2
const val MAX_GRADIENT_STOPS = 5

private val HEX_REGEX = Regex("^#(?:[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")

/**
 * Accepts both `#RRGGBB` and `#AARRGGBB` (case-insensitive) so UI colour pickers
 * and imported payloads can use either form without crashing the renderer.
 */
fun isValidHex(value: String): Boolean = HEX_REGEX.matches(value)

/**
 * A gradient needs 2..5 valid, finite stops in 0f..1f sorted by position.
 * Equal neighbouring positions are allowed (hard colour transitions are legal).
 */
fun isValidStops(stops: List<GradientStop>): Boolean {
    if (stops.size !in MIN_GRADIENT_STOPS..MAX_GRADIENT_STOPS) return false
    var previous = -1f
    for (stop in stops) {
        if (!isValidHex(stop.colorHex)) return false
        val position = stop.position
        if (position.isNaN() || position.isInfinite()) return false
        if (position < 0f || position > 1f || position < previous) return false
        previous = position
    }
    return true
}

/**
 * Resolves the effective background for a page: page-specific first, then the
 * single global row, then nothing. Duplicate entries are handled defensively by
 * preferring the lowest id, which keeps the result stable if the DB is dirty.
 */
object BackgroundResolver {
    fun resolve(pageId: Long, all: List<PageBackground>): PageBackground? {
        val pageSpecific = all
            .filter { it.scope == BackgroundScope.PAGE && it.pageId == pageId }
            .minByOrNull { it.id }
        if (pageSpecific != null) return pageSpecific

        return all
            .filter { it.scope == BackgroundScope.GLOBAL }
            .minByOrNull { it.id }
    }
}
