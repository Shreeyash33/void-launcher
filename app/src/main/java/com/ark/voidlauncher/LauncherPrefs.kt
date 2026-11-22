package com.ark.voidlauncher

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ark.voidlauncher.data.backup.CustomWidgetBundleManager
import com.ark.voidlauncher.domain.AppListAppearance
import com.ark.voidlauncher.domain.CustomPalette
import com.ark.voidlauncher.domain.CustomWidgetBundle
import com.ark.voidlauncher.domain.GestureAction
import com.ark.voidlauncher.domain.GestureBinding
import com.ark.voidlauncher.domain.GestureBindings
import com.ark.voidlauncher.domain.GestureScope
import com.ark.voidlauncher.domain.GestureTrigger
import com.ark.voidlauncher.domain.OrientationMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "void_launcher_prefs")

enum class ThemeMode { SYSTEM, DARK, LIGHT, CUSTOM }

/** Small persistent store for pinned apps and display settings. */
class LauncherPrefs(private val context: Context) {
    companion object {
        private val PINNED = stringSetPreferencesKey("pinned_packages")
        private val THEME = stringPreferencesKey("theme")
        private val HOME_X = intPreferencesKey("home_coord_x")
        private val HOME_Y = intPreferencesKey("home_coord_y")
        private val ORIENTATION = stringPreferencesKey("orientation")
        private val SHOW_COORDINATE_DETAIL = booleanPreferencesKey("show_coordinate_detail")
        private val GESTURE_BINDINGS_GLOBAL = stringSetPreferencesKey("gesture_bindings_global")
        private val GESTURE_BINDINGS_PAGES = stringSetPreferencesKey("gesture_bindings_pages")
        private val CUSTOM_WIDGET_BUNDLE = stringPreferencesKey("custom_widget_bundle_v1")

        // DataStore-only overrides (deliberately outside backup v1, like gesture bindings).
        private val DRAWER_NICKNAMES = stringPreferencesKey("drawer_nicknames_v1")
        private val APP_LIST_APPEARANCES = stringPreferencesKey("app_list_appearances_v1")

        private const val OVERRIDE_VERSION = 1
        private const val JSON_VERSION = "version"
        private const val JSON_ENTRIES = "entries"
        private const val JSON_COLOR_HEX = "colorHex"
        private const val JSON_DENSE = "dense"

        private val CUSTOM_BACKGROUND = stringPreferencesKey("custom_bg")
        private val CUSTOM_SURFACE = stringPreferencesKey("custom_surface")
        private val CUSTOM_SURFACE_VARIANT = stringPreferencesKey("custom_surface_variant")
        private val CUSTOM_ON_BACKGROUND = stringPreferencesKey("custom_on_bg")
        private val CUSTOM_ON_SURFACE = stringPreferencesKey("custom_on_surface")
        private val CUSTOM_PRIMARY = stringPreferencesKey("custom_primary")
        private val CUSTOM_SECONDARY = stringPreferencesKey("custom_secondary")
        private val CUSTOM_OUTLINE = stringPreferencesKey("custom_outline")

        private const val THEME_SYSTEM = "system"
        private const val THEME_DARK = "dark"
        private const val THEME_LIGHT = "light"
        private const val THEME_CUSTOM = "custom"

        private const val ORIENTATION_PORTRAIT = "portrait"
        private const val ORIENTATION_LANDSCAPE = "landscape"
        private const val ORIENTATION_AUTO = "auto"

        /** Encodings: G|<TRIGGER>|<ACTION> and P|<x>|<y>|<TRIGGER>|<ACTION>. */
        private fun encodeBinding(
            scope: GestureScope,
            pageCoord: Pair<Int, Int>?,
            trigger: GestureTrigger,
            action: GestureAction,
        ): String = when (scope) {
            GestureScope.GLOBAL -> "G|${trigger.name}|${action.name}"
            GestureScope.PAGE -> "P|${pageCoord!!.first}|${pageCoord.second}|${trigger.name}|${action.name}"
        }

        /** True when [raw] encodes any action for this exact scope/coord/trigger. */
        private fun isBindingFor(
            raw: String,
            scope: GestureScope,
            pageCoord: Pair<Int, Int>?,
            trigger: GestureTrigger,
        ): Boolean {
            return when (scope) {
                GestureScope.GLOBAL -> {
                    val parts = raw.split('|')
                    parts.size == 3 && parts[0] == "G" && parts[1] == trigger.name
                }
                GestureScope.PAGE -> {
                    val coord = pageCoord ?: return false
                    val parts = raw.split('|')
                    parts.size == 5 && parts[0] == "P" &&
                        parts[1] == coord.first.toString() &&
                        parts[2] == coord.second.toString() &&
                        parts[3] == trigger.name
                }
            }
        }

        private fun decodeGlobalBinding(raw: String): GestureBinding? {
            val parts = raw.split('|')
            if (parts.size != 3 || parts[0] != "G") return null
            val trigger = runCatching { GestureTrigger.valueOf(parts[1]) }.getOrNull() ?: return null
            val action = runCatching { GestureAction.valueOf(parts[2]) }.getOrNull() ?: return null
            if (action == GestureAction.NONE) return null
            return GestureBinding(trigger, action)
        }

        private fun decodePageBinding(raw: String): Pair<Pair<Int, Int>, GestureBinding>? {
            val parts = raw.split('|')
            if (parts.size != 5 || parts[0] != "P") return null
            val x = parts[1].toIntOrNull() ?: return null
            val y = parts[2].toIntOrNull() ?: return null
            val trigger = runCatching { GestureTrigger.valueOf(parts[3]) }.getOrNull() ?: return null
            val action = runCatching { GestureAction.valueOf(parts[4]) }.getOrNull() ?: return null
            if (action == GestureAction.NONE) return null
            return (x to y) to GestureBinding(trigger, action)
        }

        /** Encodes nickname overrides as `{"version":1,"entries":{"<package>":"<label>"}}`. */
        private fun encodeDrawerNicknames(nicknames: Map<String, String>): String = runCatching {
            val entries = JSONObject()
            nicknames.forEach { (packageName, label) ->
                if (packageName.isNotBlank() && label.isNotBlank()) {
                    entries.put(packageName, label)
                }
            }
            JSONObject()
                .put(JSON_VERSION, OVERRIDE_VERSION)
                .put(JSON_ENTRIES, entries)
                .toString()
        }.getOrDefault("")

        /** Any malformed row is skipped; a bad document yields an empty map. */
        private fun decodeDrawerNicknames(raw: String?): Map<String, String> = runCatching {
            if (raw.isNullOrBlank()) return@runCatching emptyMap()
            val root = JSONObject(raw)
            if (root.optInt(JSON_VERSION, -1) != OVERRIDE_VERSION) return@runCatching emptyMap()
            val entries = root.optJSONObject(JSON_ENTRIES) ?: return@runCatching emptyMap()
            val decoded = LinkedHashMap<String, String>()
            val keys = entries.keys()
            while (keys.hasNext()) {
                val packageName = keys.next() ?: continue
                val label = entries.opt(packageName) as? String ?: continue
                if (packageName.isNotBlank() && label.isNotBlank()) {
                    decoded[packageName] = label
                }
            }
            decoded.toMap()
        }.getOrDefault(emptyMap())

        /** Encodes appearance overrides as `{"version":1,"entries":{"<itemId>":{...}}}`. */
        private fun encodeAppListAppearances(
            appearances: Map<Long, AppListAppearance>,
        ): String = runCatching {
            val entries = JSONObject()
            appearances.forEach { (itemId, appearance) ->
                val row = JSONObject()
                appearance.colorHex?.takeIf { it.isNotBlank() }?.let { row.put(JSON_COLOR_HEX, it) }
                row.put(JSON_DENSE, appearance.dense)
                entries.put(itemId.toString(), row)
            }
            JSONObject()
                .put(JSON_VERSION, OVERRIDE_VERSION)
                .put(JSON_ENTRIES, entries)
                .toString()
        }.getOrDefault("")

        /** Unknown ids, bad rows and bad colours are ignored without throwing. */
        private fun decodeAppListAppearances(raw: String?): Map<Long, AppListAppearance> = runCatching {
            if (raw.isNullOrBlank()) return@runCatching emptyMap()
            val root = JSONObject(raw)
            if (root.optInt(JSON_VERSION, -1) != OVERRIDE_VERSION) return@runCatching emptyMap()
            val entries = root.optJSONObject(JSON_ENTRIES) ?: return@runCatching emptyMap()
            val decoded = LinkedHashMap<Long, AppListAppearance>()
            val keys = entries.keys()
            while (keys.hasNext()) {
                val itemId = (keys.next() ?: continue).toLongOrNull() ?: continue
                val row = entries.opt(itemId.toString()) as? JSONObject ?: continue
                val colorHex = (row.opt(JSON_COLOR_HEX) as? String)?.takeIf { it.isNotBlank() }
                decoded[itemId] = AppListAppearance(
                    colorHex = colorHex,
                    dense = row.optBoolean(JSON_DENSE, false),
                )
            }
            decoded.toMap()
        }.getOrDefault(emptyMap())
    }

