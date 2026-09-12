package com.nutrix.app.model

import kotlinx.serialization.Serializable

enum class MealType(val label: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    SNACK("Snack");

    companion object {
        /** A sensible default for "log this now" based on the hour of day. */
        fun forHour(hour: Int): MealType = when (hour) {
            in 4..10 -> BREAKFAST
            in 11..15 -> LUNCH
            in 16..21 -> DINNER
            else -> SNACK
        }
    }
}

enum class Confidence(val label: String) {
    HIGH("High confidence"),
    MEDIUM("Best estimate"),
    LOW("Rough estimate"),
}

/** A citation Nutrix can show the user: where a number actually came from. */
@Serializable
data class SourceRef(val title: String, val url: String = "")

/** One component of a dish, with its own contribution to the total. */
@Serializable
data class AnalyzedIngredient(
    val name: String,
    val grams: Double,
    val nutrients: Nutrients = Nutrients.EMPTY,
    val note: String = "",
)

/**
 * The result of pointing the camera at a plate: what it is, how much of it there is,
 * and what that portion contains. [nutrients] is for the whole portion, not per 100 g.
 */
@Serializable
data class FoodAnalysis(
    val title: String,
    val portionGrams: Double,
    val portionLabel: String = "",
    val nutrients: Nutrients = Nutrients.EMPTY,
    val ingredients: List<AnalyzedIngredient> = emptyList(),
    val confidence: Confidence = Confidence.MEDIUM,
    val summary: String = "",
    val sources: List<SourceRef> = emptyList(),
    val warnings: List<String> = emptyList(),
    val imagePath: String? = null,
) {
    val perGram: Nutrients get() = if (portionGrams > 0) nutrients / portionGrams else Nutrients.EMPTY

    /** Re-scales the whole analysis to a different portion size. */
    fun scaledTo(newGrams: Double): FoodAnalysis {
        if (portionGrams <= 0.0 || newGrams <= 0.0) return this
        val factor = newGrams / portionGrams
        return copy(
            portionGrams = newGrams,
            nutrients = nutrients * factor,
            ingredients = ingredients.map { it.copy(grams = it.grams * factor, nutrients = it.nutrients * factor) },
        )
    }
}

/** A row in the food diary. */
@Serializable
data class DiaryEntry(
    val id: Long = 0L,
    val epochDay: Long,
    val meal: MealType,
    val title: String,
    val portionGrams: Double,
    val portionLabel: String = "",
    val nutrients: Nutrients = Nutrients.EMPTY,
    val imagePath: String? = null,
    val sourceLabel: String = "",
    val recipeId: Long? = null,
    val loggedAtEpochMs: Long = 0L,
)
