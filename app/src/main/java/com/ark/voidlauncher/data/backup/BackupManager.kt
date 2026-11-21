package com.ark.voidlauncher.data.backup

import android.content.Context
import android.net.Uri
import com.ark.voidlauncher.data.db.AppCustomizationEntity
import com.ark.voidlauncher.domain.BuiltInWidgetKind
import com.ark.voidlauncher.domain.CustomPalette
import com.ark.voidlauncher.domain.EdgeInfo
import com.ark.voidlauncher.domain.FontFamily
import com.ark.voidlauncher.domain.AppFontWeight
import com.ark.voidlauncher.domain.GridItemType
import com.ark.voidlauncher.domain.PageInfo
import com.ark.voidlauncher.domain.PageType
import com.ark.voidlauncher.domain.SwipeDirection
import com.ark.voidlauncher.domain.TextAlign
import com.ark.voidlauncher.domain.TextTransform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Versioned JSON serialization for launcher backups.
 *
 * `toJson` and `fromJson` deliberately touch no Android API: they only use
 * framework `org.json` and Kotlin stdlib, so they can be exercised by plain JVM
 * unit tests. Android-dependent SAF I/O lives in the two suspend functions at
 * the bottom of this object.
 *
 * Every boundary is guarded with `runCatching`; nothing here throws. A corrupt
 * document yields `null`, and an individual malformed row is skipped while the
 * rest of the file is still recovered.
 */
object BackupManager {

    const val FORMAT_VERSION = 1

    private const val INDENT = 2

    // ------------------------------------------------------------------
    // Public codec
    // ------------------------------------------------------------------

    fun toJson(payload: BackupPayload): String = runCatching {
        JSONObject().apply {
            put("version", FORMAT_VERSION)
            put("homeCoordX", payload.homeCoordX)
            put("homeCoordY", payload.homeCoordY)
            put("theme", payload.theme)
            put("orientation", payload.orientation)
            put("showCoordinateDetail", payload.showCoordinateDetail)
            payload.customPalette?.let { put("customPalette", paletteJson(it)) }
            put("backgrounds", arrayJson(payload.backgrounds, ::backgroundJson))
            put("pages", arrayJson(payload.pages, ::pageJson))
            put("edges", arrayJson(payload.edges, ::edgeJson))
            put("items", arrayJson(payload.items, ::itemJson))
            put(
                "customizations",
                arrayJson(payload.customizations, ::customizationJson),
            )
        }.toString(INDENT)
    }.getOrDefault("")

    fun fromJson(json: String): BackupPayload? = runCatching {
        val root = JSONObject(json)
        val version = root.optInt("version", -1)
        if (version != FORMAT_VERSION) return@runCatching null

        BackupPayload(
            version = version,
            pages = rows(root.arrayOrNull("pages"), ::pageFromJson),
            edges = rows(root.arrayOrNull("edges"), ::edgeFromJson),
            items = rows(root.arrayOrNull("items"), ::itemFromJson),
            customizations = rows(
                root.arrayOrNull("customizations"),
                ::customizationFromJson,
            ),
            homeCoordX = root.optInt("homeCoordX", 0),
            homeCoordY = root.optInt("homeCoordY", 0),
            theme = root.nullableString("theme") ?: "",
            orientation = root.nullableString("orientation") ?: "PORTRAIT",
            showCoordinateDetail = root.optBoolean("showCoordinateDetail", true),
            customPalette = runCatching {
                root.optJSONObject("customPalette")?.let(::paletteFromJson)
            }.getOrNull(),
            backgrounds = rows(root.arrayOrNull("backgrounds"), ::backgroundFromJson),
        )
    }.getOrNull()

    // ------------------------------------------------------------------
    // SAF I/O (Android side)
    // ------------------------------------------------------------------

