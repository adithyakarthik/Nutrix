package com.nutrix.app.nutrition

import com.nutrix.app.model.ActivityLevel
import com.nutrix.app.model.GoalVerdict
import com.nutrix.app.model.UserProfile
import com.nutrix.app.model.WaterAdvice
import com.nutrix.app.model.WaterSettings
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.roundToInt

/** A concrete reminder plan: when to ping, how much to drink, and whether it adds up. */
data class ReminderPlan(
    val remindersPerDay: Int,
    val amountPerReminderMl: Int,
    val reachableMl: Int,
    val goalMl: Int,
    val isAchievable: Boolean,
    val advice: String,
    val suggestedIntervalMinutes: Int,
    val suggestedAmountMl: Int,
)

/**
 * Water targets, safety bounds, and reminder spacing.
 *
 * The bounds are not decoration. Over-drinking is a real risk for the athletic end of this
 * app's audience — exercise-associated hyponatraemia comes from drinking past what the kidneys
 * can clear (roughly 800-1000 ml an hour) — so a goal that is too high gets pushed back on just
 * as firmly as one that is too low.
 */
object WaterCalculator {

    private const val ABSOLUTE_CEILING_ML = 5000
    private const val ABSOLUTE_FLOOR_ML = 1200

    /** Age-banded baseline in ml per kg of bodyweight, the standard clinical rule of thumb. */
    private fun baselineMlPerKg(age: Int): Double = when {
        age <= 30 -> 38.0
        age <= 54 -> 35.0
        age <= 65 -> 30.0
        else -> 27.0
    }

    private fun activityBonusMl(level: ActivityLevel): Int = when (level) {
        ActivityLevel.NOT_ACTIVE -> 0
        ActivityLevel.LIGHTLY_ACTIVE -> 250
        ActivityLevel.ACTIVE -> 500
        ActivityLevel.VERY_ACTIVE -> 850
        ActivityLevel.ATHLETE -> 1200
    }

    fun recommendedMl(profile: UserProfile): Int {
        val base = profile.weightKg * baselineMlPerKg(profile.ageYears)
        val total = base + activityBonusMl(profile.activityLevel)
        return (total / 50.0).roundToInt() * 50  // round to a tidy 50 ml
    }

    fun lowerBoundMl(profile: UserProfile): Int =
        maxOf(ABSOLUTE_FLOOR_ML, (profile.weightKg * 22.0).roundToInt())

    fun upperBoundMl(profile: UserProfile): Int =
        minOf(ABSOLUTE_CEILING_ML, (profile.weightKg * 55.0).roundToInt())

    /** Judges the goal the user actually chose, and says why. */
    fun advise(profile: UserProfile, goalMl: Int): WaterAdvice {
        val recommended = recommendedMl(profile)
        val lower = lowerBoundMl(profile)
        val upper = upperBoundMl(profile)
        val ratio = if (recommended > 0) goalMl.toDouble() / recommended else 1.0

        val verdict = when {
            goalMl < lower -> GoalVerdict.TOO_LOW
            ratio < 0.85 -> GoalVerdict.SLIGHTLY_LOW
            goalMl > upper -> GoalVerdict.TOO_HIGH
            ratio > 1.20 -> GoalVerdict.SLIGHTLY_HIGH
            else -> GoalVerdict.HEALTHY
        }

        val litres = { ml: Int -> String.format("%.1f L", ml / 1000.0) }
        val headline = when (verdict) {
            GoalVerdict.TOO_LOW -> "That goal is too low for your body"
            GoalVerdict.SLIGHTLY_LOW -> "A bit under what your body needs"
            GoalVerdict.HEALTHY -> "Well matched to your body"
            GoalVerdict.SLIGHTLY_HIGH -> "A little more than you need"
            GoalVerdict.TOO_HIGH -> "That is more water than is safe to aim for"
        }
        val detail = when (verdict) {
            GoalVerdict.TOO_LOW ->
                "For ${profile.weightKg.roundToInt()} kg at ${profile.activityLevel.label.lowercase()} " +
                    "activity, ${litres(recommended)} is the mark. Under ${litres(lower)} you are " +
                    "running a deficit — expect flat training, headaches and poor recovery."
            GoalVerdict.SLIGHTLY_LOW ->
                "${litres(recommended)} would suit you better. You are close, so an extra glass " +
                    "or two across the day closes it."
            GoalVerdict.HEALTHY ->
                "${litres(goalMl)} sits right around the ${litres(recommended)} your bodyweight and " +
                    "training load call for. Food contributes roughly another 20% on top."
            GoalVerdict.SLIGHTLY_HIGH ->
                "More than the ${litres(recommended)} you need. Harmless if it is spread out, but " +
                    "you will spend the day finding bathrooms."
            GoalVerdict.TOO_HIGH ->
                "Above ${litres(upper)} the kidneys cannot clear it fast enough and blood sodium " +
                    "can drop — that is hyponatraemia, and it is dangerous. Come back to " +
                    "${litres(recommended)} unless a doctor set this number."
        }

        val suggestions = buildList {
            if (verdict == GoalVerdict.TOO_HIGH || verdict == GoalVerdict.SLIGHTLY_HIGH) {
                add("Never drink more than about 800 ml in a single hour.")
            }
            if (goalMl >= 3500) {
                add("Above 3.5 L a day, replace electrolytes too — plain water alone dilutes sodium.")
            }
            if (profile.activityLevel == ActivityLevel.VERY_ACTIVE ||
                profile.activityLevel == ActivityLevel.ATHLETE
            ) {
                add("Add 500-750 ml per hour of hard training, on top of this goal.")
                add("Weigh yourself before and after a long session: each kg lost is ~1 L to replace.")
            }
            add("Pale straw urine means you are on track. Clear all day means you are overdoing it.")
            if (verdict == GoalVerdict.TOO_LOW || verdict == GoalVerdict.SLIGHTLY_LOW) {
                add("Front-load: 500 ml on waking is the easiest win of the day.")
            }
        }

        return WaterAdvice(
            recommendedMl = recommended,
            lowerBoundMl = lower,
            upperBoundMl = upper,
            verdict = verdict,
            headline = headline,
            detail = detail,
            suggestions = suggestions,
        )
    }

