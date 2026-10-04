package net.bonstio.traintimes

import android.content.Context
import androidx.core.content.edit

/**
 * Manages resolving the active API key to use for requests.
 * Supports a built-in default shared key or a user-provided custom key.
 */
object ApiKeyManager {

    /**
     * Resolves and migrates the API key source:
     * - If [PREF_API_KEY_SOURCE] is not set, checks if an existing [PREF_API_KEY] is present.
     *   If an existing key exists, migrates to "CUSTOM" so users updating from older versions
     *   keep their own key. Otherwise defaults to "DEFAULT".
     */
    fun getApiKeySource(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(PREF_API_KEY_SOURCE)) {
            val userKey = prefs.getString(PREF_API_KEY, null)?.trim()
            val migratedSource = if (!userKey.isNullOrEmpty()) {
                API_KEY_SOURCE_CUSTOM
            } else {
                API_KEY_SOURCE_DEFAULT
            }
            prefs.edit { putString(PREF_API_KEY_SOURCE, migratedSource) }
            return migratedSource
        }
        return prefs.getString(PREF_API_KEY_SOURCE, API_KEY_SOURCE_DEFAULT) ?: API_KEY_SOURCE_DEFAULT
    }

    /**
     * Retrieves the effective API key based on user preference:
     * - If [PREF_API_KEY_SOURCE] is "CUSTOM", returns user-provided key.
     * - Otherwise ("DEFAULT"), returns BuildConfig.DEFAULT_RAIL_DATA_API_KEY (if non-empty),
     *   falling back to user key if available.
     */
    fun getApiKey(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val source = getApiKeySource(context)
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
