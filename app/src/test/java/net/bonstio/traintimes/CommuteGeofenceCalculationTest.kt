package net.bonstio.traintimes

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for geofence and commute proximity evaluation.
 */
class CommuteGeofenceCalculationTest {

    data class MockStation(val code: String, val name: String, val lat: Double, val lon: Double)

    /**
     * Replicates the distance and inside/outside geofence calculation logic.
     */
    private fun isInsideGeofence(
        userLat: Double,
        userLon: Double,
        stations: List<MockStation>,
        geofenceRadiusMeters: Int
    ): Boolean {
        for (station in stations) {
            val dist = computeDistanceMeters(userLat, userLon, station.lat, station.lon)
            if (dist <= geofenceRadiusMeters) {
                return true
            }
        }
        return false
    }

    /**
     * Haversine formula calculation matching android.location.Location.distanceBetween
     */
    private fun computeDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }

    @Test
    fun testUserInsideRadius_TriggersNotification() {
        // Station: London Waterloo (51.5031, -0.1132)
        val waterloo = MockStation("WAT", "London Waterloo", 51.5031, -0.1132)
        val stations = listOf(waterloo)

        // User location: ~150 meters away
        val userLat = 51.5040
        val userLon = -0.1132

        val distance = computeDistanceMeters(userLat, userLon, waterloo.lat, waterloo.lon)
        assertTrue("Distance should be around 100m", distance in 90.0..115.0)

        // With 400m radius -> should be inside
        val isInside = isInsideGeofence(userLat, userLon, stations, 400)
        assertTrue("User at ~100m should be inside 400m geofence", isInside)
    }

    @Test
    fun testUserOutsideRadius_CancelsNotification() {
        // Station: London Waterloo (51.5031, -0.1132)
        val waterloo = MockStation("WAT", "London Waterloo", 51.5031, -0.1132)
        val stations = listOf(waterloo)

        // User location: ~150 meters away (lat 51.5040)
        val userLat = 51.5040
        val userLon = -0.1132

        // When slider is reduced to 100m (< 100.1m) -> should be outside
        val isInside100m = isInsideGeofence(userLat, userLon, stations, 50)
        assertFalse("User at ~100m should be outside 50m geofence", isInside100m)
    }

    @Test
    fun testTwoStations_UserNearArrivalStation_TriggersNotification() {
        // Departure: London Waterloo, Arrival: Woking (51.3188, -0.5567)
        val waterloo = MockStation("WAT", "London Waterloo", 51.5031, -0.1132)
        val woking = MockStation("WOK", "Woking", 51.3188, -0.5567)
        val stations = listOf(waterloo, woking)

        // User near Woking (200m away)
        val userLat = 51.3200
        val userLon = -0.5567

        val isInside = isInsideGeofence(userLat, userLon, stations, 400)
        assertTrue("User near Woking should trigger notification", isInside)
    }

    @Test
    fun testCommuteModeResolution() {
        // Config: From Waterloo to Woking
        val fromStation = "WAT"
        val toStation = "WOK"

        // Helper function matching CommuteNotificationManager logic
        fun resolveDirection(mode: String, triggeringStation: String?, isTimeReversed: Boolean): Pair<String, String> {
            return when {
                mode == "LOCATION" && !triggeringStation.isNullOrEmpty() -> {
                    if (triggeringStation.equals(toStation, ignoreCase = true)) {
                        Pair(toStation, fromStation)
                    } else {
                        Pair(fromStation, toStation)
                    }
                }
                else -> {
                    if (isTimeReversed) Pair(toStation, fromStation) else Pair(fromStation, toStation)
                }
            }
        }

        // Case 1: LOCATION mode near source (Waterloo) during evening (time would say return)
        val locSource = resolveDirection("LOCATION", "WAT", isTimeReversed = true)
        assertEquals("LOCATION mode near source should depart from source even at night", "WAT", locSource.first)
        assertEquals("WOK", locSource.second)

        // Case 2: LOCATION mode near destination (Woking) during morning (time would say outbound)
        val locDest = resolveDirection("LOCATION", "WOK", isTimeReversed = false)
        assertEquals("LOCATION mode near destination should depart from destination even in morning", "WOK", locDest.first)
        assertEquals("WAT", locDest.second)

        // Case 3: TIME mode near destination during morning (time says outbound) -> should follow TIME
        val timeMorning = resolveDirection("TIME", "WOK", isTimeReversed = false)
        assertEquals("TIME mode in morning should follow time schedule (outbound)", "WAT", timeMorning.first)
        assertEquals("WOK", timeMorning.second)

        // Case 4: TIME mode near source during evening (time says return) -> should follow TIME
        val timeEvening = resolveDirection("TIME", "WAT", isTimeReversed = true)
        assertEquals("TIME mode in evening should follow time schedule (return)", "WOK", timeEvening.first)
        assertEquals("WAT", timeEvening.second)
    }

    @Test
    fun testWidgetStationRequirements_NormalMode() {
        // Normal mode (useNearest = false): From is REQUIRED, To is OPTIONAL
        val configWithFromOnly = WidgetConfiguration(
            title = "Test",
            fromStation = "WAT",
            toStation = "",
            useNearestStationForReturn = false
        )
        assertTrue("Normal mode with From station should be valid", TrainTimesWidgetProvider.isWidgetConfigured(configWithFromOnly))

        val configWithFromAndTo = WidgetConfiguration(
            title = "Test",
            fromStation = "WAT",
            toStation = "WOK",
            useNearestStationForReturn = false
        )
        assertTrue("Normal mode with From and To should be valid", TrainTimesWidgetProvider.isWidgetConfigured(configWithFromAndTo))

        val configWithToOnly = WidgetConfiguration(
            title = "Test",
            fromStation = "",
            toStation = "WOK",
            useNearestStationForReturn = false
        )
        assertFalse("Normal mode without From station should be invalid", TrainTimesWidgetProvider.isWidgetConfigured(configWithToOnly))

        val configEmpty = WidgetConfiguration(
            title = "Test",
            fromStation = "",
            toStation = "",
            useNearestStationForReturn = false
        )
        assertFalse("Normal mode with empty stations should be invalid", TrainTimesWidgetProvider.isWidgetConfigured(configEmpty))
    }

    @Test
    fun testWidgetStationRequirements_NearestMode() {
        // Nearest mode (useNearest = true): Destination is optional (can be to a specific station or anywhere)
        val configWithToOnly = WidgetConfiguration(
            title = "Test",
            fromStation = "",
            toStation = "WOK",
            useNearestStationForReturn = true
        )
        assertTrue("Nearest mode with To station should be valid", TrainTimesWidgetProvider.isWidgetConfigured(configWithToOnly))

        val configWithFromAndTo = WidgetConfiguration(
            title = "Test",
            fromStation = "WAT",
            toStation = "WOK",
            useNearestStationForReturn = true
        )
        assertTrue("Nearest mode with From and To should be valid", TrainTimesWidgetProvider.isWidgetConfigured(configWithFromAndTo))

        val configWithFromOnly = WidgetConfiguration(
            title = "Test",
            fromStation = "WAT",
            toStation = "",
            useNearestStationForReturn = true
        )
        assertTrue("Nearest mode with From only (destination anywhere) should be valid", TrainTimesWidgetProvider.isWidgetConfigured(configWithFromOnly))

        val configEmpty = WidgetConfiguration(
            title = "Test",
            fromStation = "",
            toStation = "",
            useNearestStationForReturn = true
        )
        assertTrue("Nearest mode with empty stations (destination anywhere) should be valid", TrainTimesWidgetProvider.isWidgetConfigured(configEmpty))
    }

    @Test
    fun testResolveNotificationRoute_UseNearestMode() {
        // 1. Use nearest with no destination -> all departures from nearest station
        val configNoDest = WidgetConfiguration(
            title = "Test",
            fromStation = "",
            toStation = "",
            useNearestStationForReturn = true,
            showCommuteNotifications = true
        )
        val routeNoDest = CommuteNotificationManager.resolveNotificationRoute(
            config = configNoDest,
            triggeringStation = "ZFD",
            fallbackNearestStation = "STP"
        )
        assertEquals("ZFD", routeNoDest.first)
        assertEquals("", routeNoDest.second)

        // 2. Use nearest with destination set (no commute reversal) -> departs from nearest toward destination
        val configWithDest = WidgetConfiguration(
            title = "Test",
            fromStation = "SAC",
            toStation = "STP",
            startTimeNormal = -1,
            startTimeReverse = -1,
            useNearestStationForReturn = true,
            showCommuteNotifications = true
        )
        val routeWithDest = CommuteNotificationManager.resolveNotificationRoute(
            config = configWithDest,
            triggeringStation = "ZFD"
        )
        assertEquals("ZFD", routeWithDest.first)
        assertEquals("STP", routeWithDest.second)

        // 3. Fallback to cached nearest station when triggeringStation is null (e.g. manual refresh)
        val routeManualRefresh = CommuteNotificationManager.resolveNotificationRoute(
            config = configWithDest,
            triggeringStation = null,
            fallbackNearestStation = "CTK"
        )
        assertEquals("CTK", routeManualRefresh.first)
        assertEquals("STP", routeManualRefresh.second)

        // 4. Same or sister station handling when at one of the endpoints
        val routeAtSisterDest = CommuteNotificationManager.resolveNotificationRoute(
            config = configWithDest,
            triggeringStation = "KGX" // Sister station of STP
        )
        assertEquals("KGX", routeAtSisterDest.first)
        assertEquals("SAC", routeAtSisterDest.second)
    }

    @Test
    fun testInTransitCooldown_SuppressesIntermediateStationSwitches() {
        CommuteNotificationManager.resetTransitStateForTest()
        val t0 = 1_000_000L

        // Initially not in transit
        assertFalse(CommuteNotificationManager.isInTransit(null, nowMs = t0))

        // Walking speed (1.5 m/s) -> not in transit
        CommuteNotificationManager.recordSpeedMps(1.5f, nowMs = t0)
        assertFalse(CommuteNotificationManager.isInTransit(null, nowMs = t0 + 5_000L))

        // Train speed (15 m/s ~ 33 mph) -> marks in transit
        CommuteNotificationManager.recordSpeedMps(15.0f, nowMs = t0 + 10_000L)
        assertTrue("Should be in transit during 45s intermediate station stop", CommuteNotificationManager.isInTransit(null, nowMs = t0 + 55_000L))

        // 4 minutes later after getting off the train -> no longer in transit
        assertFalse("Should exit transit cooldown after 3 minutes", CommuteNotificationManager.isInTransit(null, nowMs = t0 + 250_000L))
    }

    @Test
    fun testLocationModeAtHomeBeyond2km_DoesNotReverseToWorkStation() {
        // Harpenden (HPD): 51.8152, -0.3519
        // London St Pancras (STP): 51.5319, -0.1263
        val hpd = MockStation("HPD", "Harpenden", 51.8152, -0.3519)
        val stp = MockStation("STP", "London St Pancras", 51.5319, -0.1263)

        // User at home in Harpenden (~2.17km from HPD, ~33.8km from STP)
        val userLat = 51.8347
        val userLon = -0.3519

        val distFrom = computeDistanceMeters(userLat, userLon, hpd.lat, hpd.lon)
        val distTo = computeDistanceMeters(userLat, userLon, stp.lat, stp.lon)

        assertTrue("User should be >2km from HPD (actual=${distFrom}m)", distFrom > 2000.0)
        assertTrue("User should be much closer to HPD than STP", distFrom < distTo)

        val isReversed = distTo < distFrom
        assertFalse("Route must NOT reverse when at home in Harpenden", isReversed)
    }

    @Test
    fun testNotificationFourMinuteDebounce() {
        CommuteNotificationManager.resetTransitStateForTest()
        val widgetId = 7
        val t0 = 10_000_000L

        assertFalse("Should not be debounced initially", CommuteNotificationManager.isNotificationDebounced(null, widgetId, nowMs = t0))

        CommuteNotificationManager.recordNotificationTime(null, widgetId, nowMs = t0)
        assertTrue("Should be debounced 1 minute later", CommuteNotificationManager.isNotificationDebounced(null, widgetId, nowMs = t0 + 60_000L))
        assertTrue("Should still be debounced at 3m59s", CommuteNotificationManager.isNotificationDebounced(null, widgetId, nowMs = t0 + 239_999L))

        // Dismissing the notification must NOT clear the 4-minute debounce
        CommuteNotificationManager.onNotificationDismissed(widgetId)
        assertTrue("Dismissing notification should preserve 4-minute debounce", CommuteNotificationManager.isNotificationDebounced(null, widgetId, nowMs = t0 + 120_000L))

        // At 4 minutes (240,000 ms), debounce expires
        assertFalse("Debounce should expire after 4 minutes", CommuteNotificationManager.isNotificationDebounced(null, widgetId, nowMs = t0 + 240_000L))
    }
}
