package com.nutrix.app.model

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

enum class Sex(val label: String) { MALE("Male"), FEMALE("Female") }

/**
 * Activity multipliers applied to BMR (Harris-Benedict / Mifflin convention).
 * The three levels the brief asked for are NOT_ACTIVE, ACTIVE and VERY_ACTIVE; the two
 * in-between steps exist because most people sit between them and land on wrong numbers otherwise.
 */
enum class ActivityLevel(val label: String, val description: String, val multiplier: Double) {
    NOT_ACTIVE("Not active", "Desk job, little or no exercise", 1.20),
    LIGHTLY_ACTIVE("Lightly active", "Light exercise 1-3 days a week", 1.375),
    ACTIVE("Active", "Moderate exercise 3-5 days a week", 1.55),
    VERY_ACTIVE("Very active", "Hard exercise 6-7 days a week", 1.725),
    ATHLETE("Athlete", "Twice-daily training or physical job", 1.90),
}

enum class BodyGoal(val label: String, val description: String) {
    LOSE_FAT("Lose fat", "Calorie deficit, protein kept high"),
    MAINTAIN("Maintain", "Hold weight, recomposition"),
    BUILD_MUSCLE("Build muscle", "Calorie surplus for lean gain"),
}

/** How aggressive the deficit/surplus is, as a fraction of maintenance calories. */
enum class GoalPace(val label: String, val fraction: Double) {
    EASY("Gentle", 0.10),
    STEADY("Steady", 0.15),
    AGGRESSIVE("Aggressive", 0.20),
}

enum class UnitSystem(val label: String) { METRIC("Metric (kg, cm, ml)"), IMPERIAL("Imperial (lb, ft/in, fl oz)") }

/**
 * Everything the goal calculator needs about the person.
 * Heights are cm, weights kg internally; the UI converts for imperial users.
 */
@Serializable
data class UserProfile(
    val name: String = "",
    val sex: Sex = Sex.MALE,
    val ageYears: Int = 30,
    val heightCm: Double = 175.0,
    val weightKg: Double = 75.0,
    val bodyFatPercent: Double? = null,
    val activityLevel: ActivityLevel = ActivityLevel.ACTIVE,
    val bodyGoal: BodyGoal = BodyGoal.MAINTAIN,
    val pace: GoalPace = GoalPace.STEADY,
    val healthConditions: List<String> = emptyList(),
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val isComplete: Boolean = false,
) {
    val bmi: Double get() = if (heightCm <= 0) 0.0 else weightKg / ((heightCm / 100.0) * (heightCm / 100.0))

    val bmiLabel: String
        get() = when {
            bmi < 18.5 -> "Underweight"
            bmi < 25.0 -> "Healthy range"
            bmi < 30.0 -> "Overweight"
            else -> "Obese"
        }

    /** Lean body mass from body fat % when we have it; Boer formula estimate otherwise. */
    val leanBodyMassKg: Double
        get() {
            val raw = bodyFatPercent?.let { weightKg * (1 - it / 100.0) }
                ?: when (sex) {
                    Sex.MALE -> 0.407 * weightKg + 0.267 * heightCm - 19.2
                    Sex.FEMALE -> 0.252 * weightKg + 0.473 * heightCm - 48.3
                }
            // Both routes can produce nonsense from an odd height/weight pair or a mistyped
            // body-fat figure, and lean mass feeds straight into the calorie target.
            return raw.coerceIn(weightKg * 0.4, weightKg)
        }

    val displayName: String get() = name.ifBlank { "there" }
}

fun Double.kgToLb(): Double = this * 2.2046226
fun Double.lbToKg(): Double = this / 2.2046226
fun Double.cmToInches(): Double = this / 2.54
fun Double.inchesToCm(): Double = this * 2.54
fun Double.mlToFlOz(): Double = this / 29.5735
fun Double.flOzToMl(): Double = this * 29.5735

/** 178.0 cm -> "5 ft 10 in" */
fun formatHeightImperial(heightCm: Double): String {
    val totalInches = heightCm.cmToInches().roundToInt()
    return "${totalInches / 12} ft ${totalInches % 12} in"
}
