package com.nutrix.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.nutrix.app.data.local.dao.ChatDao
import com.nutrix.app.data.local.dao.DiaryDao
import com.nutrix.app.data.local.dao.IngredientCacheDao
import com.nutrix.app.data.local.dao.RecipeDao
import com.nutrix.app.data.local.dao.WaterDao
import com.nutrix.app.data.local.entity.ChatMessageEntity
import com.nutrix.app.data.local.entity.DiaryEntryEntity
import com.nutrix.app.data.local.entity.IngredientCacheEntity
import com.nutrix.app.data.local.entity.RecipeEntity
import com.nutrix.app.data.local.entity.WaterLogEntity

@Database(
    entities = [
        DiaryEntryEntity::class,
        RecipeEntity::class,
        WaterLogEntity::class,
        ChatMessageEntity::class,
        IngredientCacheEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class NutrixDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao
    abstract fun recipeDao(): RecipeDao
    abstract fun waterDao(): WaterDao
    abstract fun chatDao(): ChatDao
    abstract fun ingredientCacheDao(): IngredientCacheDao

    companion object {
        @Volatile
        private var instance: NutrixDatabase? = null

        fun get(context: Context): NutrixDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                NutrixDatabase::class.java,
                "nutrix.db",
            ).build().also { instance = it }
        }
    }
}
