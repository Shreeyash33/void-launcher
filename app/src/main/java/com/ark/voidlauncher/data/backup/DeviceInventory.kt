package com.ark.voidlauncher.data.backup

import android.content.Context
import android.net.Uri
import com.ark.voidlauncher.domain.PageInfo
import org.json.JSONArray
import org.json.JSONObject

/** Export-only snapshot that lets the frontend editor know what a device can render. */
data class DeviceInventory(
    val version: Int = INVENTORY_VERSION,
    val generatedAt: String,
    val gridCols: Int = PageInfo.DEFAULT_GRID_COLS,
    val gridRows: Int = PageInfo.DEFAULT_GRID_ROWS,
    val builtInWidgets: List<String>,
    val apps: List<InventoryApp>,
    val systemWidgets: List<InventorySystemWidget>,
)

data class InventoryApp(val packageName: String, val label: String)

data class InventorySystemWidget(
    val provider: String,
    val label: String,
    val minColSpan: Int,
    val minRowSpan: Int,
)

const val INVENTORY_VERSION = 1

/** Codec for `voidlauncher-inventory.json`. The launcher never imports this file. */
object DeviceInventoryManager {

    const val FILE_NAME = "voidlauncher-inventory.json"

    private const val INDENT = 2

    fun toJson(inventory: DeviceInventory): String = runCatching {
        JSONObject().apply {
            put("version", INVENTORY_VERSION)
            put("generatedAt", inventory.generatedAt)
            put("grid", JSONObject().put("cols", inventory.gridCols).put("rows", inventory.gridRows))
            put("builtInWidgets", JSONArray(inventory.builtInWidgets))
            put("apps", JSONArray().apply {
                inventory.apps.forEach { app ->
                    put(JSONObject().put("packageName", app.packageName).put("label", app.label))
                }
            })
            put("systemWidgets", JSONArray().apply {
                inventory.systemWidgets.forEach { widget ->
                    put(
                        JSONObject()
                            .put("provider", widget.provider)
                            .put("label", widget.label)
                            .put("minColSpan", widget.minColSpan)
                            .put("minRowSpan", widget.minRowSpan),
                    )
                }
            })
        }.toString(INDENT)
    }.getOrDefault("")

    fun fromJson(json: String): DeviceInventory? = runCatching {
        val root = JSONObject(json)
        if (root.optInt("version", -1) != INVENTORY_VERSION) return@runCatching null
        val grid = root.getJSONObject("grid")
        DeviceInventory(
            version = INVENTORY_VERSION,
            generatedAt = root.getString("generatedAt"),
            gridCols = grid.getInt("cols"),
            gridRows = grid.getInt("rows"),
            builtInWidgets = root.getJSONArray("builtInWidgets").let { array ->
                List(array.length()) { array.getString(it) }
            },
            apps = root.getJSONArray("apps").let { array ->
                List(array.length()) { i ->
                    val obj = array.getJSONObject(i)
                    InventoryApp(obj.getString("packageName"), obj.getString("label"))
                }
            },
            systemWidgets = root.getJSONArray("systemWidgets").let { array ->
                List(array.length()) { i ->
                    val obj = array.getJSONObject(i)
                    InventorySystemWidget(
                        provider = obj.getString("provider"),
                        label = obj.getString("label"),
                        minColSpan = obj.getInt("minColSpan"),
                        minRowSpan = obj.getInt("minRowSpan"),
                    )
                }
            },
        )
    }.getOrNull()

    suspend fun writeToUri(context: Context, uri: Uri, inventory: DeviceInventory): Boolean =
        JsonDocumentIo.write(context, uri, toJson(inventory))
}
