package com.nutrix.app

import android.app.Application
import com.nutrix.app.water.WaterNotifications
import com.nutrix.app.water.WaterReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NutrixApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        WaterNotifications.createChannel(this)

        // Reminders are rescheduled on launch as well as on boot: a settings change made on a
        // device that was then powered off would otherwise leave a stale chain queued.
        applicationScope.launch {
            val settings = container.preferences.waterSettings.first()
            WaterReminderScheduler.reschedule(this@NutrixApplication, settings)
        }
    }
}
