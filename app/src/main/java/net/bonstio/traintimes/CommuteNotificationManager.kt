package net.bonstio.traintimes

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages posting, updating, and cancelling rich commute notifications for Wear OS and phone.
 */
object CommuteNotificationManager {

    private const val TAG = "CommuteNotifManager"
    const val CHANNEL_ID = "commute_departures_channel"
    const val NOTIFICATION_ID_BASE = 2000
    const val ACTION_REFRESH_NOTIFICATION = "net.bonstio.traintimes.ACTION_REFRESH_NOTIFICATION"
    const val ACTION_DISMISS_NOTIFICATION = "net.bonstio.traintimes.ACTION_DISMISS_NOTIFICATION"
    const val EXTRA_WIDGET_ID = "appWidgetId"

    private const val MIN_AUTO_UPDATE_INTERVAL_MS = 15 * 60 * 1000L // 15 minutes throttle for automatic triggers
    private const val ARRIVAL_COOLDOWN_MS = 45 * 60 * 1000L // 45 minutes cooldown after arrival at destination
    private val lastAutoUpdateTimes = java.util.concurrent.ConcurrentHashMap<Int, Long>()
    private val lastArrivalTimes = java.util.concurrent.ConcurrentHashMap<Int, Pair<String, Long>>()
    private val activeNotificationIds = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()

    fun isNotificationActive(appWidgetId: Int): Boolean {
        return activeNotificationIds.contains(appWidgetId)
    }

