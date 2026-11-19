package com.ark.voidlauncher.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.ark.voidlauncher.domain.SwipeDirection

/**
 * A directed swipe edge. There is at most one target per (source page, direction).
 * Both endpoints cascade so removing a page cleans up the graph automatically.
 */
@Entity(
    tableName = "page_edges",
    primaryKeys = ["fromPageId", "swipeDirection"],
    indices = [Index(value = ["toPageId"])],
    foreignKeys = [
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["pageId"],
            childColumns = ["fromPageId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["pageId"],
            childColumns = ["toPageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PageEdgeEntity(
    val fromPageId: Long,
    val swipeDirection: SwipeDirection,
    val toPageId: Long,
)
