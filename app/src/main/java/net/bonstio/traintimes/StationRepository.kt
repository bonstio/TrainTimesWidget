package net.bonstio.traintimes

import android.content.Context

/**
 * Data class representing a train station.
 *
 * @property name The name of the station.
 * @property code The 3-letter CRS code of the station.
 */
data class Station(val name: String, val code: String) {
    /**
     * Returns a string representation of the station.
     *
     * @return The string representation in "Name (Code)" format.
     */
    override fun toString(): String {
        return "$name ($code)"
    }
}

/**
 * Repository for accessing station data.
 * Loads station data from a CSV asset file.
 */
object StationRepository {
    private var stations: List<Station>? = null

    /**
     * Retrieves the list of all available stations.
     * Caches the list after the first load.
     *
     * @param context The application context.
     * @return A list of [Station] objects sorted by name.
     */
    fun getStations(context: Context): List<Station> {
        if (stations == null) {
            stations = loadStations(context)
        }
        return stations!!
    }

    /**
     * Retrieves the name of a station given its code.
     *
     * @param context The application context.
     * @param code The 3-letter CRS code of the station.
     * @return The name of the station, or the code if not found.
     */
    fun getStationName(context: Context, code: String): String {
        val stations = getStations(context)
        return stations.find { it.code.equals(code, ignoreCase = true) }?.name ?: code
    }

    /**
     * Loads stations from the "stations.csv" asset file.
     *
     * @param context The application context.
     * @return A list of [Station] objects parsed from the CSV.
     */
    private fun loadStations(context: Context): List<Station> {
        val list = mutableListOf<Station>()
        try {
            context.assets.open("stations.csv").bufferedReader().use { reader ->
                reader.forEachLine { line ->
                    // Format: Name,lat,long,Code,iataAirportCode,constituentCountry
                    val parts = line.split(",")
                    if (parts.size >= 4) {
                        val name = parts[0].trim()
                        val code = parts[3].trim()

                        if (name.isNotEmpty() && code.length == 3 && code.all { it.isUpperCase() }) {
                            list.add(Station(name, code))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // File might not exist yet or error reading
        }
        return list.sortedBy { it.name }
    }
}