    fun onNotificationDismissed(appWidgetId: Int) {
        activeNotificationIds.remove(appWidgetId)
        lastAutoUpdateTimes.remove(appWidgetId)
    }

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.notification_channel_commute)
            val descriptionText = context.getString(R.string.notification_channel_commute_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun cancelNotification(context: Context, appWidgetId: Int) {
        activeNotificationIds.remove(appWidgetId)
        lastAutoUpdateTimes.remove(appWidgetId)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID_BASE + appWidgetId)
    }

    fun showOrUpdateNotification(
        context: Context,
        appWidgetId: Int,
        config: WidgetConfiguration,
        services: List<TrainService>?,
        fromStation: String,
        toStation: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Notification permission not granted")
                return
            }
        }

        createNotificationChannel(context)

        val widgetTitle = WidgetUtils.calculateDisplayTitle(
            context,
            config.titleStyle,
            config.title,
            fromStation,
            toStation,
            config.fromStation
        )
        val title = widgetTitle.ifBlank {
            val fromName = StationRepository.getStationName(context, fromStation)
            val toName = if (toStation.isNotEmpty()) StationRepository.getStationName(context, toStation) else ""
            if (toName.isNotEmpty()) "$fromName -> $toName" else fromName
        }

        val refreshIntent = Intent(context, CommuteNotificationReceiver::class.java).apply {
            action = ACTION_REFRESH_NOTIFICATION
            putExtra(EXTRA_WIDGET_ID, appWidgetId)
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val refreshAction = NotificationCompat.Action.Builder(
            R.drawable.refresh_24px,
            context.getString(R.string.notification_action_refresh),
            refreshPendingIntent
        ).build()

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.train_24px)
            .setContentTitle(title)
            .addAction(refreshAction)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setLocalOnly(false)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)

        if (services.isNullOrEmpty()) {
            val summary = context.getString(R.string.notification_no_trains)
            builder.setContentText(summary)
        } else {
            val inboxStyle = NotificationCompat.InboxStyle()
            inboxStyle.setBigContentTitle(title)

            var firstLine = ""
            for ((index, service) in services.asSequence().take(4).withIndex()) {
                val plat = if (!service.platform.isNullOrEmpty()) " [Plat ${service.platform}]" else ""
                val line = "${service.std} (${service.status})$plat → ${service.destination}"
                if (index == 0) {
                    firstLine = line
                }
                inboxStyle.addLine(line)
            }
            builder.setContentText(firstLine)
            builder.setStyle(inboxStyle)
        }

        // Delete intent when user swipes notification away
        val deleteIntent = Intent(context, CommuteNotificationReceiver::class.java).apply {
            action = ACTION_DISMISS_NOTIFICATION
            putExtra(EXTRA_WIDGET_ID, appWidgetId)
        }
        val deletePendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId,
            deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.setDeleteIntent(deletePendingIntent)

        // Wear OS wearable extender:
        // When contentIntent is omitted on the main builder, Wear OS does not display
        // the "Open on phone" button.
        val wearableExtender = NotificationCompat.WearableExtender()
            .addAction(refreshAction)
        builder.extend(wearableExtender)

        activeNotificationIds.add(appWidgetId)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_BASE + appWidgetId, builder.build())
    }

    /**
     * Fetches fresh train departures asynchronously and updates the notification.
     * @param triggeringStation Optional station code that triggered the geofence/proximity.
     *                          If provided, departure direction is anchored to depart FROM this station.
     */
    fun fetchAndUpdateNotification(
        context: Context,
        appWidgetId: Int,
        isUserInitiated: Boolean = false,
        triggeringStation: String? = null
    ) {
        val config = WidgetConfigurationStorage.loadConfiguration(context, appWidgetId) ?: return
        if (!config.showCommuteNotifications && !config.forceShowNotification) {
            cancelNotification(context, appWidgetId)
            return
        }

        // Check if entering a station is actually an ARRIVAL at the commute destination
        if (!config.forceShowNotification && !triggeringStation.isNullOrEmpty() && config.toStation.isNotEmpty()) {
            val scheduledDirection = WidgetUtils.determineDirection(config)
            val scheduledDestination = scheduledDirection.second

            // 1. Direct arrival check: The entered station matches the destination of the current commute leg
            if (triggeringStation.equals(scheduledDestination, ignoreCase = true)) {
                Log.d(TAG, "User arrived at commute destination $scheduledDestination for widget $appWidgetId. Dismissing notification.")
                lastArrivalTimes[appWidgetId] = Pair(triggeringStation, System.currentTimeMillis())
                cancelNotification(context, appWidgetId)
                return
            }

            // 2. Cooldown check: If user recently arrived at this station (within cooldown window), ignore further triggers
            val recentArrival = lastArrivalTimes[appWidgetId]
            if ((recentArrival != null) &&
                recentArrival.first.equals(triggeringStation, ignoreCase = true) &&
                ((System.currentTimeMillis() - recentArrival.second) < ARRIVAL_COOLDOWN_MS)
            ) {
                Log.d(TAG, "User recently arrived at $triggeringStation (cooldown active). Skipping notification.")
                cancelNotification(context, appWidgetId)
                return
            }
        }

        val shouldBypassThrottle = isUserInitiated || !triggeringStation.isNullOrEmpty() || config.forceShowNotification
        if (!shouldBypassThrottle) {
            val now = System.currentTimeMillis()
            val lastUpdate = lastAutoUpdateTimes[appWidgetId] ?: 0L
            if ((now - lastUpdate) < MIN_AUTO_UPDATE_INTERVAL_MS) {
                Log.d(TAG, "Skipping auto notification update for widget $appWidgetId: throttled (less than 15 mins since last auto update)")
                return
            }
            lastAutoUpdateTimes[appWidgetId] = now
        } else {
            lastAutoUpdateTimes[appWidgetId] = System.currentTimeMillis()
        }

        val apiKey = ApiKeyManager.getApiKey(context)
        if (apiKey.isNullOrEmpty()) {
            Log.w(TAG, "Cannot fetch departures for notification: API key is null or empty")
            return
        }

        val (fromStation, toStation) = when {
            // If commutingMode is LOCATION and we know which station triggered the notification
            (config.commutingMode == "LOCATION") && !triggeringStation.isNullOrEmpty() -> {
                if (triggeringStation.equals(config.toStation, ignoreCase = true)) {
                    Pair(config.toStation, config.fromStation)
                } else {
                    Pair(config.fromStation, config.toStation)
                }
            }
            // Otherwise (TIME mode or no station override provided), follow the Time-based commute schedule
            else -> {
                WidgetUtils.determineDirection(config)
            }
        }
        Log.d(TAG, "Fetching notification departures for widget $appWidgetId from $fromStation to $toStation (commutingMode=${config.commutingMode}, triggeredBy=$triggeringStation)")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = RailDataClient(apiKey)
                val nowCal = java.util.Calendar.getInstance()
                var services = client.getNextTrain(fromStation, toStation, config.timeOffset, config.departureCount)
                    .filter { !WidgetUtils.isDepartureInPast(it, nowCal) }
                if (config.enableJourneyDurationFilter) {
                    services = services.filter { service ->
                        val duration = service.duration
                        duration == null || duration <= config.maxJourneyDuration
                    }
                }
                WidgetCache.saveServices(context, appWidgetId, services)
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "Received ${services.size} departures, updating notification and widget")
                    showOrUpdateNotification(context, appWidgetId, config, services, fromStation, toStation)
                    val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
                    TrainTimesWidgetProvider.updateAppWidget(context, appWidgetManager, appWidgetId, hasData = services.isNotEmpty())
                    appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.departures_list)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching departures for notification", e)
            }
        }
    }
}
