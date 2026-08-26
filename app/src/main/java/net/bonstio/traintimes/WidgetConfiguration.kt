package net.bonstio.traintimes

import android.content.Context
import android.graphics.Color

/**
 * Data class representing the configuration of a widget instance.
 */
data class WidgetConfiguration(
    val title: String,
    val titleStyle: String = WidgetConfigurationDefaults.TITLE_STYLE,
    val showIcon: Boolean = WidgetConfigurationDefaults.SHOW_ICON,
    @Deprecated("Use stationStopsMode") val showStops: Boolean = WidgetConfigurationDefaults.SHOW_STOPS,
    val stationStopsMode: String = WidgetConfigurationDefaults.STATION_STOPS_MODE,
    val fromStation: String,
    val toStation: String,
    val alignment: String = WidgetConfigurationDefaults.ALIGNMENT,
    val startTimeNormal: Int = WidgetConfigurationDefaults.START_TIME_NORMAL,
    val startTimeReverse: Int = WidgetConfigurationDefaults.START_TIME_REVERSE,
    val timeOffset: Int = WidgetConfigurationDefaults.TIME_OFFSET,
    val departureCount: Int = WidgetConfigurationDefaults.DEPARTURE_COUNT,
    val transparency: Int = WidgetConfigurationDefaults.TRANSPARENCY,
    val textColor: Int = WidgetConfigurationDefaults.TEXT_COLOR,
    val bgColor: Int = WidgetConfigurationDefaults.BG_COLOR,
    val useSystemTextColor: Boolean = WidgetConfigurationDefaults.USE_SYSTEM_TEXT_COLOR,
    val useSystemBgColor: Boolean = WidgetConfigurationDefaults.USE_SYSTEM_BG_COLOR,
    val fontSize: Int = WidgetConfigurationDefaults.FONT_SIZE, // 0-6 scale
    val showRefreshIcon: Boolean = WidgetConfigurationDefaults.SHOW_REFRESH_ICON,
    val showSettingsIcon: Boolean = WidgetConfigurationDefaults.SHOW_SETTINGS_ICON,
    val showGpsIcon: Boolean = WidgetConfigurationDefaults.SHOW_GPS_ICON,
    val hidePastDepartures: Boolean = WidgetConfigurationDefaults.HIDE_PAST_DEPARTURES,
    val showMapsIcon: Boolean = WidgetConfigurationDefaults.SHOW_MAPS_ICON,
    val showLastUpdateTime: Boolean = WidgetConfigurationDefaults.SHOW_LAST_UPDATE_TIME,
    val showDivider: Boolean = WidgetConfigurationDefaults.SHOW_DIVIDER,
    val commutingMode: String = WidgetConfigurationDefaults.COMMUTING_MODE,
    val fontStyle: String = WidgetConfigurationDefaults.FONT_STYLE,
    val enableJourneyDurationFilter: Boolean = WidgetConfigurationDefaults.ENABLE_JOURNEY_DURATION_FILTER,
    val maxJourneyDuration: Int = WidgetConfigurationDefaults.MAX_JOURNEY_DURATION,
    val useNearestStationForReturn: Boolean = WidgetConfigurationDefaults.USE_NEAREST_STATION_FOR_RETURN,
    val showCommuteNotifications: Boolean = WidgetConfigurationDefaults.SHOW_COMMUTE_NOTIFICATIONS,
    val forceShowNotification: Boolean = WidgetConfigurationDefaults.FORCE_SHOW_NOTIFICATION
)
