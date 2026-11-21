package com.ark.voidlauncher.domain

/** Helpers for interpreting widget references that travel through backup/restore. */
object WidgetReference {
    const val PLACEHOLDER_PREFIX = "placeholder:"

    fun isPlaceholder(referenceId: String): Boolean =
        referenceId.startsWith(PLACEHOLDER_PREFIX)

    fun unwrap(referenceId: String): String =
        referenceId.removePrefix(PLACEHOLDER_PREFIX)

    /** Host-local appWidgetIds are numeric and cannot survive a device change. */
    fun isLiveId(referenceId: String): Boolean =
        referenceId.toIntOrNull() != null

    /**
     * Extracts a portable provider (a flattened ComponentName) when one is stored
     * in the reference. Numeric legacy placeholders and built-in sentinels have
     * no portable provider and return null.
     */
    fun portableProvider(referenceId: String): String? {
        val value = unwrap(referenceId).trim()
        if (value.isBlank()) return null
        if (isLiveId(value)) return null
        if (BuiltInWidgets.fromReference(value) != null) return null
        return value
    }
}
