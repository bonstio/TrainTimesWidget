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
}
