package com.nutrix.app.model

import kotlinx.serialization.Serializable

enum class GoalSource(val label: String) {
    CALCULATED("Calculated from your profile"),
    AI_ADJUSTED("Reviewed by Nutrix AI"),
    MANUAL("Edited by you"),
}

/**
 * A full day's targets: one number per nutrient, plus a record of which ones the user
 * overrode by hand so a profile change never silently stomps a deliberate choice.
 */
@Serializable
data class NutrientGoals(
    val targets: Map<Nutrient, Double> = emptyMap(),
    val manualOverrides: Map<Nutrient, Double> = emptyMap(),
    val source: GoalSource = GoalSource.CALCULATED,
    val rationale: String = "",
    val notes: List<String> = emptyList(),
    val generatedAtEpochMs: Long = 0L,
) {
    /** The number the app actually holds you to. */
    fun target(nutrient: Nutrient): Double? = manualOverrides[nutrient] ?: targets[nutrient]

    fun isOverridden(nutrient: Nutrient): Boolean = manualOverrides.containsKey(nutrient)

    fun withOverride(nutrient: Nutrient, value: Double?): NutrientGoals = copy(
        manualOverrides = if (value == null) manualOverrides - nutrient else manualOverrides + (nutrient to value),
        source = GoalSource.MANUAL,
    )

    val isEmpty: Boolean get() = targets.isEmpty() && manualOverrides.isEmpty()

    companion object {
        val EMPTY = NutrientGoals()
    }
}

/** How today's intake compares with the target for one nutrient. */
data class NutrientProgress(
    val nutrient: Nutrient,
    val consumed: Double,
    val target: Double?,
) {
    val fraction: Float
        get() = if (target == null || target <= 0.0) 0f else (consumed / target).toFloat()

    val remaining: Double get() = (target ?: 0.0) - consumed

    val status: Status
        get() {
            val t = target ?: return Status.UNTRACKED
            if (t <= 0.0) return Status.UNTRACKED
            val ratio = consumed / t
            return when (nutrient.direction) {
                GoalDirection.AT_LEAST -> when {
                    ratio >= 1.0 -> Status.MET
                    ratio >= 0.7 -> Status.CLOSE
                    else -> Status.SHORT
                }
                GoalDirection.AT_MOST -> when {
                    ratio <= 1.0 -> Status.MET
                    ratio <= 1.15 -> Status.CLOSE
                    else -> Status.OVER
                }
                GoalDirection.AROUND -> when {
                    ratio in 0.9..1.1 -> Status.MET
                    ratio > 1.1 -> Status.OVER
                    ratio >= 0.7 -> Status.CLOSE
                    else -> Status.SHORT
                }
            }
        }

    enum class Status { MET, CLOSE, SHORT, OVER, UNTRACKED }
}
