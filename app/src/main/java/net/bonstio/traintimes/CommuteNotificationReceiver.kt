package net.bonstio.traintimes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Handles actions from the commute notification (e.g. Refresh button).
 */
class CommuteNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == CommuteNotificationManager.ACTION_REFRESH_NOTIFICATION) {
            val appWidgetId = intent.getIntExtra(CommuteNotificationManager.EXTRA_WIDGET_ID, -1)
            Log.d("CommuteNotifReceiver", "Refresh requested for widget $appWidgetId")
            if (appWidgetId != -1) {
                CommuteNotificationManager.fetchAndUpdateNotification(context, appWidgetId, isUserInitiated = true)
            }
        }
    }
}
