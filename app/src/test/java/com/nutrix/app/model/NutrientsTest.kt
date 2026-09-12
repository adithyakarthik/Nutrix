package com.nutrix.app.model

import com.nutrix.app.util.NutrixJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NutrientsTest {

    private val chickenPer100g = Nutrients.of(
        Nutrient.ENERGY to 120.0,
        Nutrient.PROTEIN to 22.5,
        Nutrient.FAT to 2.6,
    )

    @Test
    fun `absent is not the same as zero`() {
        assertNull(chickenPer100g[Nutrient.IODINE])
        assertEquals(0.0, chickenPer100g.amountOr0(Nutrient.IODINE), 0.0)
        assertFalse(chickenPer100g.has(Nutrient.IODINE))
    }

    @Test
    fun `scaling a portion scales every value`() {
        val portion = chickenPer100g * 2.5
        assertEquals(300.0, portion.amountOr0(Nutrient.ENERGY), 0.01)
        assertEquals(56.25, portion.amountOr0(Nutrient.PROTEIN), 0.01)
    }

    @Test
    fun `adding two foods keeps nutrients known in only one of them`() {
        val rice = Nutrients.of(Nutrient.ENERGY to 130.0, Nutrient.CARBS to 28.2)
        val meal = chickenPer100g + rice
        assertEquals(250.0, meal.amountOr0(Nutrient.ENERGY), 0.01)
        assertEquals(28.2, meal.amountOr0(Nutrient.CARBS), 0.01)
        assertEquals(22.5, meal.amountOr0(Nutrient.PROTEIN), 0.01)
    }

    @Test
    fun `dividing by zero servings yields nothing rather than infinity`() {
        assertTrue((chickenPer100g / 0.0).isEmpty)
    }

    @Test
    fun `Atwater factors reproduce the stated energy`() {
        val meal = Nutrients.of(
            Nutrient.PROTEIN to 40.0,
            Nutrient.CARBS to 60.0,
            Nutrient.FAT to 20.0,
        )
        // 40x4 + 60x4 + 20x9 = 160 + 240 + 180
        assertEquals(580.0, meal.energyFromMacros(), 0.01)
    }

    @Test
    fun `nutrient keys survive a round trip through storage`() {
        val json = NutrixJson.instance.encodeToString(Nutrients.serializer(), chickenPer100g)
        // The stored form uses the stable key, not the Kotlin constant name, so renaming a
        // constant can never orphan a user's history.
        assertTrue(json.contains("protein_g"))
        val restored = NutrixJson.instance.decodeFromString(Nutrients.serializer(), json)
        assertEquals(chickenPer100g, restored)
    }

    @Test
    fun `grouping returns only the nutrients present, in declaration order`() {
        val meal = chickenPer100g + Nutrients.of(Nutrient.IRON to 1.2, Nutrient.CALCIUM to 15.0)
        val minerals = meal.byGroup(NutrientGroup.MINERAL).map { it.first }
        assertEquals(listOf(Nutrient.CALCIUM, Nutrient.IRON), minerals)
    }

    @Test
    fun `goal progress reads a ceiling differently from a floor`() {
        val overSodium = NutrientProgress(Nutrient.SODIUM, consumed = 3000.0, target = 2300.0)
        val overProtein = NutrientProgress(Nutrient.PROTEIN, consumed = 200.0, target = 180.0)
        assertEquals(NutrientProgress.Status.OVER, overSodium.status)
        assertEquals(NutrientProgress.Status.MET, overProtein.status)
    }

    @Test
    fun `an untracked nutrient has no status`() {
        val progress = NutrientProgress(Nutrient.SELENIUM, consumed = 12.0, target = null)
        assertEquals(NutrientProgress.Status.UNTRACKED, progress.status)
        assertEquals(0f, progress.fraction, 0.0f)
    }

    @Test
    fun `meal type follows the clock`() {
        assertEquals(MealType.BREAKFAST, MealType.forHour(8))
        assertEquals(MealType.LUNCH, MealType.forHour(13))
        assertEquals(MealType.DINNER, MealType.forHour(19))
        assertEquals(MealType.SNACK, MealType.forHour(2))
    }
}
