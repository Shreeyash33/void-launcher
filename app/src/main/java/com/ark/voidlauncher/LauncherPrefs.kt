package com.ark.voidlauncher

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "void_launcher_prefs")

enum class ThemeMode { SYSTEM, DARK, LIGHT }

/** Small persistent store for pinned apps and display settings. */
class LauncherPrefs(private val context: Context) {
    companion object {
        private val PINNED = stringSetPreferencesKey("pinned_packages")
        private val THEME = stringPreferencesKey("theme")

        private const val THEME_SYSTEM = "system"
        private const val THEME_DARK = "dark"
        private const val THEME_LIGHT = "light"
    }

    val pinnedApps: Flow<Set<String>> = context.dataStore.data.map { it[PINNED] ?: emptySet() }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        when (prefs[THEME]) {
            THEME_DARK -> ThemeMode.DARK
            THEME_LIGHT -> ThemeMode.LIGHT
            else -> ThemeMode.SYSTEM
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
        }
        context.dataStore.edit { it[THEME] = value }
    }
}
