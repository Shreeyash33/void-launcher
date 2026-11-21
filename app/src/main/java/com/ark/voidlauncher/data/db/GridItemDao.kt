package com.ark.voidlauncher.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ark.voidlauncher.domain.GridItemType
import kotlinx.coroutines.flow.Flow

@Dao
interface GridItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: GridItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<GridItemEntity>)

    @Update
    suspend fun update(item: GridItemEntity)

    @Delete
    suspend fun delete(item: GridItemEntity)

    @Query("SELECT * FROM grid_items ORDER BY pageId ASC, gridRow ASC, gridCol ASC")
    fun observeAll(): Flow<List<GridItemEntity>>

    @Query("SELECT * FROM grid_items ORDER BY pageId ASC, gridRow ASC, gridCol ASC")
    suspend fun getAllOnce(): List<GridItemEntity>

    @Query("SELECT * FROM grid_items WHERE pageId = :pageId ORDER BY gridRow ASC, gridCol ASC")
    fun observeByPage(pageId: Long): Flow<List<GridItemEntity>>

    @Query("SELECT * FROM grid_items WHERE itemType = :type AND referenceId = :referenceId ORDER BY pageId ASC")
    fun observeByReference(type: GridItemType, referenceId: String): Flow<List<GridItemEntity>>

    @Query("SELECT * FROM grid_items WHERE pageId = :pageId AND gridCol = :col AND gridRow = :row")
    fun observeByCoord(pageId: Long, col: Int, row: Int): Flow<GridItemEntity?>

    @Query("SELECT * FROM grid_items WHERE pageId = :pageId ORDER BY gridRow ASC, gridCol ASC")
    suspend fun getByPageOnce(pageId: Long): List<GridItemEntity>

    @Query("SELECT COUNT(*) FROM grid_items WHERE pageId = :pageId")
    suspend fun countOnPage(pageId: Long): Int

    @Query("DELETE FROM grid_items")
    suspend fun deleteAll()

    @Query("UPDATE grid_items SET referenceId = :referenceId WHERE itemId = :itemId")
    suspend fun updateReferenceId(itemId: Long, referenceId: String)
}
