package com.ark.voidlauncher.data.backup

import com.ark.voidlauncher.domain.CUSTOM_WIDGET_BUNDLE_VERSION
import com.ark.voidlauncher.domain.CUSTOM_WIDGET_ID_REGEX
import com.ark.voidlauncher.domain.CustomWidgetBundle
import com.ark.voidlauncher.domain.PageInfo

/** Outcome of the sidecar pre-import check; the first error is shown to the user. */
data class CustomWidgetValidationResult(val errors: List<String>) {
    val isValid: Boolean get() = errors.isEmpty()
}

/**
 * Pure-JVM hard gate for `voidlauncher-widgets.json`.
 *
 * Unknown runtime strings are preserved for forward compatibility: the renderer
 * draws a labelled placeholder, but the definition is still imported and exported.
 */
object CustomWidgetBundleValidator {

    private const val MAX_PAYLOAD_BYTES = 256 * 1024

    private val EXTERNAL_RESOURCE = Regex(
        """(?i)(<iframe\b|<img\b|<link\b|<script[^>]*\bsrc\s*=|url\s*\(|https?://|file://|content://|javascript:)""",
    )

    fun validate(bundle: CustomWidgetBundle): CustomWidgetValidationResult =
        CustomWidgetValidationResult(
            runCatching { collectErrors(bundle) }
                .getOrDefault(listOf("VALIDATION FAILED")),
        )

    private fun collectErrors(bundle: CustomWidgetBundle): List<String> {
        val errors = ArrayList<String>()
        if (bundle.version != CUSTOM_WIDGET_BUNDLE_VERSION) {
            errors += "UNSUPPORTED WIDGET BUNDLE VERSION ${bundle.version}"
        }

        val seenIds = HashSet<String>()
        for (widget in bundle.widgets) {
            if (!CUSTOM_WIDGET_ID_REGEX.matches(widget.id)) {
                errors += "INVALID WIDGET ID ${widget.id}"
            } else if (!seenIds.add(widget.id)) {
                errors += "DUPLICATE WIDGET ID ${widget.id}"
            }
            if (widget.name.isBlank() || widget.name.length > 64) {
                errors += "INVALID WIDGET NAME ${widget.id}"
            }
            if (widget.runtime.isBlank()) {
                errors += "BLANK WIDGET RUNTIME ${widget.id}"
            }
            if (widget.colSpan !in 1..PageInfo.DEFAULT_GRID_COLS) {
                errors += "INVALID WIDGET COL SPAN ${widget.id}"
            }
            if (widget.rowSpan !in 1..PageInfo.DEFAULT_GRID_ROWS) {
                errors += "INVALID WIDGET ROW SPAN ${widget.id}"
            }
            if (widget.minColSpan !in 1..widget.colSpan) {
                errors += "INVALID WIDGET MIN COL SPAN ${widget.id}"
            }
            if (widget.minRowSpan !in 1..widget.rowSpan) {
                errors += "INVALID WIDGET MIN ROW SPAN ${widget.id}"
            }
            if (widget.html.isBlank()) {
                errors += "BLANK WIDGET HTML ${widget.id}"
            }
            val payloadBytes = widget.html.toByteArray(Charsets.UTF_8).size +
                widget.css.toByteArray(Charsets.UTF_8).size +
                widget.js.toByteArray(Charsets.UTF_8).size
            if (payloadBytes > MAX_PAYLOAD_BYTES) {
                errors += "WIDGET PAYLOAD TOO LARGE ${widget.id}"
            }
            listOf(widget.html, widget.css, widget.js).forEach { source ->
                if (EXTERNAL_RESOURCE.containsMatchIn(source)) {
                    errors += "EXTERNAL WIDGET RESOURCE ${widget.id}"
                }
            }
        }
        return errors
    }
}
