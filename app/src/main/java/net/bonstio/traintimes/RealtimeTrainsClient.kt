package net.bonstio.traintimes

import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import org.json.JSONObject

/**
 * Client for interacting with the Realtime Trains API.
 * It fetches train departure information using REST JSON requests.
 *
 * @property apiKey The API key for authentication.
 */
class RealtimeTrainsClient(private val apiKey: String) {

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
        numRows: Int = 5
    ): List<TrainService> {
        val filterStr = if (toStation.isNotEmpty()) "&filterCrs=$toStation&filterType=to" else ""
        
        val url = "https://api1.raildata.org.uk/1010-live-departure-board-dep1_2/LDBWS/api/20220120/GetDepartureBoard/${fromStation}?numRows=$numRows&timeOffset=$timeOffset$filterStr&timeWindow=120"
        
        Log.d("RealtimeTrainsClient", "Requesting: $url")
        val response: HttpResponse =
            client.get(url) {
                header("x-apikey", apiKey)
            }

        val responseBody = response.bodyAsText()
        Log.d("RealtimeTrainsClient", "Response Status: ${response.status}")
        Log.d("RealtimeTrainsClient", "Response Body length: ${responseBody.length}")

        return parseTrainServicesJson(responseBody, toStation)
    }

    private fun parseTrainServicesJson(jsonString: String, toStation: String): List<TrainService> {
        val services = mutableListOf<TrainService>()
        try {
            val jsonObject = JSONObject(jsonString)
            if (jsonObject.has("trainServices") && !jsonObject.isNull("trainServices")) {
                val trainServicesArray = jsonObject.getJSONArray("trainServices")
                for (i in 0 until trainServicesArray.length()) {
                    val serviceObj = trainServicesArray.getJSONObject(i)
                    readServiceJson(serviceObj, toStation)?.let { services.add(it) }
                }
            }
        } catch (e: Exception) {
            Log.e("RealtimeTrainsClient", "Error parsing JSON response", e)
        }
        Log.d("RealtimeTrainsClient", "Parsed ${services.size} services")
        return services
    }

    private fun readServiceJson(serviceObj: JSONObject, toStation: String): TrainService? {
        try {
            val std = serviceObj.optString("std", "")
            val etd = serviceObj.optString("etd", "")
            val platform = serviceObj.optString("platform", "")
            val isCancelled = serviceObj.optBoolean("isCancelled", false)

            var destination: String? = null
            if (serviceObj.has("destination") && !serviceObj.isNull("destination")) {
                val destArray = serviceObj.getJSONArray("destination")
                if (destArray.length() > 0) {
                    destination = destArray.getJSONObject(0).optString("locationName", null)
                }
            }

            val callingPoints = mutableListOf<String>()
            var arrivalTime: String? = null

            if (serviceObj.has("subsequentCallingPoints") && !serviceObj.isNull("subsequentCallingPoints")) {
                val subsequentPoints = serviceObj.getJSONArray("subsequentCallingPoints")
                if (subsequentPoints.length() > 0) {
                    val firstList = subsequentPoints.getJSONObject(0)
                    if (firstList.has("callingPoint") && !firstList.isNull("callingPoint")) {
                        val pointsArray = firstList.getJSONArray("callingPoint")
                        for (i in 0 until pointsArray.length()) {
                            val pointObj = pointsArray.getJSONObject(i)
                            val locName = pointObj.optString("locationName", null)
                            val crs = pointObj.optString("crs", null)
                            val st = pointObj.optString("st", null)
                            
                            if (locName != null) {
                                callingPoints.add(locName)
                            }

                            if (crs != null && crs.equals(toStation, ignoreCase = true)) {
                                arrivalTime = st
                            }
                        }
                    }
                }
            }

            val finalStd = if (std.isEmpty()) null else std
            if (finalStd != null && destination != null) {
                val status = when {
                    isCancelled -> "Cancelled"
                    etd.isNotEmpty() && etd != "On time" && etd != finalStd -> "Exp $etd"
                    else -> "On time"
                }

                var duration: Int? = null
                if (arrivalTime != null) {
                    duration = calculateDuration(finalStd, arrivalTime)
                }

                return TrainService(finalStd, destination, if (platform.isEmpty()) null else platform, status, callingPoints, duration)
            } else {
                Log.w("RealtimeTrainsClient", "Incomplete service data: std=$std, dest=$destination")
                return null
            }

        } catch (e: Exception) {
            Log.e("RealtimeTrainsClient", "Error parsing individual service", e)
            return null
        }
    }

    private fun calculateDuration(start: String, end: String): Int? {
        try {
            val (h1, m1) = start.split(":").map { it.toInt() }
            val (h2, m2) = end.split(":").map { it.toInt() }
            var diff = (h2 * 60 + m2) - (h1 * 60 + m1)
            if (diff < 0) diff += 24 * 60 // Next day
            return diff
        } catch (e: Exception) {
            return null
        }
    }
}
