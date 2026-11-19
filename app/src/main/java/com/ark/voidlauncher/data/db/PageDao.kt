package com.ark.voidlauncher.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PageDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(page: PageEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(pages: List<PageEntity>)

    @Update
    suspend fun update(page: PageEntity)

    @Delete
    suspend fun delete(page: PageEntity)

    @Query("SELECT * FROM pages ORDER BY pageId ASC")
    fun observeAll(): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE pageId = :pageId")
    fun observeById(pageId: Long): Flow<PageEntity?>

    @Query("SELECT * FROM pages WHERE coordX = :coordX AND coordY = :coordY")
    fun observeByCoord(coordX: Int, coordY: Int): Flow<PageEntity?>

    @Query("SELECT * FROM pages WHERE isSecret = :isSecret ORDER BY pageId ASC")
    fun observeSecret(isSecret: Boolean): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE pageId = :pageId")
    suspend fun getByIdOnce(pageId: Long): PageEntity?

    @Query("SELECT * FROM pages WHERE coordX = :coordX AND coordY = :coordY")
    suspend fun getByCoordOnce(coordX: Int, coordY: Int): PageEntity?

    @Query("SELECT COUNT(*) FROM pages")
    suspend fun count(): Int

    @Query("DELETE FROM pages")
    suspend fun deleteAll()
}
