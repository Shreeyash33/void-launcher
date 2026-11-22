package com.ark.voidlauncher.domain

/**
 * Per-APP_LIST-widget presentation override.
 *
 * Stored in DataStore only (not part of backup v1). Hex validation belongs to the
 * editor UI; this model simply carries the value it is given.
 */
data class AppListAppearance(
    val colorHex: String? = null,
    val dense: Boolean = false,
)
