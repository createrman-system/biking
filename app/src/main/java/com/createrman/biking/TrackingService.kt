package com.createrman.biking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale

class TrackingService : Service() {
    private lateinit var locationManager: LocationManager
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    
    private val CHANNEL_ID = "tracking_channel"
    private val NOTIFICATION_ID = 1

    override fun onCreate() {
        super.onCreate()
        locationManager = LocationManager.getInstance(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        
        when (action) {
            ACTION_START -> startForegroundService()
            ACTION_STOP -> stopForegroundService()
        }
        
        return START_STICKY
    }

    private fun startForegroundService() {
        val notification = createNotification("Starting tracking...", "")
        startForeground(NOTIFICATION_ID, notification)
        
        locationManager.startTracking()
        
        // Update notification with real-time stats
        serviceScope.launch {
            combine(
                locationManager.speedFlow,
                locationManager.distanceFlow
            ) { speed, distance ->
                Pair(speed, distance)
            }.collect { (speed, distance) ->
                val speedStr = String.format(Locale.US, "%.1f km/h", speed)
                val distStr = String.format(Locale.US, "%.2f km", distance / 1000.0)
                updateNotification("Speed: $speedStr | Dist: $distStr")
            }
        }
    }

    private fun stopForegroundService() {
        locationManager.stopTracking()
        stopForeground(true)
        stopSelf()
    }

    private fun createNotification(title: String, content: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_bike)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(content: String) {
        val notification = createNotification("Biking Tracking Active", content)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Biking Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live biking stats while tracking in background"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
    }
}
