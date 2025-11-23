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
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * AppWidgetProvider implementation for the Train Times widget.
 * Refactored to reduce UI duplication and handle async updates safely.
 */
class TrainTimesWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "TrainWidget"
        const val ACTION_TOGGLE_EXPAND = "net.bonstio.traintimes.ACTION_TOGGLE_EXPAND"
        const val EXTRA_SERVICE_INDEX = "service_index"
        private const val PREF_CACHE_PREFIX = "cache_"

        // Define widget schemes for unique PendingIntents
        private const val SCHEME_TOGGLE = "trainwidget://toggle"
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        for (appWidgetId in appWidgetIds) {
            WidgetConfigurationStorage.deleteConfiguration(context, appWidgetId)
            prefs.remove(PREF_IS_EXPANDED + appWidgetId) // Legacy cleanup
            // Clean up potential indexed expansion states
            for (i in 0..99) {
                prefs.remove("${PREF_IS_EXPANDED}${appWidgetId}_$i")
            }
            prefs.remove(PREF_CACHE_PREFIX + appWidgetId)
        }
        prefs.apply()
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive received intent with action: ${intent.action}")
        super.onReceive(context, intent)

        if (intent.action == WidgetUpdateScheduler.ACTION_AUTO_UPDATE) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisAppWidget = ComponentName(context.packageName, TrainTimesWidgetProvider::class.java.name)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget)
            if (appWidgetIds.isNotEmpty()) {
                onUpdate(context, appWidgetManager, appWidgetIds)
            }
        } else if (intent.action == ACTION_TOGGLE_EXPAND) {
            handleExpandToggle(context, intent)
        }
    }

    private fun handleExpandToggle(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val serviceIndex = intent.getIntExtra(EXTRA_SERVICE_INDEX, -1)

        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID && serviceIndex != -1) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val key = "${PREF_IS_EXPANDED}${appWidgetId}_$serviceIndex"
            val current = prefs.getBoolean(key, false)
            prefs.edit().putBoolean(key, !current).apply()

            val appWidgetManager = AppWidgetManager.getInstance(context)

            // Try to load from cache first to avoid network refresh on simple UI toggle
            val cachedServices = loadServicesFromCache(context, appWidgetId)
            if (cachedServices != null) {
                renderWidget(context, appWidgetManager, appWidgetId, cachedServices)
            } else {
                onUpdate(context, appWidgetManager, intArrayOf(appWidgetId))
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate called")
        val pendingResult = goAsync()

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val apiKey = prefs.getString(PREF_API_KEY, null)
        val bgColor = prefs.getInt(PREF_BG_COLOR, Color.BLACK)

        if (apiKey.isNullOrEmpty()) {
            Log.w(TAG, "API key not configured.")
            for (appWidgetId in appWidgetIds) {
                updateAppWidgetWithSetupRequest(context, appWidgetManager, appWidgetId, bgColor)
            }
            pendingResult.finish()
            return
        }

        val client = NationalRailClient(apiKey)

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

    private suspend fun updateSingleWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        client: NationalRailClient
    ) {
        Log.d(TAG, "Updating widget $appWidgetId")
        val config = WidgetConfigurationStorage.loadConfiguration(context, appWidgetId)

        if (config != null) {
            val (fromStation, toStation) = determineDirection(config)
            val displayTitle = calculateDisplayTitle(context, config, fromStation, toStation)

            // Prepare Styling Data
            val styling = resolveWidgetStyling(context, config)

            // Show loading state immediately
            withContext(Dispatchers.Main) {
                updateAppWidgetLoading(context, appWidgetManager, appWidgetId, styling, displayTitle, config)
            }

            try {
                val trainServices = client.getNextTrain(
                    fromStation,
                    toStation,
                    config.timeOffset,
                    config.departureCount
                )

                saveServicesToCache(context, appWidgetId, trainServices)

                withContext(Dispatchers.Main) {
                    updateAppWidgetSuccess(
                        context, appWidgetManager, appWidgetId, trainServices,
                        styling, displayTitle, config, fromStation, toStation
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update widget $appWidgetId", e)
                withContext(Dispatchers.Main) {
                    updateAppWidgetWithError(
                        context, appWidgetManager, appWidgetId,
                        styling, displayTitle, config, fromStation, toStation, e.message
                    )
                }
            }
        } else {
            Log.w(TAG, "Widget $appWidgetId is not configured yet.")
        }
    }

    private fun renderWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        services: List<TrainService>
    ) {
        // This runs on main thread usually when called from toggle
        val config = WidgetConfigurationStorage.loadConfiguration(context, appWidgetId) ?: return
        val (fromStation, toStation) = determineDirection(config)
        val displayTitle = calculateDisplayTitle(context, config, fromStation, toStation)
        val styling = resolveWidgetStyling(context, config)

        updateAppWidgetSuccess(
            context, appWidgetManager, appWidgetId, services,
            styling, displayTitle, config, fromStation, toStation
        )
    }

    // --- Helper Logic ---

    private fun determineDirection(config: WidgetConfiguration): Pair<String, String> {
        val calendar = Calendar.getInstance()
        val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

        val isReversed = if (config.toStation.isNotEmpty()) {
            isTimeReversed(currentMinutes, config.startTimeNormal, config.startTimeReverse)
        } else {
            false
        }

        val fromStation = if (isReversed) config.toStation else config.fromStation
        val toStation = if (isReversed) config.fromStation else config.toStation
        return Pair(fromStation, toStation)
    }

    private fun calculateDisplayTitle(context: Context, config: WidgetConfiguration, fromStation: String, toStation: String): String {
        return when (config.titleStyle) {
            "SHORT" -> {
                if (toStation.isNotEmpty()) {
                    "${fromStation.uppercase(Locale.getDefault())} -> ${toStation.uppercase(Locale.getDefault())}"
                } else {
                    fromStation.uppercase(Locale.getDefault())
                }
            }
            "CUSTOM" -> {
                var t = config.title
                    .replace("\$f", fromStation.uppercase(Locale.getDefault()))
                    .replace("\$t", toStation.uppercase(Locale.getDefault()))

                if (t.contains("\$F") || t.contains("\$T")) {
                    val fromName = StationRepository.getStationName(context, fromStation)
                    val toName = if (toStation.isNotEmpty()) StationRepository.getStationName(context, toStation) else ""
                    t = t.replace("\$F", fromName).replace("\$T", toName)
                }
                t
            }
            else -> { // LONG or default
                val fromName = StationRepository.getStationName(context, fromStation)
                if (toStation.isNotEmpty()) {
                    val toName = StationRepository.getStationName(context, toStation)
                    "$fromName -> $toName"
                } else {
                    fromName
                }
            }
        }
    }

    // Data class to hold resolved colors and transparency to pass around easily
    data class WidgetStyling(
        val textColor: Int,
        val bgColor: Int,
        val transparency: Int,
        val useSystemTextColor: Boolean,
        val useSystemBgColor: Boolean
    )

    private fun resolveWidgetStyling(context: Context, config: WidgetConfiguration): WidgetStyling {
        val textColor = if (config.useSystemTextColor) {
            resolveColor(context, com.google.android.material.R.attr.colorOnSurface)
        } else {
            config.textColor
        }

        val widgetBgColor = if (config.useSystemBgColor) {
            resolveColor(context, com.google.android.material.R.attr.colorSurface)
        } else {
            config.bgColor
        }

        val transparency = if (config.useSystemBgColor) 255 else config.transparency

        return WidgetStyling(textColor, widgetBgColor, transparency, config.useSystemTextColor, config.useSystemBgColor)
    }

    private fun resolveColor(context: Context, attr: Int): Int {
        val wrapper = ContextThemeWrapper(context, R.style.Theme_TrainTimes)
        val typedValue = TypedValue()
        wrapper.theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    // --- Caching Logic ---

    private fun saveServicesToCache(context: Context, appWidgetId: Int, services: List<TrainService>) {
        val jsonArray = JSONArray()
        for (service in services) {
            val jsonObj = JSONObject()
            jsonObj.put("std", service.std)
            jsonObj.put("destination", service.destination)
            jsonObj.put("platform", service.platform)
            jsonObj.put("status", service.status)
            jsonObj.put("subsequentCallingPoints", JSONArray(service.subsequentCallingPoints))
            jsonArray.put(jsonObj)
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_CACHE_PREFIX + appWidgetId, jsonArray.toString()).apply()
    }

    private fun loadServicesFromCache(context: Context, appWidgetId: Int): List<TrainService>? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(PREF_CACHE_PREFIX + appWidgetId, null) ?: return null
        return try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<TrainService>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val callingPointsJson = obj.getJSONArray("subsequentCallingPoints")
                val callingPoints = mutableListOf<String>()
                for (j in 0 until callingPointsJson.length()) {
                    callingPoints.add(callingPointsJson.getString(j))
                }
                list.add(TrainService(
                    std = obj.getString("std"),
                    destination = obj.getString("destination"),
                    platform = if (obj.has("platform") && !obj.isNull("platform")) obj.getString("platform") else null,
                    status = obj.getString("status"),
                    subsequentCallingPoints = callingPoints
                ))
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error loading cache", e)
            null
        }
    }
}

