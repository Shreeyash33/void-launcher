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
        if (customId(referenceId) != null) return null
        return value
    }

    /** Extracts the stable id from `custom:<id>`, including a wrapped placeholder. */
    fun customId(referenceId: String): String? {
        val value = unwrap(referenceId).trim()
        if (!value.startsWith(CUSTOM_WIDGET_REFERENCE_PREFIX)) return null
        val id = value.removePrefix(CUSTOM_WIDGET_REFERENCE_PREFIX)
        return id.takeIf { CUSTOM_WIDGET_ID_REGEX.matches(it) }
    }

    /** True when the reference is syntactically a sidecar custom widget. */
    fun isCustom(referenceId: String): Boolean = customId(referenceId) != null

    /** Canonical layout reference for a custom widget id. */
    fun customReference(id: String): String = CUSTOM_WIDGET_REFERENCE_PREFIX + id
}
