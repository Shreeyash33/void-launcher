package com.ark.voidlauncher.data.backup

import android.content.Context
import android.net.Uri
import com.ark.voidlauncher.domain.CUSTOM_WIDGET_BUNDLE_VERSION
import com.ark.voidlauncher.domain.CUSTOM_WIDGET_RUNTIME_WEBVIEW
import com.ark.voidlauncher.domain.CustomWidgetBundle
import com.ark.voidlauncher.domain.CustomWidgetDefinition
import org.json.JSONArray
import org.json.JSONObject

/**
 * Codec for the custom widget sidecar file.
 *
 * Unlike the layout codec, a malformed widget row is fatal: the bundle validator
 * is a hard import gate and silently dropping a definition would turn a placed
 * widget into a confusing placeholder. Every public call is non-throwing.
 */
object CustomWidgetBundleManager {

    const val FILE_NAME = "voidlauncher-widgets.json"
    const val VERSION = CUSTOM_WIDGET_BUNDLE_VERSION

    private const val INDENT = 2

    fun toJson(bundle: CustomWidgetBundle): String = runCatching {
        JSONObject().apply {
            put("version", VERSION)
            val array = JSONArray()
            bundle.widgets.forEach { array.put(widgetJson(it)) }
            put("widgets", array)
        }.toString(INDENT)
    }.getOrDefault("")

    fun fromJson(json: String): CustomWidgetBundle? = runCatching {
        val root = JSONObject(json)
        if (root.optInt("version", -1) != VERSION) return@runCatching null
        val rows = root.optJSONArray("widgets") ?: return@runCatching null
        val widgets = ArrayList<CustomWidgetDefinition>(rows.length())
        for (i in 0 until rows.length()) {
            widgets += widgetFromJson(rows.getJSONObject(i)) ?: return@runCatching null
        }
        CustomWidgetBundle(version = VERSION, widgets = widgets)
    }.getOrNull()

    suspend fun writeToUri(context: Context, uri: Uri, bundle: CustomWidgetBundle): Boolean =
        JsonDocumentIo.write(context, uri, toJson(bundle))

    suspend fun readFromUri(context: Context, uri: Uri): CustomWidgetBundle? =
        JsonDocumentIo.read(context, uri)?.let(::fromJson)

    private fun widgetJson(widget: CustomWidgetDefinition): JSONObject = JSONObject().apply {
        put("id", widget.id)
        put("name", widget.name)
        put("runtime", widget.runtime)
        put("colSpan", widget.colSpan)
        put("rowSpan", widget.rowSpan)
        put("minColSpan", widget.minColSpan)
        put("minRowSpan", widget.minRowSpan)
        put("allowResize", widget.allowResize)
        put("allowOverlap", widget.allowOverlap)
        put("html", widget.html)
        put("css", widget.css)
        put("js", widget.js)
    }

    private fun widgetFromJson(obj: JSONObject): CustomWidgetDefinition? = runCatching {
        CustomWidgetDefinition(
            id = obj.getString("id"),
            name = obj.getString("name"),
            runtime = obj.optString("runtime", CUSTOM_WIDGET_RUNTIME_WEBVIEW),
            colSpan = obj.getInt("colSpan"),
            rowSpan = obj.getInt("rowSpan"),
            minColSpan = obj.optInt("minColSpan", 1),
            minRowSpan = obj.optInt("minRowSpan", 1),
            allowResize = obj.optBoolean("allowResize", true),
            allowOverlap = obj.optBoolean("allowOverlap", true),
            html = obj.getString("html"),
            css = obj.optString("css", ""),
            js = obj.optString("js", ""),
        )
    }.getOrNull()
}