    /**
     * Returns `true` only when serialization produced a non-empty document,
     * the SAF output stream opened, and the UTF-8 write completed without
     * throwing. Every failure path (including a null stream or a failing
     * close) resolves to `false`; this function never throws.
     */
    suspend fun writeToUri(context: Context, uri: Uri, payload: BackupPayload): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val json = toJson(payload)
                if (json.isEmpty()) {
                    false
                } else {
                    val output = context.contentResolver.openOutputStream(uri)
                    if (output == null) {
                        false
                    } else {
                        output.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                        true
                    }
                }
            }.getOrDefault(false)
        }

    suspend fun readFromUri(context: Context, uri: Uri): BackupPayload? =
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    fromJson(input.readBytes().toString(Charsets.UTF_8))
                }
            }.getOrNull()
        }

    // ------------------------------------------------------------------
    // Serialization helpers
    // ------------------------------------------------------------------

    private fun <T> arrayJson(items: List<T>, toJson: (T) -> JSONObject): JSONArray {
        val array = JSONArray()
        for (item in items) {
            array.put(toJson(item))
        }
        return array
    }

    private fun pageJson(page: PageInfo): JSONObject = JSONObject().apply {
        put("pageId", page.pageId)
        put("coordX", page.coordX)
        put("coordY", page.coordY)
        put("title", page.title)
        put("gridCols", page.gridCols)
        put("gridRows", page.gridRows)
        put("isSecret", page.isSecret)
        put("pageType", page.pageType.name)
    }

    private fun edgeJson(edge: EdgeInfo): JSONObject = JSONObject().apply {
        put("fromPageId", edge.fromPageId)
        put("swipeDirection", edge.swipeDirection.name)
        put("toPageId", edge.toPageId)
    }

    private fun itemJson(item: BackedUpItem): JSONObject = JSONObject().apply {
        put("pageCoordX", item.pageCoordX)
        put("pageCoordY", item.pageCoordY)
        put("itemType", item.itemType.name)
        put("col", item.col)
        put("row", item.row)
        put("colSpan", item.colSpan)
        put("rowSpan", item.rowSpan)
        // Portable identity only: never the live appWidgetId.
        put("referenceId", item.referenceId)
        put("allowOverlap", item.allowOverlap)
        item.widgetKind?.let { put("widgetKind", it.name) }
        item.appListPackages?.let { put("appListPackages", it) }
    }

    private fun customizationJson(c: AppCustomizationEntity): JSONObject = JSONObject().apply {
        put("packageName", c.packageName)
        put("isFavorite", c.isFavorite)
        put("isHidden", c.isHidden)
        c.customNickname?.let { put("customNickname", it) }
        c.fontFamily?.let { put("fontFamily", it.name) }
        c.fontSizeSp?.let { put("fontSizeSp", it.toDouble()) }
        c.fontWeight?.let { put("fontWeight", it.name) }
        c.letterSpacingSp?.let { put("letterSpacingSp", it.toDouble()) }
        c.textColorHex?.let { put("textColorHex", it) }
        c.textAlign?.let { put("textAlign", it.name) }
        c.textTransform?.let { put("textTransform", it.name) }
    }

    private fun paletteJson(p: CustomPalette): JSONObject = JSONObject().apply {
        put("background", p.background)
        put("surface", p.surface)
        put("surfaceVariant", p.surfaceVariant)
        put("onBackground", p.onBackground)
        put("onSurface", p.onSurface)
        put("primary", p.primary)
        put("secondary", p.secondary)
        put("outline", p.outline)
    }

    private fun backgroundJson(b: BackedUpBackground): JSONObject = JSONObject().apply {
        put("scope", b.scope)
        b.pageCoordX?.let { put("pageCoordX", it) }
        b.pageCoordY?.let { put("pageCoordY", it) }
        put("kind", b.kind)
        b.colorHex?.let { put("colorHex", it) }
        b.gradientSpec?.let { put("gradientSpec", it) }
        put("gradientAngle", b.gradientAngle.toDouble())
        b.imageToken?.let { put("imageToken", it) }
        b.imageBase64?.let { put("imageBase64", it) }
    }

    // ------------------------------------------------------------------
    // Deserialization helpers — a null result means "skip this row".
    // ------------------------------------------------------------------

    private fun JSONObject.arrayOrNull(key: String): JSONArray? = runCatching {
        if (has(key) && !isNull(key)) getJSONArray(key) else null
    }.getOrNull()

    private fun JSONObject.nullableString(key: String): String? = runCatching {
        if (has(key) && !isNull(key)) getString(key) else null
    }.getOrNull()

    private fun JSONObject.nullableFloat(key: String): Float? = runCatching {
        if (has(key) && !isNull(key)) getDouble(key).toFloat() else null
    }.getOrNull()

    private fun JSONObject.nullableInt(key: String): Int? = runCatching {
        if (has(key) && !isNull(key)) getInt(key) else null
    }.getOrNull()

    private inline fun <reified T : Enum<T>> JSONObject.nullableEnum(key: String): T? {
        val raw = nullableString(key) ?: return null
        return runCatching { enumValueOf<T>(raw) }.getOrNull()
    }

    private fun <T> rows(array: JSONArray?, parse: (JSONObject) -> T?): List<T> {
        if (array == null) return emptyList()
        val result = ArrayList<T>()
        val length = runCatching { array.length() }.getOrDefault(0)
        for (i in 0 until length) {
            val obj = runCatching { array.getJSONObject(i) }.getOrNull() ?: continue
            val parsed = runCatching { parse(obj) }.getOrNull() ?: continue
            result.add(parsed)
        }
        return result
    }

    private fun pageFromJson(obj: JSONObject): PageInfo? = runCatching {
        PageInfo(
            pageId = obj.getLong("pageId"),
            coordX = obj.getInt("coordX"),
            coordY = obj.getInt("coordY"),
            title = obj.getString("title"),
            gridCols = obj.optInt("gridCols", PageInfo.DEFAULT_GRID_COLS),
            gridRows = obj.optInt("gridRows", PageInfo.DEFAULT_GRID_ROWS),
            isSecret = obj.optBoolean("isSecret", false),
            pageType = obj.nullableEnum<PageType>("pageType") ?: PageType.WIDGET,
        )
    }.getOrNull()

    private fun edgeFromJson(obj: JSONObject): EdgeInfo? = runCatching {
        EdgeInfo(
            fromPageId = obj.getLong("fromPageId"),
            swipeDirection = enumValueOf<SwipeDirection>(obj.getString("swipeDirection")),
            toPageId = obj.getLong("toPageId"),
        )
    }.getOrNull()

    private fun itemFromJson(obj: JSONObject): BackedUpItem? = runCatching {
        BackedUpItem(
            pageCoordX = obj.getInt("pageCoordX"),
            pageCoordY = obj.getInt("pageCoordY"),
            itemType = enumValueOf<GridItemType>(obj.getString("itemType")),
            col = obj.getInt("col"),
            row = obj.getInt("row"),
            colSpan = obj.getInt("colSpan"),
            rowSpan = obj.getInt("rowSpan"),
            referenceId = obj.getString("referenceId"),
            allowOverlap = obj.optBoolean("allowOverlap", false),
            widgetKind = obj.nullableEnum<BuiltInWidgetKind>("widgetKind"),
            appListPackages = obj.nullableString("appListPackages"),
        )
    }.getOrNull()

    private fun customizationFromJson(obj: JSONObject): AppCustomizationEntity? = runCatching {
        val packageName = obj.getString("packageName")
        if (packageName.isBlank()) return@runCatching null
        AppCustomizationEntity(
            packageName = packageName,
            isFavorite = obj.optBoolean("isFavorite", false),
            isHidden = obj.optBoolean("isHidden", false),
            customNickname = obj.nullableString("customNickname"),
            fontFamily = obj.nullableEnum<FontFamily>("fontFamily"),
            fontSizeSp = obj.nullableFloat("fontSizeSp"),
            fontWeight = obj.nullableEnum<AppFontWeight>("fontWeight"),
            letterSpacingSp = obj.nullableFloat("letterSpacingSp"),
            textColorHex = obj.nullableString("textColorHex"),
            textAlign = obj.nullableEnum<TextAlign>("textAlign"),
            textTransform = obj.nullableEnum<TextTransform>("textTransform"),
        )
    }.getOrNull()

    private fun paletteFromJson(obj: JSONObject): CustomPalette? = runCatching {
        CustomPalette(
            background = obj.getString("background"),
            surface = obj.getString("surface"),
            surfaceVariant = obj.getString("surfaceVariant"),
            onBackground = obj.getString("onBackground"),
            onSurface = obj.getString("onSurface"),
            primary = obj.getString("primary"),
            secondary = obj.getString("secondary"),
            outline = obj.getString("outline"),
        )
    }.getOrNull()

    private fun backgroundFromJson(obj: JSONObject): BackedUpBackground? = runCatching {
        val scope = obj.getString("scope")
        val kind = obj.getString("kind")
        if (scope.isBlank() || kind.isBlank()) return@runCatching null
        BackedUpBackground(
            scope = scope,
            pageCoordX = obj.nullableInt("pageCoordX"),
            pageCoordY = obj.nullableInt("pageCoordY"),
            kind = kind,
            colorHex = obj.nullableString("colorHex"),
            gradientSpec = obj.nullableString("gradientSpec"),
            gradientAngle = obj.nullableFloat("gradientAngle") ?: 0f,
            imageToken = obj.nullableString("imageToken"),
            imageBase64 = obj.nullableString("imageBase64"),
        )
    }.getOrNull()
}
