package com.ark.voidlauncher.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ark.voidlauncher.domain.GridItemType

/**
 * A placed item on a page. [referenceId] holds a package name for APP_SHORTCUT
 * or a widget id string for WIDGET. No unique cell index: widgets may overlap.
 */
@Entity(
    tableName = "grid_items",
    foreignKeys = [
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["pageId"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["pageId"]),
        Index(value = ["referenceId"]),
    ],
)
data class GridItemEntity(
    @PrimaryKey(autoGenerate = true)
    val itemId: Long = 0,
    val pageId: Long,
    val itemType: GridItemType = GridItemType.APP_SHORTCUT,
    @ColumnInfo(name = "gridCol")
    val col: Int = 0,
    @ColumnInfo(name = "gridRow")
    val row: Int = 0,
    val colSpan: Int = 1,
    val rowSpan: Int = 1,
    val referenceId: String,
    val themeId: String? = null,
    val allowOverlap: Boolean = false,
)
