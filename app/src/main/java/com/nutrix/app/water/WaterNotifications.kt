package com.nutrix.app.water

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nutrix.app.MainActivity
import com.nutrix.app.R

object WaterNotifications {

    const val CHANNEL_ID = "water_reminders"
    const val NOTIFICATION_ID = 4201

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.water_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.water_channel_description)
            setShowBadge(true)
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Posts the nudge. The amount is in the title because the whole point of the setting is
     * that the user decided how much a drink is — a generic "time to hydrate" wastes it.
     */
    fun showReminder(context: Context, amountMl: Int, consumedMl: Int, goalMl: Int) {
        if (!hasPermission(context)) return

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.DESTINATION_WATER)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val logIntent = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, WaterActionReceiver::class.java).apply {
                action = WaterActionReceiver.ACTION_LOG
                putExtra(WaterActionReceiver.EXTRA_AMOUNT_ML, amountMl)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val snoozeIntent = PendingIntent.getBroadcast(
            context,
            2,
            Intent(context, WaterActionReceiver::class.java).apply {
                action = WaterActionReceiver.ACTION_SNOOZE
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val remaining = (goalMl - consumedMl).coerceAtLeast(0)
        val body = if (remaining == 0) {
            "You have hit your ${formatLitres(goalMl)} goal today. Keep sipping if you are still thirsty."
        } else {
            "${formatLitres(consumedMl)} of ${formatLitres(goalMl)} so far — ${formatLitres(remaining)} to go."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_water_notification)
            .setContentTitle("Time for $amountMl ml of water")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setProgress(goalMl.coerceAtLeast(1), consumedMl.coerceAtMost(goalMl), false)
            .addAction(R.drawable.ic_water_notification, "Drank $amountMl ml", logIntent)
            .addAction(R.drawable.ic_water_notification, "Snooze 15 min", snoozeIntent)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun formatLitres(ml: Int): String =
        if (ml < 1000) "$ml ml" else String.format("%.1f L", ml / 1000.0)
}
