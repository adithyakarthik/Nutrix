package com.nutrix.app.water

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nutrix.app.NutrixApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** WorkManager survives reboots, but a re-install or a cleared queue does not — so re-arm here. */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val container = (context.applicationContext as? NutrixApplication)?.container ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WaterReminderScheduler.reschedule(context, container.waterRepository.currentSettings())
            } finally {
                pending.finish()
            }
        }
    }
}
