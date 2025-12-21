package net.bonstio.traintimes

import android.graphics.Color

object WidgetConfigurationDefaults {
    const val TITLE_STYLE = "SHORT"
    const val SHOW_ICON = true
    const val SHOW_STOPS = true // For migration
    const val STATION_STOPS_MODE = "FIRST"
    const val ALIGNMENT = "START"
    const val START_TIME_NORMAL = -1
    const val START_TIME_REVERSE = -1
    const val TIME_OFFSET = 0
    const val DEPARTURE_COUNT = 9
    const val TRANSPARENCY = 128
    const val TEXT_COLOR = Color.WHITE
    const val BG_COLOR = Color.BLACK
    const val USE_SYSTEM_TEXT_COLOR = false
    const val USE_SYSTEM_BG_COLOR = false
    const val FONT_SIZE = 3 // Regular
    const val SHOW_REFRESH_ICON = true
    const val SHOW_SETTINGS_ICON = false
    const val HIDE_PAST_DEPARTURES = false
    const val SHOW_MAPS_ICON = true
    const val SHOW_LAST_UPDATE_TIME = true
    const val SHOW_DIVIDER = true
    const val COMMUTING_MODE = "TIME" // "TIME" or "LOCATION"
    const val FONT_STYLE = "SYSTEM" // "SYSTEM" or "RETRO"
    const val ENABLE_JOURNEY_DURATION_FILTER = false
    const val MAX_JOURNEY_DURATION = 30
    const val USE_NEAREST_STATION_FOR_RETURN = false
}
