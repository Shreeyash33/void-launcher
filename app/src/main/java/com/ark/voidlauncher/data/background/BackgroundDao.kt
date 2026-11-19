package com.ark.voidlauncher.data.background

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BackgroundDao {
    /** Ordered by id so a duplicate-global row resolves deterministically. */
    @Query("SELECT * FROM backgrounds ORDER BY id ASC")
    fun observeAll(): Flow<List<BackgroundEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BackgroundEntity): Long

    @Query("DELETE FROM backgrounds WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM backgrounds WHERE pageId = :pageId")
    suspend fun deleteForPage(pageId: Long)

    @Query("DELETE FROM backgrounds WHERE scope = 'GLOBAL'")
    suspend fun clearGlobal()

    @Query("SELECT * FROM backgrounds ORDER BY id ASC")
    suspend fun getAllOnce(): List<BackgroundEntity>

    @Query("DELETE FROM backgrounds")
    suspend fun deleteAll()
}
