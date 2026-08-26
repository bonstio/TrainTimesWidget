package net.bonstio.traintimes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

/**
 * Handles geofence transition events when entering or exiting near departure/arrival stations.
 */
class CommuteGeofenceReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CommuteGeofenceReceiver"
        const val ACTION_GEOFENCE_TRANSITION = "net.bonstio.traintimes.ACTION_GEOFENCE_TRANSITION"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return
        if (geofencingEvent.hasError()) {
            Log.e(TAG, "Geofencing error: ${geofencingEvent.errorCode}")
            return
        }

        val transition = geofencingEvent.geofenceTransition
        val triggeringGeofences = geofencingEvent.triggeringGeofences ?: return

        Log.d(TAG, "Geofence transition: $transition with ${triggeringGeofences.size} geofences")

        if (transition == Geofence.GEOFENCE_TRANSITION_ENTER || transition == Geofence.GEOFENCE_TRANSITION_DWELL) {
            for (geofence in triggeringGeofences) {
                // Request ID is formatted as "widget_${widgetId}_station_${stationCode}"
                val parts = geofence.requestId.split("_")
                if (parts.size >= 4 && parts[0] == "widget") {
                    val appWidgetId = parts[1].toIntOrNull() ?: continue
                    Log.d(TAG, "Triggering notification update for widget $appWidgetId due to geofence ${geofence.requestId}")
                    CommuteNotificationManager.fetchAndUpdateNotification(context, appWidgetId)
                }
            }
        } else if (transition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            for (geofence in triggeringGeofences) {
                val parts = geofence.requestId.split("_")
                if (parts.size >= 4 && parts[0] == "widget") {
                    val appWidgetId = parts[1].toIntOrNull() ?: continue
                    val config = WidgetConfigurationStorage.loadConfiguration(context, appWidgetId)
                    if (config != null && !config.forceShowNotification) {
                        Log.d(TAG, "Cancelling notification for widget $appWidgetId on geofence exit")
                        CommuteNotificationManager.cancelNotification(context, appWidgetId)
                    }
                }
            }
        }
    }
}
