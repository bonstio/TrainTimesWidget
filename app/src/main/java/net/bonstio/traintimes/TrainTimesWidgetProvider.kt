package net.bonstio.traintimes

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
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
import java.util.Calendar
import java.util.Locale

/**
 * AppWidgetProvider implementation for the Train Times widget.
 * This class handles the widget updates and user interactions.
 */
class TrainTimesWidgetProvider : AppWidgetProvider() {

    /**
     * Called when the widget instances are deleted.
     * Cleans up the configuration associated with the deleted widgets.
     *
     * @param context The Context in which this receiver is running.
     * @param appWidgetIds The appWidgetIds that have been deleted.
     */
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            WidgetConfigurationStorage.deleteConfiguration(context, appWidgetId)
        }
    }

    /**
     * Called when the widget receives an Intent.
     * Logs the received action for debugging purposes.
     *
     * @param context The Context in which this receiver is running.
     * @param intent The Intent being received.
     */
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("TrainWidget", "onReceive received intent with action: ${intent.action}")
        super.onReceive(context, intent)
    }

    /**
     * Called when the widget needs to be updated.
     * Fetches train times asynchronously and updates the widget UI.
     *
     * @param context The Context in which this receiver is running.
     * @param appWidgetManager The AppWidgetManager to update the widget.
     * @param appWidgetIds The appWidgetIds that need to be updated.
     */
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d("TrainWidget", "onUpdate called")
        val pendingResult = goAsync()

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val apiKey = prefs.getString(PREF_API_KEY, null)
        val bgColor = prefs.getInt(PREF_BG_COLOR, Color.BLACK)

        if (apiKey.isNullOrEmpty()) {
            Log.w("TrainWidget", "API key not configured.")
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
                    Log.d("TrainWidget", "Updating widget $appWidgetId")
                    val config = WidgetConfigurationStorage.loadConfiguration(context, appWidgetId)

                    if (config != null) {
                        val calendar = Calendar.getInstance()
                        val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
                        val isReversed = isTimeReversed(
                            currentMinutes,
                            config.startTimeNormal,
                            config.startTimeReverse
                        )

                        val fromStation = if (isReversed) config.toStation else config.fromStation
                        val toStation = if (isReversed) config.fromStation else config.toStation

                        val displayTitle = when (config.titleStyle) {
                            "SHORT" -> "${fromStation.uppercase(Locale.getDefault())} -> ${
                                toStation.uppercase(
                                    Locale.getDefault()
                                )
                            }"

                            "CUSTOM" -> {
                                var t = config.title
                                    .replace("\$f", fromStation.uppercase(Locale.getDefault()))
                                    .replace("\$t", toStation.uppercase(Locale.getDefault()))

                                if (t.contains("\$F") || t.contains("\$T")) {
                                    val fromName =
                                        StationRepository.getStationName(context, fromStation)
                                    val toName = StationRepository.getStationName(context, toStation)
                                    t = t.replace("\$F", fromName)
                                        .replace("\$T", toName)
                                }
                                t
                            }

                            else -> {
                                // LONG or default
                                val fromName = StationRepository.getStationName(context, fromStation)
                                val toName = StationRepository.getStationName(context, toStation)
                                "$fromName -> $toName"
                            }
                        }

                        val textColor = if (config.useSystemTextColor) {
                            resolveColor(
                                context,
                                com.google.android.material.R.attr.colorOnSurface
                            )
                        } else {
                            config.textColor
                        }

                        val widgetBgColor = if (config.useSystemBgColor) {
                            resolveColor(context, com.google.android.material.R.attr.colorSurface)
                        } else {
                            config.bgColor
                        }

                        val transparency = if (config.useSystemBgColor) 255 else config.transparency

                        try {
                            val trainServices = client.getNextTrain(
                                fromStation,
                                toStation,
                                config.timeOffset,
                                config.departureCount
                            )
                            withContext(Dispatchers.Main) {
                                updateAppWidget(
                                    context, appWidgetManager, appWidgetId, trainServices,
                                    transparency, textColor, widgetBgColor, displayTitle,
                                    config.alignment, config.showIcon, config.showStops,
                                    config.useSystemTextColor, config.useSystemBgColor,
                                    config.fontSize
                                )
                            }
                        } catch (e: Exception) {
                            Log.e("TrainWidget", "Failed to update widget $appWidgetId", e)
                            withContext(Dispatchers.Main) {
                                updateAppWidgetWithError(
                                    context, appWidgetManager, appWidgetId,
                                    transparency, textColor, widgetBgColor, displayTitle,
                                    config.alignment, config.showIcon,
                                    config.useSystemTextColor, config.useSystemBgColor,
                                    config.fontSize
                                )
                            }
                        }
                    } else {
                        Log.w("TrainWidget", "Widget $appWidgetId is not configured yet.")
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Resolves a color attribute from the current theme context.
     *
     * @param context The context to use for theme resolution.
     * @param attr The attribute ID to resolve.
     * @return The resolved color int.
     */
    private fun resolveColor(context: Context, attr: Int): Int {
        val wrapper = ContextThemeWrapper(context, R.style.Theme_TrainTimes)
        val typedValue = TypedValue()
        wrapper.theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }
}

