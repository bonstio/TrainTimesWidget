package net.bonstio.traintimes

import android.content.Context
import android.graphics.Color

/**
 * Data class representing the configuration of a widget instance.
 */
data class WidgetConfiguration(
    val title: String,
    val titleStyle: String = "SHORT",
    val showIcon: Boolean = true,
    @Deprecated("Use stationStopsMode") val showStops: Boolean = true,
    val stationStopsMode: String = "FIRST",
    val fromStation: String,
    val toStation: String,
    val alignment: String = "START",
    val startTimeNormal: Int = -1,
    val startTimeReverse: Int = -1,
    val timeOffset: Int = 0,
    val departureCount: Int = 5,
    val transparency: Int = 128,
    val textColor: Int = Color.WHITE,
    val bgColor: Int = Color.BLACK,
    val useSystemTextColor: Boolean = false,
    val useSystemBgColor: Boolean = false,
    val fontSize: Int = 2, // 0 = Extra Small, 1 = Small, 2 = Regular, 3 = Large, 4 = Extra Large
    val showRefreshIcon: Boolean = true,
    val showSettingsIcon: Boolean = false,
    val hidePastDepartures: Boolean = false,
    val showMapsIcon: Boolean = true,
    val showLastUpdateTime: Boolean = true
)

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
    private const val PREF_HIDE_PAST_DEPARTURES_KEY = "hide_past_departures_"
    private const val PREF_SHOW_STOPS_KEY = "show_stops_"
    private const val PREF_STATION_STOPS_MODE_KEY = "station_stops_mode_"
    private const val PREF_FROM_STATION_KEY = "from_station_"
    private const val PREF_TO_STATION_KEY = "to_station_"
    private const val PREF_ALIGNMENT_KEY = "alignment_"
    private const val PREF_START_NORMAL_KEY = "start_normal_"
    private const val PREF_START_REVERSE_KEY = "start_reverse_"
    private const val PREF_TIME_OFFSET_KEY = "time_offset_"
    private const val PREF_DEPARTURE_COUNT_KEY = "departure_count_"
    private const val PREF_TRANSPARENCY_KEY = "transparency_"
    private const val PREF_TEXT_COLOR_KEY = "text_color_"
    private const val PREF_BG_COLOR_KEY = "bg_color_"
    private const val PREF_USE_SYSTEM_TEXT_COLOR_KEY = "use_system_text_color_"
    private const val PREF_USE_SYSTEM_BG_COLOR_KEY = "use_system_bg_color_"
    private const val PREF_FONT_SIZE_KEY = "font_size_"
    private const val PREF_SHOW_MAPS_ICON_KEY = "show_maps_icon_"
    private const val PREF_SHOW_LAST_UPDATE_TIME_KEY = "show_last_update_time_"

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
        hidePastDepartures: Boolean,
        showStops: Boolean,
        stationStopsMode: String,
        fromStation: String,
        toStation: String,
        alignment: String,
        startTimeNormal: Int,
        startTimeReverse: Int,
        timeOffset: Int,
        departureCount: Int,
        transparency: Int,
        textColor: Int,
        bgColor: Int,
        useSystemTextColor: Boolean,
        useSystemBgColor: Boolean,
        fontSize: Int,
        showMapsIcon: Boolean,
        showLastUpdateTime: Boolean
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, 0).edit()
        prefs.putString(PREF_TITLE_KEY + appWidgetId, title)
        prefs.putString(PREF_TITLE_STYLE_KEY + appWidgetId, titleStyle)
        prefs.remove(PREF_IS_AUTO_TITLE_KEY + appWidgetId)
        prefs.putBoolean(PREF_SHOW_ICON_KEY + appWidgetId, showIcon)
        prefs.putBoolean(PREF_SHOW_REFRESH_ICON_KEY + appWidgetId, showRefreshIcon)
        prefs.putBoolean(PREF_SHOW_SETTINGS_ICON_KEY + appWidgetId, showSettingsIcon)
        prefs.putBoolean(PREF_HIDE_PAST_DEPARTURES_KEY + appWidgetId, hidePastDepartures)
        prefs.putBoolean(PREF_SHOW_STOPS_KEY + appWidgetId, showStops)
        prefs.putString(PREF_STATION_STOPS_MODE_KEY + appWidgetId, stationStopsMode)
        prefs.putString(PREF_FROM_STATION_KEY + appWidgetId, fromStation)
        prefs.putString(PREF_TO_STATION_KEY + appWidgetId, toStation)
        prefs.putString(PREF_ALIGNMENT_KEY + appWidgetId, alignment)
        prefs.putInt(PREF_START_NORMAL_KEY + appWidgetId, startTimeNormal)
        prefs.putInt(PREF_START_REVERSE_KEY + appWidgetId, startTimeReverse)
        prefs.putInt(PREF_TIME_OFFSET_KEY + appWidgetId, timeOffset)
        prefs.putInt(PREF_DEPARTURE_COUNT_KEY + appWidgetId, departureCount)
        prefs.putInt(PREF_TRANSPARENCY_KEY + appWidgetId, transparency)
        prefs.putInt(PREF_TEXT_COLOR_KEY + appWidgetId, textColor)
        prefs.putInt(PREF_BG_COLOR_KEY + appWidgetId, bgColor)
        prefs.putBoolean(PREF_USE_SYSTEM_TEXT_COLOR_KEY + appWidgetId, useSystemTextColor)
        prefs.putBoolean(PREF_USE_SYSTEM_BG_COLOR_KEY + appWidgetId, useSystemBgColor)
        prefs.putInt(PREF_FONT_SIZE_KEY + appWidgetId, fontSize)
        prefs.putBoolean(PREF_SHOW_MAPS_ICON_KEY + appWidgetId, showMapsIcon)
        prefs.putBoolean(PREF_SHOW_LAST_UPDATE_TIME_KEY + appWidgetId, showLastUpdateTime)
        prefs.apply()
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
                titleStyle = "SHORT"
            }
        }

        val showIcon = prefs.getBoolean(PREF_SHOW_ICON_KEY + appWidgetId, true)
        val showRefreshIcon = prefs.getBoolean(PREF_SHOW_REFRESH_ICON_KEY + appWidgetId, true)
        val showSettingsIcon = prefs.getBoolean(PREF_SHOW_SETTINGS_ICON_KEY + appWidgetId, false)
        val hidePastDepartures = prefs.getBoolean(PREF_HIDE_PAST_DEPARTURES_KEY + appWidgetId, false)
        val showStops = prefs.getBoolean(PREF_SHOW_STOPS_KEY + appWidgetId, true)
        
        var stationStopsMode = prefs.getString(PREF_STATION_STOPS_MODE_KEY + appWidgetId, null)
        if (stationStopsMode == null) {
            stationStopsMode = if (showStops) "FIRST" else "NONE"
        }
        
        val fromStation = prefs.getString(PREF_FROM_STATION_KEY + appWidgetId, null)
        val toStation = prefs.getString(PREF_TO_STATION_KEY + appWidgetId, null)
        val alignment = prefs.getString(PREF_ALIGNMENT_KEY + appWidgetId, "START")
        val startTimeNormal = prefs.getInt(PREF_START_NORMAL_KEY + appWidgetId, -1)
        val startTimeReverse = prefs.getInt(PREF_START_REVERSE_KEY + appWidgetId, -1)
        val timeOffset = prefs.getInt(PREF_TIME_OFFSET_KEY + appWidgetId, 0)
        val departureCount = prefs.getInt(PREF_DEPARTURE_COUNT_KEY + appWidgetId, 5)
        val transparency = prefs.getInt(PREF_TRANSPARENCY_KEY + appWidgetId, 128)
        val textColor = prefs.getInt(PREF_TEXT_COLOR_KEY + appWidgetId, Color.WHITE)
        val bgColor = prefs.getInt(PREF_BG_COLOR_KEY + appWidgetId, Color.BLACK)
        val useSystemTextColor = prefs.getBoolean(PREF_USE_SYSTEM_TEXT_COLOR_KEY + appWidgetId, false)
        val useSystemBgColor = prefs.getBoolean(PREF_USE_SYSTEM_BG_COLOR_KEY + appWidgetId, false)
        // Default font size was 1 (Regular). Now let's map old values to new scale if needed.
        // Old: 0=Small, 1=Regular, 2=Large
        // New: 0=Extra Small, 1=Small, 2=Regular, 3=Large, 4=Extra Large
        var fontSize = prefs.getInt(PREF_FONT_SIZE_KEY + appWidgetId, -1)
        if (fontSize == -1) {
             fontSize = 2 // Default Regular
        }
        
        val showMapsIcon = prefs.getBoolean(PREF_SHOW_MAPS_ICON_KEY + appWidgetId, true)
        val showLastUpdateTime = prefs.getBoolean(PREF_SHOW_LAST_UPDATE_TIME_KEY + appWidgetId, true)

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
                hidePastDepartures,
                showMapsIcon,
                showLastUpdateTime
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
        prefs.remove(PREF_HIDE_PAST_DEPARTURES_KEY + appWidgetId)
        prefs.remove(PREF_SHOW_STOPS_KEY + appWidgetId)
        prefs.remove(PREF_STATION_STOPS_MODE_KEY + appWidgetId)
        prefs.remove(PREF_FROM_STATION_KEY + appWidgetId)
        prefs.remove(PREF_TO_STATION_KEY + appWidgetId)
        prefs.remove(PREF_ALIGNMENT_KEY + appWidgetId)
        prefs.remove(PREF_START_NORMAL_KEY + appWidgetId)
        prefs.remove(PREF_START_REVERSE_KEY + appWidgetId)
        prefs.remove(PREF_TIME_OFFSET_KEY + appWidgetId)
        prefs.remove(PREF_DEPARTURE_COUNT_KEY + appWidgetId)
        prefs.remove(PREF_TRANSPARENCY_KEY + appWidgetId)
        prefs.remove(PREF_TEXT_COLOR_KEY + appWidgetId)
        prefs.remove(PREF_BG_COLOR_KEY + appWidgetId)
        prefs.remove(PREF_USE_SYSTEM_TEXT_COLOR_KEY + appWidgetId)
        prefs.remove(PREF_USE_SYSTEM_BG_COLOR_KEY + appWidgetId)
        prefs.remove(PREF_FONT_SIZE_KEY + appWidgetId)
        prefs.remove(PREF_SHOW_MAPS_ICON_KEY + appWidgetId)
        prefs.remove(PREF_SHOW_LAST_UPDATE_TIME_KEY + appWidgetId)
        prefs.apply()
    }
}