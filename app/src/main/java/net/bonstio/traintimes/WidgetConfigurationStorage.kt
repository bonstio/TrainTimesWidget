package net.bonstio.traintimes

import android.content.Context
import android.graphics.Color

/**
 * Helper object to save and load widget configurations from SharedPreferences.
 */
object WidgetConfigurationStorage {
    private const val PREFS_NAME = "net.bonstio.traintimes.widget"
    private const val PREF_TITLE_KEY = "title_"
    private const val PREF_TITLE_STYLE_KEY = "title_style_"
    private const val PREF_IS_AUTO_TITLE_KEY = "is_auto_title_" // Deprecated
    private const val PREF_SHOW_ICON_KEY = "show_icon_"
    private const val PREF_SHOW_REFRESH_ICON_KEY = "show_refresh_icon_"
    private const val PREF_SHOW_SETTINGS_ICON_KEY = "show_settings_icon_"
    private const val PREF_SHOW_GPS_ICON_KEY = "show_gps_icon_"
    private const val PREF_SHOW_STOPS_KEY = "show_stops_"
    private const val PREF_STATION_STOPS_MODE_KEY = "station_stops_mode_"
    private const val PREF_FROM_STATION_KEY = "from_station_"
    private const val PREF_TO_STATION_KEY = "to_station_"
    private const val PREF_ALIGNMENT_KEY = "alignment_"
    private const val PREF_START_NORMAL_KEY = "start_normal_"
    private const val PREF_START_REVERSE_KEY = "start_reverse_"
    private const val PREF_TRANSPARENCY_KEY = "transparency_"
    private const val PREF_TEXT_COLOR_KEY = "text_color_"
    private const val PREF_BG_COLOR_KEY = "bg_color_"
    private const val PREF_USE_SYSTEM_TEXT_COLOR_KEY = "use_system_text_color_"
    private const val PREF_USE_SYSTEM_BG_COLOR_KEY = "use_system_bg_color_"
    private const val PREF_FONT_SIZE_KEY = "font_size_"
    private const val PREF_SHOW_MAPS_ICON_KEY = "show_maps_icon_"
    private const val PREF_SHOW_LAST_UPDATE_TIME_KEY = "show_last_update_time_"
    private const val PREF_SHOW_DIVIDER_KEY = "show_divider_"
    private const val PREF_COMMUTING_MODE_KEY = "commuting_mode_"
    private const val PREF_FONT_STYLE_KEY = "font_style_"
    private const val PREF_ENABLE_JOURNEY_DURATION_FILTER_KEY = "enable_journey_duration_filter_"
    private const val PREF_MAX_JOURNEY_DURATION_KEY = "max_journey_duration_"
    private const val PREF_USE_NEAREST_STATION_FOR_RETURN_KEY = "use_nearest_station_return_"

