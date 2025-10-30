package com.call.janmapping

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*

class LocationMonitorService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val notifiedStores = mutableSetOf<String>()

    companion object {
        private const val CHANNEL_ID = "location_monitor_channel"
        private const val NOTIFICATION_ID = 1001
        private const val STORE_NOTIFICATION_BASE_ID = 2000
        private const val DISTANCE_THRESHOLD_METERS = 50f

        var stores: List<StoreItem> = emptyList()
        var janList: List<String> = emptyList()
        var productNames: List<String> = emptyList()

        fun start(context: Context, storeList: List<StoreItem>, jans: List<String>, products: List<String> = emptyList()) {
            stores = storeList
            janList = jans
            productNames = products
            val intent = Intent(context, LocationMonitorService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, LocationMonitorService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("LocationMonitor", "Service created")

        createNotificationChannel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    checkStoreProximity(location)
                }
            }
        }

        startForeground(NOTIFICATION_ID, createForegroundNotification())
        startLocationUpdates()
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            10000L
        ).apply {
            setMinUpdateIntervalMillis(5000L)
            setMaxUpdateDelayMillis(15000L)
        }.build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.d("LocationMonitor", "Location updates started")
        } catch (e: SecurityException) {
            Log.e("LocationMonitor", "Location permission not granted", e)
        }
    }

    private fun checkStoreProximity(currentLocation: Location) {
        Log.d("LocationMonitor", "Checking proximity at (${currentLocation.latitude}, ${currentLocation.longitude})")

        stores.forEach { store ->
            val storeLocation = Location("").apply {
                latitude = store.lat
                longitude = store.lng
            }

            val distance = currentLocation.distanceTo(storeLocation)
            Log.d("LocationMonitor", "Distance to ${store.name}: ${distance}m")

            if (distance <= DISTANCE_THRESHOLD_METERS && !notifiedStores.contains(store.store_id)) {
                showStoreNotification(store, distance)
                notifiedStores.add(store.store_id)
            } else if (distance > DISTANCE_THRESHOLD_METERS * 2) {
                notifiedStores.remove(store.store_id)
            }
        }
    }

    private fun showStoreNotification(store: StoreItem, distance: Float) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val productText = if (productNames.isNotEmpty()) {
            val displayProducts = productNames.take(3).joinToString("、")
            val remaining = productNames.size - 3
            if (remaining > 0) {
                "$displayProducts 他${remaining}件"
            } else {
                displayProducts
            }
        } else {
            "おすすめ商品"
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle("${store.name} が近くにあります！")
            .setContentText("$productText が近くにあります (距離: ${distance.toInt()}m)")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("${store.name} まで約${distance.toInt()}mです。\n\n【おすすめ商品】\n$productText"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(STORE_NOTIFICATION_BASE_ID + store.store_id.hashCode(), notification)
        Log.d("LocationMonitor", "Notification shown for ${store.name} with products: $productText")
    }

    private fun createForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("店舗を監視中")
            .setContentText("近くの店舗を検索しています")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "店舗接近通知",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "店舗に近づいた際の通知"
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        notifiedStores.clear()
        Log.d("LocationMonitor", "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
