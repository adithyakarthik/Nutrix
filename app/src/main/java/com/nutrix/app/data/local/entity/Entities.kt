package com.nutrix.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nutrix.app.model.DiaryEntry
import com.nutrix.app.model.MealType
import com.nutrix.app.model.Nutrients
import com.nutrix.app.model.Recipe
import com.nutrix.app.model.RecipeIngredient
import com.nutrix.app.model.SourceRef
import com.nutrix.app.model.WaterLogEntry

@Entity(tableName = "diary_entries", indices = [Index("epochDay")])
data class DiaryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val epochDay: Long,
    val meal: MealType,
    val title: String,
    val portionGrams: Double,
    val portionLabel: String,
    val nutrients: Nutrients,
    val imagePath: String?,
    val sourceLabel: String,
    val recipeId: Long?,
    val loggedAtEpochMs: Long,
) {
    fun toModel() = DiaryEntry(
        id = id,
        epochDay = epochDay,
        meal = meal,
        title = title,
        portionGrams = portionGrams,
        portionLabel = portionLabel,
        nutrients = nutrients,
        imagePath = imagePath,
        sourceLabel = sourceLabel,
        recipeId = recipeId,
        loggedAtEpochMs = loggedAtEpochMs,
    )

    companion object {
        fun from(entry: DiaryEntry) = DiaryEntryEntity(
            id = entry.id,
            epochDay = entry.epochDay,
            meal = entry.meal,
            title = entry.title,
            portionGrams = entry.portionGrams,
            portionLabel = entry.portionLabel,
            nutrients = entry.nutrients,
            imagePath = entry.imagePath,
            sourceLabel = entry.sourceLabel,
            recipeId = entry.recipeId,
            loggedAtEpochMs = entry.loggedAtEpochMs,
        )
    }
}

@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val description: String,
    val servings: Int,
    val ingredients: List<RecipeIngredient>,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
) {
    fun toModel() = Recipe(
        id = id,
        name = name,
        description = description,
        servings = servings,
        ingredients = ingredients,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
    )

    companion object {
        fun from(recipe: Recipe) = RecipeEntity(
            id = recipe.id,
            name = recipe.name,
            description = recipe.description,
            servings = recipe.servings,
            ingredients = recipe.ingredients,
            createdAtEpochMs = recipe.createdAtEpochMs,
            updatedAtEpochMs = recipe.updatedAtEpochMs,
        )
    }
}

@Entity(tableName = "water_log", indices = [Index("epochDay")])
data class WaterLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val epochDay: Long,
    val amountMl: Int,
    val loggedAtEpochMs: Long,
) {
    fun toModel() = WaterLogEntry(id = id, epochDay = epochDay, amountMl = amountMl, loggedAtEpochMs = loggedAtEpochMs)
}

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "role") val role: String,
    val text: String,
    val sentAtEpochMs: Long,
    val sources: List<SourceRef> = emptyList(),
)

/** A cached per-100 g lookup so the same ingredient is never billed to the API twice. */
@Entity(tableName = "ingredient_cache", indices = [Index(value = ["query"], unique = true)])
data class IngredientCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val query: String,
    val displayName: String,
    val per100g: Nutrients,
    val sourceLabel: String,
    val cachedAtEpochMs: Long,
)
