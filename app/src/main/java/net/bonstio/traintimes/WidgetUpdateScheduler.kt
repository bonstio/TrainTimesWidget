package net.bonstio.traintimes

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * Helper object to schedule automatic widget updates using AlarmManager.
 */
object WidgetUpdateScheduler {
    const val ACTION_AUTO_UPDATE = "net.bonstio.traintimes.ACTION_AUTO_UPDATE"

    /**
     * Schedules or cancels the automatic update alarm based on the interval.
     *
     * @param context The application context.
     * @param intervalMinutes The update interval in minutes. If 0, the alarm is cancelled.
     */
    fun scheduleUpdate(context: Context, intervalMinutes: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, TrainTimesWidgetProvider::class.java).apply {
            action = ACTION_AUTO_UPDATE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Always cancel existing alarm first
        alarmManager.cancel(pendingIntent)

        if (intervalMinutes > 0) {
            val intervalMillis = intervalMinutes * 60 * 1000L
            // Start the first update after one interval
            val triggerAtMillis = SystemClock.elapsedRealtime() + intervalMillis
            alarmManager.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME,
                triggerAtMillis,
                intervalMillis,
                pendingIntent
            )
        }
    }
}