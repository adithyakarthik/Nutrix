package com.nutrix.app.data.repository

import com.nutrix.app.data.local.dao.RecipeDao
import com.nutrix.app.data.local.entity.RecipeEntity
import com.nutrix.app.model.Recipe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RecipeRepository(private val dao: RecipeDao) {

    fun observeAll(): Flow<List<Recipe>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    fun search(query: String): Flow<List<Recipe>> =
        dao.search(query).map { rows -> rows.map { it.toModel() } }

    suspend fun byId(id: Long): Recipe? = dao.byId(id)?.toModel()

    suspend fun save(recipe: Recipe): Long {
        val now = System.currentTimeMillis()
        val entity = RecipeEntity.from(
            recipe.copy(
                createdAtEpochMs = recipe.createdAtEpochMs.takeIf { it > 0 } ?: now,
                updatedAtEpochMs = now,
            ),
        )
        return if (recipe.id == 0L) {
            dao.insert(entity)
        } else {
            dao.upsert(entity)
            recipe.id
        }
    }

    suspend fun delete(id: Long) = dao.deleteById(id)
}
