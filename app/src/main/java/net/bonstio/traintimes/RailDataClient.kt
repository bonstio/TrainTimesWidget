package net.bonstio.traintimes

import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * Client for interacting with the Rail Data API (LDBWS REST).
 * It fetches train departure information using JSON requests.
 *
 * @property apiKey The API key for authentication with the Rail Data API.
 */
class RailDataClient(private val apiKey: String) {

    private val client = HttpClient(Android) {
        engine {
            connectTimeout = 10_000
            socketTimeout = 10_000
        }
        expectSuccess = true
    }

    /**
     * Fetches the next train services between two stations.
     *
     * @param fromStation The CRS code of the departure station.
     * @param toStation The CRS code of the arrival station.
     * @param timeOffset An offset in minutes to apply to the current time for the query.
     * @param numRows The number of services to retrieve.
     * @return A list of [TrainService] objects representing the departures.
     * @throws Exception If the network request fails or parsing errors occur.
     */
    suspend fun getNextTrain(
        fromStation: String,
        toStation: String,
        timeOffset: Int = 0,
        numRows: Int = 5,
    ): List<TrainService> {
        val endpoint = "https://api1.raildata.org.uk/1010-live-departure-board-dep1_2/LDBWS/api/20220120/GetDepBoardWithDetails/$fromStation"

        val response: HttpResponse = client.get(endpoint) {
            header("x-apikey", apiKey)
            parameter("numRows", numRows)
            parameter("timeOffset", timeOffset)
            parameter("timeWindow", 120)
            if (toStation.isNotEmpty()) {
                parameter("filterCrs", toStation)
                parameter("filterType", "to")
            }
        }

        val responseBody = response.bodyAsText()
        Log.d("RailDataClient", "Response Status: ${response.status}")
        Log.d("RailDataClient", "Response Body length: ${responseBody.length}")

        return parseTrainServices(responseBody, toStation)
    }

    /**
     * Fetches departure board with details formatted as a JSON array string matching the
     * Worldline Tiger CIS web board schema (used by tiger.html / index.html).
     *
     * @param fromStation Origin CRS code (e.g. "STP")
     * @param toStation Target destination CRS code (e.g. "HPD")
     * @param filterByTo Whether to filter API results to services calling at [toStation]
     * @param numRows Number of departures to retrieve
     */
    suspend fun getWebDeparturesJson(
        fromStation: String,
        toStation: String = "",
        filterByTo: Boolean = true,
        numRows: Int = 20
    ): String {
        val endpoint = "https://api1.raildata.org.uk/1010-live-departure-board-dep1_2/LDBWS/api/20220120/GetDepBoardWithDetails/$fromStation"

        val response: HttpResponse = client.get(endpoint) {
            header("x-apikey", apiKey)
            parameter("numRows", numRows)
            parameter("timeOffset", 0)
            parameter("timeWindow", 120)
            if (toStation.isNotEmpty() && filterByTo) {
                parameter("filterCrs", toStation)
                parameter("filterType", "to")
            }
        }

        val responseBody = response.bodyAsText()
        return transformDarwinToWebJson(responseBody, toStation)
    }