    /**
     * Saves the configuration for a specific widget ID.
     */
    fun saveConfiguration(
        context: Context,
        appWidgetId: Int,
        title: String,
        titleStyle: String,
        showIcon: Boolean,
        showRefreshIcon: Boolean,
        showSettingsIcon: Boolean,
        showGpsIcon: Boolean,
        showStops: Boolean,
        stationStopsMode: String,
        fromStation: String,
        toStation: String,
        alignment: String,
        startTimeNormal: Int,
        startTimeReverse: Int,
        transparency: Int,
        textColor: Int,
        bgColor: Int,
        useSystemTextColor: Boolean,
        useSystemBgColor: Boolean,
        fontSize: Int,
        showMapsIcon: Boolean,
        showLastUpdateTime: Boolean,
        showDivider: Boolean,
        commutingMode: String,
        fontStyle: String,
        enableJourneyDurationFilter: Boolean,
        maxJourneyDuration: Int,
        useNearestStationForReturn: Boolean
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, 0).edit()
        prefs.putString(PREF_TITLE_KEY + appWidgetId, title)
        prefs.putString(PREF_TITLE_STYLE_KEY + appWidgetId, titleStyle)
        prefs.remove(PREF_IS_AUTO_TITLE_KEY + appWidgetId)
        prefs.putBoolean(PREF_SHOW_ICON_KEY + appWidgetId, showIcon)
        prefs.putBoolean(PREF_SHOW_REFRESH_ICON_KEY + appWidgetId, showRefreshIcon)
        prefs.putBoolean(PREF_SHOW_SETTINGS_ICON_KEY + appWidgetId, showSettingsIcon)
        prefs.putBoolean(PREF_SHOW_GPS_ICON_KEY + appWidgetId, showGpsIcon)
        prefs.putBoolean(PREF_SHOW_STOPS_KEY + appWidgetId, showStops)
        prefs.putString(PREF_STATION_STOPS_MODE_KEY + appWidgetId, stationStopsMode)
        prefs.putString(PREF_FROM_STATION_KEY + appWidgetId, fromStation)
        prefs.putString(PREF_TO_STATION_KEY + appWidgetId, toStation)
        prefs.putString(PREF_ALIGNMENT_KEY + appWidgetId, alignment)
        prefs.putInt(PREF_START_NORMAL_KEY + appWidgetId, startTimeNormal)
        prefs.putInt(PREF_START_REVERSE_KEY + appWidgetId, startTimeReverse)
        prefs.putInt(PREF_TRANSPARENCY_KEY + appWidgetId, transparency)
        prefs.putInt(PREF_TEXT_COLOR_KEY + appWidgetId, textColor)
        prefs.putInt(PREF_BG_COLOR_KEY + appWidgetId, bgColor)
        prefs.putBoolean(PREF_USE_SYSTEM_TEXT_COLOR_KEY + appWidgetId, useSystemTextColor)
        prefs.putBoolean(PREF_USE_SYSTEM_BG_COLOR_KEY + appWidgetId, useSystemBgColor)
        prefs.putInt(PREF_FONT_SIZE_KEY + appWidgetId, fontSize)
        prefs.putBoolean(PREF_SHOW_MAPS_ICON_KEY + appWidgetId, showMapsIcon)
        prefs.putBoolean(PREF_SHOW_LAST_UPDATE_TIME_KEY + appWidgetId, showLastUpdateTime)
        prefs.putBoolean(PREF_SHOW_DIVIDER_KEY + appWidgetId, showDivider)
        prefs.putString(PREF_COMMUTING_MODE_KEY + appWidgetId, commutingMode)
        prefs.putString(PREF_FONT_STYLE_KEY + appWidgetId, fontStyle)
        prefs.putBoolean(PREF_ENABLE_JOURNEY_DURATION_FILTER_KEY + appWidgetId, enableJourneyDurationFilter)
        prefs.putInt(PREF_MAX_JOURNEY_DURATION_KEY + appWidgetId, maxJourneyDuration)
        prefs.putBoolean(PREF_USE_NEAREST_STATION_FOR_RETURN_KEY + appWidgetId, useNearestStationForReturn)
        // Use commit to ensure data is written before we broadcast the update
        prefs.commit()
    }

    /**
     * Loads the configuration for a specific widget ID.
     */
    fun loadConfiguration(context: Context, appWidgetId: Int): WidgetConfiguration? {
        val prefs = context.getSharedPreferences(PREFS_NAME, 0)
        val title = prefs.getString(PREF_TITLE_KEY + appWidgetId, null)

        var titleStyle = prefs.getString(PREF_TITLE_STYLE_KEY + appWidgetId, null)
        if (titleStyle == null) {
            if (prefs.contains(PREF_IS_AUTO_TITLE_KEY + appWidgetId)) {
                val isAutoTitle = prefs.getBoolean(PREF_IS_AUTO_TITLE_KEY + appWidgetId, true)
                titleStyle = if (isAutoTitle) "SHORT" else "CUSTOM"
            } else {
                titleStyle = WidgetConfigurationDefaults.TITLE_STYLE
            }
        }

        val showIcon = prefs.getBoolean(PREF_SHOW_ICON_KEY + appWidgetId, WidgetConfigurationDefaults.SHOW_ICON)
        val showRefreshIcon = prefs.getBoolean(PREF_SHOW_REFRESH_ICON_KEY + appWidgetId, WidgetConfigurationDefaults.SHOW_REFRESH_ICON)
        val showSettingsIcon = prefs.getBoolean(PREF_SHOW_SETTINGS_ICON_KEY + appWidgetId, WidgetConfigurationDefaults.SHOW_SETTINGS_ICON)
        val showGpsIcon = prefs.getBoolean(PREF_SHOW_GPS_ICON_KEY + appWidgetId, WidgetConfigurationDefaults.SHOW_GPS_ICON)
        
        // Always true now
        val hidePastDepartures = true 
        
        val showStops = prefs.getBoolean(PREF_SHOW_STOPS_KEY + appWidgetId, WidgetConfigurationDefaults.SHOW_STOPS)
        
        var stationStopsMode = prefs.getString(PREF_STATION_STOPS_MODE_KEY + appWidgetId, null)
        if (stationStopsMode == null) {
            stationStopsMode = if (showStops) WidgetConfigurationDefaults.STATION_STOPS_MODE else "NONE"
        }
        
        val fromStation = prefs.getString(PREF_FROM_STATION_KEY + appWidgetId, null)
        val toStation = prefs.getString(PREF_TO_STATION_KEY + appWidgetId, null)
        val alignment = prefs.getString(PREF_ALIGNMENT_KEY + appWidgetId, WidgetConfigurationDefaults.ALIGNMENT)
        val startTimeNormal = prefs.getInt(PREF_START_NORMAL_KEY + appWidgetId, WidgetConfigurationDefaults.START_TIME_NORMAL)
        val startTimeReverse = prefs.getInt(PREF_START_REVERSE_KEY + appWidgetId, WidgetConfigurationDefaults.START_TIME_REVERSE)
        val timeOffset = WidgetConfigurationDefaults.TIME_OFFSET
        val departureCount = WidgetConfigurationDefaults.DEPARTURE_COUNT
        val transparency = prefs.getInt(PREF_TRANSPARENCY_KEY + appWidgetId, WidgetConfigurationDefaults.TRANSPARENCY)
        val textColor = prefs.getInt(PREF_TEXT_COLOR_KEY + appWidgetId, WidgetConfigurationDefaults.TEXT_COLOR)
        val bgColor = prefs.getInt(PREF_BG_COLOR_KEY + appWidgetId, WidgetConfigurationDefaults.BG_COLOR)
        var useSystemTextColor = prefs.getBoolean(PREF_USE_SYSTEM_TEXT_COLOR_KEY + appWidgetId, WidgetConfigurationDefaults.USE_SYSTEM_TEXT_COLOR)
        var useSystemBgColor = prefs.getBoolean(PREF_USE_SYSTEM_BG_COLOR_KEY + appWidgetId, WidgetConfigurationDefaults.USE_SYSTEM_BG_COLOR)

        // Migrate legacy default widgets (which had black background and white text with useSystem=false)
        if (!useSystemBgColor && bgColor == Color.BLACK && (transparency == 128 || transparency == 255)) {
            useSystemBgColor = true
        }
        if (!useSystemTextColor && textColor == Color.WHITE) {
            useSystemTextColor = true
        }
        
        var fontSize = prefs.getInt(PREF_FONT_SIZE_KEY + appWidgetId, -1)
        if (fontSize == -1) {
             fontSize = WidgetConfigurationDefaults.FONT_SIZE
        }
        
        val showMapsIcon = prefs.getBoolean(PREF_SHOW_MAPS_ICON_KEY + appWidgetId, WidgetConfigurationDefaults.SHOW_MAPS_ICON)
        val showLastUpdateTime = prefs.getBoolean(PREF_SHOW_LAST_UPDATE_TIME_KEY + appWidgetId, WidgetConfigurationDefaults.SHOW_LAST_UPDATE_TIME)
        val showDivider = prefs.getBoolean(PREF_SHOW_DIVIDER_KEY + appWidgetId, WidgetConfigurationDefaults.SHOW_DIVIDER)
        val commutingMode = prefs.getString(PREF_COMMUTING_MODE_KEY + appWidgetId, WidgetConfigurationDefaults.COMMUTING_MODE) ?: WidgetConfigurationDefaults.COMMUTING_MODE
        val fontStyle = prefs.getString(PREF_FONT_STYLE_KEY + appWidgetId, WidgetConfigurationDefaults.FONT_STYLE) ?: WidgetConfigurationDefaults.FONT_STYLE
        
        val enableJourneyDurationFilter = prefs.getBoolean(PREF_ENABLE_JOURNEY_DURATION_FILTER_KEY + appWidgetId, WidgetConfigurationDefaults.ENABLE_JOURNEY_DURATION_FILTER)
        val maxJourneyDuration = prefs.getInt(PREF_MAX_JOURNEY_DURATION_KEY + appWidgetId, WidgetConfigurationDefaults.MAX_JOURNEY_DURATION)
        val useNearestStationForReturn = prefs.getBoolean(PREF_USE_NEAREST_STATION_FOR_RETURN_KEY + appWidgetId, WidgetConfigurationDefaults.USE_NEAREST_STATION_FOR_RETURN)

        return if (title != null && fromStation != null && toStation != null && alignment != null) {
            WidgetConfiguration(
                title,
                titleStyle,
                showIcon,
                showStops,
                stationStopsMode,
                fromStation,
                toStation,
                alignment,
                startTimeNormal,
                startTimeReverse,
                timeOffset,
                departureCount,
                transparency,
                textColor,
                bgColor,
                useSystemTextColor,
                useSystemBgColor,
                fontSize,
                showRefreshIcon,
                showSettingsIcon,
                showGpsIcon,
                hidePastDepartures,
                showMapsIcon,
                showLastUpdateTime,
                showDivider,
                commutingMode,
                fontStyle,
                enableJourneyDurationFilter,
                maxJourneyDuration,
                useNearestStationForReturn
            )
        } else {
            null
        }
    }

    /**
     * Deletes the configuration for a specific widget ID.
     */
    fun deleteConfiguration(context: Context, appWidgetId: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, 0).edit()
        prefs.remove(PREF_TITLE_KEY + appWidgetId)
        prefs.remove(PREF_TITLE_STYLE_KEY + appWidgetId)
        prefs.remove(PREF_IS_AUTO_TITLE_KEY + appWidgetId)
        prefs.remove(PREF_SHOW_ICON_KEY + appWidgetId)
        prefs.remove(PREF_SHOW_REFRESH_ICON_KEY + appWidgetId)
        prefs.remove(PREF_SHOW_SETTINGS_ICON_KEY + appWidgetId)
        prefs.remove(PREF_SHOW_GPS_ICON_KEY + appWidgetId)
        // PREF_HIDE_PAST_DEPARTURES_KEY is now legacy/unused
        prefs.remove(PREF_SHOW_STOPS_KEY + appWidgetId)
        prefs.remove(PREF_STATION_STOPS_MODE_KEY + appWidgetId)
        prefs.remove(PREF_FROM_STATION_KEY + appWidgetId)
        prefs.remove(PREF_TO_STATION_KEY + appWidgetId)
        prefs.remove(PREF_ALIGNMENT_KEY + appWidgetId)
        prefs.remove(PREF_START_NORMAL_KEY + appWidgetId)
        prefs.remove(PREF_START_REVERSE_KEY + appWidgetId)
        prefs.remove(PREF_TRANSPARENCY_KEY + appWidgetId)
        prefs.remove(PREF_TEXT_COLOR_KEY + appWidgetId)
        prefs.remove(PREF_BG_COLOR_KEY + appWidgetId)
        prefs.remove(PREF_USE_SYSTEM_TEXT_COLOR_KEY + appWidgetId)
        prefs.remove(PREF_USE_SYSTEM_BG_COLOR_KEY + appWidgetId)
        prefs.remove(PREF_FONT_SIZE_KEY + appWidgetId)
        prefs.remove(PREF_SHOW_MAPS_ICON_KEY + appWidgetId)
        prefs.remove(PREF_SHOW_LAST_UPDATE_TIME_KEY + appWidgetId)
        prefs.remove(PREF_SHOW_DIVIDER_KEY + appWidgetId)
        prefs.remove(PREF_COMMUTING_MODE_KEY + appWidgetId)
        prefs.remove(PREF_FONT_STYLE_KEY + appWidgetId)
        prefs.remove(PREF_ENABLE_JOURNEY_DURATION_FILTER_KEY + appWidgetId)
        prefs.remove(PREF_MAX_JOURNEY_DURATION_KEY + appWidgetId)
        prefs.remove(PREF_USE_NEAREST_STATION_FOR_RETURN_KEY + appWidgetId)
        prefs.apply()
    }
}
