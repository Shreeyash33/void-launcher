package com.ark.voidlauncher.domain

/**
 * Portable identity for a web-authored widget.
 *
 * The layout file stores only the reference; the sidecar bundle stores the
 * definition. [runtime] is deliberately a string rather than an enum so a file
 * written by a newer editor round-trips through this launcher even when its
 * renderer is not known yet.
 */
data class CustomWidgetDefinition(
    val id: String,
    val name: String,
    val runtime: String = CUSTOM_WIDGET_RUNTIME_WEBVIEW,
    val colSpan: Int = 4,
    val rowSpan: Int = 2,
    val minColSpan: Int = 1,
    val minRowSpan: Int = 1,
    val allowResize: Boolean = true,
    val allowOverlap: Boolean = true,
    val html: String,
    val css: String = "",
    val js: String = "",
)

/** Version-1 sidecar document. Widget order is preserved for stable exports. */
data class CustomWidgetBundle(
    val version: Int = CUSTOM_WIDGET_BUNDLE_VERSION,
    val widgets: List<CustomWidgetDefinition> = emptyList(),
)

const val CUSTOM_WIDGET_BUNDLE_VERSION = 1
const val CUSTOM_WIDGET_RUNTIME_WEBVIEW = "WEBVIEW"

/** Layout reference prefix for a widget defined in the sidecar bundle. */
const val CUSTOM_WIDGET_REFERENCE_PREFIX = "custom:"

/** Stable, filename-safe IDs; the length is part of the version-1 contract. */
val CUSTOM_WIDGET_ID_REGEX = Regex("^[A-Za-z0-9._-]{1,64}$")
