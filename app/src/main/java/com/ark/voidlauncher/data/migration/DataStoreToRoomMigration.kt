package com.ark.voidlauncher.data.migration

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.withTransaction
import com.ark.voidlauncher.data.db.AppCustomizationEntity
import com.ark.voidlauncher.data.db.GridItemEntity
import com.ark.voidlauncher.data.db.PageEntity
import com.ark.voidlauncher.data.db.VoidDatabase
import com.ark.voidlauncher.domain.GridItemType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/*
 * Same store name as LauncherPrefs.kt so the legacy keys are read from the same
 * preferences file. Prefer passing the existing DataStore instance explicitly
 * (see DataStoreToRoomMigration's third constructor parameter).
 */
private val Context.launcherDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "void_launcher_prefs",
)

private val PINNED_PACKAGES = stringSetPreferencesKey("pinned_packages")
private val THEME = stringPreferencesKey("theme")
private val MIGRATION_V1_DONE = booleanPreferencesKey("migration_v1_done")

/**
 * One-shot import of legacy DataStore settings into Room.
 *
 * Pinned packages become favourite [AppCustomizationEntity] rows and APP_SHORTCUT
 * [GridItemEntity] rows stacked in sequential rows (column 0) on the origin page.
 * The theme key is deliberately left untouched; [storedTheme] exposes its raw
 * value for the future settings UI. A boolean DataStore flag makes the import
 * run at most once.
 *
 * [dataStore] defaults to a delegate with the same name as `LauncherPrefs`. If
 * the lead exposes that DataStore, pass the same instance here to avoid two
 * DataStore instances backed by the same file. Re-running the import is safe in
 * either case because all writes use REPLACE.
 */
class DataStoreToRoomMigration(
    private val context: Context,
    private val database: VoidDatabase,
    private val dataStore: DataStore<Preferences> = context.launcherDataStore,
) {
    /** Raw legacy theme value ("system" / "dark" / "light"), if one was stored. */
    val storedTheme: Flow<String?> = dataStore.data.map { it[THEME] }

    suspend fun migrate() {
        val prefs = dataStore.data.first()
        if (prefs[MIGRATION_V1_DONE] == true) return

        val pinnedPackages = prefs[PINNED_PACKAGES].orEmpty().sorted()

        val pageDao = database.pageDao()
        val originPageId = pageDao.getByCoordOnce(
            coordX = VoidDatabase.ORIGIN_COORD_X,
            coordY = VoidDatabase.ORIGIN_COORD_Y,
        )?.pageId ?: pageDao.insert(
            PageEntity(
                coordX = VoidDatabase.ORIGIN_COORD_X,
                coordY = VoidDatabase.ORIGIN_COORD_Y,
                title = PageEntity.ORIGIN_TITLE,
                gridCols = PageEntity.DEFAULT_GRID_COLS,
                gridRows = PageEntity.DEFAULT_GRID_ROWS,
            ),
        )

        database.withTransaction {
            pinnedPackages.forEachIndexed { index, packageName ->
                database.appCustomizationDao().insert(
                    AppCustomizationEntity(packageName = packageName, isFavorite = true),
                )
                database.gridItemDao().insert(
                    GridItemEntity(
                        pageId = originPageId,
                        itemType = GridItemType.APP_SHORTCUT,
                        col = 0,
                        row = index,
                        referenceId = packageName,
                    ),
                )
            }
        }

        dataStore.edit { it[MIGRATION_V1_DONE] = true }
    }
}
