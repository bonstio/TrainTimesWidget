package net.bonstio.traintimes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RailDataClientTest {

    @Test
    fun testParseTrainServices() {
        val json = """
        {
          "trainServices": [
            {
              "std": "11:54",
              "etd": "On time",
              "platform": "5",
              "isCancelled": false,
              "destination": [
                {
                  "locationName": "London Euston",
                  "crs": "EUS",
                  "via": "via Wilmslow"
                }
              ],
              "subsequentCallingPoints": [
                {
                  "callingPoint": [
                    {
                      "locationName": "Stockport",
                      "crs": "SPT",
                      "st": "12:02"
                    },
                    {
                      "locationName": "London Euston",
                      "crs": "EUS",
                      "st": "14:09"
                    }
                  ]
                }
              ]
            },
            {
              "std": "12:00",
              "etd": "12:15",
              "platform": "3",
              "isCancelled": false,
              "destination": [
                {
                  "locationName": "Leeds",
                  "crs": "LDS"
                }
              ]
            },
            {
              "std": "12:30",
              "etd": "Cancelled",
              "isCancelled": true,
              "destination": [
                {
                  "locationName": "Liverpool Lime Street",
                  "crs": "LIV"
                }
              ]
            }
          ]
        }
        """.trimIndent()

        val client = RailDataClient("dummy_key")
        val services = client.parseTrainServices(json, "EUS")

        assertEquals(3, services.size)

        // Service 1
        assertEquals("11:54", services[0].std)
        assertEquals("London Euston", services[0].destination)
        assertEquals("5", services[0].platform)
        assertEquals("On time", services[0].status)
        assertEquals(listOf("Stockport", "London Euston"), services[0].subsequentCallingPoints)
        assertEquals(135, services[0].duration) // 11:54 to 14:09 -> 135 mins

        // Service 2
        assertEquals("12:00", services[1].std)
        assertEquals("Leeds", services[1].destination)
        assertEquals("3", services[1].platform)
        assertEquals("Exp 12:15", services[1].status)
        assertEquals(emptyList<String>(), services[1].subsequentCallingPoints)
        assertNull(services[1].duration)

        // Service 3
        assertEquals("12:30", services[2].std)
        assertEquals("Liverpool Lime Street", services[2].destination)
        assertNull(services[2].platform)
        assertEquals("Cancelled", services[2].status)
    }

    @Test
    fun testParseBusServices() {
        val json = """
        {
          "busServices": [
            {
              "std": "13:00",
              "etd": "On time",
              "destination": [
                {
                  "locationName": "Replacement Bus Stop",
                  "crs": "RBS"
                }
              ]
            }
          ]
        }
        """.trimIndent()

        val client = RailDataClient("dummy_key")
        val services = client.parseTrainServices(json, "RBS")

        assertEquals(1, services.size)
        assertEquals("13:00", services[0].std)
        assertEquals("Replacement Bus Stop", services[0].destination)
        assertNull(services[0].platform)
        assertEquals("On time", services[0].status)
    }

    @Test
    fun testParseEmptyResponse() {
        val json = """
        {
          "locationName": "Manchester Piccadilly",
          "crs": "MAN"
        }
        """.trimIndent()

        val client = RailDataClient("dummy_key")
        val services = client.parseTrainServices(json, "EUS")

        assertTrue(services.isEmpty())
    }
}