    /** Shared DataStore instance so migrations read the same file as prefs. */
    val dataStore: DataStore<Preferences> get() = context.dataStore

    val pinnedApps: Flow<Set<String>> = context.dataStore.data.map { it[PINNED] ?: emptySet() }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        when (prefs[THEME]) {
            THEME_DARK -> ThemeMode.DARK
            THEME_LIGHT -> ThemeMode.LIGHT
            THEME_CUSTOM -> ThemeMode.CUSTOM
            else -> ThemeMode.SYSTEM
        }
    }

    /** Portrait is the default so the launcher never tilts unexpectedly. */
    val orientationMode: Flow<OrientationMode> = context.dataStore.data.map { prefs ->
        when (prefs[ORIENTATION]) {
            ORIENTATION_LANDSCAPE -> OrientationMode.LANDSCAPE
            ORIENTATION_AUTO -> OrientationMode.AUTO
            else -> OrientationMode.PORTRAIT
        }
    }

    /** Persisted custom palette. Falls back to the warm dark default on a fresh install. */
    val customPalette: Flow<CustomPalette> = context.dataStore.data.map { prefs ->
        val fallback = CustomPalette.darkDefault()
        CustomPalette(
            background = prefs[CUSTOM_BACKGROUND] ?: fallback.background,
            surface = prefs[CUSTOM_SURFACE] ?: fallback.surface,
            surfaceVariant = prefs[CUSTOM_SURFACE_VARIANT] ?: fallback.surfaceVariant,
            onBackground = prefs[CUSTOM_ON_BACKGROUND] ?: fallback.onBackground,
            onSurface = prefs[CUSTOM_ON_SURFACE] ?: fallback.onSurface,
            primary = prefs[CUSTOM_PRIMARY] ?: fallback.primary,
            secondary = prefs[CUSTOM_SECONDARY] ?: fallback.secondary,
            outline = prefs[CUSTOM_OUTLINE] ?: fallback.outline,
        )
    }

    /** Configurable home coordinate. Defaults to the origin (0, 0). */
    val homeCoord: Flow<Pair<Int, Int>> = context.dataStore.data.map { prefs ->
        Pair(prefs[HOME_X] ?: 0, prefs[HOME_Y] ?: 0)
    }

    /** Whether the small coordinate label is shown on pages and map tiles. */
    val showCoordinateDetail: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SHOW_COORDINATE_DETAIL] ?: true
    }

    /** Sidecar widget definitions; an unreadable value falls back to an empty bundle. */
    val customWidgetBundle: Flow<CustomWidgetBundle> = context.dataStore.data.map { prefs ->
        prefs[CUSTOM_WIDGET_BUNDLE]?.let(CustomWidgetBundleManager::fromJson)
            ?: CustomWidgetBundle()
    }

    /** Drawer-only nicknames keyed by package. Never falls back to Room nicknames. */
    val drawerNicknames: Flow<Map<String, String>> = context.dataStore.data.map { prefs ->
        prefs[DRAWER_NICKNAMES]?.let(::decodeDrawerNicknames) ?: emptyMap()
    }

    /** Per-widget APP_LIST appearance overrides keyed by grid item id. */
    val appListAppearances: Flow<Map<Long, AppListAppearance>> = context.dataStore.data.map { prefs ->
        prefs[APP_LIST_APPEARANCES]?.let(::decodeAppListAppearances) ?: emptyMap()
    }

    /** Persisted gesture bindings; page entries take precedence over globals. */
    val gestureBindings: Flow<GestureBindings> = context.dataStore.data.map { prefs ->
        val global = (prefs[GESTURE_BINDINGS_GLOBAL] ?: emptySet())
            .mapNotNull { decodeGlobalBinding(it) }
            .toSet()
        val byPageCoord = (prefs[GESTURE_BINDINGS_PAGES] ?: emptySet())
            .mapNotNull { decodePageBinding(it) }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, bindings) -> bindings.toSet() }
        GestureBindings(global = global, byPageCoord = byPageCoord)
    }

    /**
     * Adds or replaces one binding. Passing [action] as null or NONE removes it.
     * [pageCoord] is required for PAGE scope and ignored for GLOBAL scope.
     */
    suspend fun setGestureBinding(
        scope: GestureScope,
        pageCoord: Pair<Int, Int>?,
        trigger: GestureTrigger,
        action: GestureAction?,
    ) {
        require(scope != GestureScope.PAGE || pageCoord != null) {
            "pageCoord is required when scope is PAGE"
        }
        val removing = action == null || action == GestureAction.NONE
        val key = if (scope == GestureScope.GLOBAL) GESTURE_BINDINGS_GLOBAL else GESTURE_BINDINGS_PAGES
        val replacement = if (removing) null else encodeBinding(scope, pageCoord, trigger, action!!)

        context.dataStore.edit { prefs ->
            // Drop any prior action for this trigger so a rebind never leaves a stale twin.
            val kept = (prefs[key] ?: emptySet())
                .filterNot { isBindingFor(it, scope, pageCoord, trigger) }
                .toSet()
            prefs[key] = if (replacement == null) kept else kept + replacement
        }
    }

    /**
     * Sets or removes one drawer-only nickname. Passing [label] as null (or blank)
     * removes that package's override; other packages are left untouched.
     */
    suspend fun setDrawerNickname(packageName: String, label: String?) {
        if (packageName.isBlank()) return
        runCatching {
            context.dataStore.edit { prefs ->
                val updated = decodeDrawerNicknames(prefs[DRAWER_NICKNAMES]).toMutableMap()
                if (label == null || label.isBlank()) {
                    updated.remove(packageName)
                } else {
                    updated[packageName] = label
                }
                val encoded = encodeDrawerNicknames(updated)
                if (encoded.isEmpty()) {
                    prefs.remove(DRAWER_NICKNAMES)
                } else {
                    prefs[DRAWER_NICKNAMES] = encoded
                }
            }
        }
    }

    /** Removes every drawer-only nickname so system/global labels apply again. */
    suspend fun clearDrawerNicknames() {
        runCatching {
            context.dataStore.edit { prefs -> prefs.remove(DRAWER_NICKNAMES) }
        }
    }

    /**
     * Sets or removes one APP_LIST widget appearance. Passing [appearance] as null
     * removes only that item's override and preserves all other widgets.
     */
    suspend fun setAppListAppearance(itemId: Long, appearance: AppListAppearance?) {
        runCatching {
            context.dataStore.edit { prefs ->
                val updated = decodeAppListAppearances(prefs[APP_LIST_APPEARANCES]).toMutableMap()
                if (appearance == null) {
                    updated.remove(itemId)
                } else {
                    updated[itemId] = appearance
                }
                val encoded = encodeAppListAppearances(updated)
                if (encoded.isEmpty()) {
                    prefs.remove(APP_LIST_APPEARANCES)
                } else {
                    prefs[APP_LIST_APPEARANCES] = encoded
                }
            }
        }
    }

    /** Removes every APP_LIST appearance override so widgets render with defaults. */
    suspend fun clearAppListAppearances() {
        runCatching {
            context.dataStore.edit { prefs -> prefs.remove(APP_LIST_APPEARANCES) }
        }
    }

    suspend fun setShowCoordinateDetail(show: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[SHOW_COORDINATE_DETAIL] = show
        }
    }

    /** Replaces all custom widget definitions with a validated version-1 bundle. */
    suspend fun setCustomWidgetBundle(bundle: CustomWidgetBundle) {
        val json = CustomWidgetBundleManager.toJson(bundle)
        if (json.isEmpty()) return
        context.dataStore.edit { prefs ->
            prefs[CUSTOM_WIDGET_BUNDLE] = json
        }
    }

    /**
     * Clears every preference so all flows fall back to their fresh-install values
     * (SYSTEM theme, PORTRAIT, coordinate detail ON, origin HOME, no pins/palette/gesture bindings).
     */
    suspend fun reset() {
        context.dataStore.edit { it.clear() }
    }

    suspend fun setHomeCoord(x: Int, y: Int) {
        context.dataStore.edit { prefs ->
            prefs[HOME_X] = x
            prefs[HOME_Y] = y
        }
    }

    suspend fun togglePinned(packageName: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[PINNED] ?: emptySet()
            prefs[PINNED] = if (packageName in current) current - packageName else current + packageName
        }
    }

    suspend fun setTheme(mode: ThemeMode) {
        val value = when (mode) {
            ThemeMode.SYSTEM -> THEME_SYSTEM
            ThemeMode.DARK -> THEME_DARK
            ThemeMode.LIGHT -> THEME_LIGHT
            ThemeMode.CUSTOM -> THEME_CUSTOM
        }
        context.dataStore.edit { it[THEME] = value }
    }

    suspend fun setOrientation(mode: OrientationMode) {
        val value = when (mode) {
            OrientationMode.PORTRAIT -> ORIENTATION_PORTRAIT
            OrientationMode.LANDSCAPE -> ORIENTATION_LANDSCAPE
            OrientationMode.AUTO -> ORIENTATION_AUTO
        }
        context.dataStore.edit { it[ORIENTATION] = value }
    }

    suspend fun setCustomPalette(palette: CustomPalette) {
        context.dataStore.edit { prefs ->
            prefs[CUSTOM_BACKGROUND] = palette.background
            prefs[CUSTOM_SURFACE] = palette.surface
            prefs[CUSTOM_SURFACE_VARIANT] = palette.surfaceVariant
            prefs[CUSTOM_ON_BACKGROUND] = palette.onBackground
            prefs[CUSTOM_ON_SURFACE] = palette.onSurface
            prefs[CUSTOM_PRIMARY] = palette.primary
            prefs[CUSTOM_SECONDARY] = palette.secondary
            prefs[CUSTOM_OUTLINE] = palette.outline
        }
    }

    /** Removes all persisted palette overrides so the built-in default applies again. */
    suspend fun clearCustomPalette() {
        context.dataStore.edit { prefs ->
            listOf(
                CUSTOM_BACKGROUND,
                CUSTOM_SURFACE,
                CUSTOM_SURFACE_VARIANT,
                CUSTOM_ON_BACKGROUND,
                CUSTOM_ON_SURFACE,
                CUSTOM_PRIMARY,
                CUSTOM_SECONDARY,
                CUSTOM_OUTLINE,
            ).forEach { prefs.remove(it) }
        }
    }

    /** Raw string helpers for backup import/export (invalid values are ignored). */
    suspend fun restorePreferences(theme: String?, orientation: String?, palette: CustomPalette?) {
        context.dataStore.edit { prefs ->
            if (theme != null && theme in setOf(THEME_SYSTEM, THEME_DARK, THEME_LIGHT, THEME_CUSTOM)) {
                prefs[THEME] = theme
            }
            if (orientation != null && orientation in setOf(ORIENTATION_PORTRAIT, ORIENTATION_LANDSCAPE, ORIENTATION_AUTO)) {
                prefs[ORIENTATION] = orientation
            }
            if (palette != null) {
                prefs[CUSTOM_BACKGROUND] = palette.background
                prefs[CUSTOM_SURFACE] = palette.surface
                prefs[CUSTOM_SURFACE_VARIANT] = palette.surfaceVariant
                prefs[CUSTOM_ON_BACKGROUND] = palette.onBackground
                prefs[CUSTOM_ON_SURFACE] = palette.onSurface
                prefs[CUSTOM_PRIMARY] = palette.primary
                prefs[CUSTOM_SECONDARY] = palette.secondary
                prefs[CUSTOM_OUTLINE] = palette.outline
            }
        }
    }

    /** Values for backup export. */
    val storedTheme: Flow<String> = context.dataStore.data.map { it[THEME] ?: THEME_SYSTEM }
    val storedOrientation: Flow<String> = context.dataStore.data.map { it[ORIENTATION] ?: ORIENTATION_PORTRAIT }
}
