package com.ark.voidlauncher

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ark.voidlauncher.domain.CustomPalette
import com.ark.voidlauncher.domain.OrientationMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
