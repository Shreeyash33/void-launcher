package com.ark.voidlauncher.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PageEntity::class,
        PageEdgeEntity::class,
        AppCustomizationEntity::class,
        GridItemEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class VoidDatabase : RoomDatabase() {
    abstract fun pageDao(): PageDao
    abstract fun pageEdgeDao(): PageEdgeDao
    abstract fun appCustomizationDao(): AppCustomizationDao
    abstract fun gridItemDao(): GridItemDao

    companion object {
        const val DATABASE_NAME = "void_launcher.db"

        /** Coordinates of the origin ("Home") page. */
        const val ORIGIN_COORD_X = 0
        const val ORIGIN_COORD_Y = 0

        /**
         * Row id of the page seeded by [SeedCallback]. Prefer looking the page up by
         * coordinates in case the table was ever reset and re-seeded.
         */
        const val ORIGIN_PAGE_ID = 1L

        @Volatile
        private var instance: VoidDatabase? = null

        /** Singleton builder that installs the origin-page seed callback. */
        fun getInstance(context: Context): VoidDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    VoidDatabase::class.java,
                    DATABASE_NAME,
                )
                    .addCallback(SeedCallback())
                    .build()
                    .also { instance = it }
            }
    }

    /** Inserts the origin page when the database is created or found empty. */
    class SeedCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            seedIfEmpty(db)
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            seedIfEmpty(db)
        }

        private fun seedIfEmpty(db: SupportSQLiteDatabase) {
            val cursor = db.query("SELECT COUNT(*) FROM `pages`")
            val pageCount = try {
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            } finally {
                cursor.close()
            }
            if (pageCount == 0) {
                db.execSQL(
                    "INSERT INTO `pages` (`coordX`, `coordY`, `title`, `gridCols`, `gridRows`, `isSecret`) " +
                        "VALUES (" +
                        "${VoidDatabase.ORIGIN_COORD_X}, " +
                        "${VoidDatabase.ORIGIN_COORD_Y}, " +
                        "'${PageEntity.ORIGIN_TITLE}', " +
                        "${PageEntity.DEFAULT_GRID_COLS}, " +
                        "${PageEntity.DEFAULT_GRID_ROWS}, " +
                        "0)",
                )
            }
        }
    }
}
