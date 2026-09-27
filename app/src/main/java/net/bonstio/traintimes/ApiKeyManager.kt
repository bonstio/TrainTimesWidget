package net.bonstio.traintimes

import android.content.Context

/**
 * Manages resolving the active API key to use for requests.
 * Supports a built-in default shared key or a user-provided custom key.
 */
object ApiKeyManager {

    /**
     * Retrieves the effective API key based on user preference:
     * - If [PREF_API_KEY_SOURCE] is "CUSTOM", returns user-provided key.
     * - Otherwise ("DEFAULT"), returns BuildConfig.DEFAULT_RAIL_DATA_API_KEY (if non-empty),
     *   falling back to user key if available.
     */
    fun getApiKey(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val source = prefs.getString(PREF_API_KEY_SOURCE, API_KEY_SOURCE_DEFAULT)
        val userKey = prefs.getString(PREF_API_KEY, null)?.trim()

        return if (source == API_KEY_SOURCE_CUSTOM) {
            userKey.takeIf { !it.isNullOrEmpty() }
        } else {
            val defaultKey = BuildConfig.DEFAULT_RAIL_DATA_API_KEY.trim()
            if (defaultKey.isNotEmpty()) {
                defaultKey
            } else {
                userKey.takeIf { !it.isNullOrEmpty() }
            }
        }
    }

    /**
     * Checks if a valid API key (either default or custom) is currently configured.
     */
    fun hasApiKey(context: Context): Boolean {
        return !getApiKey(context).isNullOrEmpty()
    }
}
