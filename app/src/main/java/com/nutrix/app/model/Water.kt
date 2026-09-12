package com.nutrix.app.model

import kotlinx.serialization.Serializable

/**
 * Reminder configuration. The gap and the per-drink amount are both user-editable, which is
 * why the app checks them against each other: a 2 L goal with 100 ml sips every 3 hours
 * cannot succeed inside a waking day, and Nutrix says so instead of nagging pointlessly.
 */
@Serializable
data class WaterSettings(
    val dailyGoalMl: Int = 2500,
    val goalIsManual: Boolean = false,
    val remindersEnabled: Boolean = true,
    val intervalMinutes: Int = 90,
    val amountPerReminderMl: Int = 250,
    /** Minutes from midnight; reminders never fire outside this window. */
    val activeStartMinute: Int = 8 * 60,
    val activeEndMinute: Int = 22 * 60,
) {
    val activeMinutes: Int get() = (activeEndMinute - activeStartMinute).coerceAtLeast(0)

    val remindersPerDay: Int get() = if (intervalMinutes <= 0) 0 else activeMinutes / intervalMinutes + 1

    val reachableMl: Int get() = remindersPerDay * amountPerReminderMl
}

@Serializable
data class WaterLogEntry(
    val id: Long = 0L,
    val epochDay: Long,
    val amountMl: Int,
    val loggedAtEpochMs: Long,
)

enum class GoalVerdict(val label: String) {
    TOO_LOW("Below what your body needs"),
    SLIGHTLY_LOW("A little low"),
    HEALTHY("Well matched to your body"),
    SLIGHTLY_HIGH("A little high"),
    TOO_HIGH("Higher than recommended"),
}

/**
 * The app's read on a water goal: what it recommends for this body, the safe band around it,
 * and plain-language advice about the number the user chose.
 */
data class WaterAdvice(
    val recommendedMl: Int,
    val lowerBoundMl: Int,
    val upperBoundMl: Int,
    val verdict: GoalVerdict,
    val headline: String,
    val detail: String,
    val suggestions: List<String> = emptyList(),
)
