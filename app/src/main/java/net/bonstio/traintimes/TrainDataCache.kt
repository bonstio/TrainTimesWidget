package net.bonstio.traintimes

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Helper object to cache TrainService data to SharedPreferences to allow widget UI updates without re-fetching.
 */
object TrainDataCache {
    private const val PREFS_CACHE = "net.bonstio.traintimes.cache"
    private const val KEY_SERVICES = "services_"

    fun saveServices(context: Context, appWidgetId: Int, services: List<TrainService>) {
        val prefs = context.getSharedPreferences(PREFS_CACHE, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        services.forEach { service ->
            val jsonObject = JSONObject()
            jsonObject.put("std", service.std)
            jsonObject.put("destination", service.destination)
            jsonObject.put("platform", service.platform)
            jsonObject.put("status", service.status)
            val pointsArray = JSONArray()
            service.subsequentCallingPoints.forEach { pointsArray.put(it) }
            jsonObject.put("subsequentCallingPoints", pointsArray)
            jsonArray.put(jsonObject)
        }
        prefs.edit().putString(KEY_SERVICES + appWidgetId, jsonArray.toString()).apply()
    }

    fun loadServices(context: Context, appWidgetId: Int): List<TrainService>? {
        val prefs = context.getSharedPreferences(PREFS_CACHE, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_SERVICES + appWidgetId, null) ?: return null
        
        return try {
            val services = mutableListOf<TrainService>()
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val std = obj.getString("std")
                val destination = obj.getString("destination")
                val platform = if (obj.has("platform") && !obj.isNull("platform")) obj.getString("platform") else null
                val status = obj.getString("status")
                val pointsArray = obj.getJSONArray("subsequentCallingPoints")
                val points = mutableListOf<String>()
                for (j in 0 until pointsArray.length()) {
                    points.add(pointsArray.getString(j))
                }
                services.add(TrainService(std, destination, platform, status, points))
            }
            services
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}