/**
 * Checks if the current time falls within the reverse direction time range.
 */
private fun isTimeReversed(currentMinutes: Int, startNormal: Int, startReverse: Int): Boolean {
    if (startNormal == -1 || startReverse == -1) return false
    if (startNormal < startReverse) {
        return !(currentMinutes >= startNormal && currentMinutes < startReverse)
    } else if (startNormal > startReverse) {
        return currentMinutes >= startReverse && currentMinutes < startNormal
    }
    return false
}

// --- UI Construction Helpers ---

/**
 * Creates the base RemoteViews object with common styling (bg, title, header buttons).
 * This eliminates duplication between Loading, Success, and Error states.
 */
internal fun createBaseWidgetView(
    context: Context,
    appWidgetId: Int,
    styling: TrainTimesWidgetProvider.WidgetStyling,
    title: String,
    config: WidgetConfiguration
): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_layout)

    // Background
    if (!styling.useSystemBgColor) {
        val backgroundColor = Color.argb(
            styling.transparency,
            Color.red(styling.bgColor),
            Color.green(styling.bgColor),
            Color.blue(styling.bgColor)
        )
        views.setInt(R.id.widget_root, "setBackgroundColor", backgroundColor)
    }

    // Font Size Logic
    val titleSize = when (config.fontSize) {
        0 -> 14f
        2 -> 18f
        else -> 16f
    }

    // Reset visibility of main containers (Clean slate)
    views.setViewVisibility(R.id.content_container, View.VISIBLE)
    views.setViewVisibility(R.id.loading_container, View.GONE)
    views.setViewVisibility(R.id.departures_container, View.GONE)
    views.setViewVisibility(R.id.error_container, View.GONE)
    views.setViewVisibility(R.id.setup_message, View.GONE)
    views.setViewVisibility(R.id.last_updated, View.GONE)
    views.setViewVisibility(R.id.open_in_maps, View.GONE)

    // Title & Header setup
    if (title.isEmpty()) {
        views.setViewVisibility(R.id.widget_title, View.GONE)
        views.setViewVisibility(R.id.widget_icon, View.GONE)
        views.setViewVisibility(R.id.refresh_button, View.GONE)
        views.setViewVisibility(R.id.settings_button, View.GONE)
    } else {
        views.setViewVisibility(R.id.widget_title, View.VISIBLE)
        views.setTextViewText(R.id.widget_title, title)

        if (!styling.useSystemTextColor) {
            views.setTextColor(R.id.widget_title, styling.textColor)
        }

        val gravity = when (config.alignment) {
            "CENTER" -> Gravity.CENTER_HORIZONTAL
            "END" -> Gravity.END
            else -> Gravity.START
        }
        views.setInt(R.id.widget_title, "setGravity", gravity)
        views.setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_SP, titleSize)

        // Icon
        if (config.showIcon) {
            views.setViewVisibility(R.id.widget_icon, View.VISIBLE)
            if (!styling.useSystemTextColor) {
                views.setInt(R.id.widget_icon, "setColorFilter", styling.textColor)
            }
        } else {
            views.setViewVisibility(R.id.widget_icon, View.GONE)
        }

        // Buttons
        views.setViewVisibility(R.id.refresh_button, View.VISIBLE)
        views.setViewVisibility(R.id.settings_button, View.VISIBLE)
        if (!styling.useSystemTextColor) {
            views.setInt(R.id.refresh_button, "setColorFilter", styling.textColor)
            views.setInt(R.id.settings_button, "setColorFilter", styling.textColor)
        }

        // Setup Refresh Intent (Generic implementation for all states)
        val intent = Intent(context, TrainTimesWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, appWidgetId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.refresh_button, pendingIntent)

        // Also bind retry button here, so it's always ready if the error view shows up
        views.setOnClickPendingIntent(R.id.retry_button, pendingIntent)

        // Bind refresh action to the title as well
        views.setOnClickPendingIntent(R.id.widget_title, pendingIntent)

        // Setup Settings Intent
        val settingsIntent = Intent(context, TrainTimesWidgetConfigureActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("trainwidget://settings/$appWidgetId") // Unique data ensuring separate PendingIntents per widget
        }
        val settingsPendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId,
            settingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.settings_button, settingsPendingIntent)
    }

    return views
}