/**
 * Checks if the current time falls within the reverse direction time range.
 *
 * @param currentMinutes The current time in minutes from midnight.
 * @param startNormal The start time for normal direction in minutes from midnight.
 * @param startReverse The start time for reverse direction in minutes from midnight.
 * @return True if the route should be reversed, false otherwise.
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

/**
 * Updates the App Widget with the fetched train services.
 *
 * @param context The application context.
 * @param appWidgetManager The AppWidgetManager instance.
 * @param appWidgetId The ID of the widget to update.
 * @param services The list of train services to display.
 * @param transparency The transparency of the background (0-255).
 * @param textColor The color of the text.
 * @param bgColor The background color.
 * @param title The title to display on the widget.
 * @param alignment The alignment of the title (START, CENTER, END).
 * @param showIcon Whether to show the train icon.
 * @param showStops Whether to show intermediate stops for the first train.
 * @param useSystemTextColor Whether to use the system text color.
 * @param useSystemBgColor Whether to use the system background color.
 * @param fontSize The font size setting (0=Small, 1=Regular, 2=Large).
 */
internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    services: List<TrainService>,
    transparency: Int,
    textColor: Int,
    bgColor: Int,
    title: String,
    alignment: String,
    showIcon: Boolean,
    showStops: Boolean,
    useSystemTextColor: Boolean,
    useSystemBgColor: Boolean,
    fontSize: Int
) {
    Log.d("TrainWidget", "updateAppWidget called for widget $appWidgetId")
    val views = RemoteViews(context.packageName, R.layout.widget_layout)

    if (!useSystemBgColor) {
        val backgroundColor = Color.argb(
            transparency,
            Color.red(bgColor),
            Color.green(bgColor),
            Color.blue(bgColor)
        )
        views.setInt(R.id.widget_root, "setBackgroundColor", backgroundColor)
    }

    // Determine font sizes
    // 0=Small, 1=Regular, 2=Large
    // Regular: Title 16sp, Body 14sp
    // Small: Title 14sp, Body 12sp
    // Large: Title 18sp, Body 16sp
    val titleSize = when (fontSize) {
        0 -> 14f
        2 -> 18f
        else -> 16f
    }
    val bodySize = when (fontSize) {
        0 -> 12f
        2 -> 16f
        else -> 14f
    }

    // Ensure content container is visible
    views.setViewVisibility(R.id.content_container, View.VISIBLE)

    if (title.isEmpty()) {
        views.setViewVisibility(R.id.widget_title, View.GONE)
        views.setViewVisibility(R.id.widget_icon, View.GONE)
    } else {
        views.setViewVisibility(R.id.widget_title, View.VISIBLE)
        views.setTextViewText(R.id.widget_title, title)
        if (!useSystemTextColor) {
            views.setTextColor(R.id.widget_title, textColor)
        }
        val gravity = when (alignment) {
            "CENTER" -> Gravity.CENTER_HORIZONTAL
            "END" -> Gravity.END
            else -> Gravity.START
        }
        views.setInt(R.id.widget_title, "setGravity", gravity)
        views.setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_SP, titleSize)

        if (showIcon) {
            views.setViewVisibility(R.id.widget_icon, View.VISIBLE)
            if (!useSystemTextColor) {
                views.setInt(R.id.widget_icon, "setColorFilter", textColor)
            }
        } else {
            views.setViewVisibility(R.id.widget_icon, View.GONE)
        }
    }

    if (services.isEmpty()) {
        views.setViewVisibility(R.id.departures_container, View.GONE)
        views.setViewVisibility(R.id.error_message, View.VISIBLE)
        views.setTextViewText(R.id.error_message, context.getString(R.string.no_trains_found))
        if (!useSystemTextColor) {
            views.setTextColor(R.id.error_message, textColor)
        }
        views.setViewVisibility(R.id.setup_message, View.GONE)
    } else {
        views.setViewVisibility(R.id.error_message, View.GONE)
        views.setViewVisibility(R.id.setup_message, View.GONE)
        views.setViewVisibility(R.id.departures_container, View.VISIBLE)
        views.removeAllViews(R.id.departures_container)

        val intent = Intent(context, TrainTimesWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        services.forEachIndexed { index, service ->
            val departureView = RemoteViews(context.packageName, R.layout.departure_layout)

            departureView.setTextViewText(R.id.departure_time, service.std)
            departureView.setTextViewText(R.id.destination, service.destination)

            val platformText = service.platform?.let { "Platform $it" } ?: ""
            departureView.setTextViewText(R.id.platform, platformText)

            departureView.setTextViewText(R.id.status, service.status)

            if (!useSystemTextColor) {
                departureView.setTextColor(R.id.departure_time, textColor)
                departureView.setTextColor(R.id.destination, textColor)
                departureView.setTextColor(R.id.platform, textColor)
                departureView.setTextColor(R.id.status, textColor)
            }

            // Set font sizes for body text
            departureView.setTextViewTextSize(
                R.id.departure_time,
                TypedValue.COMPLEX_UNIT_SP,
                bodySize
            )
            departureView.setTextViewTextSize(
                R.id.destination,
                TypedValue.COMPLEX_UNIT_SP,
                bodySize
            )
            departureView.setTextViewTextSize(
                R.id.platform,
                TypedValue.COMPLEX_UNIT_SP,
                bodySize
            )
            departureView.setTextViewTextSize(
                R.id.status,
                TypedValue.COMPLEX_UNIT_SP,
                bodySize
            )

            if (index == 0 && service.subsequentCallingPoints.isNotEmpty() && showStops) {
                val callingPointsText =
                    "Calling at ${service.subsequentCallingPoints.joinToString(", ")}"
                departureView.setTextViewText(R.id.calling_points, callingPointsText)
                if (!useSystemTextColor) {
                    departureView.setTextColor(R.id.calling_points, textColor)
                }
                departureView.setTextViewTextSize(
                    R.id.calling_points,
                    TypedValue.COMPLEX_UNIT_SP,
                    bodySize
                )
                departureView.setViewVisibility(R.id.calling_points, View.VISIBLE)
            } else {
                departureView.setViewVisibility(R.id.calling_points, View.GONE)
            }

            views.addView(R.id.departures_container, departureView)
        }
    }

    appWidgetManager.updateAppWidget(appWidgetId, views)
}

