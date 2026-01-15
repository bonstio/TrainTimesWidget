package net.bonstio.traintimes

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import kotlin.math.max

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
        val allServices = WidgetCache.loadServices(context, appWidgetId)
        
        if (config != null && config!!.enableJourneyDurationFilter) {
            services = allServices.filter {
                it.duration == null || it.duration <= config!!.maxJourneyDuration
            }
        } else {
            services = allServices
        }
        
        Log.d(TAG, "onDataSetChanged: Loaded ${allServices.size} services from cache, filtered to ${services.size} for widgetId=$appWidgetId")

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
        
        val useRetro = config?.fontStyle == "RETRO"
        
        var widgetWidthPx = 0
        var contentStartOffsetPx = 0
        
        val layoutId = if (useRetro) R.layout.departure_layout_retro else R.layout.departure_layout
        val departureView = RemoteViews(context.packageName, layoutId)

        if (config == null || styling == null) {
            return departureView
        }

        val bodySize = WidgetUtils.getBodySize(config!!.fontSize)
        val textColor = if (styling!!.useSystemTextColor) {
            WidgetUtils.getThemeColor(context, com.google.android.material.R.attr.colorOnSurface)
        } else {
            styling!!.textColor
        }

        if (useRetro) {
            // Render text as images
            val smallBodySize = max(8f, bodySize - 3f)

            // Helper to generate bitmap (returns null if text empty)
            fun genBitmap(text: String, size: Float, maxWidth: Int? = null): Bitmap? {
                return if (text.isNotEmpty()) {
                    BitmapGenerator.textAsBitmap(context, text, size, textColor, R.font.pixeloid_sans, maxWidth = maxWidth)
                } else null
            }

            // 1. Generate auxiliary columns first to know their widths
            val timeBitmap = genBitmap(service.std, bodySize)
            
            val platformText = service.platform?.let { "Pl. $it" } ?: ""
            val platformBitmap = genBitmap(platformText, smallBodySize)
            
            val statusBitmap = genBitmap(service.status, smallBodySize)

            // 2. Set auxiliary views
            if (timeBitmap != null) {
                departureView.setImageViewBitmap(R.id.departure_time_img, timeBitmap)
                departureView.setViewVisibility(R.id.departure_time_img, View.VISIBLE)
            } else {
                departureView.setViewVisibility(R.id.departure_time_img, View.GONE)
            }

            if (platformBitmap != null) {
                departureView.setImageViewBitmap(R.id.platform_img, platformBitmap)
                departureView.setViewVisibility(R.id.platform_img, View.VISIBLE)
            } else {
                departureView.setViewVisibility(R.id.platform_img, View.GONE)
            }

            if (statusBitmap != null) {
                departureView.setImageViewBitmap(R.id.status_img, statusBitmap)
                departureView.setViewVisibility(R.id.status_img, View.VISIBLE)
            } else {
                departureView.setViewVisibility(R.id.status_img, View.GONE)
            }

            // 3. Calculate available width for Destination
            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val density = displayMetrics.density
            
            // Get accurate widget width if possible, fallback to screen width
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val minWidthPx = if (minWidthDp > 0) {
                 TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    minWidthDp.toFloat(),
                    displayMetrics
                ).toInt()
            } else screenWidth
            
            widgetWidthPx = minWidthPx
            contentStartOffsetPx = (timeBitmap?.width ?: 0) + (8 * density).toInt()
            
            // Margins/Padding estimate (in pixels)
            // 8dp between Time and Dest
            // 8dp between Dest and Platform
            // 8dp between Platform and Status
            // Plus widget padding (~24dp total?)
            val marginsApprox = (60 * density).toInt() 
            
            val occupiedWidth = (timeBitmap?.width ?: 0) + (platformBitmap?.width ?: 0) + (statusBitmap?.width ?: 0) + marginsApprox
            val maxDestWidth = max(100, minWidthPx - occupiedWidth) // Ensure at least some width

            // 4. Generate Destination Bitmap with limit
            val destBitmap = genBitmap(service.destination, bodySize, maxWidth = maxDestWidth)
            if (destBitmap != null) {
                departureView.setImageViewBitmap(R.id.destination_img, destBitmap)
                departureView.setViewVisibility(R.id.destination_img, View.VISIBLE)
            } else {
                departureView.setViewVisibility(R.id.destination_img, View.GONE)
            }
            
        } else {
            // Standard TextViews
            departureView.setTextViewText(R.id.departure_time, service.std)
            departureView.setTextViewText(R.id.destination, service.destination)
            val platformText = service.platform?.let { "Pl. $it" } ?: ""
            departureView.setTextViewText(R.id.platform, platformText)
            departureView.setTextViewText(R.id.status, service.status)

            // Styling for TextViews
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
        }

        // Stops Logic
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = "${PREF_IS_EXPANDED}${appWidgetId}_$position"
        val isExpanded = prefs.getBoolean(key, false)

        val shouldShowStops = isExpanded || when(config!!.stationStopsMode) {
            "ALL" -> true
            "FIRST" -> (position == 0)
            "NONE" -> false
            else -> (position == 0)
        }

        val fillInIntent = Intent().apply {
            val extras = Bundle()
            extras.putInt(TrainTimesWidgetProvider.EXTRA_SERVICE_INDEX, position)
            putExtras(extras)
        }
        departureView.setOnClickFillInIntent(R.id.departure_row, fillInIntent)

        // Unified logic for calling points: Always use TextView
        if (service.subsequentCallingPoints.isNotEmpty() && shouldShowStops) {
            val callingPointsText = "Calling at ${service.subsequentCallingPoints.joinToString(", ")}"
            
            departureView.setTextViewText(R.id.calling_points, callingPointsText)

            if (styling!!.useSystemTextColor) {
                departureView.setColorAttr(R.id.calling_points, "setTextColor", com.google.android.material.R.attr.colorOnSurface)
            } else {
                departureView.setTextColor(R.id.calling_points, styling!!.textColor)
            }
            val sizeUnit = TypedValue.COMPLEX_UNIT_SP
            departureView.setTextViewTextSize(R.id.calling_points, sizeUnit, bodySize)
            departureView.setViewVisibility(R.id.calling_points, View.VISIBLE)

            val maxLines = if (isExpanded) 100 else 1
            departureView.setInt(R.id.calling_points, "setMaxLines", maxLines)
            
        } else {
            departureView.setViewVisibility(R.id.calling_points, View.GONE)
        }

        return departureView
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 2

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true
}