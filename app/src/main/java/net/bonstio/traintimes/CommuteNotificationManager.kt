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
    const val EXTRA_WIDGET_ID = "appWidgetId"

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
        val title = if (widgetTitle.isNotBlank()) {
            widgetTitle
        } else {
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
            for ((index, service) in services.take(4).withIndex()) {
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

        // Wear OS wearable extender:
        // When contentIntent is omitted on the main builder, Wear OS does not display
        // the "Open on phone" button.
        val wearableExtender = NotificationCompat.WearableExtender()
            .addAction(refreshAction)
        builder.extend(wearableExtender)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_BASE + appWidgetId, builder.build())
    }

    /**
     * Fetches fresh train departures asynchronously and updates the notification.
     */
    fun fetchAndUpdateNotification(context: Context, appWidgetId: Int) {
        val config = WidgetConfigurationStorage.loadConfiguration(context, appWidgetId) ?: return
        if (!config.showCommuteNotifications && !config.forceShowNotification) {
            cancelNotification(context, appWidgetId)
            return
        }

        val prefs = context.getSharedPreferences(TrainTimesWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE)
        val apiKey = prefs.getString(TrainTimesWidgetProvider.PREF_API_KEY, null)
        if (apiKey.isNullOrEmpty()) return

        val (fromStation, toStation) = WidgetUtils.determineDirection(config)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = RailDataClient(apiKey)
                var services = client.getNextTrain(fromStation, toStation, config.timeOffset, config.departureCount)
                if (config.enableJourneyDurationFilter) {
                    services = services.filter { service ->
                        val duration = service.duration
                        duration == null || duration <= config.maxJourneyDuration
                    }
                }
                withContext(Dispatchers.Main) {
                    showOrUpdateNotification(context, appWidgetId, config, services, fromStation, toStation)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching departures for notification", e)
            }
        }
    }
}