/**
 * Updates the App Widget to display an error message.
 *
 * @param context The application context.
 * @param appWidgetManager The AppWidgetManager instance.
 * @param appWidgetId The ID of the widget to update.
 * @param transparency The transparency of the background (0-255).
 * @param textColor The color of the text.
 * @param bgColor The background color.
 * @param title The title to display on the widget.
 * @param alignment The alignment of the title (START, CENTER, END).
 * @param showIcon Whether to show the train icon.
 * @param useSystemTextColor Whether to use the system text color.
 * @param useSystemBgColor Whether to use the system background color.
 * @param fontSize The font size setting (0=Small, 1=Regular, 2=Large).
 */
internal fun updateAppWidgetWithError(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    transparency: Int,
    textColor: Int,
    bgColor: Int,
    title: String,
    alignment: String,
    showIcon: Boolean,
    useSystemTextColor: Boolean,
    useSystemBgColor: Boolean,
    fontSize: Int
) {
    val views = RemoteViews(context.packageName, R.layout.widget_layout)

    if (!useSystemBgColor) {
        val backgroundColor = Color.argb(
            transparency,
            Color.red(bgColor),
            Color.green(bgColor),
            Color.blue(bgColor)
        )
        views.setInt(R.id.widget_root, "setBackgroundColor", backgroundColor)
    }

    // Determine font sizes
    val titleSize = when (fontSize) {
        0 -> 14f
        2 -> 18f
        else -> 16f
    }

    // Ensure content container is visible
    views.setViewVisibility(R.id.content_container, View.VISIBLE)

    if (title.isEmpty()) {
        views.setViewVisibility(R.id.widget_title, View.GONE)
        views.setViewVisibility(R.id.widget_icon, View.GONE)
    } else {
        views.setViewVisibility(R.id.widget_title, View.VISIBLE)
        views.setTextViewText(R.id.widget_title, title)
        if (!useSystemTextColor) {
            views.setTextColor(R.id.widget_title, textColor)
        }
        val gravity = when (alignment) {
            "CENTER" -> Gravity.CENTER_HORIZONTAL
            "END" -> Gravity.END
            else -> Gravity.START
        }
        views.setInt(R.id.widget_title, "setGravity", gravity)
        views.setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_SP, titleSize)

        if (showIcon) {
            views.setViewVisibility(R.id.widget_icon, View.VISIBLE)
            if (!useSystemTextColor) {
                views.setInt(R.id.widget_icon, "setColorFilter", textColor)
            }
        } else {
            views.setViewVisibility(R.id.widget_icon, View.GONE)
        }
    }

    views.setViewVisibility(R.id.departures_container, View.GONE)
    views.setViewVisibility(R.id.error_message, View.VISIBLE)
    views.setViewVisibility(R.id.setup_message, View.GONE)
    views.setTextViewText(R.id.error_message, context.getString(R.string.widget_error))
    if (!useSystemTextColor) {
        views.setTextColor(R.id.error_message, textColor)
    }

    val intent = Intent(context, TrainTimesWidgetProvider::class.java).apply {
        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
    }
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        appWidgetId,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

    appWidgetManager.updateAppWidget(appWidgetId, views)
}

