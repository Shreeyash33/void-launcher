package com.ark.voidlauncher.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ark.voidlauncher.domain.SwipeDirection
import kotlinx.coroutines.flow.Flow

@Dao
interface PageEdgeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(edge: PageEdgeEntity)

    @Update
    suspend fun update(edge: PageEdgeEntity)

    @Delete
    suspend fun delete(edge: PageEdgeEntity)

    @Query("SELECT * FROM page_edges ORDER BY fromPageId ASC, swipeDirection ASC")
    fun observeAll(): Flow<List<PageEdgeEntity>>

    @Query("SELECT * FROM page_edges WHERE fromPageId = :fromPageId ORDER BY swipeDirection ASC")
    fun observeOutgoing(fromPageId: Long): Flow<List<PageEdgeEntity>>

    @Query("SELECT * FROM page_edges WHERE toPageId = :toPageId ORDER BY fromPageId ASC")
    fun observeIncoming(toPageId: Long): Flow<List<PageEdgeEntity>>

    @Query("SELECT * FROM page_edges WHERE fromPageId = :fromPageId AND swipeDirection = :direction")
    fun observeEdge(fromPageId: Long, direction: SwipeDirection): Flow<PageEdgeEntity?>

    @Query("SELECT * FROM page_edges WHERE fromPageId = :fromPageId AND swipeDirection = :direction")
    suspend fun getEdgeOnce(fromPageId: Long, direction: SwipeDirection): PageEdgeEntity?

    @Query("DELETE FROM page_edges WHERE fromPageId = :fromPageId")
    suspend fun deleteOutgoing(fromPageId: Long)
}
