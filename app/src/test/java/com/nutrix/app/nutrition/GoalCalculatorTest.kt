package com.nutrix.app.nutrition

import com.nutrix.app.model.ActivityLevel
import com.nutrix.app.model.BodyGoal
import com.nutrix.app.model.Nutrient
import com.nutrix.app.model.Sex
import com.nutrix.app.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalCalculatorTest {

    private val baseProfile = UserProfile(
        sex = Sex.MALE,
        ageYears = 30,
        heightCm = 178.0,
        weightKg = 80.0,
        activityLevel = ActivityLevel.ACTIVE,
        bodyGoal = BodyGoal.MAINTAIN,
        isComplete = true,
    )

    @Test
    fun `every tracked nutrient gets a target`() {
        val goals = GoalCalculator.calculate(baseProfile)
        Nutrient.entries.forEach { nutrient ->
            assertNotNull("no target for ${nutrient.key}", goals.target(nutrient))
        }
    }

    @Test
    fun `micronutrient targets follow the DRI tables`() {
        val goals = GoalCalculator.calculate(baseProfile)
        assertEquals(90.0, goals.target(Nutrient.VITAMIN_C)!!, 0.01)
        assertEquals(8.0, goals.target(Nutrient.IRON)!!, 0.01)
        assertEquals(150.0, goals.target(Nutrient.IODINE)!!, 0.01)
    }

    @Test
    fun `premenopausal women get the higher iron target`() {
        val goals = GoalCalculator.calculate(baseProfile.copy(sex = Sex.FEMALE))
        assertEquals(18.0, goals.target(Nutrient.IRON)!!, 0.01)
    }

    @Test
    fun `fibre scales with calories at 14g per 1000 kcal`() {
        val goals = GoalCalculator.calculate(baseProfile)
        val energy = goals.target(Nutrient.ENERGY)!!
        assertEquals(energy * 14.0 / 1000.0, goals.target(Nutrient.FIBER)!!, 0.01)
    }

    @Test
    fun `heavy training raises iron and magnesium above the plain RDA`() {
        val trained = GoalCalculator.calculate(baseProfile.copy(activityLevel = ActivityLevel.ATHLETE))
        val untrained = GoalCalculator.calculate(baseProfile.copy(activityLevel = ActivityLevel.NOT_ACTIVE))
        assertTrue(trained.target(Nutrient.IRON)!! > untrained.target(Nutrient.IRON)!!)
        assertTrue(trained.target(Nutrient.MAGNESIUM)!! > untrained.target(Nutrient.MAGNESIUM)!!)
        assertTrue(trained.notes.isNotEmpty())
    }

    @Test
    fun `hypertension tightens the sodium ceiling`() {
        val goals = GoalCalculator.calculate(
            baseProfile.copy(healthConditions = listOf("High blood pressure")),
        )
        assertEquals(1500.0, goals.target(Nutrient.SODIUM)!!, 0.01)
        assertTrue(goals.notes.any { it.contains("DASH") })
    }

    @Test
    fun `diabetes tightens free sugars to 5 percent of energy`() {
        val goals = GoalCalculator.calculate(baseProfile.copy(healthConditions = listOf("Type 2 diabetes")))
        val energy = goals.target(Nutrient.ENERGY)!!
        assertEquals(energy * 0.05 / 4.0, goals.target(Nutrient.SUGAR)!!, 0.01)
    }

    @Test
    fun `clinically managed conditions get a warning rather than invented targets`() {
        val pregnancy = GoalCalculator.calculate(baseProfile.copy(healthConditions = listOf("Pregnant, 2nd trimester")))
        val kidney = GoalCalculator.calculate(baseProfile.copy(healthConditions = listOf("Stage 3 kidney disease")))
        assertTrue(pregnancy.notes.any { it.startsWith("⚠") })
        assertTrue(kidney.notes.any { it.startsWith("⚠") })
    }

    @Test
    fun `manual overrides survive a recalculation`() {
        val goals = GoalCalculator.calculate(baseProfile).withOverride(Nutrient.PROTEIN, 200.0)
        val recalculated = GoalCalculator.calculate(baseProfile.copy(weightKg = 90.0))
            .copy(manualOverrides = goals.manualOverrides)
        assertEquals(200.0, recalculated.target(Nutrient.PROTEIN)!!, 0.01)
        assertTrue(recalculated.isOverridden(Nutrient.PROTEIN))
    }

    @Test
    fun `a rationale is always produced`() {
        assertTrue(GoalCalculator.calculate(baseProfile).rationale.isNotBlank())
    }
}
