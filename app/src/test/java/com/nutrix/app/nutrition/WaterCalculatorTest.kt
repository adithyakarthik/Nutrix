package com.nutrix.app.nutrition

import com.nutrix.app.model.ActivityLevel
import com.nutrix.app.model.GoalVerdict
import com.nutrix.app.model.UserProfile
import com.nutrix.app.model.WaterSettings
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterCalculatorTest {

    private val profile = UserProfile(
        ageYears = 28,
        heightCm = 180.0,
        weightKg = 80.0,
        activityLevel = ActivityLevel.ACTIVE,
        isComplete = true,
    )

    @Test
    fun `recommendation scales with bodyweight and training load`() {
        val light = WaterCalculator.recommendedMl(profile.copy(activityLevel = ActivityLevel.NOT_ACTIVE))
        val heavy = WaterCalculator.recommendedMl(profile.copy(activityLevel = ActivityLevel.ATHLETE))
        assertTrue(heavy > light)
        val heavier = WaterCalculator.recommendedMl(profile.copy(weightKg = 100.0))
        assertTrue(heavier > WaterCalculator.recommendedMl(profile))
    }

    @Test
    fun `a goal near the recommendation is judged healthy`() {
        val recommended = WaterCalculator.recommendedMl(profile)
        assertEquals(GoalVerdict.HEALTHY, WaterCalculator.advise(profile, recommended).verdict)
    }

    @Test
    fun `an unsafe goal is called out, not quietly accepted`() {
        val advice = WaterCalculator.advise(profile, 6000)
        assertEquals(GoalVerdict.TOO_HIGH, advice.verdict)
        assertTrue(advice.detail.contains("hyponatraemia"))
        assertTrue(advice.suggestions.any { it.contains("800 ml") })
    }

    @Test
    fun `a starvation goal is called out too`() {
        assertEquals(GoalVerdict.TOO_LOW, WaterCalculator.advise(profile, 900).verdict)
    }

    @Test
    fun `a schedule that cannot reach the goal is flagged with a workable alternative`() {
        val settings = WaterSettings(
            dailyGoalMl = 3000,
            intervalMinutes = 180,
            amountPerReminderMl = 100,
            activeStartMinute = 8 * 60,
            activeEndMinute = 22 * 60,
        )
        val plan = WaterCalculator.planReminders(settings)
        assertFalse(plan.isAchievable)
        assertTrue(plan.reachableMl < settings.dailyGoalMl)

        val fixed = settings.copy(
            intervalMinutes = plan.suggestedIntervalMinutes,
            amountPerReminderMl = plan.suggestedAmountMl,
        )
        assertTrue(
            "the suggestion should actually reach the goal",
            WaterCalculator.planReminders(fixed).isAchievable,
        )
    }

    @Test
    fun `a workable schedule is left alone`() {
        val settings = WaterSettings(
            dailyGoalMl = 2500,
            intervalMinutes = 90,
            amountPerReminderMl = 250,
            activeStartMinute = 8 * 60,
            activeEndMinute = 22 * 60,
        )
        assertTrue(WaterCalculator.planReminders(settings).isAchievable)
    }

    @Test
    fun `the next reminder lands one gap later inside the window`() {
        val settings = WaterSettings(intervalMinutes = 90, activeStartMinute = 8 * 60, activeEndMinute = 22 * 60)
        val now = LocalDateTime.of(2026, 3, 2, 10, 0)
        assertEquals(LocalDateTime.of(2026, 3, 2, 11, 30), WaterCalculator.nextReminderAt(settings, now))
    }

    @Test
    fun `reminders do not fire before the window opens`() {
        val settings = WaterSettings(intervalMinutes = 90, activeStartMinute = 8 * 60, activeEndMinute = 22 * 60)
        val night = LocalDateTime.of(2026, 3, 2, 5, 0)
        assertEquals(LocalDateTime.of(2026, 3, 2, 8, 0), WaterCalculator.nextReminderAt(settings, night))
    }

    @Test
    fun `the last slot of the day rolls over to tomorrow morning`() {
        val settings = WaterSettings(intervalMinutes = 90, activeStartMinute = 8 * 60, activeEndMinute = 22 * 60)
        val late = LocalDateTime.of(2026, 3, 2, 21, 30)
        assertEquals(LocalDateTime.of(2026, 3, 3, 8, 0), WaterCalculator.nextReminderAt(settings, late))

        val afterHours = LocalDateTime.of(2026, 3, 2, 23, 45)
        assertEquals(LocalDateTime.of(2026, 3, 3, 8, 0), WaterCalculator.nextReminderAt(settings, afterHours))
    }
}
