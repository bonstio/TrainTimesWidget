package net.bonstio.traintimes

import org.junit.Test
import org.junit.Assert.*
import java.util.Calendar

/**
 * Unit tests for the Train Times widget provider.
 */
class TrainTimesWidgetProviderTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testDepartureFiltering() {
        // Mock TrainService data
        val services = listOf(
            TrainService("15:10", "Worcester", "8", "Exp 17:00", emptyList()), // Future (via Exp)
            TrainService("16:10", "Wigan North Western", "8", "On time", emptyList()), // -1 min (buffer should keep)
            TrainService("16:12", "Norwich", "9", "On time", emptyList()), // Future
            TrainService("16:15", "London Euston", "7", "On time", emptyList()), // Future
            TrainService("16:19", "Manchester Oxford Road", "5", "On time", emptyList()), // Future
            TrainService("16:21", "Stalybridge", "3", "On time", emptyList()), // Future
            TrainService("16:00", "Old Service", "1", "On time", emptyList()), // -11 mins (should be hidden)
            TrainService("16:06", "Buffered Service", "2", "On time", emptyList()) // -5 mins (should be kept)
        )

        // Set the current time to 16:11
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 16)
            set(Calendar.MINUTE, 11)
        }

        // Filter the services
        val filteredServices = services.filter { !WidgetUtils.isDepartureInPast(it, now) }

        // Assert the unfiltered list has the correct size
        assertEquals(8, services.size)

        // Assert the filtered list
        // Expected: 6 original (since Wigan is now kept) + 1 Buffered Service = 7. Old Service hidden.
        assertEquals(7, filteredServices.size)
        
        // Verify order and presence
        val destinations = filteredServices.map { it.destination }
        assertTrue(destinations.contains("Worcester"))
        assertTrue(destinations.contains("Wigan North Western")) // Should now be present
        assertTrue(destinations.contains("Norwich"))
        assertTrue(destinations.contains("London Euston"))
        assertTrue(destinations.contains("Manchester Oxford Road"))
        assertTrue(destinations.contains("Stalybridge"))
        assertFalse(destinations.contains("Old Service")) // Should be hidden
        assertTrue(destinations.contains("Buffered Service")) // Should be present
    }
}