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
    private const val NEAREST_STATIONS_GEOFENCE_COUNT = 25
    private const val MAX_TOTAL_GEOFENCES = 95
    private const val GEOFENCE_RECENTER_DISTANCE_METERS = 500f

    @Volatile
    private var lastGeofenceCenterLocation: android.location.Location? = null

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
     * @param referenceLocation Optional current location used to register nearby dynamic geofences for "Use nearest" widgets
     */
    fun updateGeofences(
        context: Context,
        targetWidgetId: Int? = null,
        referenceLocation: android.location.Location? = null
    ) {
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
        var hasNearestModeWidgetWithNotifications = false

        val effectiveCenterLocation = referenceLocation ?: lastGeofenceCenterLocation
        val nearbyDynamicStationCodes: List<String> = if (effectiveCenterLocation != null) {
            StationRepository.findNearestStations(
                context,
                effectiveCenterLocation.latitude,
                effectiveCenterLocation.longitude,
                NEAREST_STATIONS_GEOFENCE_COUNT
            ).map { it.first.code }
        } else {
            emptyList()
        }

        for (id in ids) {
            val config = WidgetConfigurationStorage.loadConfiguration(context, id) ?: continue

            if (config.forceShowNotification) {
                // If forced, immediately update notification (user-initiated only when explicitly saving this widget's config)
                CommuteNotificationManager.fetchAndUpdateNotification(
                    context,
                    id,
                    isUserInitiated = (targetWidgetId == id),
                    triggeringLocation = effectiveCenterLocation
                )
            } else if (!config.showCommuteNotifications) {
                CommuteNotificationManager.cancelNotification(context, id)
            }

            if (config.showCommuteNotifications) {
                activeWidgetIdsWithNotifications.add(id)
                val stationsToGeofence = mutableListOf<String>()
                if (config.fromStation.isNotEmpty()) {
                    stationsToGeofence.addAll(StationRepository.getEquivalentStations(config.fromStation, config.considerNearbyStations))
                }
                if (config.toStation.isNotEmpty()) {
                    stationsToGeofence.addAll(StationRepository.getEquivalentStations(config.toStation, config.considerNearbyStations))
                }
                if (config.useNearestStationForReturn) {
                    hasNearestModeWidgetWithNotifications = true
                    stationsToGeofence.addAll(nearbyDynamicStationCodes)
                }

                for (code in stationsToGeofence.distinct()) {
                    if (geofenceList.size >= MAX_TOTAL_GEOFENCES) break
                    val station = StationRepository.getStation(context, code)
                    if ((station != null) && (station.lat != 0.0) && (station.lon != 0.0)) {
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

        if (referenceLocation != null && hasNearestModeWidgetWithNotifications) {
            lastGeofenceCenterLocation = referenceLocation
        }

        if (geofenceList.isEmpty() && activeWidgetIdsWithNotifications.isEmpty()) {
            Log.d(TAG, "No geofences to register, clearing existing")
            geofencingClient.removeGeofences(getGeofencePendingIntent(context))
            for (id in ids) {
                val config = WidgetConfigurationStorage.loadConfiguration(context, id)
                if (config != null && !config.forceShowNotification) {
                    CommuteNotificationManager.cancelNotification(context, id)
                }
            }
            return
        }

        if (geofenceList.isNotEmpty()) {
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
        }

        if (referenceLocation == null && activeWidgetIdsWithNotifications.isNotEmpty()) {
            // Immediately check current location to post/dismiss notification and seed dynamic nearest geofences
            checkCurrentLocationImmediate(
                context,
                activeWidgetIdsWithNotifications,
                needsDynamicGeofenceSeed = hasNearestModeWidgetWithNotifications && effectiveCenterLocation == null,
                targetWidgetId = targetWidgetId
            )
        }
    }

    /**
     * Called when a new location is observed (e.g. from LocationService or WidgetUpdateWorker).
     * Evaluates proximity for active notification widgets and refreshes dynamic nearest-station geofences if moved significantly.
     */
    fun onLocationChanged(context: Context, location: android.location.Location?) {
        if (location == null) return
        CommuteNotificationManager.recordLocationSpeed(location)

        val am = android.appwidget.AppWidgetManager.getInstance(context)
        val ids = am.getAppWidgetIds(android.content.ComponentName(context, TrainTimesWidgetProvider::class.java)).toSet()
        if (ids.isEmpty()) return

        val activeNotificationWidgetIds = mutableSetOf<Int>()
        var hasNearestModeWidgetWithNotifications = false

        for (id in ids) {
            val config = WidgetConfigurationStorage.loadConfiguration(context, id) ?: continue
            if (config.showCommuteNotifications || config.forceShowNotification) {
                activeNotificationWidgetIds.add(id)
                if (config.showCommuteNotifications && config.useNearestStationForReturn) {
                    hasNearestModeWidgetWithNotifications = true
                }
            }
        }

        if (activeNotificationWidgetIds.isEmpty()) return

        if (hasNearestModeWidgetWithNotifications) {
            val prevCenter = lastGeofenceCenterLocation
            val shouldRecenter = if (prevCenter == null) {
                true
            } else {
                val dist = FloatArray(1)
                android.location.Location.distanceBetween(
                    prevCenter.latitude,
                    prevCenter.longitude,
                    location.latitude,
                    location.longitude,
                    dist
                )
                dist[0] >= GEOFENCE_RECENTER_DISTANCE_METERS
            }
            if (shouldRecenter) {
                Log.d(TAG, "Recentering dynamic geofences around (${location.latitude}, ${location.longitude})")
                updateGeofences(context, referenceLocation = location)
            }
        }

        evaluateAndApply(context, activeNotificationWidgetIds, location)
    }

    private fun checkCurrentLocationImmediate(
        context: Context,
        widgetIds: Set<Int>,
        needsDynamicGeofenceSeed: Boolean = false,
        targetWidgetId: Int? = null
    ) {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            return
        }

        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        fusedClient.lastLocation.addOnSuccessListener { lastLoc ->
            if (lastLoc != null) {
                if (needsDynamicGeofenceSeed) {
                    updateGeofences(context, targetWidgetId = targetWidgetId, referenceLocation = lastLoc)
                }
                evaluateAndApply(context, widgetIds, lastLoc)
            } else {
                fusedClient.getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { currLoc ->
                        if (currLoc != null) {
                            if (needsDynamicGeofenceSeed) {
                                updateGeofences(context, targetWidgetId = targetWidgetId, referenceLocation = currLoc)
                            }
                            evaluateAndApply(context, widgetIds, currLoc)
                        }
                    }
            }
        }
    }

    internal fun findMatchedStationForLocation(
        context: Context,
        config: WidgetConfiguration,
        location: android.location.Location
    ): String? {
        val results = FloatArray(1)

        if (config.useNearestStationForReturn) {
            // 1. If a destination is set, check if user is within geofenceRadius of the scheduled destination first
            // so arrival dismissal triggers reliably even if another station is slightly closer.
            if (config.toStation.isNotEmpty()) {
                val scheduledDestination = WidgetUtils.determineDirection(config).second.ifEmpty { config.toStation }
                val destCodes = StationRepository.getEquivalentStations(scheduledDestination, config.considerNearbyStations)
                for (code in destCodes) {
                    val destStation = StationRepository.getStation(context, code) ?: continue
                    if (destStation.lat != 0.0 || destStation.lon != 0.0) {
                        android.location.Location.distanceBetween(
                            location.latitude,
                            location.longitude,
                            destStation.lat,
                            destStation.lon,
                            results
                        )
                        if (results[0] <= config.geofenceRadius) {
                            return code
                        }
                    }
                }
            }

            // 2. Otherwise check the closest station across all UK stations
            val nearestWithDist = StationRepository.findNearestStationWithDistance(context, location.latitude, location.longitude)
            if (nearestWithDist != null && nearestWithDist.second <= config.geofenceRadius) {
                return nearestWithDist.first.code
            }
            return null
        }

        // Fixed stations mode: check fromStation and toStation (plus sister stations if enabled)
        val stations = mutableListOf<String>()
        if (config.fromStation.isNotEmpty()) {
            stations.addAll(StationRepository.getEquivalentStations(config.fromStation, config.considerNearbyStations))
        }
        if (config.toStation.isNotEmpty()) {
            stations.addAll(StationRepository.getEquivalentStations(config.toStation, config.considerNearbyStations))
        }

        var closestCode: String? = null
        var closestDist = Float.MAX_VALUE

        for (code in stations.distinct()) {
            val station = StationRepository.getStation(context, code) ?: continue
            if (station.lat != 0.0 || station.lon != 0.0) {
                android.location.Location.distanceBetween(
                    location.latitude,
                    location.longitude,
                    station.lat,
                    station.lon,
                    results
                )
                if (results[0] <= config.geofenceRadius && results[0] < closestDist) {
                    closestDist = results[0]
                    closestCode = code
                }
            }
        }

        return closestCode
    }

    private fun evaluateAndApply(context: Context, widgetIds: Set<Int>, location: android.location.Location) {
        CommuteNotificationManager.recordLocationSpeed(location)

        for (id in widgetIds) {
            val config = WidgetConfigurationStorage.loadConfiguration(context, id) ?: continue
            if (config.forceShowNotification) {
                if (config.useNearestStationForReturn) {
                    val nearest = StationRepository.findNearestStation(context, location.latitude, location.longitude)
                    CommuteNotificationManager.fetchAndUpdateNotification(
                        context,
                        id,
                        triggeringStation = nearest?.code,
                        triggeringLocation = location
                    )
                }
                continue
            }
            if (!config.showCommuteNotifications) continue

            val matchedStationCode = findMatchedStationForLocation(context, config, location)

            if (matchedStationCode != null) {
                Log.d(TAG, "Location check: user inside geofence radius for widget $id near $matchedStationCode -> updating notification")
                CommuteNotificationManager.fetchAndUpdateNotification(
                    context,
                    id,
                    isUserInitiated = false,
                    triggeringStation = matchedStationCode,
                    triggeringLocation = location
                )
            } else {
                Log.d(TAG, "Location check: user outside geofence radius for widget $id -> cancelling notification")
                CommuteNotificationManager.cancelNotification(context, id)
            }
        }
    }
}
