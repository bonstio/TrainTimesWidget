package net.bonstio.traintimes

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

/**
 * Manages registering and unregistering geofences for departure and arrival stations.
 */
object CommuteGeofenceManager {

    private const val TAG = "CommuteGeofenceManager"

    private fun getGeofencePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, CommuteGeofenceReceiver::class.java).apply {
            action = CommuteGeofenceReceiver.ACTION_GEOFENCE_TRANSITION
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    /**
     * Updates geofences for all widgets or clears them if disabled.
     * @param targetWidgetId Optional specific widget ID being configured (e.g. before it is in AppWidgetManager list)
     */
    fun updateGeofences(context: Context, targetWidgetId: Int? = null) {
        val am = android.appwidget.AppWidgetManager.getInstance(context)
        val ids = am.getAppWidgetIds(android.content.ComponentName(context, TrainTimesWidgetProvider::class.java)).toMutableSet()
        if ((targetWidgetId != null) && (targetWidgetId != android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID)) {
            ids.add(targetWidgetId)
        }
        val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)

        val hasFineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFineLocation) {
            Log.w(TAG, "Location permission not granted, removing all geofences")
            geofencingClient.removeGeofences(getGeofencePendingIntent(context))
            return
        }

        val geofenceList = mutableListOf<Geofence>()
        val activeWidgetIdsWithNotifications = mutableSetOf<Int>()

        for (id in ids) {
            val config = WidgetConfigurationStorage.loadConfiguration(context, id) ?: continue

            if (config.forceShowNotification) {
                // If forced, immediately update notification
                CommuteNotificationManager.fetchAndUpdateNotification(context, id)
            } else if (!config.showCommuteNotifications) {
                CommuteNotificationManager.cancelNotification(context, id)
            }

            // Only register geofences if showCommuteNotifications is ON and useNearestStationForReturn is OFF
            if (config.showCommuteNotifications && !config.useNearestStationForReturn) {
                activeWidgetIdsWithNotifications.add(id)
                val stationsToGeofence = mutableListOf<String>()
                if (config.fromStation.isNotEmpty()) stationsToGeofence.add(config.fromStation)
                if (config.toStation.isNotEmpty()) stationsToGeofence.add(config.toStation)

                for (code in stationsToGeofence.distinct()) {
                    val station = StationRepository.getStation(context, code)
                    if (station != null && station.lat != 0.0 && station.lon != 0.0) {
                        val requestId = "widget_${id}_station_${station.code}"
                        val geofence = Geofence.Builder()
                            .setRequestId(requestId)
                            .setCircularRegion(station.lat, station.lon, config.geofenceRadius.toFloat())
                            .setExpirationDuration(Geofence.NEVER_EXPIRE)
                            .setTransitionTypes(
                                Geofence.GEOFENCE_TRANSITION_ENTER or
                                        Geofence.GEOFENCE_TRANSITION_EXIT or
                                        Geofence.GEOFENCE_TRANSITION_DWELL
                            )
                            .setLoiteringDelay(30_000) // 30s dwell
                            .build()
                        geofenceList.add(geofence)
                    }
                }
            }
        }

        if (geofenceList.isEmpty()) {
            Log.d(TAG, "No geofences to register, clearing existing")
            geofencingClient.removeGeofences(getGeofencePendingIntent(context))
        } else {
            val request = GeofencingRequest.Builder().apply {
                setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER or GeofencingRequest.INITIAL_TRIGGER_DWELL)
                addGeofences(geofenceList)
            }.build()

            try {
                geofencingClient.removeGeofences(getGeofencePendingIntent(context)).addOnCompleteListener {
                    geofencingClient.addGeofences(request, getGeofencePendingIntent(context)).addOnSuccessListener {
                        Log.d(TAG, "Successfully registered ${geofenceList.size} geofences")
                    }.addOnFailureListener { e ->
                        Log.e(TAG, "Failed to register geofences", e)
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException registering geofences", e)
            }

            // Immediately check current location to post or dismiss notification right away
            checkCurrentLocationImmediate(context, activeWidgetIdsWithNotifications)
        }

        // If geofenceList became empty (e.g. notifications disabled), also evaluate cancellation
        if (geofenceList.isEmpty()) {
            for (id in ids) {
                val config = WidgetConfigurationStorage.loadConfiguration(context, id)
                if (config != null && !config.forceShowNotification) {
                    CommuteNotificationManager.cancelNotification(context, id)
                }
            }
        }
    }

    private fun checkCurrentLocationImmediate(context: Context, widgetIds: Set<Int>) {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            return
        }

        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        fusedClient.lastLocation.addOnSuccessListener { lastLoc ->
            if (lastLoc != null) {
                evaluateAndApply(context, widgetIds, lastLoc)
            } else {
                fusedClient.getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { currLoc ->
                        if (currLoc != null) {
                            evaluateAndApply(context, widgetIds, currLoc)
                        }
                    }
            }
        }
    }

    private fun evaluateAndApply(context: Context, widgetIds: Set<Int>, location: android.location.Location) {
        for (id in widgetIds) {
            val config = WidgetConfigurationStorage.loadConfiguration(context, id) ?: continue
            if (config.forceShowNotification) continue // already handled
            if (!config.showCommuteNotifications || config.useNearestStationForReturn) continue

            val stations = mutableListOf<String>()
            if (config.fromStation.isNotEmpty()) stations.add(config.fromStation)
            if (config.toStation.isNotEmpty()) stations.add(config.toStation)

            var matchedStationCode: String? = null
            val results = FloatArray(1)

            for (code in stations.distinct()) {
                val station = StationRepository.getStation(context, code) ?: continue
                if (station.lat != 0.0 && station.lon != 0.0) {
                    android.location.Location.distanceBetween(
                        location.latitude,
                        location.longitude,
                        station.lat,
                        station.lon,
                        results
                    )
                    if (results[0] <= config.geofenceRadius) {
                        matchedStationCode = code
                        break
                    }
                }
            }

            if (matchedStationCode != null) {
                Log.d(TAG, "Immediate check: user inside geofence radius for widget $id near $matchedStationCode -> updating notification")
                CommuteNotificationManager.fetchAndUpdateNotification(context, id, isUserInitiated = true, triggeringStation = matchedStationCode)
            } else {
                Log.d(TAG, "Immediate check: user outside geofence radius for widget $id -> cancelling notification")
                CommuteNotificationManager.cancelNotification(context, id)
            }
        }
    }
}
