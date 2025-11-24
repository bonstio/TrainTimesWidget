package net.bonstio.traintimes

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.util.Log
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TrainTimesWidgetProvider : AppWidgetProvider() {

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        for (appWidgetId in appWidgetIds) {
            WidgetConfigurationStorage.deleteConfiguration(context, appWidgetId)
            prefs.remove(PREF_IS_EXPANDED + appWidgetId) // Legacy cleanup
            for (i in 0..99) {
                prefs.remove("${PREF_IS_EXPANDED}${appWidgetId}_$i")
            }
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
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
        
        // Trigger data fetch
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val apiKey = prefs.getString(PREF_API_KEY, null)
        val bgColor = prefs.getInt(PREF_BG_COLOR, Color.BLACK)

        if (apiKey.isNullOrEmpty()) {
            for (appWidgetId in appWidgetIds) {
                updateAppWidgetWithSetupRequest(context, appWidgetManager, appWidgetId, bgColor)
            }
            return
        }

        val client = NationalRailClient(apiKey)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (appWidgetId in appWidgetIds) {
                    updateSingleWidget(context, appWidgetManager, appWidgetId, client)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Global update error", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "TrainWidget"
        const val ACTION_TOGGLE_EXPAND = "net.bonstio.traintimes.ACTION_TOGGLE_EXPAND"
        const val EXTRA_SERVICE_INDEX = "service_index"

        internal fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val config = WidgetConfigurationStorage.loadConfiguration(context, appWidgetId)
            if (config == null) {
                updateAppWidgetWithSetupRequest(context, appWidgetManager, appWidgetId, Color.BLACK)
                return
            }

            val (fromStation, toStation) = determineDirection(config)
            val displayTitle = calculateDisplayTitle(context, config, fromStation, toStation)
            val styling = resolveWidgetStyling(context, config)

            val views = createBaseWidgetView(context, appWidgetId, styling, displayTitle, config, fromStation)

            // Set up the RemoteViews object to use a RemoteViews adapter.
            val intent = Intent(context, TrainTimesWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.departures_list, intent)

            // The empty view is displayed when the collection has no items.
            views.setEmptyView(R.id.departures_list, R.id.error_container)
            views.setTextViewText(R.id.error_message, context.getString(R.string.no_trains_found))

            // Template to handle taps on list items
            val toggleIntent = Intent(context, TrainTimesWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE_EXPAND
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                // Unique data is crucial for unique PendingIntent, but for a template,
                // the fill-in intent provides uniqueness? 
                // Actually, for Broadcast PendingIntents, filterEquals is used.
                // We need unique PendingIntent for each widget so they don't conflict.
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            val togglePendingIntent = PendingIntent.getBroadcast(
                context, appWidgetId, toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE // MUTABLE required for fill-in
            )
            views.setPendingIntentTemplate(R.id.departures_list, togglePendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private suspend fun updateSingleWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            client: NationalRailClient
        ) {
            Log.d(TAG, "Updating widget $appWidgetId")
            val config = WidgetConfigurationStorage.loadConfiguration(context, appWidgetId) ?: return

            try {
                val (fromStation, toStation) = determineDirection(config)
                var trainServices = client.getNextTrain(
                    fromStation,
                    toStation,
                    config.timeOffset,
                    config.departureCount
                )
                
                if (config.hidePastDepartures) {
                    val now = Calendar.getInstance()
                    trainServices = trainServices.filter { !isDepartureInPast(it, now) }
                }

                WidgetCache.saveServices(context, appWidgetId, trainServices)

                withContext(Dispatchers.Main) {
                    updateAppWidget(context, appWidgetManager, appWidgetId)
                    appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.departures_list)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update widget $appWidgetId", e)
            }
        }

        private fun createBaseWidgetView(
            context: Context, appWidgetId: Int, styling: WidgetStyling,
            title: String, config: WidgetConfiguration, fromStation: String
        ): RemoteViews {
             val views = RemoteViews(context.packageName, R.layout.widget_layout)

            if (!styling.useSystemBgColor) {
                val backgroundColor = Color.argb(styling.transparency, Color.red(styling.bgColor), Color.green(styling.bgColor), Color.blue(styling.bgColor))
                views.setInt(R.id.widget_root, "setBackgroundColor", backgroundColor)
            }

            val titleSize = when (config.fontSize) {
                0 -> 14f
                2 -> 18f
                else -> 16f
            }

            views.setViewVisibility(R.id.loading_container, View.GONE)
            views.setViewVisibility(R.id.departures_list, View.VISIBLE)
            views.setViewVisibility(R.id.error_container, View.VISIBLE) // Visible but empty
            views.setViewVisibility(R.id.setup_message, View.GONE)

            // Update Last Updated time
            val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            views.setTextViewText(R.id.last_updated, context.getString(R.string.last_update_format, currentTime))
            views.setViewVisibility(R.id.last_updated, if (config.showLastUpdateTime) View.VISIBLE else View.GONE)
            if (!styling.useSystemTextColor) {
                views.setTextColor(R.id.last_updated, styling.textColor)
            }

            // Open in Maps
            if (config.showMapsIcon) {
                views.setViewVisibility(R.id.open_in_maps, View.VISIBLE)
                if (!styling.useSystemTextColor) {
                    views.setTextColor(R.id.open_in_maps, styling.textColor)
                }
                
                val stationName = StationRepository.getStationName(context, fromStation)
                val mapUri = Uri.parse("geo:0,0?q=${Uri.encode(stationName + context.getString(R.string.station_suffix))}")
                val mapIntent = Intent(Intent.ACTION_VIEW, mapUri)
                val mapPendingIntent = PendingIntent.getActivity(
                    context,
                    appWidgetId + 1000,
                    mapIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.open_in_maps, mapPendingIntent)
            } else {
                views.setViewVisibility(R.id.open_in_maps, View.GONE)
            }

            views.setTextViewText(R.id.widget_title, title)
            if (!styling.useSystemTextColor) {
                views.setTextColor(R.id.widget_title, styling.textColor)
            }
            views.setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_SP, titleSize)

            val gravity = when (config.alignment) {
                "CENTER" -> Gravity.CENTER_HORIZONTAL
                "END" -> Gravity.END
                else -> Gravity.START
            }
            views.setInt(R.id.widget_title, "setGravity", gravity)

            views.setViewVisibility(R.id.widget_icon, if (config.showIcon) View.VISIBLE else View.GONE)
            if (config.showIcon && !styling.useSystemTextColor) {
                views.setInt(R.id.widget_icon, "setColorFilter", styling.textColor)
            }

            views.setViewVisibility(R.id.refresh_button, if (config.showRefreshIcon) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.settings_button, if (config.showSettingsIcon) View.VISIBLE else View.GONE)
            if (!styling.useSystemTextColor) {
                views.setInt(R.id.refresh_button, "setColorFilter", styling.textColor)
                views.setInt(R.id.settings_button, "setColorFilter", styling.textColor)
            }

            val refreshIntent = Intent(context, TrainTimesWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(context, appWidgetId, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.refresh_button, refreshPendingIntent)

            val settingsIntent = Intent(context, TrainTimesWidgetConfigureActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse("trainwidget://settings/$appWidgetId")
            }
            val settingsPendingIntent = PendingIntent.getActivity(context, appWidgetId, settingsIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.settings_button, settingsPendingIntent)

            return views
        }

        private fun updateAppWidgetWithSetupRequest(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, bgColor: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            val backgroundColor = Color.argb(128, Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor))
            views.setInt(R.id.widget_root, "setBackgroundColor", backgroundColor)
            views.setViewVisibility(R.id.content_container, View.GONE)
            views.setViewVisibility(R.id.setup_message, View.VISIBLE)
            views.setTextViewText(R.id.setup_message, context.getString(R.string.setup_api_key))
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.setup_message, pendingIntent)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun resolveWidgetStyling(context: Context, config: WidgetConfiguration): WidgetStyling {
            val textColor = if (config.useSystemTextColor) resolveColor(context, com.google.android.material.R.attr.colorOnSurface) else config.textColor
            val widgetBgColor = if (config.useSystemBgColor) resolveColor(context, com.google.android.material.R.attr.colorSurface) else config.bgColor
            val transparency = if (config.useSystemBgColor) 255 else config.transparency
            return WidgetStyling(textColor, widgetBgColor, transparency, config.useSystemTextColor, config.useSystemBgColor)
        }

        fun determineDirection(config: WidgetConfiguration): Pair<String, String> {
            val calendar = Calendar.getInstance()
            val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
            val isReversed = if (config.toStation.isNotEmpty()) isTimeReversed(currentMinutes, config.startTimeNormal, config.startTimeReverse) else false
            val fromStation = if (isReversed) config.toStation else config.fromStation
            val toStation = if (isReversed) config.fromStation else config.toStation
            return Pair(fromStation, toStation)
        }

        private fun calculateDisplayTitle(context: Context, config: WidgetConfiguration, fromStation: String, toStation: String): String {
            return when (config.titleStyle) {
                "SHORT" -> if (toStation.isNotEmpty()) "${fromStation.uppercase()} -> ${toStation.uppercase()}" else fromStation.uppercase()
                "CUSTOM" -> {
                    var t = config.title.replace("\$f", fromStation.uppercase()).replace("\$t", toStation.uppercase())
                    if (t.contains("\$F") || t.contains("\$T")) {
                        val fromName = StationRepository.getStationName(context, fromStation)
                        val toName = if (toStation.isNotEmpty()) StationRepository.getStationName(context, toStation) else ""
                        t = t.replace("\$F", fromName).replace("\$T", toName)
                    }
                    t
                }
                else -> {
                    val fromName = StationRepository.getStationName(context, fromStation)
                    if (toStation.isNotEmpty()) "$fromName -> ${StationRepository.getStationName(context, toStation)}" else fromName
                }
            }
        }

        private fun resolveColor(context: Context, attr: Int): Int {
            val wrapper = ContextThemeWrapper(context, R.style.Theme_TrainTimes)
            val typedValue = TypedValue()
            wrapper.theme.resolveAttribute(attr, typedValue, true)
            return typedValue.data
        }

        private fun isTimeReversed(currentMinutes: Int, startNormal: Int, startReverse: Int): Boolean {
            if (startNormal == -1 || startReverse == -1) return false
            return if (startNormal < startReverse) {
                !(currentMinutes >= startNormal && currentMinutes < startReverse)
            } else if (startNormal > startReverse) {
                currentMinutes >= startReverse && currentMinutes < startNormal
            } else false
        }

        internal fun isDepartureInPast(service: TrainService, now: Calendar): Boolean {
            val timeToParse = if (service.status.contains("Exp")) {
                service.status.split(" ")[1]
            } else if (service.status != "On time" && service.status.matches(Regex("\\d{2}:\\d{2}"))) {
                service.status
            } else {
                service.std
            }

            return try {
                val parts = timeToParse.split(":")
                val departureHour = parts[0].toInt()
                val departureMinute = parts[1].toInt()
                val departureTotalMinutes = departureHour * 60 + departureMinute

                val currentTotalMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
                
                departureTotalMinutes < currentTotalMinutes
            } catch (e: Exception) {
                false
            }
        }
    }
}

// Data class at top level to avoid Companion.WidgetStyling confusion
data class WidgetStyling(
    val textColor: Int, val bgColor: Int, val transparency: Int,
    val useSystemTextColor: Boolean, val useSystemBgColor: Boolean
)
