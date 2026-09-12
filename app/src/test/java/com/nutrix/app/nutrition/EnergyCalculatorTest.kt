package com.nutrix.app.nutrition

import com.nutrix.app.model.ActivityLevel
import com.nutrix.app.model.BodyGoal
import com.nutrix.app.model.GoalPace
import com.nutrix.app.model.Sex
import com.nutrix.app.model.UserProfile
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnergyCalculatorTest {

    private val lifter = UserProfile(
        sex = Sex.MALE,
        ageYears = 28,
        heightCm = 180.0,
        weightKg = 82.0,
        activityLevel = ActivityLevel.VERY_ACTIVE,
        bodyGoal = BodyGoal.BUILD_MUSCLE,
        isComplete = true,
    )

    @Test
    fun `Mifflin-St Jeor matches the published formula`() {
        // 10(82) + 6.25(180) - 5(28) + 5 = 820 + 1125 - 140 + 5 = 1810
        assertEquals(1810.0, EnergyCalculator.mifflinStJeorBmr(lifter), 0.01)
    }

    @Test
    fun `female offset is applied`() {
        val female = lifter.copy(sex = Sex.FEMALE)
        // Same body, 166 kcal lower: the +5 becomes -161.
        assertEquals(
            EnergyCalculator.mifflinStJeorBmr(lifter) - 166.0,
            EnergyCalculator.mifflinStJeorBmr(female),
            0.01,
        )
    }

    @Test
    fun `body fat percentage switches the equation to Katch-McArdle`() {
        val plan = EnergyCalculator.plan(lifter.copy(bodyFatPercent = 12.0))
        assertTrue(plan.usedKatchMcArdle)
        // 370 + 21.6 x 72.16 kg lean = about 1929 kcal
        assertEquals(1929.0, plan.bmr.toDouble(), 5.0)
    }

    @Test
    fun `activity multiplier drives maintenance`() {
        val sedentary = EnergyCalculator.plan(lifter.copy(activityLevel = ActivityLevel.NOT_ACTIVE))
        val athlete = EnergyCalculator.plan(lifter.copy(activityLevel = ActivityLevel.ATHLETE))
        assertTrue(athlete.tdee > sedentary.tdee)
        assertEquals(1810 * 1.2, sedentary.tdee.toDouble(), 1.0)
    }

    @Test
    fun `a deficit never drops below BMR`() {
        // A small, very sedentary person on an aggressive cut is where naive formulas produce
        // targets nobody can eat at.
        val small = UserProfile(
            sex = Sex.FEMALE,
            ageYears = 55,
            heightCm = 152.0,
            weightKg = 48.0,
            activityLevel = ActivityLevel.NOT_ACTIVE,
            bodyGoal = BodyGoal.LOSE_FAT,
            pace = GoalPace.AGGRESSIVE,
            isComplete = true,
        )
        val plan = EnergyCalculator.plan(small)
        assertTrue("target must not fall below BMR", plan.targetCalories >= plan.bmr)
        assertTrue("floor should be reported", plan.wasFloored)
    }

    @Test
    fun `cutting gets more protein per kilo than maintaining`() {
        val cutting = EnergyCalculator.proteinGrams(lifter.copy(bodyGoal = BodyGoal.LOSE_FAT))
        val maintaining = EnergyCalculator.proteinGrams(lifter.copy(bodyGoal = BodyGoal.MAINTAIN))
        assertTrue(cutting > maintaining)
    }

    @Test
    fun `protein stays inside sane bounds even with an absurd body fat figure`() {
        val protein = EnergyCalculator.proteinGrams(lifter.copy(bodyFatPercent = 3.0))
        assertTrue(protein <= lifter.weightKg * 2.6)
        assertTrue(protein >= lifter.weightKg * 1.2)
    }

    @Test
    fun `macros add up to the calorie target`() {
        val plan = EnergyCalculator.plan(lifter)
        val protein = EnergyCalculator.proteinGrams(lifter)
        val fat = EnergyCalculator.fatGrams(lifter, plan.targetCalories)
        val carbs = EnergyCalculator.carbGrams(plan.targetCalories, protein, fat)
        val fromMacros = protein * 4 + carbs * 4 + fat * 9
        assertTrue(
            "macro calories $fromMacros should match target ${plan.targetCalories}",
            abs(fromMacros - plan.targetCalories) < 20,
        )
    }

    @Test
    fun `fat never falls below the hormonal floor`() {
        val fat = EnergyCalculator.fatGrams(lifter, 1200)
        assertTrue(fat >= lifter.weightKg * 0.6)
    }
}
