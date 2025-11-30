package net.bonstio.traintimes

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.net.Uri
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

class TrainTimesWidgetProvider : AppWidgetProvider() {

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        for (appWidgetId in appWidgetIds) {
            WidgetConfigurationStorage.deleteConfiguration(context, appWidgetId)
            prefs.remove(PREF_IS_EXPANDED + appWidgetId) // Legacy cleanup
            for (i in 0..99) {
                prefs.remove("${PREF_IS_EXPANDED}${appWidgetId}_$i")
            }
            prefs.remove(PREF_LAST_ERROR + appWidgetId)
            prefs.remove(PREF_EFFECTIVE_FROM + appWidgetId)
            prefs.remove(PREF_EFFECTIVE_TO + appWidgetId)
        }
        prefs.apply()
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE_EXPAND) {
            val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            val serviceIndex = intent.getIntExtra(EXTRA_SERVICE_INDEX, -1)

            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID && serviceIndex != -1) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val key = "${PREF_IS_EXPANDED}${appWidgetId}_$serviceIndex"
                val current = prefs.getBoolean(key, false)
                prefs.edit().putBoolean(key, !current).apply()

                val appWidgetManager = AppWidgetManager.getInstance(context)
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.departures_list)
            }
        } else if (intent.action == ACTION_WIDGET_PINNED) {
             val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
             if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                 updateAppWidget(context, AppWidgetManager.getInstance(context), appWidgetId)
             }
        } else if (intent.action == ACTION_WIDGET_STYLE_UPDATE) {
            val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                updateAppWidget(context, appWidgetManager, appWidgetId)
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.departures_list)
            }
        } else if (intent.action == WidgetUpdateScheduler.ACTION_AUTO_UPDATE) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisAppWidget = ComponentName(context.packageName, TrainTimesWidgetProvider::class.java.name)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget)
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val apiKey = prefs.getString(PREF_API_KEY, null)

        for (appWidgetId in appWidgetIds) {
            val config = WidgetConfigurationStorage.loadConfiguration(context, appWidgetId)
            if (config == null || apiKey.isNullOrEmpty()) {
                val isApiKeyMissing = apiKey.isNullOrEmpty()
                updateAppWidgetWithSetupRequest(context, appWidgetManager, appWidgetId, isApiKeyMissing)
            } else {
                // Do not show loading indicator proactively to avoid flickering/flashing
                // showLoadingState(context, appWidgetManager, appWidgetId)
            }
        }

        if (apiKey.isNullOrEmpty()) {
            return
        }

        val client = NationalRailClient(apiKey)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (appWidgetId in appWidgetIds) {
                    // Only fetch for configured widgets
                    if (WidgetConfigurationStorage.loadConfiguration(context, appWidgetId) != null) {
                        updateSingleWidget(context, appWidgetManager, appWidgetId, client)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Global update error", e)
                // Optionally, update UI to show a generic error state for all widgets
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "TrainWidget"
        const val ACTION_TOGGLE_EXPAND = "net.bonstio.traintimes.ACTION_TOGGLE_EXPAND"
        const val ACTION_WIDGET_PINNED = "net.bonstio.traintimes.ACTION_WIDGET_PINNED"
        const val ACTION_WIDGET_STYLE_UPDATE = "net.bonstio.traintimes.ACTION_WIDGET_STYLE_UPDATE"
        const val EXTRA_SERVICE_INDEX = "service_index"
        const val PREFS_NAME = "net.bonstio.traintimes.widget"
        const val PREF_API_KEY = "api_key"
        const val PREF_BG_COLOR = "bg_color"
        const val PREF_IS_EXPANDED = "is_expanded_"
        const val PREF_LAST_ERROR = "last_error_"
        const val PREF_EFFECTIVE_FROM = "effective_from_"
        const val PREF_EFFECTIVE_TO = "effective_to_"

        internal fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, hasData: Boolean? = null) {
            val config = WidgetConfigurationStorage.loadConfiguration(context, appWidgetId)
            if (config == null) {
                val apiKey = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(PREF_API_KEY, null)
                updateAppWidgetWithSetupRequest(context, appWidgetManager, appWidgetId, apiKey.isNullOrEmpty())
                return
            }

            // Logic duplicated here for display title consistency
            val (fromStation, toStation) = if (config.commutingMode == "LOCATION") {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val ef = prefs.getString(PREF_EFFECTIVE_FROM + appWidgetId, null)
                val et = prefs.getString(PREF_EFFECTIVE_TO + appWidgetId, null)
                if (ef != null && et != null) {
                    Pair(ef, et)
                } else {
                    WidgetUtils.determineDirection(config)
                }
            } else {
                WidgetUtils.determineDirection(config)
            }
            
            val displayTitle = WidgetUtils.calculateDisplayTitle(context, config.titleStyle, config.title, fromStation, toStation)
            val styling = WidgetUtils.resolveWidgetStyling(context, config)

            val views = createBaseWidgetView(context, appWidgetId, styling, displayTitle, config, fromStation, toStation)

            val intent = Intent(context, TrainTimesWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.departures_list, intent)
            
            val effectiveHasData = hasData ?: WidgetCache.loadServices(context, appWidgetId).isNotEmpty()
            if (!effectiveHasData) {
                views.setEmptyView(R.id.departures_list, R.id.error_container)
            }
            
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val errorState = prefs.getString(PREF_LAST_ERROR + appWidgetId, null)

            val errorMessage = when (errorState) {
                "NETWORK" -> context.getString(R.string.network_error)
                "GENERIC" -> context.getString(R.string.widget_error)
                else -> {
                    if (toStation.isNotEmpty()) {
                        context.getString(R.string.no_trains_found_from_to, fromStation, toStation)
                    } else {
                        context.getString(R.string.no_trains_found_from, fromStation)
                    }
                }
            }
            views.setTextViewText(R.id.error_message, errorMessage)

            val toggleIntent = Intent(context, TrainTimesWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE_EXPAND
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            val togglePendingIntent = PendingIntent.getBroadcast(
                context, appWidgetId, toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.departures_list, togglePendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private suspend fun getLastLocation(context: Context): Location? {
             if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return null
            }

            return suspendCancellableCoroutine { cont ->
                val client = LocationServices.getFusedLocationProviderClient(context)
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
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

        private suspend fun updateSingleWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            client: NationalRailClient
        ) {
            val config = WidgetConfigurationStorage.loadConfiguration(context, appWidgetId) ?: return

            var handled = false
            var trainServices: List<TrainService> = emptyList()
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            try {
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
                             
                             // If closer to "To" station, assume we are departing from there
                             if (distTo[0] < distFrom[0]) {
                                 Log.d(TAG, "Location based swap: Closest to ${toStationObj.code} (${distTo[0]}m vs ${distFrom[0]}m)")
                                 val temp = fromStation
                                 fromStation = toStation
                                 toStation = temp
                             } else {
                                 Log.d(TAG, "Location based keep: Closest to ${fromStationObj.code} (${distFrom[0]}m vs ${distTo[0]}m)")
                             }
                        }
                    }
                } else {
                    val pair = WidgetUtils.determineDirection(config)
                    fromStation = pair.first
                    toStation = pair.second
                }
                
                // Save effective stations so updateAppWidget can use the correct title
                prefs.edit()
                    .putString(PREF_EFFECTIVE_FROM + appWidgetId, fromStation)
                    .putString(PREF_EFFECTIVE_TO + appWidgetId, toStation)
                    .apply()

                trainServices = client.getNextTrain(fromStation, toStation, config.timeOffset, config.departureCount)
                if (config.hidePastDepartures) {
                    trainServices = trainServices.filter { !WidgetUtils.isDepartureInPast(it, Calendar.getInstance()) }
                }
                WidgetCache.saveServices(context, appWidgetId, trainServices)
                prefs.edit().remove(PREF_LAST_ERROR + appWidgetId).apply()
            } catch (e: ClientRequestException) {
                if (e.response.status == HttpStatusCode.Unauthorized) {
                    Log.w(TAG, "Unauthorized API key for widget $appWidgetId")
                    withContext(Dispatchers.Main) {
                        updateAppWidgetWithSetupRequest(context, appWidgetManager, appWidgetId, isApiKeyMissing = false, showInvalidKeyError = true)
                    }
                    handled = true
                } else {
                    Log.e(TAG, "Failed to update widget $appWidgetId", e)
                    WidgetCache.saveServices(context, appWidgetId, emptyList())
                    prefs.edit().putString(PREF_LAST_ERROR + appWidgetId, "GENERIC").apply()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update widget $appWidgetId", e)
                WidgetCache.saveServices(context, appWidgetId, emptyList()) // Clear cache on error
                val errorType = if (e is IOException) "NETWORK" else "GENERIC"
                prefs.edit().putString(PREF_LAST_ERROR + appWidgetId, errorType).apply()
            } finally {
                if (!handled) {
                    withContext(Dispatchers.Main) {
                        updateAppWidget(context, appWidgetManager, appWidgetId, hasData = trainServices.isNotEmpty())
                        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.departures_list)
                    }
                }
            }
        }
        
        private fun showLoadingState(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val config = WidgetConfigurationStorage.loadConfiguration(context, appWidgetId) ?: return
            val styling = WidgetUtils.resolveWidgetStyling(context, config)
            val (fromStation, toStation) = WidgetUtils.determineDirection(config)
            val title = WidgetUtils.calculateDisplayTitle(context, config.titleStyle, config.title, fromStation, toStation)
            
            val views = createBaseWidgetView(context, appWidgetId, styling, title, config, fromStation, toStation, isLoading = true)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun createBaseWidgetView(
            context: Context, appWidgetId: Int, styling: WidgetStyling,
            title: String, config: WidgetConfiguration, fromStation: String, toStation: String, isLoading: Boolean = false
        ): RemoteViews {
            val layoutId = if (config.showDivider) R.layout.widget_layout else R.layout.widget_layout_no_divider
            val views = RemoteViews(context.packageName, layoutId)

            // Theming
            if (styling.useSystemBgColor) {
                views.setColorAttr(R.id.widget_root, "setBackgroundColor", com.google.android.material.R.attr.colorSurfaceContainerHighest)
            } else {
                val backgroundColor = Color.argb(styling.transparency, Color.red(styling.bgColor), Color.green(styling.bgColor), Color.blue(styling.bgColor))
                views.setInt(R.id.widget_root, "setBackgroundColor", backgroundColor)
            }

            val allTextViewIds = intArrayOf(R.id.widget_title, R.id.last_updated, R.id.open_in_maps, R.id.error_message, R.id.error_details)
            for (id in allTextViewIds) {
                if (styling.useSystemTextColor) {
                    views.setColorAttr(id, "setTextColor", com.google.android.material.R.attr.colorOnSurface)
                } else {
                    views.setTextColor(id, styling.textColor)
                }
            }
            
            val allImageViewIds = intArrayOf(R.id.widget_icon, R.id.refresh_button, R.id.settings_button)
            for (id in allImageViewIds) {
                if (styling.useSystemTextColor) {
                    views.setColorAttr(id, "setColorFilter", com.google.android.material.R.attr.colorOnSurface)
                } else {
                    views.setInt(id, "setColorFilter", styling.textColor)
                }
            }

            // Visibility
            views.setViewVisibility(R.id.content_container, View.VISIBLE)
            views.setViewVisibility(R.id.setup_message, View.GONE)
            views.setViewVisibility(R.id.loading_container, if (isLoading) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.departures_list, if (isLoading) View.GONE else View.VISIBLE)
            views.setViewVisibility(R.id.error_container, View.GONE)

            // Content
            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_SP, WidgetUtils.getTitleSize(config.fontSize))
            val gravity = when (config.alignment) {
                "CENTER" -> Gravity.CENTER_HORIZONTAL
                "END" -> Gravity.END
                else -> Gravity.START
            }
            views.setInt(R.id.widget_title, "setGravity", gravity)

            views.setViewVisibility(R.id.widget_icon, if (config.showIcon) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.refresh_button, if (config.showRefreshIcon) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.settings_button, if (config.showSettingsIcon) View.VISIBLE else View.GONE)

            // Footer
            val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            views.setTextViewText(R.id.last_updated, context.getString(R.string.last_update_format, currentTime))
            views.setViewVisibility(R.id.last_updated, if (config.showLastUpdateTime) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.open_in_maps, if (config.showMapsIcon) View.VISIBLE else View.GONE)

            // Intents
            val refreshIntent = Intent(context, TrainTimesWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(context, appWidgetId, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.refresh_button, refreshPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_title, refreshPendingIntent)
            views.setOnClickPendingIntent(R.id.retry_button, refreshPendingIntent)


            val settingsIntent = Intent(context, TrainTimesWidgetConfigureActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse("trainwidget://settings/$appWidgetId")
            }
            val settingsPendingIntent = PendingIntent.getActivity(context, appWidgetId, settingsIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.settings_button, settingsPendingIntent)
            
            if (config.showMapsIcon) {
                val fromStationName = StationRepository.getStationName(context, fromStation) + context.getString(R.string.station_suffix)
                
                val mapUri = if (toStation.isNotEmpty()) {
                    val toStationName = StationRepository.getStationName(context, toStation) + context.getString(R.string.station_suffix)
                     Uri.parse("https://www.google.com/maps/dir/?api=1&origin=${Uri.encode(fromStationName)}&destination=${Uri.encode(toStationName)}&travelmode=transit")
                } else {
                    Uri.parse("geo:0,0?q=${Uri.encode(fromStationName)}")
                }

                val mapIntent = Intent(Intent.ACTION_VIEW, mapUri)
                val mapPendingIntent = PendingIntent.getActivity(
                    context, appWidgetId + 1000, mapIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.open_in_maps, mapPendingIntent)
            }

            return views
        }

        private fun updateAppWidgetWithSetupRequest(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, isApiKeyMissing: Boolean, showInvalidKeyError: Boolean = false) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            
            views.setColorAttr(R.id.widget_root, "setBackgroundColor", com.google.android.material.R.attr.colorSurfaceContainerHighest)
            views.setColorAttr(R.id.setup_message, "setTextColor", com.google.android.material.R.attr.colorOnSurface)

            views.setViewVisibility(R.id.content_container, View.GONE)
            views.setViewVisibility(R.id.loading_container, View.GONE)
            views.setViewVisibility(R.id.error_container, View.GONE)
            views.setViewVisibility(R.id.setup_message, View.VISIBLE)
            
            val messageRes = if (isApiKeyMissing || showInvalidKeyError) R.string.setup_api_key else R.string.configure_widget
            val intentTarget = if (isApiKeyMissing || showInvalidKeyError) MainActivity::class.java else TrainTimesWidgetConfigureActivity::class.java
            
            views.setTextViewText(R.id.setup_message, context.getString(messageRes))
            
            val intent = Intent(context, intentTarget).apply {
                if (intentTarget == TrainTimesWidgetConfigureActivity::class.java) {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                if (showInvalidKeyError) {
                    putExtra(MainActivity.EXTRA_INVALID_API_KEY, true)
                }
            }
            
            val pendingIntent = PendingIntent.getActivity(
                context, appWidgetId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.setup_message, pendingIntent)
            
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}