package com.nutrix.app.water

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nutrix.app.NutrixApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Handles the notification's "Drank it" and "Snooze" buttons without opening the app. */
class WaterActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as? NutrixApplication)?.container ?: return
        when (intent.action) {
            ACTION_LOG -> {
                val amount = intent.getIntExtra(EXTRA_AMOUNT_ML, 250)
                // goAsync keeps the process alive for the database write; a plain coroutine
                // launched from onReceive can be killed before it commits.
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        container.waterRepository.log(amount)
                        WaterNotifications.cancel(context)
                    } finally {
                        pending.finish()
                    }
                }
            }
            ACTION_SNOOZE -> {
                WaterNotifications.cancel(context)
                WaterReminderScheduler.snooze(context, SNOOZE_MINUTES)
            }
        }
    }

    companion object {
        const val ACTION_LOG = "com.nutrix.app.action.LOG_WATER"
        const val ACTION_SNOOZE = "com.nutrix.app.action.SNOOZE_WATER"
        const val EXTRA_AMOUNT_ML = "amount_ml"
        const val SNOOZE_MINUTES = 15
    }
}