internal fun updateAppWidgetLoading(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    styling: TrainTimesWidgetProvider.WidgetStyling,
    title: String,
    config: WidgetConfiguration
) {
    val views = createBaseWidgetView(context, appWidgetId, styling, title, config)
    views.setViewVisibility(R.id.loading_container, View.VISIBLE)
    appWidgetManager.updateAppWidget(appWidgetId, views)
}

internal fun updateAppWidgetSuccess(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    services: List<TrainService>,
    styling: TrainTimesWidgetProvider.WidgetStyling,
    title: String,
    config: WidgetConfiguration,
    fromStation: String,
    toStation: String
) {
    val views = createBaseWidgetView(context, appWidgetId, styling, title, config)

    // Last Updated Text
    val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    views.setTextViewText(R.id.last_updated, context.getString(R.string.last_update_format, currentTime))
    views.setViewVisibility(R.id.last_updated, View.VISIBLE)
    if (!styling.useSystemTextColor) {
        views.setTextColor(R.id.last_updated, styling.textColor)
    }

    // Google Maps Link
    setupMapsButton(context, views, appWidgetId, fromStation, toStation, styling.textColor, styling.useSystemTextColor)

    if (services.isEmpty()) {
        views.setViewVisibility(R.id.error_container, View.VISIBLE)
        views.setTextViewText(R.id.error_message, context.getString(R.string.no_trains_found))
        views.setViewVisibility(R.id.error_details, View.GONE)
        if (!styling.useSystemTextColor) {
            views.setTextColor(R.id.error_message, styling.textColor)
        }
    } else {
        views.setViewVisibility(R.id.departures_container, View.VISIBLE)
        views.removeAllViews(R.id.departures_container)

        populateDeparturesList(context, views, appWidgetId, services, styling, config)
    }

    appWidgetManager.updateAppWidget(appWidgetId, views)
}

