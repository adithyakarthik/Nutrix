package com.nutrix.app.data.repository

import com.nutrix.app.data.local.dao.DiaryDao
import com.nutrix.app.data.local.entity.DiaryEntryEntity
import com.nutrix.app.model.DiaryEntry
import com.nutrix.app.model.FoodAnalysis
import com.nutrix.app.model.MealType
import com.nutrix.app.model.Nutrients
import com.nutrix.app.model.Recipe
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DiaryRepository(private val dao: DiaryDao) {

    fun observeDay(date: LocalDate): Flow<List<DiaryEntry>> =
        dao.observeDay(date.toEpochDay()).map { rows -> rows.map { it.toModel() } }

    fun observeDayTotals(date: LocalDate): Flow<Nutrients> =
        observeDay(date).map { entries -> Nutrients.sum(entries.map { it.nutrients }) }

    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<DiaryEntry>> =
        dao.observeRange(from.toEpochDay(), to.toEpochDay()).map { rows -> rows.map { it.toModel() } }

    fun observeRecent(limit: Int = 20): Flow<List<DiaryEntry>> =
        dao.observeRecent(limit).map { rows -> rows.map { it.toModel() } }

    suspend fun log(analysis: FoodAnalysis, meal: MealType, date: LocalDate): Long =
        dao.insert(
            DiaryEntryEntity(
                epochDay = date.toEpochDay(),
                meal = meal,
                title = analysis.title,
                portionGrams = analysis.portionGrams,
                portionLabel = analysis.portionLabel,
                nutrients = analysis.nutrients,
                imagePath = analysis.imagePath,
                sourceLabel = analysis.sources.firstOrNull()?.title ?: "Photo analysis",
                recipeId = null,
                loggedAtEpochMs = System.currentTimeMillis(),
            ),
        )

    suspend fun logRecipe(recipe: Recipe, servings: Double, meal: MealType, date: LocalDate): Long =
        dao.insert(
            DiaryEntryEntity(
                epochDay = date.toEpochDay(),
                meal = meal,
                title = recipe.name,
                portionGrams = recipe.gramsPerServing * servings,
                portionLabel = formatServings(servings),
                nutrients = recipe.perServing * servings,
                imagePath = null,
                sourceLabel = "Saved recipe",
                recipeId = recipe.id,
                loggedAtEpochMs = System.currentTimeMillis(),
            ),
        )

    suspend fun update(entry: DiaryEntry) = dao.upsert(DiaryEntryEntity.from(entry))

    suspend fun delete(id: Long) = dao.deleteById(id)

    private fun formatServings(servings: Double): String {
        val text = if (servings == servings.toLong().toDouble()) {
            servings.toLong().toString()
        } else {
            String.format("%.1f", servings)
        }
        return "$text ${if (servings == 1.0) "serving" else "servings"}"
    }
}
