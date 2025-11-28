package net.bonstio.traintimes

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService

class TrainTimesWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return TrainTimesRemoteViewsFactory(this.applicationContext, intent)
    }
}

class TrainTimesRemoteViewsFactory(
    private val context: Context,
    private val intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private var services: List<TrainService> = emptyList()
    private var config: WidgetConfiguration? = null
    private var styling: WidgetStyling? = null
    private val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
    
    companion object {
        private const val TAG = "TrainWidgetService"
    }


    override fun onCreate() {
        Log.d(TAG, "onCreate: widgetId=$appWidgetId")
    }

    override fun onDataSetChanged() {
        Log.d(TAG, "onDataSetChanged: widgetId=$appWidgetId")
        config = WidgetConfigurationStorage.loadConfiguration(context, appWidgetId)
        
        // Load from cache instead of network
        services = WidgetCache.loadServices(context, appWidgetId)
        Log.d(TAG, "onDataSetChanged: Loaded ${services.size} services from cache for widgetId=$appWidgetId")

        if (config != null) {
            styling = WidgetUtils.resolveWidgetStyling(context, config!!)
        } else {
            Log.w(TAG, "onDataSetChanged: Config is null for widgetId=$appWidgetId")
        }
    }

    override fun onDestroy() {
        services = emptyList()
    }

    override fun getCount(): Int = services.size

    override fun getViewAt(position: Int): RemoteViews? {
        if (position >= services.size) return null

        val service = services[position]
        val departureView = RemoteViews(context.packageName, R.layout.departure_layout)

        if (config == null || styling == null) {
            return departureView
        }

        val bodySize = WidgetUtils.getBodySize(config!!.fontSize)

        departureView.setTextViewText(R.id.departure_time, service.std)
        departureView.setTextViewText(R.id.destination, service.destination)
        val platformText = service.platform?.let { "Platform $it" } ?: ""
        departureView.setTextViewText(R.id.platform, platformText)
        departureView.setTextViewText(R.id.status, service.status)

        // Styling
        if (styling!!.useSystemTextColor) {
            val attr = com.google.android.material.R.attr.colorOnSurface
            departureView.setColorAttr(R.id.departure_time, "setTextColor", attr)
            departureView.setColorAttr(R.id.destination, "setTextColor", attr)
            departureView.setColorAttr(R.id.platform, "setTextColor", attr)
            departureView.setColorAttr(R.id.status, "setTextColor", attr)
        } else {
            val tc = styling!!.textColor
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
        // Check if this row is expanded
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = "${PREF_IS_EXPANDED}${appWidgetId}_$position"
        val isExpanded = prefs.getBoolean(key, false)

        // Determine visibility based on config and expansion state
        val shouldShowStops = isExpanded || when(config!!.stationStopsMode) {
            "ALL" -> true
            "FIRST" -> (position == 0)
            "NONE" -> false
            else -> (position == 0)
        }

        // Always set the click intent so the user can expand/collapse
        val fillInIntent = Intent().apply {
            val extras = Bundle()
            extras.putInt(TrainTimesWidgetProvider.EXTRA_SERVICE_INDEX, position)
            putExtras(extras)
        }
        departureView.setOnClickFillInIntent(R.id.departure_row, fillInIntent)

        if (service.subsequentCallingPoints.isNotEmpty() && shouldShowStops) {
            val callingPointsText = "Calling at ${service.subsequentCallingPoints.joinToString(", ")}"
            departureView.setTextViewText(R.id.calling_points, callingPointsText)

            if (styling!!.useSystemTextColor) {
                departureView.setColorAttr(R.id.calling_points, "setTextColor", com.google.android.material.R.attr.colorOnSurface)
            } else {
                departureView.setTextColor(R.id.calling_points, styling!!.textColor)
            }
            departureView.setTextViewTextSize(R.id.calling_points, sizeUnit, bodySize)
            departureView.setViewVisibility(R.id.calling_points, View.VISIBLE)

            // If expanded, show all lines. If just visible due to config, show 1 line.
            val maxLines = if (isExpanded) 100 else 1
            departureView.setInt(R.id.calling_points, "setMaxLines", maxLines)
        } else {
            departureView.setViewVisibility(R.id.calling_points, View.GONE)
        }

        return departureView
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true
}