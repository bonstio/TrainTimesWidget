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
