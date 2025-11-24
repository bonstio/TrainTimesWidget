package net.bonstio.traintimes

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import kotlinx.coroutines.runBlocking

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


    override fun onCreate() {
        // No-op
    }

    override fun onDataSetChanged() {
        config = WidgetConfigurationStorage.loadConfiguration(context, appWidgetId)
        
        // Load from cache instead of network
        services = WidgetCache.loadServices(context, appWidgetId)

        if (config != null) {
            styling = TrainTimesWidgetProvider.resolveWidgetStyling(context, config!!)
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

        val bodySize = when (config!!.fontSize) {
            0 -> 10f // Extra Small
            1 -> 12f // Small
            3 -> 16f // Large
            4 -> 18f // Extra Large
            else -> 14f // Regular (2)
        }

        departureView.setTextViewText(R.id.departure_time, service.std)
        departureView.setTextViewText(R.id.destination, service.destination)
        val platformText = service.platform?.let { "Platform $it" } ?: ""
        departureView.setTextViewText(R.id.platform, platformText)
        departureView.setTextViewText(R.id.status, service.status)

        // Styling
        if (!styling!!.useSystemTextColor) {
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
        val showStops = when(config!!.stationStopsMode) {
            "ALL" -> true
            "FIRST" -> (position == 0)
            "NONE" -> false
            else -> (position == 0)
        }

        if (service.subsequentCallingPoints.isNotEmpty() && showStops) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val key = "${PREF_IS_EXPANDED}${appWidgetId}_$position"
            val isExpanded = prefs.getBoolean(key, false)

            val callingPointsText = "Calling at ${service.subsequentCallingPoints.joinToString(", ")}"
            departureView.setTextViewText(R.id.calling_points, callingPointsText)

            if (!styling!!.useSystemTextColor) {
                departureView.setTextColor(R.id.calling_points, styling!!.textColor)
            }
            departureView.setTextViewTextSize(R.id.calling_points, sizeUnit, bodySize)
            departureView.setViewVisibility(R.id.calling_points, View.VISIBLE)

            val maxLines = if (isExpanded) 100 else 1
            departureView.setInt(R.id.calling_points, "setMaxLines", maxLines)

            val fillInIntent = Intent().apply {
                val extras = Bundle()
                extras.putInt(TrainTimesWidgetProvider.EXTRA_SERVICE_INDEX, position)
                putExtras(extras)
            }
            departureView.setOnClickFillInIntent(R.id.departure_row, fillInIntent)
        } else {
            departureView.setViewVisibility(R.id.calling_points, View.GONE)
            val fillInIntent = Intent()
            departureView.setOnClickFillInIntent(R.id.departure_row, fillInIntent)
        }

        return departureView
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true
}