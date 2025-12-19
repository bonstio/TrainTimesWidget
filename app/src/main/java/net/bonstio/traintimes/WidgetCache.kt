package net.bonstio.traintimes

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object WidgetCache {
    private const val PREFS_NAME = "net.bonstio.traintimes.widget"
    private const val PREF_CACHE_PREFIX = "cache_"

    fun saveServices(context: Context, appWidgetId: Int, services: List<TrainService>) {
        val jsonArray = JSONArray()
        for (service in services) {
            val jsonObj = JSONObject()
            jsonObj.put("std", service.std)
            jsonObj.put("destination", service.destination)
            jsonObj.put("platform", service.platform)
            jsonObj.put("status", service.status)
            jsonObj.put("subsequentCallingPoints", JSONArray(service.subsequentCallingPoints))
            if (service.duration != null) {
                jsonObj.put("duration", service.duration)
            }
            jsonArray.put(jsonObj)
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Use commit() to ensure data is written before notifying the widget to update
        prefs.edit().putString(PREF_CACHE_PREFIX + appWidgetId, jsonArray.toString()).commit()
    }

    fun loadServices(context: Context, appWidgetId: Int): List<TrainService> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(PREF_CACHE_PREFIX + appWidgetId, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<TrainService>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val callingPointsJson = obj.getJSONArray("subsequentCallingPoints")
                val callingPoints = mutableListOf<String>()
                for (j in 0 until callingPointsJson.length()) {
                    callingPoints.add(callingPointsJson.getString(j))
                }
                
                val duration = if (obj.has("duration")) obj.getInt("duration") else null

                list.add(TrainService(
                    std = obj.getString("std"),
                    destination = obj.getString("destination"),
                    platform = if (obj.has("platform") && !obj.isNull("platform")) obj.getString("platform") else null,
                    status = obj.getString("status"),
                    subsequentCallingPoints = callingPoints,
                    duration = duration
                ))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }
}