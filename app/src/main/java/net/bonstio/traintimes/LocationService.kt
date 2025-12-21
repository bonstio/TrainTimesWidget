package net.bonstio.traintimes

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.location.*

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    companion object {
        private const val TAG = "LocationService"
        private const val CHANNEL_ID = "location_tracking_channel"
        private const val NOTIFICATION_ID = 101

        fun update(context: Context) {
            val am = AppWidgetManager.getInstance(context)
            val ids = am.getAppWidgetIds(ComponentName(context, TrainTimesWidgetProvider::class.java))
            
            var needsLocation = false
            for (id in ids) {
                val config = WidgetConfigurationStorage.loadConfiguration(context, id)
                if (config != null && (config.commutingMode == "LOCATION" || config.useNearestStationForReturn)) {
                    needsLocation = true
                    break
                }
            }

            Log.d(TAG, "update: needsLocation=$needsLocation")
            if (needsLocation) {
                start(context)
            } else {
                stop(context)
            }
        }

        private fun start(context: Context) {
            val intent = Intent(context, LocationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        private fun stop(context: Context) {
            context.stopService(Intent(context, LocationService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                Log.d(TAG, "Location changed: ${locationResult.lastLocation}")
                triggerWidgetUpdate()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        startForeground(NOTIFICATION_ID, createNotification())
        requestLocationUpdates()
        return START_STICKY
    }

    private fun requestLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "No location permission")
            stopSelf()
            return
        }

        // Use HIGH_ACCURACY to ensure GPS hardware is used
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 300000L) // 5 mins
            .setMinUpdateIntervalMillis(60000L) // 1 min min interval
            .setMinUpdateDistanceMeters(200f) // 200 meters
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, mainLooper)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: ${e.message}")
            stopSelf()
        }
    }

    private fun triggerWidgetUpdate() {
        val am = AppWidgetManager.getInstance(this)
        val ids = am.getAppWidgetIds(ComponentName(this, TrainTimesWidgetProvider::class.java))
        
        if (ids.isEmpty()) return

        val data = Data.Builder()
            .putIntArray(WidgetUpdateWorker.KEY_WIDGET_IDS, ids)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
            .setInputData(data)
            .build()

        // Use unique work to avoid multiple updates at once
        WorkManager.getInstance(this).enqueueUniqueWork(
            "location_triggered_update",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Location Tracking", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Train Times")
            .setContentText("Monitoring location for station-based updates")
            .setSmallIcon(R.drawable.train_24px)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