    /**
     * Checks that the reminder gap and the per-drink amount can actually add up to the goal
     * inside the active window, and proposes a pairing that does when they cannot.
     */
    fun planReminders(settings: WaterSettings): ReminderPlan {
        val windowMinutes = settings.activeMinutes.coerceAtLeast(0)
        val interval = settings.intervalMinutes.coerceAtLeast(15)
        val reminders = if (windowMinutes == 0) 0 else windowMinutes / interval + 1
        val reachable = reminders * settings.amountPerReminderMl
        val goal = settings.dailyGoalMl

        // A drink is comfortable up to ~400 ml; beyond that, shorten the gap instead.
        val suggestedAmount = when {
            reminders <= 0 -> 250
            else -> ((goal.toDouble() / reminders) / 25.0).roundToInt() * 25
        }.coerceIn(150, 400)
        val neededReminders = if (suggestedAmount > 0) {
            Math.ceil(goal.toDouble() / suggestedAmount).toInt().coerceAtLeast(1)
        } else {
            1
        }
        val suggestedInterval = if (neededReminders > 1) {
            (windowMinutes / (neededReminders - 1)).coerceIn(30, 240)
        } else {
            interval
        }

        val shortfall = goal - reachable
        val achievable = shortfall <= goal / 20  // within 5% counts as met
        val advice = when {
            reminders == 0 ->
                "Your active window is empty — widen the start and end times before turning reminders on."
            achievable && settings.amountPerReminderMl > 500 ->
                "This reaches your goal, but ${settings.amountPerReminderMl} ml in one go is a lot to " +
                    "drink at once. Smaller and more often absorbs better."
            achievable ->
                "$reminders reminders of ${settings.amountPerReminderMl} ml covers " +
                    "${String.format("%.1f L", reachable / 1000.0)} — your goal is met with room to spare."
            else ->
                "$reminders reminders of ${settings.amountPerReminderMl} ml only reaches " +
                    "${String.format("%.1f L", reachable / 1000.0)}, leaving ${shortfall} ml short. " +
                    "Try $suggestedAmount ml every ${formatGap(suggestedInterval)} instead."
        }

        return ReminderPlan(
            remindersPerDay = reminders,
            amountPerReminderMl = settings.amountPerReminderMl,
            reachableMl = reachable,
            goalMl = goal,
            isAchievable = achievable,
            advice = advice,
            suggestedIntervalMinutes = suggestedInterval,
            suggestedAmountMl = suggestedAmount,
        )
    }

    /**
     * The next moment a reminder should fire: the next slot inside today's window, or the start
     * of tomorrow's once the day is done. Pure arithmetic, so the scheduler stays a thin shell
     * over something that can be tested.
     */
    fun nextReminderAt(settings: WaterSettings, from: LocalDateTime): LocalDateTime {
        val startTime = LocalTime.ofSecondOfDay(settings.activeStartMinute.coerceIn(0, 1439).toLong() * 60)
        val endTime = LocalTime.ofSecondOfDay(settings.activeEndMinute.coerceIn(0, 1439).toLong() * 60)

        val todayStart = from.toLocalDate().atTime(startTime)
        val todayEnd = from.toLocalDate().atTime(endTime)

        return when {
            from.isBefore(todayStart) -> todayStart
            from.isAfter(todayEnd) -> todayStart.plusDays(1)
            else -> {
                val candidate = from.plusMinutes(settings.intervalMinutes.coerceAtLeast(1).toLong())
                if (candidate.isAfter(todayEnd)) todayStart.plusDays(1) else candidate
            }
        }
    }

    fun formatGap(minutes: Int): String = when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0 -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}
