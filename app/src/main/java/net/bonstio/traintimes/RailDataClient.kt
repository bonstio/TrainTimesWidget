package net.bonstio.traintimes

import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
        considerNearbyStations: Boolean = false
    ): List<TrainService> {
        val fromStations = StationRepository.getEquivalentStations(fromStation, considerNearbyStations)
        val toStations = if (toStation.isNotEmpty()) {
            StationRepository.getEquivalentStations(toStation, considerNearbyStations)
        } else {
            listOf("")
        }

        if (fromStations.size <= 1 && toStations.size <= 1) {
            val responseBody = fetchDarwinBoard(fromStation, toStation, timeOffset, numRows)
            return parseTrainServices(responseBody, toStation)
        }

        val routePairs = mutableListOf<Pair<String, String>>()
        for (orig in fromStations) {
            for (dest in toStations) {
                if (dest.isEmpty() || !orig.equals(dest, ignoreCase = true)) {
                    routePairs.add(orig to dest)
                }
            }
        }

        val hasMultipleDestinations = toStations.size > 1
        var lastError: Exception? = null

        val resultsPerRoute = coroutineScope {
            routePairs.map { (orig, dest) ->
                async {
                    try {
                        val body = fetchDarwinBoard(orig, dest, timeOffset, numRows)
                        val parsed = parseTrainServices(
                            jsonStr = body,
                            toStation = dest,
                            originCrs = orig,
                            primaryFromCrs = fromStation,
                            hasMultipleDestinations = hasMultipleDestinations
                        )
                        orig to parsed
                    } catch (e: Exception) {
                        Log.w("RailDataClient", "Failed fetching route $orig -> $dest", e)
                        lastError = e
                        null
                    }
                }
            }.awaitAll()
        }

        val successfulResults = resultsPerRoute.filterNotNull()
        if (successfulResults.isEmpty() && lastError != null) {
            throw lastError!!
        }

        return mergeAndSortTrainServices(successfulResults, numRows)
    }

    private suspend fun fetchDarwinBoard(
        fromStation: String,
        toStation: String,
        timeOffset: Int,
        numRows: Int
    ): String {
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
        Log.d("RailDataClient", "Response Status ($fromStation->$toStation): ${response.status}")
        Log.d("RailDataClient", "Response Body length: ${responseBody.length}")
        return responseBody
    }

    fun mergeAndSortTrainServices(
        routeResults: List<Pair<String, List<TrainService>>>,
        maxRows: Int
    ): List<TrainService> {
        val deduplicated = mutableListOf<TrainService>()
        val seenKeys = mutableSetOf<String>()

        for ((orig, services) in routeResults) {
            for (svc in services) {
                val baseDest = svc.destination.replace(Regex("\\s*\\(via [A-Z]{3}\\)$"), "")
                val key = "${orig.uppercase()}_${svc.std}_$baseDest"
                if (seenKeys.add(key)) {
                    deduplicated.add(svc)
                }
            }
        }

        if (deduplicated.isEmpty()) return emptyList()

        val refMinutes = parseTimeToMinutes(deduplicated.first().std) ?: 0
        return deduplicated
            .sortedBy { sortKeyMinutes(it.std, refMinutes) }
            .take(maxRows)
    }

    /**
     * Fetches departure board with details formatted as a JSON array string matching the
     * Worldline Tiger CIS web board schema (used by tiger.html / index.html).
     *
     * @param fromStation Origin CRS code (e.g. "STP")
     * @param toStation Target destination CRS code (e.g. "HPD")
     * @param filterByTo Whether to filter API results to services calling at [toStation]
     * @param numRows Number of departures to retrieve
     * @param considerNearbyStations Whether to also include nearby sister stations
     */
    suspend fun getWebDeparturesJson(
        fromStation: String,
        toStation: String = "",
        filterByTo: Boolean = true,
        numRows: Int = 20,
        considerNearbyStations: Boolean = false
    ): String {
        val fromStations = StationRepository.getEquivalentStations(fromStation, considerNearbyStations)
        val effectiveTo = if (filterByTo) toStation else ""
        val toStations = if (effectiveTo.isNotEmpty()) {
            StationRepository.getEquivalentStations(effectiveTo, considerNearbyStations)
        } else {
            listOf("")
        }

        if (fromStations.size <= 1 && toStations.size <= 1) {
            val responseBody = fetchDarwinBoard(fromStation, effectiveTo, 0, numRows)
            return transformDarwinToWebJson(responseBody, toStation)
        }

        val routePairs = mutableListOf<Pair<String, String>>()
        for (orig in fromStations) {
            for (dest in toStations) {
                if (dest.isEmpty() || !orig.equals(dest, ignoreCase = true)) {
                    routePairs.add(orig to dest)
                }
            }
        }

        val hasMultipleDestinations = toStations.size > 1
        var lastError: Exception? = null

        val jsonArraysPerRoute = coroutineScope {
            routePairs.map { (orig, dest) ->
                async {
                    try {
                        val body = fetchDarwinBoard(orig, dest, 0, numRows)
                        val targetForTransform = if (dest.isNotEmpty()) dest else toStation
                        val arr = JSONArray(
                            transformDarwinToWebJson(
                                jsonStr = body,
                                targetCrs = targetForTransform,
                                originCrs = orig,
                                primaryFromCrs = fromStation,
                                hasMultipleDestinations = hasMultipleDestinations
                            )
                        )
                        orig to arr
                    } catch (e: Exception) {
                        Log.w("RailDataClient", "Failed fetching web route $orig -> $dest", e)
                        lastError = e
                        null
                    }
                }
            }.awaitAll()
        }

        val successfulArrays = jsonArraysPerRoute.filterNotNull()
        if (successfulArrays.isEmpty() && lastError != null) {
            throw lastError!!
        }

        val mergedList = mutableListOf<JSONObject>()
        val seenKeys = mutableSetOf<String>()
        for ((orig, arr) in successfulArrays) {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val due = obj.optString("due")
                val baseDest = obj.optString("destination").replace(Regex("\\s*\\(via [A-Z]{3}\\)$"), "")
                val key = "${orig.uppercase()}_${due}_$baseDest"
                if (seenKeys.add(key)) {
                    mergedList.add(obj)
                }
            }
        }

        val refMinutes = mergedList.firstOrNull()?.optString("due")?.let { parseTimeToMinutes(it) } ?: 0
        val sortedList = mergedList
            .sortedBy { sortKeyMinutes(it.optString("due"), refMinutes) }
            .take(numRows)

        val resultArray = JSONArray()
        for (obj in sortedList) {
            resultArray.put(obj)
        }
        return resultArray.toString()
    }

    fun transformDarwinToWebJson(
        jsonStr: String,
        targetCrs: String,
        originCrs: String = "",
        primaryFromCrs: String = "",
        hasMultipleDestinations: Boolean = false
    ): String {
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

            val isSisterOrigin = originCrs.isNotEmpty() &&
                primaryFromCrs.isNotEmpty() &&
                !originCrs.equals(primaryFromCrs, ignoreCase = true)

            for (serviceObj in allServices) {
                val std = serviceObj.optString("std").ifEmpty { null } ?: continue
                val etd = serviceObj.optString("etd")
                val isCancelled = serviceObj.optBoolean("isCancelled", false)
                val rawPlatform = if (serviceObj.has("platform") && !serviceObj.isNull("platform")) {
                    serviceObj.optString("platform").ifBlank { null }
                } else null

                val platform = if (isSisterOrigin) {
                    if (rawPlatform != null) "${originCrs.uppercase()} $rawPlatform" else originCrs.uppercase()
                } else {
                    rawPlatform
                }

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

                val displayDestName = if (hasMultipleDestinations &&
                    targetCrs.isNotEmpty() &&
                    (destCrs == null || !destCrs.equals(targetCrs, ignoreCase = true))
                ) {
                    "$destName (via ${targetCrs.uppercase()})"
                } else {
                    destName
                }

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
                    put("destination", displayDestName)
                    if (viaText != null) put("via", viaText) else put("via", JSONObject.NULL)
                    if (platform != null) put("platform", platform) else put("platform", JSONObject.NULL)
                    put("expected", expected)
                    if (length != null) put("coaches", length) else put("coaches", JSONObject.NULL)
                    put("isFast", isFast)
                    put("callsAt", callsAt)
                    if (targetCrs.isNotEmpty()) put("targetCrs", targetCrs.uppercase())
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

    fun parseTrainServices(
        jsonStr: String,
        toStation: String,
        originCrs: String = "",
        primaryFromCrs: String = "",
        hasMultipleDestinations: Boolean = false
    ): List<TrainService> {
        val services = mutableListOf<TrainService>()
        val json = JSONObject(jsonStr)

        val trainServices = json.optJSONArray("trainServices")
        if (trainServices != null) {
            for (i in 0 until trainServices.length()) {
                val serviceObj = trainServices.optJSONObject(i) ?: continue
                try {
                    readService(serviceObj, toStation, originCrs, primaryFromCrs, hasMultipleDestinations)?.let { services.add(it) }
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
                    readService(serviceObj, toStation, originCrs, primaryFromCrs, hasMultipleDestinations)?.let { services.add(it) }
                } catch (e: Exception) {
                    Log.e("RailDataClient", "Error parsing bus service", e)
                }
            }
        }

        Log.d("RailDataClient", "Parsed ${services.size} services")
        return services
    }

    private fun readService(
        serviceObj: JSONObject,
        toStation: String,
        originCrs: String = "",
        primaryFromCrs: String = "",
        hasMultipleDestinations: Boolean = false
    ): TrainService? {
        val std = serviceObj.optString("std").ifEmpty { null }
        val etd = serviceObj.optString("etd").ifEmpty { null }
        val rawPlatform = if (serviceObj.has("platform") && !serviceObj.isNull("platform")) {
            serviceObj.optString("platform").ifBlank { null }
        } else {
            null
        }
        val isCancelled = serviceObj.optBoolean("isCancelled", false)

        val isSisterOrigin = originCrs.isNotEmpty() &&
            primaryFromCrs.isNotEmpty() &&
            !originCrs.equals(primaryFromCrs, ignoreCase = true)

        val platform = if (isSisterOrigin) {
            if (rawPlatform != null) "${originCrs.uppercase()} Pl. $rawPlatform" else originCrs.uppercase()
        } else {
            rawPlatform
        }

        val rawDestination = readDestination(serviceObj)
        val destCrs = readDestinationCrs(serviceObj)

        val destination = if (rawDestination != null &&
            hasMultipleDestinations &&
            toStation.isNotEmpty() &&
            (destCrs == null || !destCrs.equals(toStation, ignoreCase = true))
        ) {
            "$rawDestination (via ${toStation.uppercase()})"
        } else {
            rawDestination
        }

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

    private fun parseTimeToMinutes(timeStr: String): Int? {
        return try {
            val (h, m) = timeStr.split(":").map { it.toInt() }
            h * 60 + m
        } catch (_: Exception) {
            null
        }
    }

    private fun sortKeyMinutes(timeStr: String, referenceMinutes: Int): Int {
        val mins = parseTimeToMinutes(timeStr) ?: return Int.MAX_VALUE
        return when {
            mins - referenceMinutes < -360 -> mins + 24 * 60
            mins - referenceMinutes > 1080 -> mins - 24 * 60
            else -> mins
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

    private fun readDestinationCrs(serviceObj: JSONObject): String? {
        val destinationArray = serviceObj.optJSONArray("destination") ?: return null
        if (destinationArray.length() == 0) return null
        val locationObj = destinationArray.optJSONObject(0) ?: return null
        return locationObj.optString("crs").ifEmpty { null }
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
