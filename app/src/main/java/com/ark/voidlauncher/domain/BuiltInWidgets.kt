package com.ark.voidlauncher.domain

/**
 * Built-in widgets are first-class grid items, not Android AppWidgets.
 *
 * [widgetKind] is stored on the grid item, but [referenceId] also carries a
 * stable sentinel so an item copied by an older code path can still be
 * recognised. The prefix cannot collide with a decimal appWidgetId or with a
 * package name.
 */
enum class BuiltInWidgetKind { APP_LIST, CLOCK, PAGE_INFO }

object BuiltInWidgets {
    const val REFERENCE_PREFIX = "builtin:"
    const val LABEL_APP_LIST = "APP LIST"
    const val LABEL_CLOCK = "CLOCK"
    const val LABEL_PAGE_INFO = "PAGE INFO"

    const val CLOCK_COL_SPAN = 4
    const val CLOCK_ROW_SPAN = 2
    const val APP_LIST_COL_SPAN = 4
    const val APP_LIST_ROW_SPAN = 3
    const val PAGE_INFO_COL_SPAN = 2
    const val PAGE_INFO_ROW_SPAN = 2

    /** Geometry used when a built-in widget is placed without explicit spans. */
    data class BuiltInSpan(val colSpan: Int, val rowSpan: Int)

    fun defaultSpan(kind: BuiltInWidgetKind): BuiltInSpan = when (kind) {
        BuiltInWidgetKind.CLOCK -> BuiltInSpan(CLOCK_COL_SPAN, CLOCK_ROW_SPAN)
        BuiltInWidgetKind.APP_LIST -> BuiltInSpan(APP_LIST_COL_SPAN, APP_LIST_ROW_SPAN)
        BuiltInWidgetKind.PAGE_INFO -> BuiltInSpan(PAGE_INFO_COL_SPAN, PAGE_INFO_ROW_SPAN)
    }

    fun reference(kind: BuiltInWidgetKind): String = REFERENCE_PREFIX + kind.name

    fun fromReference(referenceId: String?): BuiltInWidgetKind? =
        referenceId?.takeIf { it.startsWith(REFERENCE_PREFIX) }
            ?.removePrefix(REFERENCE_PREFIX)
            ?.let { runCatching { enumValueOf<BuiltInWidgetKind>(it) }.getOrNull() }

    fun label(kind: BuiltInWidgetKind): String = when (kind) {
        BuiltInWidgetKind.APP_LIST -> LABEL_APP_LIST
        BuiltInWidgetKind.CLOCK -> LABEL_CLOCK
        BuiltInWidgetKind.PAGE_INFO -> LABEL_PAGE_INFO
    }

    /** Newline is not legal inside an Android package name, so it is a safe delimiter. */
    fun encodePackages(packages: List<String>): String? =
        packages.map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .joinToString(separator = "\n")
            .takeIf { it.isNotBlank() }

    fun decodePackages(encoded: String?): List<String> =
        encoded?.split('\n')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?.sorted()
            ?: emptyList()
}
