package net.bonstio.traintimes

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Filter
import java.util.Locale

/**
 * ArrayAdapter for filtering and displaying Station objects in an AutoCompleteTextView.
 * Supports filtering by station name or code.
 *
 * @param context The application context.
 * @param stations The list of all available stations.
 */
class StationAdapter(context: Context, stations: List<Station>) :
    ArrayAdapter<Station>(
        context,
        android.R.layout.simple_dropdown_item_1line,
        ArrayList(stations)
    ) {

    private val allStations = ArrayList(stations)

    /**
     * Returns a custom Filter that matches user input against station names and codes.
     *
     * @return A Filter instance.
     */
    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val results = FilterResults()
                if (constraint != null) {
                    val query = constraint.toString().lowercase(Locale.getDefault())
                    val filtered = allStations.filter {
                        it.name.lowercase(Locale.getDefault()).contains(query) ||
                                it.code.lowercase(Locale.getDefault()).contains(query)
                    }
                    results.values = filtered
                    results.count = filtered.size
                } else {
                    results.values = ArrayList(allStations)
                    results.count = allStations.size
                }
                return results
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                clear()
                if (results != null && results.count > 0) {
                    @Suppress("UNCHECKED_CAST")
                    addAll(results.values as List<Station>)
                }
                notifyDataSetChanged()
            }

            override fun convertResultToString(resultValue: Any): CharSequence {
                return (resultValue as Station).toString()
            }
        }
    }
}