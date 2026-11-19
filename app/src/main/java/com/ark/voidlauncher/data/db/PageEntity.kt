package com.ark.voidlauncher.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A page in the launcher grid graph. Coordinates are globally unique. */
@Entity(
    tableName = "pages",
    indices = [Index(value = ["coordX", "coordY"], unique = true)],
)
data class PageEntity(
    @PrimaryKey(autoGenerate = true)
    val pageId: Long = 0,
    val coordX: Int = 0,
    val coordY: Int = 0,
    val title: String = DEFAULT_TITLE,
    val gridCols: Int = DEFAULT_GRID_COLS,
    val gridRows: Int = DEFAULT_GRID_ROWS,
    val isSecret: Boolean = false,
) {
    companion object {
        const val DEFAULT_TITLE = "Page"
        const val ORIGIN_TITLE = "Home"
        const val DEFAULT_GRID_COLS = 10
        const val DEFAULT_GRID_ROWS = 20
    }
}
