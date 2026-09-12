package com.nutrix.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.nutrix.app.data.local.entity.ChatMessageEntity
import com.nutrix.app.data.local.entity.DiaryEntryEntity
import com.nutrix.app.data.local.entity.IngredientCacheEntity
import com.nutrix.app.data.local.entity.RecipeEntity
import com.nutrix.app.data.local.entity.WaterLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryDao {
    @Query("SELECT * FROM diary_entries WHERE epochDay = :epochDay ORDER BY loggedAtEpochMs ASC")
    fun observeDay(epochDay: Long): Flow<List<DiaryEntryEntity>>

    @Query("SELECT * FROM diary_entries WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay ASC")
    fun observeRange(from: Long, to: Long): Flow<List<DiaryEntryEntity>>

    @Query("SELECT * FROM diary_entries ORDER BY loggedAtEpochMs DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<DiaryEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: DiaryEntryEntity): Long

    @Upsert
    suspend fun upsert(entry: DiaryEntryEntity)

    @Delete
    suspend fun delete(entry: DiaryEntryEntity)

    @Query("DELETE FROM diary_entries WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipes ORDER BY updatedAtEpochMs DESC")
    fun observeAll(): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun byId(id: Long): RecipeEntity?

    @Query("SELECT * FROM recipes WHERE name LIKE '%' || :query || '%' ORDER BY updatedAtEpochMs DESC")
    fun search(query: String): Flow<List<RecipeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recipe: RecipeEntity): Long

    @Upsert
    suspend fun upsert(recipe: RecipeEntity)

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface WaterDao {
    @Query("SELECT * FROM water_log WHERE epochDay = :epochDay ORDER BY loggedAtEpochMs ASC")
    fun observeDay(epochDay: Long): Flow<List<WaterLogEntity>>

    @Query("SELECT COALESCE(SUM(amountMl), 0) FROM water_log WHERE epochDay = :epochDay")
    fun observeDayTotal(epochDay: Long): Flow<Int>

    @Query("SELECT COALESCE(SUM(amountMl), 0) FROM water_log WHERE epochDay = :epochDay")
    suspend fun dayTotal(epochDay: Long): Int

    @Query("SELECT * FROM water_log WHERE epochDay BETWEEN :from AND :to")
    fun observeRange(from: Long, to: Long): Flow<List<WaterLogEntity>>

    @Insert
    suspend fun insert(entry: WaterLogEntity): Long

    @Query("DELETE FROM water_log WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM water_log WHERE epochDay = :epochDay ORDER BY loggedAtEpochMs DESC LIMIT 1")
    suspend fun lastOfDay(epochDay: Long): WaterLogEntity?
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages ORDER BY sentAtEpochMs ASC, id ASC")
    fun observeAll(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY sentAtEpochMs DESC, id DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<ChatMessageEntity>

    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages")
    suspend fun clear()
}

@Dao
interface IngredientCacheDao {
    @Query("SELECT * FROM ingredient_cache WHERE query = :query LIMIT 1")
    suspend fun find(query: String): IngredientCacheEntity?

    @Query("SELECT * FROM ingredient_cache ORDER BY cachedAtEpochMs DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<IngredientCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: IngredientCacheEntity)
}
