package net.bonstio.traintimes

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Helper object to schedule automatic widget updates using WorkManager.
 */
object WidgetUpdateScheduler {
    private const val WORK_NAME = "widget_auto_update"

    /**
     * Schedules or cancels the automatic update worker based on the interval.
     *
     * @param context The application context.
     * @param intervalMinutes The update interval in minutes. If 0, the worker is cancelled.
     * Note: WorkManager minimum interval is 15 minutes.
     */
    fun scheduleUpdate(context: Context, intervalMinutes: Int) {
        val workManager = WorkManager.getInstance(context)

        if (intervalMinutes > 0) {
            // WorkManager has a minimum interval of 15 minutes.
            val interval = intervalMinutes.toLong().coerceAtLeast(15)
            
            val workRequest = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
                interval, TimeUnit.MINUTES
            ).build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        } else {
            workManager.cancelUniqueWork(WORK_NAME)
        }
    }
}
