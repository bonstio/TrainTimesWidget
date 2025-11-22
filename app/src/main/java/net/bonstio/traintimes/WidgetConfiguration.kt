package net.bonstio.traintimes

import android.content.Context
import android.graphics.Color

/**
 * Data class representing the configuration of a widget instance.
 *
 * @property title The title displayed on the widget.
 * @property titleStyle The style of the title (e.g., "SHORT", "LONG", "CUSTOM").
 * @property showIcon Whether to show the train icon.
 * @property showStops Whether to show the calling points for the first service.
 * @property fromStation The CRS code of the departure station.
 * @property toStation The CRS code of the arrival station.
 * @property alignment The alignment of the title ("START", "CENTER", "END").
 * @property startTimeNormal The start time (in minutes from midnight) for normal route direction.
 * @property startTimeReverse The start time (in minutes from midnight) for reverse route direction.
 * @property timeOffset The offset in minutes to apply to queries.
 * @property departureCount The number of departures to request.
 * @property transparency The transparency level of the widget background (0-255).
 * @property textColor The color of the widget text.
 * @property bgColor The background color of the widget.
 * @property useSystemTextColor Whether to use the system theme's text color.
 * @property useSystemBgColor Whether to use the system theme's background color.
 * @property fontSize The font size setting (0=Small, 1=Regular, 2=Large).
 */
data class WidgetConfiguration(
    val title: String,
    val titleStyle: String = "SHORT",
    val showIcon: Boolean = true,
    val showStops: Boolean = true,
    val fromStation: String,
    val toStation: String,
    val alignment: String = "START",
    val startTimeNormal: Int = 270, // 04:30
    val startTimeReverse: Int = 720,  // 12:00
    val timeOffset: Int = 0,
    val departureCount: Int = 4,
    val transparency: Int = 128,
    val textColor: Int = Color.WHITE,
    val bgColor: Int = Color.BLACK,
    val useSystemTextColor: Boolean = false,
    val useSystemBgColor: Boolean = false,
    val fontSize: Int = 1 // 0 = Small, 1 = Regular, 2 = Large
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
    private const val PREF_SHOW_STOPS_KEY = "show_stops_"
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

    /**
     * Saves the configuration for a specific widget ID.
     */
    fun saveConfiguration(
        context: Context,
        appWidgetId: Int,
        title: String,
        titleStyle: String,
        showIcon: Boolean,
        showStops: Boolean,
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
        fontSize: Int
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, 0).edit()
        prefs.putString(PREF_TITLE_KEY + appWidgetId, title)
        prefs.putString(PREF_TITLE_STYLE_KEY + appWidgetId, titleStyle)
        prefs.remove(PREF_IS_AUTO_TITLE_KEY + appWidgetId) // Clean up deprecated key
        prefs.putBoolean(PREF_SHOW_ICON_KEY + appWidgetId, showIcon)
        prefs.putBoolean(PREF_SHOW_STOPS_KEY + appWidgetId, showStops)
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
        prefs.apply()
    }

    /**
     * Loads the configuration for a specific widget ID.
     * Includes migration logic for older configuration formats.
     *
     * @param context The application context.
     * @param appWidgetId The ID of the widget to load configuration for.
     * @return The [WidgetConfiguration] or null if not found.
     */
    fun loadConfiguration(context: Context, appWidgetId: Int): WidgetConfiguration? {
        val prefs = context.getSharedPreferences(PREFS_NAME, 0)
        val title = prefs.getString(PREF_TITLE_KEY + appWidgetId, null)

        // Migration logic
        var titleStyle = prefs.getString(PREF_TITLE_STYLE_KEY + appWidgetId, null)
        if (titleStyle == null) {
            if (prefs.contains(PREF_IS_AUTO_TITLE_KEY + appWidgetId)) {
                val isAutoTitle = prefs.getBoolean(PREF_IS_AUTO_TITLE_KEY + appWidgetId, true)
                titleStyle =
                    if (isAutoTitle) "SHORT" else "CUSTOM" // Previous auto title used short codes
            } else {
                titleStyle = "SHORT" // Default for new widgets
            }
        }

        val showIcon = prefs.getBoolean(PREF_SHOW_ICON_KEY + appWidgetId, true)
        val showStops = prefs.getBoolean(PREF_SHOW_STOPS_KEY + appWidgetId, true)
        val fromStation = prefs.getString(PREF_FROM_STATION_KEY + appWidgetId, null)
        val toStation = prefs.getString(PREF_TO_STATION_KEY + appWidgetId, null)
        val alignment = prefs.getString(PREF_ALIGNMENT_KEY + appWidgetId, "START")
        val startTimeNormal = prefs.getInt(PREF_START_NORMAL_KEY + appWidgetId, 270)
        val startTimeReverse = prefs.getInt(PREF_START_REVERSE_KEY + appWidgetId, 720)
        val timeOffset = prefs.getInt(PREF_TIME_OFFSET_KEY + appWidgetId, 0)
        val departureCount = prefs.getInt(PREF_DEPARTURE_COUNT_KEY + appWidgetId, 4)
        val transparency = prefs.getInt(PREF_TRANSPARENCY_KEY + appWidgetId, 128)
        val textColor = prefs.getInt(PREF_TEXT_COLOR_KEY + appWidgetId, Color.WHITE)
        val bgColor = prefs.getInt(PREF_BG_COLOR_KEY + appWidgetId, Color.BLACK)
        val useSystemTextColor =
            prefs.getBoolean(PREF_USE_SYSTEM_TEXT_COLOR_KEY + appWidgetId, false)
        val useSystemBgColor = prefs.getBoolean(PREF_USE_SYSTEM_BG_COLOR_KEY + appWidgetId, false)
        val fontSize = prefs.getInt(PREF_FONT_SIZE_KEY + appWidgetId, 1)

        return if (title != null && fromStation != null && toStation != null && alignment != null) {
            WidgetConfiguration(
                title,
                titleStyle,
                showIcon,
                showStops,
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
                fontSize
            )
        } else {
            null
        }
    }

    /**
     * Deletes the configuration for a specific widget ID.
     *
     * @param context The application context.
     * @param appWidgetId The ID of the widget to delete configuration for.
     */
    fun deleteConfiguration(context: Context, appWidgetId: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, 0).edit()
        prefs.remove(PREF_TITLE_KEY + appWidgetId)
        prefs.remove(PREF_TITLE_STYLE_KEY + appWidgetId)
        prefs.remove(PREF_IS_AUTO_TITLE_KEY + appWidgetId)
        prefs.remove(PREF_SHOW_ICON_KEY + appWidgetId)
        prefs.remove(PREF_SHOW_STOPS_KEY + appWidgetId)
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
        prefs.apply()
    }
}