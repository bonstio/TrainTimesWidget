package net.bonstio.traintimes

import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/**
 * Client for interacting with the National Rail Darwin OpenLDBWS API.
 * It fetches train departure information using SOAP requests.
 *
 * @property apiKey The API key for authentication with the National Rail API.
 */
class NationalRailClient(private val apiKey: String) {

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
        val filterCrsXml = if (toStation.isNotEmpty()) {
            """
            <ldb:filterCrs>$toStation</ldb:filterCrs>
            <ldb:filterType>to</ldb:filterType>
            """.trimIndent()
        } else {
            ""
        }

        val requestBody = """
            <x:Envelope xmlns:x="http://schemas.xmlsoap.org/soap/envelope/" xmlns:ldb="http://thalesgroup.com/RTTI/2017-10-01/ldb/" xmlns:typ4="http://thalesgroup.com/RTTI/2013-11-28/Token/types">
                <x:Header>
                    <typ4:AccessToken><typ4:TokenValue>$apiKey</typ4:TokenValue></typ4:AccessToken>
                </x:Header>
                <x:Body>
                    <ldb:GetDepBoardWithDetailsRequest>
                        <ldb:numRows>$numRows</ldb:numRows>
                        <ldb:crs>$fromStation</ldb:crs>
                        <ldb:timeOffset>$timeOffset</ldb:timeOffset>
                        $filterCrsXml
                        <ldb:timeWindow>120</ldb:timeWindow>
                    </ldb:GetDepBoardWithDetailsRequest>
                </x:Body>
            </x:Envelope>
        """.trimIndent()

        Log.d("NationalRailClient", "Request Body: $requestBody")

        val response: HttpResponse =
            client.post("https://lite.realtime.nationalrail.co.uk/OpenLDBWS/ldb11.asmx") {
                contentType(ContentType.Text.Xml.withParameter("charset", "utf-8"))
                header(HttpHeaders.CacheControl, "no-cache")
                setBody(requestBody)
            }

        val responseBody = response.bodyAsText()
        Log.d("NationalRailClient", "Response Status: ${response.status}")
        Log.d("NationalRailClient", "Response Body length: ${responseBody.length}")

        return parseTrainServices(responseBody, toStation)
    }

    private fun parseTrainServices(xml: String, toStation: String): List<TrainService> {
        val services = mutableListOf<TrainService>()
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "service") {
                try {
                    readService(parser, toStation)?.let { services.add(it) }
                } catch (e: Exception) {
                    Log.e("NationalRailClient", "Error parsing service", e)
                }
            }
            eventType = parser.next()
        }
        Log.d("NationalRailClient", "Parsed ${services.size} services")
        return services
    }

    private fun readService(parser: XmlPullParser, toStation: String): TrainService? {
        // parser is at <service>
        var std: String? = null
        var destination: String? = null
        var platform: String? = null
        var etd: String? = null
        var isCancelled = false
        val callingPoints = mutableListOf<String>()
        var arrivalTime: String? = null

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            when (parser.name) {
                "std" -> std = parser.nextText()
                "etd" -> etd = parser.nextText()
                "platform" -> platform = parser.nextText()
                "isCancelled" -> isCancelled = parser.nextText().toBoolean()
                "destination" -> destination = readDestination(parser)
                "subsequentCallingPoints" -> {
                    val result = readCallingPoints(parser, toStation)
                    callingPoints.addAll(result.first)
                    if (result.second != null) {
                        arrivalTime = result.second
                    }
                }
                else -> skip(parser)
            }
        }

        return if (std != null && destination != null) {
            val status = when {
                isCancelled -> "Cancelled"
                etd != null && etd != "On time" && etd != std -> "Exp $etd"
                else -> "On time"
            }
            
            var duration: Int? = null
            if (arrivalTime != null) {
                duration = calculateDuration(std, arrivalTime)
            }
            
            TrainService(std, destination, platform, status, callingPoints, duration)
        } else {
            Log.w("NationalRailClient", "Incomplete service data: std=$std, dest=$destination")
            null
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

    private fun readDestination(parser: XmlPullParser): String? {
        // parser is at <destination>
        var locationName: String? = null
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            
            if (parser.name == "location") {
                while (parser.next() != XmlPullParser.END_TAG) {
                    if (parser.eventType != XmlPullParser.START_TAG) continue
                    
                    if (parser.name == "locationName") {
                        locationName = parser.nextText()
                    } else {
                        skip(parser)
                    }
                }
            } else {
                skip(parser)
            }
        }
        return locationName
    }

    private fun readCallingPoints(parser: XmlPullParser, targetCrs: String): Pair<List<String>, String?> {
        // parser is at <subsequentCallingPoints>
        val callingPoints = mutableListOf<String>()
        var foundArrivalTime: String? = null
        
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            
            if (parser.name == "callingPointList") {
                while (parser.next() != XmlPullParser.END_TAG) {
                    if (parser.eventType != XmlPullParser.START_TAG) continue
                    
                    if (parser.name == "callingPoint") {
                        val result = readCallingPoint(parser, targetCrs)
                        result.first?.let { callingPoints.add(it) }
                        if (result.second != null) {
                            foundArrivalTime = result.second
                        }
                    } else {
                        skip(parser)
                    }
                }
            } else {
                skip(parser)
            }
        }
        return Pair(callingPoints, foundArrivalTime)
    }

    private fun readCallingPoint(parser: XmlPullParser, targetCrs: String): Pair<String?, String?> {
        // parser is at <callingPoint>
        var locationName: String? = null
        var crs: String? = null
        var st: String? = null
        
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            
            when (parser.name) {
                "locationName" -> locationName = parser.nextText()
                "crs" -> crs = parser.nextText()
                "st" -> st = parser.nextText()
                else -> skip(parser)
            }
        }
        
        val arrivalTime = if (crs != null && crs.equals(targetCrs, ignoreCase = true)) {
            st
        } else {
            null
        }
        
        return Pair(locationName, arrivalTime)
    }

    private fun skip(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) {
            throw IllegalStateException()
        }
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
    }
}
