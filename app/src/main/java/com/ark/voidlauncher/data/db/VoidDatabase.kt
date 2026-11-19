package com.ark.voidlauncher.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ark.voidlauncher.data.background.BackgroundDao
import com.ark.voidlauncher.data.background.BackgroundEntity
import com.ark.voidlauncher.domain.PageType

@Database(
    entities = [
        PageEntity::class,
        PageEdgeEntity::class,
        AppCustomizationEntity::class,
        GridItemEntity::class,
        BackgroundEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class VoidDatabase : RoomDatabase() {
    abstract fun pageDao(): PageDao
    abstract fun pageEdgeDao(): PageEdgeDao
    abstract fun appCustomizationDao(): AppCustomizationDao
    abstract fun gridItemDao(): GridItemDao
    abstract fun backgroundDao(): BackgroundDao

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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }

        /**
         * v1 installs had only the origin page and no edges, which made every swipe
         * a no-op. Seed the four neighbours and a connected edge set in place.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                SeedCallback().seedUpgradeMap(db)
            }
        }

        /**
         * v3 adds the background table for P10. No FK is declared so the migration
         * stays a single CREATE TABLE; stale rows for deleted pages are harmless and
         * are ignored by the resolver.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `backgrounds` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`scope` TEXT NOT NULL, " +
                        "`pageId` INTEGER, " +
                        "`kind` TEXT NOT NULL, " +
                        "`colorHex` TEXT, " +
                        "`gradientSpec` TEXT, " +
                        "`gradientAngle` REAL NOT NULL, " +
                        "`imageToken` TEXT" +
                        ")",
                )
            }
        }

        /**
         * v4 adds page roles and built-in widget metadata. Existing rows stay
         * usable: unknown page roles become WIDGET and legacy widgets keep
         * their null [widgetKind], which is how they remain distinguishable.
         * The seeded origin and its DOWN neighbour take the HOME/APP_DRAWER
         * roles so upgrades match a fresh install.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `pages` ADD COLUMN `pageType` TEXT NOT NULL " +
                        "DEFAULT '${PageType.WIDGET.name}'",
                )
                db.execSQL("ALTER TABLE `grid_items` ADD COLUMN `widgetKind` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `grid_items` ADD COLUMN `appListPackages` TEXT DEFAULT NULL")
                db.execSQL(
                    "UPDATE `pages` SET `pageType` = ? WHERE `coordX` = ? AND `coordY` = ?",
                    arrayOf<Any>(PageType.HOME.name, ORIGIN_COORD_X, ORIGIN_COORD_Y),
                )
                db.execSQL(
                    "UPDATE `pages` SET `pageType` = ? WHERE `coordX` = ? AND `coordY` = ?",
                    arrayOf<Any>(PageType.APP_DRAWER.name, APP_DRAWER_COORD_X, APP_DRAWER_COORD_Y),
                )
            }
        }

        const val APP_DRAWER_COORD_X = 0
        const val APP_DRAWER_COORD_Y = 1
    }

    /**
     * Seeds a small, connected starter map so the launcher is navigable on first
     * run. Home links to four neighbours; each neighbour links back to Home, which
     * satisfies the "every page has a directed path home" graph invariant.
     */
    class SeedCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            seedDefaultMap(db)
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            // Safety net only: never re-add edges the user may have deliberately removed.
            seedOriginIfMissing(db)
        }

        fun seedUpgradeMap(db: SupportSQLiteDatabase) {
            if (pageIdAt(db, ORIGIN_COORD_X, ORIGIN_COORD_Y) == null) {
                insertPage(
                    db, ORIGIN_COORD_X, ORIGIN_COORD_Y,
                    PageEntity.ORIGIN_TITLE, PageType.HOME,
                )
            }
            DEFAULT_NEIGHBORS.forEach { (x, y) ->
                if (pageIdAt(db, x, y) == null) {
                    val type = if (x == APP_DRAWER_COORD_X && y == APP_DRAWER_COORD_Y) {
                        PageType.APP_DRAWER
                    } else {
                        PageType.WIDGET
                    }
                    insertPage(db, x, y, "Page $x,$y", type)
                }
            }
            if (countRows(db, "page_edges") == 0) seedDefaultEdges(db)
        }

        private fun seedDefaultMap(db: SupportSQLiteDatabase) {
            seedUpgradeMap(db)
        }

        private fun seedOriginIfMissing(db: SupportSQLiteDatabase) {
            if (countRows(db, "pages") == 0 &&
                pageIdAt(db, ORIGIN_COORD_X, ORIGIN_COORD_Y) == null
            ) {
                insertPage(
                    db, ORIGIN_COORD_X, ORIGIN_COORD_Y,
                    PageEntity.ORIGIN_TITLE, PageType.HOME,
                )
            }
        }

        private fun seedDefaultEdges(db: SupportSQLiteDatabase) {
            // Home -> each neighbour.
            insertEdge(db, 0, 0, "RIGHT", 1, 0)
            insertEdge(db, 0, 0, "LEFT", -1, 0)
            insertEdge(db, 0, 0, "UP", 0, -1)
            insertEdge(db, 0, 0, "DOWN", 0, 1)
            // Each neighbour -> Home, so no page is stranded.
            insertEdge(db, 1, 0, "LEFT", 0, 0)
            insertEdge(db, -1, 0, "RIGHT", 0, 0)
            insertEdge(db, 0, -1, "DOWN", 0, 0)
            insertEdge(db, 0, 1, "UP", 0, 0)
        }

        private fun insertEdge(
            db: SupportSQLiteDatabase,
            fromX: Int,
            fromY: Int,
            direction: String,
            toX: Int,
            toY: Int,
        ) {
            val from = pageIdAt(db, fromX, fromY) ?: return
            val to = pageIdAt(db, toX, toY) ?: return
            db.execSQL(
                "INSERT OR REPLACE INTO `page_edges` (`fromPageId`, `swipeDirection`, `toPageId`) " +
                    "VALUES (?, ?, ?)",
                arrayOf<Any>(from, direction, to),
            )
        }

        private fun insertPage(
            db: SupportSQLiteDatabase,
            x: Int,
            y: Int,
            title: String,
            pageType: PageType = PageType.WIDGET,
        ) {
            db.execSQL(
                "INSERT INTO `pages` " +
                    "(`coordX`, `coordY`, `title`, `gridCols`, `gridRows`, `isSecret`, `pageType`) " +
                    "VALUES (?, ?, ?, ?, ?, 0, ?)",
                arrayOf<Any>(
                    x, y, title,
                    PageEntity.DEFAULT_GRID_COLS, PageEntity.DEFAULT_GRID_ROWS,
                    pageType.name,
                ),
            )
        }

        private fun pageIdAt(db: SupportSQLiteDatabase, x: Int, y: Int): Long? {
            val cursor = db.query(
                "SELECT `pageId` FROM `pages` WHERE `coordX` = ? AND `coordY` = ? LIMIT 1",
                arrayOf<Any>(x, y),
            )
            return try {
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            } finally {
                cursor.close()
            }
        }

        private fun countRows(db: SupportSQLiteDatabase, table: String): Int {
            val cursor = db.query("SELECT COUNT(*) FROM `$table`")
            return try {
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            } finally {
                cursor.close()
            }
        }

        private val DEFAULT_NEIGHBORS = listOf(1 to 0, -1 to 0, 0 to -1, 0 to 1)
    }
}
