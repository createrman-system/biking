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
import com.createrman.biking.data.tracking.TrackingRepository
import com.createrman.biking.domain.model.TrackingStatus
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class TrackingService : Service() {
    private val trackingRepository: TrackingRepository by inject()

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private val channelId = "tracking_channel"
    private val notificationId = 1
    private var observing = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(notificationId, createNotification("Starting ride..."))
                trackingRepository.start()
                observeTracking()
            }
            ACTION_PAUSE -> trackingRepository.pause()
            ACTION_RESUME -> trackingRepository.resume()
            ACTION_STOP -> {
                serviceScope.launch {
                    trackingRepository.stopAndSave()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun observeTracking() {
        if (observing) return
        observing = true
        serviceScope.launch {
            trackingRepository.uiState.collect { state ->
                updateNotification(
                    content = String.format(
                        Locale.US,
                        "%.1f km/h | %.2f km | %s | +%.0f m",
                        state.speedMetersPerSecond * 3.6f,
                        state.distanceMeters / 1000.0,
                        formatDuration(state.movingTimeSeconds),
                        state.elevationGainMeters,
                    ),
                    paused = state.status == TrackingStatus.Paused || state.status == TrackingStatus.AutoPaused,
                )
            }
        }
    }

    private fun createNotification(content: String, paused: Boolean = false): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(this, 0, openIntent, immutableFlag())
        val pauseResumeAction = if (paused) ACTION_RESUME else ACTION_PAUSE
        val pauseResumeTitle = if (paused) "Resume" else "Pause"

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(if (paused) "Biking paused" else "Biking tracking")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_bike)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPendingIntent)
            .addAction(0, pauseResumeTitle, serviceIntent(pauseResumeAction, 10))
            .addAction(0, "Stop", serviceIntent(ACTION_STOP, 11))
            .build()
    }

    private fun updateNotification(content: String, paused: Boolean) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, createNotification(content, paused))
    }

    private fun serviceIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, TrackingService::class.java).apply { this.action = action }
        return PendingIntent.getService(this, requestCode, intent, immutableFlag())
    }

    private fun immutableFlag(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Biking Tracking", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    private fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return "%02d:%02d:%02d".format(hours, minutes, secs)
    }

    companion object {
        const val ACTION_START = "com.createrman.biking.START"
        const val ACTION_PAUSE = "com.createrman.biking.PAUSE"
        const val ACTION_RESUME = "com.createrman.biking.RESUME"
        const val ACTION_STOP = "com.createrman.biking.STOP"
    }
}
