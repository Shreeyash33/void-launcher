package com.ark.voidlauncher.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AppCustomizationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(customization: AppCustomizationEntity)

    @Update
    suspend fun update(customization: AppCustomizationEntity)

    @Delete
    suspend fun delete(customization: AppCustomizationEntity)

    @Query("SELECT * FROM app_customizations ORDER BY packageName ASC")
    fun observeAll(): Flow<List<AppCustomizationEntity>>

    @Query("SELECT * FROM app_customizations WHERE packageName = :packageName")
    fun observeByPackage(packageName: String): Flow<AppCustomizationEntity?>

    @Query("SELECT * FROM app_customizations WHERE isFavorite = 1 ORDER BY packageName ASC")
    fun observeFavorites(): Flow<List<AppCustomizationEntity>>

    @Query("SELECT * FROM app_customizations WHERE packageName = :packageName")
    suspend fun getByPackageOnce(packageName: String): AppCustomizationEntity?
}