    fun transformDarwinToWebJson(jsonStr: String, targetCrs: String): String {
        val jsonArray = JSONArray()
        try {
            val root = JSONObject(jsonStr)
            val trainServices = root.optJSONArray("trainServices") ?: JSONArray()
            val busServices = root.optJSONArray("busServices") ?: JSONArray()

            val allServices = mutableListOf<JSONObject>()
            for (i in 0 until trainServices.length()) {
                trainServices.optJSONObject(i)?.let { allServices.add(it) }
            }
            for (i in 0 until busServices.length()) {
                busServices.optJSONObject(i)?.let { allServices.add(it) }
            }

            for (serviceObj in allServices) {
                val std = serviceObj.optString("std").ifEmpty { null } ?: continue
                val etd = serviceObj.optString("etd")
                val isCancelled = serviceObj.optBoolean("isCancelled", false)
                val platform = if (serviceObj.has("platform") && !serviceObj.isNull("platform")) {
                    serviceObj.optString("platform").ifBlank { null }
                } else null

                val length = if (serviceObj.has("length") && !serviceObj.isNull("length")) {
                    serviceObj.optInt("length", 0).takeIf { it > 0 }
                } else null

                var destName: String? = null
                var viaText: String? = null
                var destCrs: String? = null
                val destArray = serviceObj.optJSONArray("destination")
                if (destArray != null && destArray.length() > 0) {
                    val destObj = destArray.optJSONObject(0)
                    if (destObj != null) {
                        destName = destObj.optString("locationName").ifEmpty { null }
                        destCrs = destObj.optString("crs").ifEmpty { null }
                        val rawVia = destObj.optString("via").ifEmpty { null }
                        if (!rawVia.isNullOrBlank()) {
                            viaText = rawVia.replace(Regex("(?i)^via\\s+"), "").trim()
                        }
                    }
                }
                if (destName == null) continue

                val expected = when {
                    isCancelled || etd.equals("Cancelled", ignoreCase = true) -> "Cancelled"
                    etd.equals("On time", ignoreCase = true) -> "On time"
                    etd.equals("Delayed", ignoreCase = true) -> "Delayed"
                    etd.isNotEmpty() && etd != std -> {
                        if (etd.matches(Regex("\\d{1,2}:\\d{2}"))) "Exp $etd" else etd
                    }
                    else -> "On time"
                }

                val callingPoints = JSONArray()
                val callsAt = JSONArray()
                var targetArr: String? = null
                var targetStopIdx: Int? = null

                val subsequentCallingPoints = serviceObj.optJSONArray("subsequentCallingPoints")
                if (subsequentCallingPoints != null) {
                    for (i in 0 until subsequentCallingPoints.length()) {
                        val callingPointListObj = subsequentCallingPoints.optJSONObject(i) ?: continue
                        val callingPointArray = callingPointListObj.optJSONArray("callingPoint") ?: continue
                        for (j in 0 until callingPointArray.length()) {
                            val pointObj = callingPointArray.optJSONObject(j) ?: continue
                            val locationName = pointObj.optString("locationName").ifEmpty { null } ?: continue
                            val crs = pointObj.optString("crs")
                            val st = pointObj.optString("st").ifEmpty { null } ?: ""

                            val cpObj = JSONObject().apply {
                                put("name", locationName)
                                put("crs", crs)
                                put("time", st)
                            }
                            callingPoints.put(cpObj)
                            callsAt.put(crs)

                            if (targetCrs.isNotEmpty() && crs.equals(targetCrs, ignoreCase = true) && targetArr == null) {
                                targetArr = st.ifEmpty { null }
                                targetStopIdx = callingPoints.length() - 1
                            }
                        }
                    }
                }

                if (!destCrs.isNullOrEmpty()) {
                    var alreadyInCallsAt = false
                    for (k in 0 until callsAt.length()) {
                        if (callsAt.optString(k).equals(destCrs, ignoreCase = true)) {
                            alreadyInCallsAt = true
                            break
                        }
                    }
                    if (!alreadyInCallsAt) {
                        callsAt.put(destCrs)
                    }
                    if (targetCrs.isNotEmpty() && destCrs.equals(targetCrs, ignoreCase = true) && targetArr == null) {
                        if (callingPoints.length() > 0) {
                            val lastObj = callingPoints.optJSONObject(callingPoints.length() - 1)
                            targetArr = lastObj?.optString("time")?.ifEmpty { null }
                            targetStopIdx = callingPoints.length() - 1
                        }
                    }
                }

                val duration = if (targetArr != null) calculateDuration(std, targetArr) else null
                val isFast = if (targetStopIdx != null) {
                    targetStopIdx <= 2
                } else {
                    callingPoints.length() <= 4
                }

                val transformedObj = JSONObject().apply {
                    put("due", std)
                    put("destination", destName)
                    if (viaText != null) put("via", viaText) else put("via", JSONObject.NULL)
                    if (platform != null) put("platform", platform) else put("platform", JSONObject.NULL)
                    put("expected", expected)
                    if (length != null) put("coaches", length) else put("coaches", JSONObject.NULL)
                    put("isFast", isFast)
                    put("callsAt", callsAt)
                    if (targetArr != null) put("targetArr", targetArr) else put("targetArr", JSONObject.NULL)
                    if (duration != null) put("durationMin", duration) else put("durationMin", JSONObject.NULL)
                    put("callingPoints", callingPoints)
                }

                jsonArray.put(transformedObj)
            }
        } catch (e: Exception) {
            Log.e("RailDataClient", "Error transforming Darwin JSON to WebBoard schema", e)
        }
        return jsonArray.toString()
    }

