package net.bonstio.traintimes

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.coroutines.resume

class WidgetUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "WidgetUpdateWorker"
        const val KEY_WIDGET_IDS = "widget_ids"
        private const val NOTIFICATION_CHANNEL_ID = "widget_update_channel"
        private const val NOTIFICATION_ID = 42
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // Try to promote to foreground service to access location in background
        try {
            setForeground(createForegroundInfo())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to run as foreground service", e)
        }

        Log.d(TAG, "Starting widget update work")
        val context = applicationContext
        val appWidgetManager = AppWidgetManager.getInstance(context)
        
        val widgetIds = inputData.getIntArray(KEY_WIDGET_IDS) ?: AppWidgetManager.getInstance(context)
            .getAppWidgetIds(android.content.ComponentName(context, TrainTimesWidgetProvider::class.java))

        val prefs = context.getSharedPreferences(TrainTimesWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE)
        val apiKey = prefs.getString(TrainTimesWidgetProvider.PREF_API_KEY, null)

        if (apiKey.isNullOrEmpty()) {
            Log.w(TAG, "No API key found, skipping update")
            return@withContext Result.failure()
        }

        val client = RailDataClient(apiKey)
        var success = true

        for (appWidgetId in widgetIds) {
            try {
                updateSingleWidget(context, appWidgetManager, appWidgetId, client)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update widget $appWidgetId", e)
                success = false
            }
        }

        if (success) Result.success() else Result.retry()
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val notification = createNotification()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotification(): Notification {
        val context = applicationContext
        val channelId = NOTIFICATION_CHANNEL_ID
        val title = context.getString(R.string.app_name)
        val content = "Updating train times..." // Ideally string resource

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Widget Updates", NotificationManager.IMPORTANCE_LOW)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setTicker(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.train_24px)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private suspend fun updateSingleWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        client: RailDataClient
    ) {
        val startTime = System.currentTimeMillis()
        val config = WidgetConfigurationStorage.loadConfiguration(context, appWidgetId) ?: return
        val prefs = context.getSharedPreferences(TrainTimesWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE)

        val originalFrom = config.fromStation
        val originalTo = config.toStation
        
        var effectiveFrom = originalFrom
        var effectiveTo = originalTo
        var isReversed = false

        Log.d(TAG, "Widget $appWidgetId update: From=$originalFrom, To=$originalTo, Mode=${config.commutingMode}")

        // Optimize: Fetch location once if needed by either feature
        var location: Location? = null
        if (config.commutingMode == "LOCATION" || config.useNearestStationForReturn) {
            val locStart = System.currentTimeMillis()
            location = fetchLocation(context)
            Log.d(TAG, "Location fetch took ${System.currentTimeMillis() - locStart}ms. Location found: ${location != null} (Provider: ${location?.provider}, Accuracy: ${location?.accuracy})")
        }

        if (config.commutingMode == "LOCATION") {
            if (location != null) {
                val fromStationObj = StationRepository.getStation(context, originalFrom)
                val toStationObj = StationRepository.getStation(context, originalTo)

                if (fromStationObj != null && toStationObj != null) {
                    val distFrom = FloatArray(1)
                    Location.distanceBetween(location.latitude, location.longitude, fromStationObj.lat, fromStationObj.lon, distFrom)

                    val distTo = FloatArray(1)
                    Location.distanceBetween(location.latitude, location.longitude, toStationObj.lat, toStationObj.lon, distTo)

                    Log.d(TAG, "Distances: To $originalFrom=${distFrom[0]}m, To $originalTo=${distTo[0]}m")

                    if (distTo[0] < distFrom[0]) {
                        isReversed = true
                        Log.d(TAG, "Reversing route based on location")
                    }
                }
            } else {
                Log.w(TAG, "Location unavailable for commuting logic")
            }
        } else {
            val pair = WidgetUtils.determineDirection(config)
            // If the start station is the 'To' station, it's reversed
            if (pair.first == originalTo && originalTo.isNotEmpty()) {
                isReversed = true
                Log.d(TAG, "Reversing route based on time")
            }
        }

        if (isReversed) {
            effectiveFrom = originalTo
            effectiveTo = originalFrom
        }
        
        // "Use nearest station" logic. 
        if (config.useNearestStationForReturn) {
             if (location != null) {
                  val nearestStart = System.currentTimeMillis()
                  val nearest = StationRepository.findNearestStation(context, location.latitude, location.longitude)
                  Log.d(TAG, "Nearest station search took ${System.currentTimeMillis() - nearestStart}ms. Result: ${nearest?.code}")
                  
                  if (nearest != null) {
                      effectiveFrom = nearest.code
                      Log.d(TAG, "Overriding start station to nearest: ${nearest.code}")
                  }
             } else if (config.commutingMode != "LOCATION") {
                 Log.w(TAG, "Location unavailable for nearest station logic")
             }
        }

        Log.d(TAG, "Final route: $effectiveFrom -> $effectiveTo")

        prefs.edit()
            .putString(TrainTimesWidgetProvider.PREF_EFFECTIVE_FROM + appWidgetId, effectiveFrom)
            .putString(TrainTimesWidgetProvider.PREF_EFFECTIVE_TO + appWidgetId, effectiveTo)
            .apply()

        try {
            val apiStart = System.currentTimeMillis()
            var trainServices = client.getNextTrain(effectiveFrom, effectiveTo, config.timeOffset, config.departureCount)
            Log.d(TAG, "API call took ${System.currentTimeMillis() - apiStart}ms. Found ${trainServices.size} trains")
            
            val originalCount = trainServices.size
            
            // Always filter past departures with buffer
            trainServices = trainServices.filter { !WidgetUtils.isDepartureInPast(it, Calendar.getInstance()) }
            
            if (trainServices.isEmpty() && originalCount > 0) {
                // Trains were found but filtered out
                prefs.edit().putString(TrainTimesWidgetProvider.PREF_LAST_ERROR + appWidgetId, "FILTERED").apply()
            } else {
                prefs.edit().remove(TrainTimesWidgetProvider.PREF_LAST_ERROR + appWidgetId).apply()
            }

            WidgetCache.saveServices(context, appWidgetId, trainServices)
            prefs.edit()
                .putLong(TrainTimesWidgetProvider.PREF_LAST_SUCCESSFUL_UPDATE + appWidgetId, System.currentTimeMillis())
                .apply()
            
            withContext(Dispatchers.Main) {
                TrainTimesWidgetProvider.updateAppWidget(context, appWidgetManager, appWidgetId, hasData = trainServices.isNotEmpty())
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.departures_list)
            }
            Log.d(TAG, "Total update time: ${System.currentTimeMillis() - startTime}ms")
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching data for widget $appWidgetId", e)
            val errorType = if (e is java.io.IOException) "NETWORK" else "GENERIC"
            prefs.edit().putString(TrainTimesWidgetProvider.PREF_LAST_ERROR + appWidgetId, errorType).apply()
            withContext(Dispatchers.Main) {
                TrainTimesWidgetProvider.updateAppWidget(context, appWidgetManager, appWidgetId, hasData = false)
            }
            throw e 
        }
    }

    private suspend fun fetchLocation(context: Context): Location? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        return suspendCancellableCoroutine { cont ->
            val client = LocationServices.getFusedLocationProviderClient(context)
            val cancellationTokenSource = CancellationTokenSource()

            cont.invokeOnCancellation {
                try {
                    cancellationTokenSource.cancel()
                } catch (e: Exception) {
                    // Ignore
                }
            }
            
            // Try to get fresh location first with HIGH_ACCURACY (GPS) because user clicked refresh.
            // Use CurrentLocationRequest to enforce max age and timeout.
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setDurationMillis(30000) 
                .setMaxUpdateAgeMillis(60000)
                .build()

            client.getCurrentLocation(request, cancellationTokenSource.token)
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        cont.resume(location)
                    } else {
                        // If null, try last known location as fallback
                        client.lastLocation
                            .addOnSuccessListener { lastLoc: Location? ->
                                if (lastLoc != null && isLocationFresh(lastLoc)) {
                                    cont.resume(lastLoc)
                                } else {
                                    Log.w(TAG, "Last location is null or too old. Age: ${if (lastLoc != null) (System.currentTimeMillis() - lastLoc.time) / 1000 else "N/A"}s")
                                    cont.resume(null)
                                }
                            }
                            .addOnFailureListener {
                                cont.resume(null)
                            }
                    }
                }
                .addOnFailureListener {
                    // On failure, also try last known
                    client.lastLocation
                        .addOnSuccessListener { lastLoc: Location? ->
                            if (lastLoc != null && isLocationFresh(lastLoc)) {
                                cont.resume(lastLoc)
                            } else {
                                cont.resume(null)
                            }
                        }
                        .addOnFailureListener {
                            cont.resume(null)
                        }
                }
        }
    }
    
    private fun isLocationFresh(location: Location): Boolean {
        // Discard location if older than 30 minutes
        val age = System.currentTimeMillis() - location.time
        return age < 30 * 60 * 1000
    }
}
