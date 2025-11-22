package net.bonstio.traintimes

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import kotlinx.coroutines.runBlocking

/**
 * Service that provides the RemoteViewsFactory for the Train Times widget.
 * This service allows the widget to display a collection of data (the train departures).
 */
class TrainTimesWidgetService : RemoteViewsService() {
    /**
     * returns a new instance of the RemoteViewsFactory.
     *
     * @param intent The intent that triggered this service.
     * @return A new TrainTimesRemoteViewsFactory.
     */
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return TrainTimesRemoteViewsFactory(this.applicationContext, intent)
    }
}

/**
 * Factory class that provides data to the collection view in the widget.
 * It acts like an Adapter in a standard Android ListView/RecyclerView.
 *
 * @property context The application context.
 * @property intent The intent that created this factory.
 */
class TrainTimesRemoteViewsFactory(
    private val context: Context,
    private val intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private var services: List<TrainService> = emptyList()
    private lateinit var client: NationalRailClient
    private var textColor: Int = Color.WHITE

    /**
     * Called when the factory is first created.
     * Setup any connections or resources here.
     */
    override fun onCreate() {
        // Connect to the data source
    }

    /**
     * Called when the underlying data set has changed or when the factory is first created.
     * This is where we fetch the data for the widget.
     * Note: This method is called on a binder thread, so we can perform synchronous network calls or blocking operations.
     */
    override fun onDataSetChanged() {
        val appWidgetId = intent.getIntExtra("appWidgetId", -1)
        val config = WidgetConfigurationStorage.loadConfiguration(context, appWidgetId)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val apiKey = prefs.getString(PREF_API_KEY, null)

        if (config != null && apiKey != null) {
            client = NationalRailClient(apiKey)
            textColor = config.textColor
            runBlocking {
                services = client.getNextTrain(config.fromStation, config.toStation)
            }
        }
    }

    /**
     * Called when the factory is destroyed.
     * Clean up any resources here.
     */
    override fun onDestroy() {
        // Close the data source
    }

    /**
     * Returns the number of items in the data set.
     *
     * @return The count of train services.
     */
    override fun getCount(): Int {
        return services.size
    }

    /**
     * Creates a RemoteViews object for the item at the specified position.
     *
     * @param position The position of the item in the data set.
     * @return A RemoteViews object populated with the data at the specified position.
     */
    override fun getViewAt(position: Int): RemoteViews {
        val service = services[position]
        val views = RemoteViews(context.packageName, R.layout.departure_layout)

        views.setTextViewText(R.id.departure_time, service.std)
        views.setTextColor(R.id.departure_time, textColor)
        views.setTextViewText(R.id.destination, service.destination)
        views.setTextColor(R.id.destination, textColor)

        val platformText = service.platform?.let { "Platform $it" } ?: ""
        views.setTextViewText(R.id.platform, platformText)
        views.setTextColor(R.id.platform, textColor)

        views.setTextViewText(R.id.status, service.status)
        views.setTextColor(R.id.status, textColor)

        return views
    }

    /**
     * Returns a RemoteViews object to be used as a loading indicator.
     * Returning null uses the default loading view.
     *
     * @return A RemoteViews object or null.
     */
    override fun getLoadingView(): RemoteViews? {
        return null
    }

    /**
     * Returns the number of view types.
     *
     * @return The number of view types (1 in this case).
     */
    override fun getViewTypeCount(): Int {
        return 1
    }

    /**
     * Returns the stable ID for the item at the specified position.
     *
     * @param position The position of the item.
     * @return The stable ID.
     */
    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    /**
     * Indicates whether the item IDs are stable across data changes.
     *
     * @return True if IDs are stable, false otherwise.
     */
    override fun hasStableIds(): Boolean {
        return true
    }
}