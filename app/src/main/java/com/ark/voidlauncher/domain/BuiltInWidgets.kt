package com.ark.voidlauncher.domain

/**
 * Built-in widgets are first-class grid items, not Android AppWidgets.
 *
 * [widgetKind] is stored on the grid item, but [referenceId] also carries a
 * stable sentinel so an item copied by an older code path can still be
 * recognised. The prefix cannot collide with a decimal appWidgetId or with a
 * package name.
 */
enum class BuiltInWidgetKind { APP_LIST, CLOCK }

object BuiltInWidgets {
    const val REFERENCE_PREFIX = "builtin:"
    const val LABEL_APP_LIST = "APP LIST"
    const val LABEL_CLOCK = "CLOCK"

    fun reference(kind: BuiltInWidgetKind): String = REFERENCE_PREFIX + kind.name

    fun fromReference(referenceId: String?): BuiltInWidgetKind? =
        referenceId?.takeIf { it.startsWith(REFERENCE_PREFIX) }
            ?.removePrefix(REFERENCE_PREFIX)
            ?.let { runCatching { enumValueOf<BuiltInWidgetKind>(it) }.getOrNull() }

    fun label(kind: BuiltInWidgetKind): String = when (kind) {
        BuiltInWidgetKind.APP_LIST -> LABEL_APP_LIST
        BuiltInWidgetKind.CLOCK -> LABEL_CLOCK
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
