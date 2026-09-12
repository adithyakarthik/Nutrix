package com.nutrix.app.nutrition

import com.nutrix.app.model.ActivityLevel
import com.nutrix.app.model.BodyGoal
import com.nutrix.app.model.Sex
import com.nutrix.app.model.UserProfile
import kotlin.math.roundToInt

/** Energy budget for one day, and how it was arrived at. */
data class EnergyPlan(
    val bmr: Int,
    val tdee: Int,
    val targetCalories: Int,
    val deltaFromMaintenance: Int,
    val usedKatchMcArdle: Boolean,
    val wasFloored: Boolean,
)

/**
 * Resting and total energy expenditure.
 *
 * Mifflin-St Jeor is the default because it is the most accurate predictive equation for the
 * general population. When body fat percentage is known, Katch-McArdle is used instead — it
 * works from lean mass, which is what actually burns the calories, and is the better fit for
 * the lean, muscular users this app is built for.
 */
object EnergyCalculator {

    fun mifflinStJeorBmr(profile: UserProfile): Double {
        val base = 10.0 * profile.weightKg + 6.25 * profile.heightCm - 5.0 * profile.ageYears
        return when (profile.sex) {
            Sex.MALE -> base + 5.0
            Sex.FEMALE -> base - 161.0
        }
    }

    fun katchMcArdleBmr(profile: UserProfile): Double = 370.0 + 21.6 * profile.leanBodyMassKg

    fun bmr(profile: UserProfile): Double =
        if (profile.bodyFatPercent != null) katchMcArdleBmr(profile) else mifflinStJeorBmr(profile)

    fun tdee(profile: UserProfile): Double = bmr(profile) * profile.activityLevel.multiplier

    /**
     * The daily calorie target. Deficits and surpluses are a percentage of maintenance rather
     * than a flat number so they scale with body size, and the result is never allowed below
     * BMR or below a hard floor — a target you cannot eat at is a target you will abandon.
     */
    fun plan(profile: UserProfile): EnergyPlan {
        val bmrValue = bmr(profile)
        val tdeeValue = bmrValue * profile.activityLevel.multiplier
        val raw = when (profile.bodyGoal) {
            BodyGoal.LOSE_FAT -> tdeeValue * (1.0 - profile.pace.fraction)
            BodyGoal.MAINTAIN -> tdeeValue
            BodyGoal.BUILD_MUSCLE -> tdeeValue * (1.0 + profile.pace.fraction * 0.6)
        }
        val hardFloor = when (profile.sex) {
            Sex.MALE -> 1500.0
            Sex.FEMALE -> 1200.0
        }
        val floor = maxOf(bmrValue, hardFloor)
        val target = maxOf(raw, floor)
        return EnergyPlan(
            bmr = bmrValue.roundToInt(),
            tdee = tdeeValue.roundToInt(),
            targetCalories = target.roundToInt(),
            deltaFromMaintenance = (target - tdeeValue).roundToInt(),
            usedKatchMcArdle = profile.bodyFatPercent != null,
            wasFloored = target > raw + 1.0,
        )
    }

    /**
     * Protein target in grams. Driven by lean mass where known, then sanity-clamped against
     * bodyweight so a mis-entered body-fat figure cannot produce an absurd number.
     * Ranges follow the sports-nutrition consensus of 1.6-2.2 g/kg for muscle gain, with the
     * upper end during a deficit where protein protects lean mass.
     */
    fun proteinGrams(profile: UserProfile): Double {
        val perKgLean = when (profile.bodyGoal) {
            BodyGoal.LOSE_FAT -> 2.6
            BodyGoal.MAINTAIN -> 2.2
            BodyGoal.BUILD_MUSCLE -> 2.4
        }
        val activityBump = when (profile.activityLevel) {
            ActivityLevel.NOT_ACTIVE -> -0.3
            ActivityLevel.LIGHTLY_ACTIVE -> -0.1
            ActivityLevel.ACTIVE -> 0.0
            ActivityLevel.VERY_ACTIVE -> 0.1
            ActivityLevel.ATHLETE -> 0.2
        }
        val fromLean = profile.leanBodyMassKg * (perKgLean + activityBump)
        return fromLean.coerceIn(profile.weightKg * 1.2, profile.weightKg * 2.6)
    }

    /** Fat target in grams: a share of calories, never below the 0.6 g/kg hormonal floor. */
    fun fatGrams(profile: UserProfile, targetCalories: Int): Double {
        val share = when (profile.bodyGoal) {
            BodyGoal.LOSE_FAT -> 0.25
            BodyGoal.MAINTAIN -> 0.28
            BodyGoal.BUILD_MUSCLE -> 0.27
        }
        val fromCalories = targetCalories * share / 9.0
        return maxOf(fromCalories, profile.weightKg * 0.6)
    }

    /** Carbs take whatever calories protein and fat leave behind. */
    fun carbGrams(targetCalories: Int, proteinGrams: Double, fatGrams: Double): Double =
        ((targetCalories - proteinGrams * 4 - fatGrams * 9) / 4.0).coerceAtLeast(30.0)
}
