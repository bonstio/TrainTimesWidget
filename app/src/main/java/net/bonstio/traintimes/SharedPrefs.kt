package net.bonstio.traintimes

/**
 * Constants used for SharedPreferences keys and names.
 */

/**
 * Name of the SharedPreferences file for the widget application.
 * FIX: Updated to match the one used in TrainTimesWidgetProvider and other files.
 */
const val PREFS_NAME = "net.bonstio.traintimes.widget"

/**
 * Key for storing the Rail Data API key.
 */
const val PREF_API_KEY = "api_key"

/**
 * Key for storing the chosen API key source ("DEFAULT" or "CUSTOM").
 */
const val PREF_API_KEY_SOURCE = "api_key_source"
const val API_KEY_SOURCE_DEFAULT = "DEFAULT"
const val API_KEY_SOURCE_CUSTOM = "CUSTOM"

/**
 * Key for storing the widget update frequency in minutes (0 = manual only).
 */
const val PREF_UPDATE_FREQUENCY = "update_frequency"

/**
 * Key for storing the expanded state of calling points (appended with widgetId).
 */
const val PREF_IS_EXPANDED = "is_expanded_"

/**
 * Key for storing the transparency preference (deprecated/unused in new logic, moved to per-widget config).
 */
const val PREF_TRANSPARENCY = "transparency"

/**
 * Key for storing the text color preference (deprecated/unused in new logic, moved to per-widget config).
 */
const val PREF_TEXT_COLOR = "text_color"

/**
 * Key for storing the background color preference (deprecated/unused in new logic, moved to per-widget config).
 */
const val PREF_BG_COLOR = "bg_color"

/**
 * Key for tracking if the prominent disclosure dialog has been shown.
 */
const val PREF_PROMINENT_DISCLOSURE_SHOWN = "prominent_disclosure_shown"

/**
 * Key prefix for tracking if the user dismissed the shared key warning banner on a specific widget (legacy boolean).
 */
const val PREF_DISMISSED_SHARED_KEY_BANNER = "dismissed_shared_key_banner_"

/**
 * Key prefix for tracking the timestamp when the user dismissed the shared key warning banner on a specific widget.
 */
const val PREF_DISMISSED_SHARED_KEY_BANNER_TIME = "dismissed_shared_key_banner_time_"

/**
 * Duration for which the shared key warning banner stays dismissed: 5 weeks (35 days).
 */
const val SHARED_KEY_BANNER_DISMISS_DURATION_MS = 35L * 24 * 60 * 60 * 1000L