/**
 * Updates the App Widget to display a setup request message (e.g. missing API key).
 *
 * @param context The application context.
 * @param appWidgetManager The AppWidgetManager instance.
 * @param appWidgetId The ID of the widget to update.
 * @param bgColor The background color to use (will be applied with default transparency).
 */
internal fun updateAppWidgetWithSetupRequest(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    bgColor: Int
) {
    Log.d("TrainWidget", "Showing setup request for widget $appWidgetId")
    val views = RemoteViews(context.packageName, R.layout.widget_layout)

    // Use default transparency (128) if we don't know it, or just use 255 (opaque)?
    // Or stick to XML default (which is #80000000 = 128 alpha black).
    // Let's use the bgColor from prefs with 128 alpha to be consistent with default widget look?
    // Prefs default for opacity is 128 in WidgetConfigurationStorage logic.
    val transparency = 128
    val backgroundColor = Color.argb(
        transparency,
        Color.red(bgColor),
        Color.green(bgColor),
        Color.blue(bgColor)
    )
    views.setInt(R.id.widget_root, "setBackgroundColor", backgroundColor)

    // Hide everything else
    views.setViewVisibility(R.id.content_container, View.GONE)
    views.setViewVisibility(R.id.error_message, View.GONE)

    // Show setup message
    views.setViewVisibility(R.id.setup_message, View.VISIBLE)
    views.setTextViewText(R.id.setup_message, context.getString(R.string.setup_api_key))

    // Set click listener to launch MainActivity
    val intent = Intent(context, MainActivity::class.java)
    val pendingIntent = PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.setup_message, pendingIntent)
    views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

    appWidgetManager.updateAppWidget(appWidgetId, views)
}