    fun parseTrainServices(jsonStr: String, toStation: String): List<TrainService> {
        val services = mutableListOf<TrainService>()
        val json = JSONObject(jsonStr)

        val trainServices = json.optJSONArray("trainServices")
        if (trainServices != null) {
            for (i in 0 until trainServices.length()) {
                val serviceObj = trainServices.optJSONObject(i) ?: continue
                try {
                    readService(serviceObj, toStation)?.let { services.add(it) }
                } catch (e: Exception) {
                    Log.e("RailDataClient", "Error parsing train service", e)
                }
            }
        }

        val busServices = json.optJSONArray("busServices")
        if (busServices != null) {
            for (i in 0 until busServices.length()) {
                val serviceObj = busServices.optJSONObject(i) ?: continue
                try {
                    readService(serviceObj, toStation)?.let { services.add(it) }
                } catch (e: Exception) {
                    Log.e("RailDataClient", "Error parsing bus service", e)
                }
            }
        }

        Log.d("RailDataClient", "Parsed ${services.size} services")
        return services
    }

    private fun readService(serviceObj: JSONObject, toStation: String): TrainService? {
        val std = serviceObj.optString("std").ifEmpty { null }
        val etd = serviceObj.optString("etd").ifEmpty { null }
        val platform = if (serviceObj.has("platform") && !serviceObj.isNull("platform")) {
            serviceObj.optString("platform").ifBlank { null }
        } else {
            null
        }
        val isCancelled = serviceObj.optBoolean("isCancelled", false)

        val destination = readDestination(serviceObj)

        val (callingPoints, arrivalTime) = readCallingPoints(serviceObj, toStation)

        return if ((std != null) && (destination != null)) {
            val status = when {
                isCancelled || etd.equals("Cancelled", ignoreCase = true) -> "Cancelled"
                etd != null && !etd.equals("On time", ignoreCase = true) && etd != std -> "Exp $etd"
                else -> "On time"
            }

            val duration = arrivalTime?.let { calculateDuration(std, it) }

            TrainService(std, destination, platform, status, callingPoints, duration)
        } else {
            Log.w("RailDataClient", "Incomplete service data: std=$std, dest=$destination")
            null
        }
    }

    private fun calculateDuration(start: String, end: String): Int? {
        return try {
            val (h1, m1) = start.split(":").map { it.toInt() }
            val (h2, m2) = end.split(":").map { it.toInt() }
            var diff = (h2 * 60 + m2) - (h1 * 60 + m1)
            if (diff < 0) diff += 24 * 60 // Next day
            diff
        } catch (_: Exception) {
            null
        }
    }

    private fun readDestination(serviceObj: JSONObject): String? {
        val destinationArray = serviceObj.optJSONArray("destination") ?: return null
        if (destinationArray.length() == 0) return null
        val locationObj = destinationArray.optJSONObject(0) ?: return null
        return locationObj.optString("locationName").ifEmpty { null }
    }

    private fun readCallingPoints(serviceObj: JSONObject, targetCrs: String): Pair<List<String>, String?> {
        val callingPoints = mutableListOf<String>()
        var foundArrivalTime: String? = null

        val subsequentCallingPoints = serviceObj.optJSONArray("subsequentCallingPoints") ?: return Pair(callingPoints, null)
        for (i in 0 until subsequentCallingPoints.length()) {
            val callingPointListObj = subsequentCallingPoints.optJSONObject(i) ?: continue
            val callingPointArray = callingPointListObj.optJSONArray("callingPoint") ?: continue
            for (j in 0 until callingPointArray.length()) {
                val pointObj = callingPointArray.optJSONObject(j) ?: continue
                val locationName = pointObj.optString("locationName").ifEmpty { null }
                val crs = pointObj.optString("crs")
                val st = pointObj.optString("st").ifEmpty { null }

                if (locationName != null) {
                    callingPoints.add(locationName)
                }
                if (targetCrs.isNotEmpty() && crs.equals(targetCrs, ignoreCase = true) && st != null && foundArrivalTime == null) {
                    foundArrivalTime = st
                }
            }
        }
        return Pair(callingPoints, foundArrivalTime)
    }
}
