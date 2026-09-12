package com.nutrix.app.model

import kotlinx.serialization.Serializable

/** One line of a saved recipe: an ingredient and how much of it goes in. */
@Serializable
data class RecipeIngredient(
    val name: String,
    val grams: Double,
    /** Nutrients for this ingredient at [grams], already scaled. */
    val nutrients: Nutrients = Nutrients.EMPTY,
    val sourceLabel: String = "",
    val measureLabel: String = "",
)

@Serializable
data class Recipe(
    val id: Long = 0L,
    val name: String,
    val description: String = "",
    val servings: Int = 1,
    val ingredients: List<RecipeIngredient> = emptyList(),
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
) {
    val totalGrams: Double get() = ingredients.sumOf { it.grams }

    val totalNutrients: Nutrients get() = Nutrients.sum(ingredients.map { it.nutrients })

    val perServing: Nutrients get() = totalNutrients / servings.coerceAtLeast(1).toDouble()

    val gramsPerServing: Double get() = totalGrams / servings.coerceAtLeast(1)
}
