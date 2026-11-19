package com.ark.voidlauncher.domain.background

/**
 * Pure-JVM codec for gradient stops. Storage format:
 *
 * ```
 * #AARRGGBB@0.0|#AARRGGBB@1.0
 * ```
 *
 * `Float.toString()` is locale-independent and round-trips an exact Float value,
 * so it is used instead of a locale-sensitive `String.format`.
 */
object GradientCodec {
    private const val STOP_SEPARATOR = "|"
    private const val POSITION_SEPARATOR = "@"

    /** Stops are sorted before encoding; invalid input is still encoded verbatim. */
    fun encode(stops: List<GradientStop>): String =
        stops.sortedBy { it.position }.joinToString(STOP_SEPARATOR) { stop ->
            stop.colorHex + POSITION_SEPARATOR + stop.position.toString()
        }

    /**
     * Defensive parse: any malformed segment, out-of-range position, duplicate
     * count outside 2..5, or non-monotonic order yields an empty list rather than
     * throwing, so a corrupt row can never break background rendering.
     */
    fun decode(spec: String?): List<GradientStop> {
        if (spec.isNullOrBlank()) return emptyList()
        val segments = spec.split(STOP_SEPARATOR)
        if (segments.size !in MIN_GRADIENT_STOPS..MAX_GRADIENT_STOPS) return emptyList()

        val parsed = segments.map(::parseStop)
        if (parsed.any { it == null }) return emptyList()

        val stops = parsed.filterNotNull().sortedBy { it.position }
        return if (isValidStops(stops)) stops else emptyList()
    }

    private fun parseStop(segment: String): GradientStop? {
        // Use the last '@' so a future colour syntax containing '@' stays parseable.
        val separator = segment.lastIndexOf('@')
        if (separator <= 0 || separator == segment.length - 1) return null
        val colorHex = segment.substring(0, separator)
        val position = segment.substring(separator + 1).toFloatOrNull() ?: return null
        if (!isValidHex(colorHex)) return null
        if (position.isNaN() || position.isInfinite()) return null
        if (position < 0f || position > 1f) return null
        return GradientStop(colorHex, position)
    }
}

/** Convenience wrappers so callers need not import the codec object name. */
fun encodeGradientStops(stops: List<GradientStop>): String = GradientCodec.encode(stops)

fun decodeGradientStops(spec: String?): List<GradientStop> = GradientCodec.decode(spec)

/** Short aliases matching the frozen contract's `encode` / `decode` wording. */
fun encode(stops: List<GradientStop>): String = GradientCodec.encode(stops)

fun decode(spec: String?): List<GradientStop> = GradientCodec.decode(spec)
