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
        // Mock TrainService data based on the provided XML
        val services = listOf(
            TrainService("15:10", "Worcester", "8", "Exp 17:00", emptyList()),
            TrainService("16:10", "Wigan North Western", "8", "On time", emptyList()),
            TrainService("16:12", "Norwich", "9", "On time", emptyList()),
            TrainService("16:15", "London Euston", "7", "On time", emptyList()),
            TrainService("16:19", "Manchester Oxford Road", "5", "On time", emptyList()),
            TrainService("16:21", "Stalybridge", "3", "On time", emptyList())
        )

        // Set the current time to 16:11
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 16)
            set(Calendar.MINUTE, 11)
        }

        // Filter the services
        val filteredServices = services.filter { !TrainTimesWidgetProvider.isDepartureInPast(it, now) }

        // Assert the unfiltered list has the correct size
        assertEquals(6, services.size)

        // Assert the filtered list
        assertEquals(5, filteredServices.size)
        assertEquals("Worcester", filteredServices[0].destination)
        assertEquals("Norwich", filteredServices[1].destination)
        assertEquals("London Euston", filteredServices[2].destination)
        assertEquals("Manchester Oxford Road", filteredServices[3].destination)
        assertEquals("Stalybridge", filteredServices[4].destination)
    }
}