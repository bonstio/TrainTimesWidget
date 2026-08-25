package net.bonstio.traintimes

import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
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