internal fun updateAppWidgetWithError(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    styling: TrainTimesWidgetProvider.WidgetStyling,
    title: String,
    config: WidgetConfiguration,
    fromStation: String,
    toStation: String,
    errorMessage: String?
) {
    val views = createBaseWidgetView(context, appWidgetId, styling, title, config)

    views.setViewVisibility(R.id.error_container, View.VISIBLE)
    views.setTextViewText(R.id.error_message, context.getString(R.string.widget_error))

    if (errorMessage != null) {
        views.setTextViewText(R.id.error_details, errorMessage)
        views.setViewVisibility(R.id.error_details, View.VISIBLE)
    } else {
        views.setViewVisibility(R.id.error_details, View.GONE)
    }

    // Maps link is still useful even on error
    setupMapsButton(context, views, appWidgetId, fromStation, toStation, styling.textColor, styling.useSystemTextColor)

    // Colorize error elements
    if (!styling.useSystemTextColor) {
        views.setTextColor(R.id.error_message, styling.textColor)
        views.setTextColor(R.id.error_details, styling.textColor)
        views.setTextColor(R.id.open_in_maps, styling.textColor)
        views.setTextColor(R.id.last_updated, styling.textColor)
    }

    // Show timestamp so user knows when it failed
    val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    views.setTextViewText(R.id.last_updated, context.getString(R.string.last_update_format, currentTime))
    views.setViewVisibility(R.id.last_updated, View.VISIBLE)

    appWidgetManager.updateAppWidget(appWidgetId, views)
}

private fun setupMapsButton(
    context: Context,
    views: RemoteViews,
    appWidgetId: Int,
    fromStation: String,
    toStation: String,
    textColor: Int,
    useSystemTextColor: Boolean
) {
    val fromName = StationRepository.getStationName(context, fromStation)
    val toName = if (toStation.isNotEmpty()) StationRepository.getStationName(context, toStation) else ""

    val uriString = if (toName.isNotEmpty()) {
        "https://www.google.com/maps/dir/?api=1&origin=${Uri.encode(fromName)}&destination=${Uri.encode(toName)}&travelmode=transit"
    } else {
        "https://www.google.com/maps/search/?api=1&query=${Uri.encode(fromName)}"
    }

    val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString))
    val mapPendingIntent = PendingIntent.getActivity(
        context, appWidgetId, mapIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    views.setOnClickPendingIntent(R.id.open_in_maps, mapPendingIntent)
    views.setViewVisibility(R.id.open_in_maps, View.VISIBLE)

    if (!useSystemTextColor) {
        views.setTextColor(R.id.open_in_maps, textColor)
    }
}

