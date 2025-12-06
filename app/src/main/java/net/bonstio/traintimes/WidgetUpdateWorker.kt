package net.bonstio.traintimes

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
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
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
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

        val client = NationalRailClient(apiKey)
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

    private suspend fun updateSingleWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        client: NationalRailClient
    ) {
        val config = WidgetConfigurationStorage.loadConfiguration(context, appWidgetId) ?: return
        val prefs = context.getSharedPreferences(TrainTimesWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE)

        var fromStation = config.fromStation
        var toStation = config.toStation

        if (config.commutingMode == "LOCATION") {
            val location = getLastLocation(context)
            if (location != null) {
                val fromStationObj = StationRepository.getStation(context, fromStation)
                val toStationObj = StationRepository.getStation(context, toStation)

                if (fromStationObj != null && toStationObj != null) {
                    val distFrom = FloatArray(1)
                    Location.distanceBetween(location.latitude, location.longitude, fromStationObj.lat, fromStationObj.lon, distFrom)

                    val distTo = FloatArray(1)
                    Location.distanceBetween(location.latitude, location.longitude, toStationObj.lat, toStationObj.lon, distTo)

                    if (distTo[0] < distFrom[0]) {
                        val temp = fromStation
                        fromStation = toStation
                        toStation = temp
                    }
                }
            }
        } else {
            val pair = WidgetUtils.determineDirection(config)
            fromStation = pair.first
            toStation = pair.second
        }

        prefs.edit()
            .putString(TrainTimesWidgetProvider.PREF_EFFECTIVE_FROM + appWidgetId, fromStation)
            .putString(TrainTimesWidgetProvider.PREF_EFFECTIVE_TO + appWidgetId, toStation)
            .apply()

        try {
            var trainServices = client.getNextTrain(fromStation, toStation, config.timeOffset, config.departureCount)
            if (config.hidePastDepartures) {
                trainServices = trainServices.filter { !WidgetUtils.isDepartureInPast(it, Calendar.getInstance()) }
            }
            WidgetCache.saveServices(context, appWidgetId, trainServices)
            prefs.edit().remove(TrainTimesWidgetProvider.PREF_LAST_ERROR + appWidgetId).apply()
            
            withContext(Dispatchers.Main) {
                TrainTimesWidgetProvider.updateAppWidget(context, appWidgetManager, appWidgetId, hasData = trainServices.isNotEmpty())
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.departures_list)
            }
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

    private suspend fun getLastLocation(context: Context): Location? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        return suspendCancellableCoroutine { cont ->
            val client = LocationServices.getFusedLocationProviderClient(context)
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location: Location? ->
                    cont.resume(location)
                }
                .addOnFailureListener {
                    cont.resume(null)
                }
                .addOnCanceledListener {
                    cont.resume(null)
                }
        }
    }
}
