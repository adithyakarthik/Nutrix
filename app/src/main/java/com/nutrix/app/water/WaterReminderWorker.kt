package com.nutrix.app.water

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nutrix.app.NutrixApplication
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Fires one reminder and queues the next.
 *
 * It stays quiet when the goal is already met — an app that nags you after you have done the
 * thing gets its notifications turned off, and then it cannot help at all.
 */
class WaterReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? NutrixApplication)?.container ?: return Result.success()
        val settings = container.waterRepository.currentSettings()
        if (!settings.remindersEnabled) {
            return Result.success()
        }

        val now = LocalDateTime.now()
        val insideWindow = now.toLocalTime() >= LocalTime.ofSecondOfDay(settings.activeStartMinute.toLong() * 60) &&
            now.toLocalTime() <= LocalTime.ofSecondOfDay(settings.activeEndMinute.coerceAtMost(1439).toLong() * 60)

        if (insideWindow) {
            val consumed = container.waterRepository.dayTotal(LocalDate.now())
            if (consumed < settings.dailyGoalMl) {
                WaterNotifications.showReminder(
                    context = applicationContext,
                    amountMl = settings.amountPerReminderMl,
                    consumedMl = consumed,
                    goalMl = settings.dailyGoalMl,
                )
            }
        }

        WaterReminderScheduler.reschedule(applicationContext, settings, now)
        return Result.success()
    }
}
