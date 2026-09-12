package com.nutrix.app.water

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.nutrix.app.model.WaterSettings
import com.nutrix.app.nutrition.WaterCalculator
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Schedules the next water nudge.
 *
 * A self-rescheduling one-time job, not a periodic one: WorkManager's periodic minimum is 15
 * minutes and its period cannot be changed without recreating the work anyway, while the gap
 * here is a user setting that has to be respected exactly and must stop at the end of their
 * waking window. Each run queues the next.
 */
object WaterReminderScheduler {

    const val WORK_NAME = "nutrix_water_reminder"

    fun reschedule(context: Context, settings: WaterSettings, from: LocalDateTime = LocalDateTime.now()) {
        val workManager = WorkManager.getInstance(context)
        if (!settings.remindersEnabled || settings.intervalMinutes <= 0 || settings.activeMinutes <= 0) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val next = WaterCalculator.nextReminderAt(settings, from)
        val delay = Duration.between(from, next).toMillis().coerceAtLeast(60_000L)

        workManager.enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<WaterReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .addTag(WORK_NAME)
                .build(),
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /** Queues a single reminder [minutes] from now without disturbing the daily rhythm. */
    fun snooze(context: Context, minutes: Int) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<WaterReminderWorker>()
                .setInitialDelay(minutes.toLong(), TimeUnit.MINUTES)
                .addTag(WORK_NAME)
                .build(),
        )
    }
}