private fun populateDeparturesList(
    context: Context,
    views: RemoteViews,
    appWidgetId: Int,
    services: List<TrainService>,
    styling: TrainTimesWidgetProvider.WidgetStyling,
    config: WidgetConfiguration
) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val bodySize = when (config.fontSize) {
        0 -> 12f
        2 -> 16f
        else -> 14f
    }

    services.forEachIndexed { index, service ->
        val departureView = RemoteViews(context.packageName, R.layout.departure_layout)

        departureView.setTextViewText(R.id.departure_time, service.std)
        departureView.setTextViewText(R.id.destination, service.destination)
        val platformText = service.platform?.let { "Platform $it" } ?: ""
        departureView.setTextViewText(R.id.platform, platformText)
        departureView.setTextViewText(R.id.status, service.status)

        // Styling
        if (!styling.useSystemTextColor) {
            val tc = styling.textColor
            departureView.setTextColor(R.id.departure_time, tc)
            departureView.setTextColor(R.id.destination, tc)
            departureView.setTextColor(R.id.platform, tc)
            departureView.setTextColor(R.id.status, tc)
        }

        // Sizing
        val sizeUnit = TypedValue.COMPLEX_UNIT_SP
        departureView.setTextViewTextSize(R.id.departure_time, sizeUnit, bodySize)
        departureView.setTextViewTextSize(R.id.destination, sizeUnit, bodySize)
        departureView.setTextViewTextSize(R.id.platform, sizeUnit, bodySize)
        departureView.setTextViewTextSize(R.id.status, sizeUnit, bodySize)

        // Stops Logic
        val showStops = when(config.stationStopsMode) {
            "ALL" -> true
            "FIRST" -> (index == 0)
            "NONE" -> false
            else -> (index == 0)
        }

        if (service.subsequentCallingPoints.isNotEmpty() && showStops) {
            val key = "${PREF_IS_EXPANDED}${appWidgetId}_$index"
            val isExpanded = prefs.getBoolean(key, false)

            val callingPointsText = "Calling at ${service.subsequentCallingPoints.joinToString(", ")}"
            departureView.setTextViewText(R.id.calling_points, callingPointsText)

            if (!styling.useSystemTextColor) {
                departureView.setTextColor(R.id.calling_points, styling.textColor)
            }
            departureView.setTextViewTextSize(R.id.calling_points, sizeUnit, bodySize)
            departureView.setViewVisibility(R.id.calling_points, View.VISIBLE)

            val maxLines = if (isExpanded) 100 else 1
            departureView.setInt(R.id.calling_points, "setMaxLines", maxLines)

            val toggleIntent = Intent(context, TrainTimesWidgetProvider::class.java).apply {
                action = TrainTimesWidgetProvider.ACTION_TOGGLE_EXPAND
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(TrainTimesWidgetProvider.EXTRA_SERVICE_INDEX, index)
                data = Uri.parse("trainwidget://toggle/$appWidgetId/$index")
            }
            val togglePendingIntent = PendingIntent.getBroadcast(
                context, appWidgetId, toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            departureView.setOnClickPendingIntent(R.id.calling_points, togglePendingIntent)

        } else {
            departureView.setViewVisibility(R.id.calling_points, View.GONE)
        }

        views.addView(R.id.departures_container, departureView)
    }
}

/**
 * Updates the App Widget to display a setup request message.
 * Kept separate as it uses a significantly different layout structure (hiding content).
 */
internal fun updateAppWidgetWithSetupRequest(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    bgColor: Int
) {
    Log.d("TrainWidget", "Showing setup request for widget $appWidgetId")
    val views = RemoteViews(context.packageName, R.layout.widget_layout)

    val transparency = 128
    val backgroundColor = Color.argb(transparency, Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor))
    views.setInt(R.id.widget_root, "setBackgroundColor", backgroundColor)

    views.setViewVisibility(R.id.content_container, View.GONE)
    views.setViewVisibility(R.id.error_container, View.GONE)
    views.setViewVisibility(R.id.setup_message, View.VISIBLE)
    views.setTextViewText(R.id.setup_message, context.getString(R.string.setup_api_key))

    val intent = Intent(context, MainActivity::class.java)
    val pendingIntent = PendingIntent.getActivity(
        context, 0, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.setup_message, pendingIntent)

    appWidgetManager.updateAppWidget(appWidgetId, views)
}