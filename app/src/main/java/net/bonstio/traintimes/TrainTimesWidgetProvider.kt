package net.bonstio.traintimes

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.text.Html
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.text.SimpleDateFormat
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
            prefs.remove(PREF_LAST_ERROR + appWidgetId)
            prefs.remove(PREF_EFFECTIVE_FROM + appWidgetId)
            prefs.remove(PREF_EFFECTIVE_TO + appWidgetId)
            prefs.remove(PREF_LAST_SUCCESSFUL_UPDATE + appWidgetId)
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
            }
        }

        if (!apiKey.isNullOrEmpty()) {
            val data = Data.Builder()
                .putIntArray(WidgetUpdateWorker.KEY_WIDGET_IDS, appWidgetIds)
                .build()

            val request = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                .setInputData(data)
                .build()

            WorkManager.getInstance(context).enqueue(request)
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
        const val PREF_LAST_SUCCESSFUL_UPDATE = "last_successful_update_"

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
            
            val displayTitle = WidgetUtils.calculateDisplayTitle(context, config.titleStyle, config.title, fromStation, toStation, config.fromStation)
            val styling = WidgetUtils.resolveWidgetStyling(context, config)

            val views = createBaseWidgetView(context, appWidgetId, styling, displayTitle, config, fromStation, toStation)

            val intent = Intent(context, TrainTimesWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.departures_list, intent)
            
            val cachedServices = WidgetCache.loadServices(context, appWidgetId)
            val filteredServices = if (config.enableJourneyDurationFilter) {
                 cachedServices.filter { it.duration == null || it.duration <= config.maxJourneyDuration }
            } else {
                 cachedServices
            }

            val effectiveHasData = filteredServices.isNotEmpty()
            
            if (!effectiveHasData) {
                views.setEmptyView(R.id.departures_list, R.id.error_container)
            }
            
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            var errorState = prefs.getString(PREF_LAST_ERROR + appWidgetId, null)
            
            if (!effectiveHasData && cachedServices.isNotEmpty() && errorState == null) {
                errorState = "FILTERED"
            }

            val errorMessage = when (errorState) {
                "NETWORK" -> context.getString(R.string.network_error)
                "GENERIC" -> context.getString(R.string.widget_error)
                "FILTERED" -> context.getString(R.string.trains_filtered_out)
                else -> {
                    if (toStation.isNotEmpty()) {
                        context.getString(R.string.no_trains_found_from_to, fromStation, toStation)
                    } else {
                        context.getString(R.string.no_trains_found_from, fromStation)
                    }
                }
            }
            views.setTextViewText(R.id.error_message, errorMessage)
            
            if (errorState == "FILTERED") {
                views.setTextViewText(R.id.error_details, Html.fromHtml("<u>" + context.getString(R.string.tap_to_change_filters) + "</u>", Html.FROM_HTML_MODE_LEGACY))
                views.setColorAttr(R.id.error_details, "setTextColor", android.R.attr.colorAccent)
                views.setViewVisibility(R.id.error_details, View.VISIBLE)
                
                val settingsIntent = Intent(context, TrainTimesWidgetConfigureActivity::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    putExtra(TrainTimesWidgetConfigureActivity.EXTRA_OPEN_TAB, 3) // Tab Index 3 = Advanced
                    data = Uri.parse("trainwidget://settings/$appWidgetId")
                }
                val settingsPendingIntent = PendingIntent.getActivity(context, appWidgetId, settingsIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                views.setOnClickPendingIntent(R.id.error_message, settingsPendingIntent)
                views.setOnClickPendingIntent(R.id.error_details, settingsPendingIntent)
            } else {
                views.setViewVisibility(R.id.error_details, View.GONE)
                
                // Keep refresh intent for other errors
                val refreshIntent = Intent(context, TrainTimesWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
                }
                val refreshPendingIntent = PendingIntent.getBroadcast(context, appWidgetId, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                views.setOnClickPendingIntent(R.id.error_message, refreshPendingIntent)
            }

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

        private fun createBaseWidgetView(
            context: Context, appWidgetId: Int, styling: WidgetStyling,
            title: String, config: WidgetConfiguration, fromStation: String, toStation: String, isLoading: Boolean = false
        ): RemoteViews {
            val useRetro = config.fontStyle == "RETRO"
            val layoutId = if (useRetro) {
                if (config.showDivider) R.layout.widget_layout_retro else R.layout.widget_layout_no_divider_retro
            } else {
                if (config.showDivider) R.layout.widget_layout else R.layout.widget_layout_no_divider
            }
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
            
            // Note: refresh_button removed from here to handle separately with system color
            val allImageViewIds = intArrayOf(R.id.widget_icon, R.id.settings_button)
            for (id in allImageViewIds) {
                if (styling.useSystemTextColor) {
                    views.setColorAttr(id, "setColorFilter", com.google.android.material.R.attr.colorOnSurface)
                } else {
                    views.setInt(id, "setColorFilter", styling.textColor)
                }
            }
            
            // Handle refresh button with system palette (colorAccent)
            views.setColorAttr(R.id.refresh_button, "setColorFilter", android.R.attr.colorAccent)

            // Visibility
            views.setViewVisibility(R.id.content_container, View.VISIBLE)
            views.setViewVisibility(R.id.setup_message, View.GONE)
            views.setViewVisibility(R.id.loading_container, if (isLoading) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.departures_list, if (isLoading) View.GONE else View.VISIBLE)
            views.setViewVisibility(R.id.error_container, View.GONE)

            // Content
            if (useRetro) {
                views.setViewVisibility(R.id.widget_title, View.GONE)
                views.setViewVisibility(R.id.widget_title_image, View.VISIBLE)
                
                val titleSize = WidgetUtils.getTitleSize(config.fontSize)
                val bitmap = BitmapGenerator.textAsBitmap(
                    context, title, titleSize, styling.textColor, R.font.pixeloid_sans
                )
                if (bitmap != null) {
                    views.setImageViewBitmap(R.id.widget_title_image, bitmap)
                }
                
                // Set alignment gravity
                val gravity = when (config.alignment) {
                    "CENTER" -> Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL
                    "END" -> Gravity.END or Gravity.CENTER_VERTICAL
                    else -> Gravity.START or Gravity.CENTER_VERTICAL
                }
                views.setInt(R.id.widget_title_wrapper, "setGravity", gravity)
                
            } else {
                views.setTextViewText(R.id.widget_title, title)
                views.setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_SP, WidgetUtils.getTitleSize(config.fontSize))
                val gravity = when (config.alignment) {
                    "CENTER" -> Gravity.CENTER_HORIZONTAL
                    "END" -> Gravity.END
                    else -> Gravity.START
                }
                views.setInt(R.id.widget_title, "setGravity", gravity)
            }

            // Icon
            val iconResId = if (useRetro) R.drawable.train_pixel_24px else R.drawable.train_24px
            views.setImageViewResource(R.id.widget_icon, iconResId)
            views.setViewVisibility(R.id.widget_icon, if (config.showIcon) View.VISIBLE else View.GONE)
            
            // Standard Settings and Refresh Icons
            views.setImageViewResource(R.id.refresh_button, R.drawable.refresh_24px)
            views.setImageViewResource(R.id.settings_button, R.drawable.settings)
            
            views.setViewVisibility(R.id.refresh_button, if (config.showRefreshIcon) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.settings_button, if (config.showSettingsIcon) View.VISIBLE else View.GONE)

            // Footer
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastUpdate = prefs.getLong(PREF_LAST_SUCCESSFUL_UPDATE + appWidgetId, 0L)
            val displayTime = if (lastUpdate > 0) Date(lastUpdate) else Date()
            
            val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(displayTime)
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
            views.setOnClickPendingIntent(R.id.widget_title_image, refreshPendingIntent)
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
            // Removed text color override to allow layout attribute ?attr/colorOnPrimary to work
            // views.setColorAttr(R.id.setup_message, "setTextColor", android.R.attr.colorAccent)

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