package com.nutrix.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RecipeTest {

    private val recipe = Recipe(
        name = "Chicken and rice",
        servings = 4,
        ingredients = listOf(
            RecipeIngredient(
                name = "Chicken breast",
                grams = 600.0,
                nutrients = Nutrients.of(Nutrient.ENERGY to 720.0, Nutrient.PROTEIN to 135.0),
            ),
            RecipeIngredient(
                name = "White rice, cooked",
                grams = 800.0,
                nutrients = Nutrients.of(Nutrient.ENERGY to 1040.0, Nutrient.CARBS to 225.6),
            ),
        ),
    )

    @Test
    fun `totals are the sum of the ingredients`() {
        assertEquals(1400.0, recipe.totalGrams, 0.01)
        assertEquals(1760.0, recipe.totalNutrients.amountOr0(Nutrient.ENERGY), 0.01)
    }

    @Test
    fun `per serving divides by the serving count`() {
        assertEquals(440.0, recipe.perServing.amountOr0(Nutrient.ENERGY), 0.01)
        assertEquals(33.75, recipe.perServing.amountOr0(Nutrient.PROTEIN), 0.01)
        assertEquals(350.0, recipe.gramsPerServing, 0.01)
    }

    @Test
    fun `a recipe with no servings set does not divide by zero`() {
        val broken = recipe.copy(servings = 0)
        assertEquals(1760.0, broken.perServing.amountOr0(Nutrient.ENERGY), 0.01)
    }

    @Test
    fun `rescaling an analysis keeps the ratios intact`() {
        val analysis = FoodAnalysis(
            title = "Dal",
            portionGrams = 250.0,
            nutrients = Nutrients.of(Nutrient.ENERGY to 300.0, Nutrient.PROTEIN to 18.0),
            ingredients = listOf(AnalyzedIngredient(name = "Lentils", grams = 100.0)),
        )
        val bigger = analysis.scaledTo(500.0)
        assertEquals(600.0, bigger.nutrients.amountOr0(Nutrient.ENERGY), 0.01)
        assertEquals(36.0, bigger.nutrients.amountOr0(Nutrient.PROTEIN), 0.01)
        assertEquals(200.0, bigger.ingredients.first().grams, 0.01)
    }

    @Test
    fun `rescaling a zero-weight analysis is a no-op rather than a crash`() {
        val empty = FoodAnalysis(title = "Unknown", portionGrams = 0.0)
        assertEquals(0.0, empty.scaledTo(200.0).portionGrams, 0.01)
    }
}
