package com.apptcheck.agent.service

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.apptcheck.agent.MainActivity
import com.apptcheck.agent.R
import kotlinx.coroutines.*
import rust_engine.* // Import generated UniFFI bindings

/**
 * BookingForegroundService ensures the Rust Engine remains active during
 * high-precision strikes, even if the user minimizes the app or the screen turns off.
 */
class BookingForegroundService : Service() {
    private val TAG = "BookingService"
    private val CHANNEL_ID = "BookingAgentChannel"
    private val NOTIFICATION_ID = 101

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    
    // The native Rust Booking Agent
    private val agent = BookingAgent()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val configJson = intent?.getStringExtra("CONFIG_JSON") ?: ""
        val runId = intent?.getStringExtra("RUN_ID") ?: ""

        if (configJson.isEmpty() || runId.isEmpty()) {
            Log.e(TAG, "Missing configuration or Run ID. Stopping service.")
            stopSelf()
            return START_NOT_STICKY
        }

        // 1. Enter Foreground State with Notification
        val notification = createNotification("Preparing Strike...", "Agent ID: $runId")
        startForeground(NOTIFICATION_ID, notification)

        // 2. Acquire WakeLock to prevent CPU from sleeping during precision timing
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ApptCheck::StrikeLock").apply {
            acquire(10 * 60 * 1000L /* 10 minutes max safety timeout */)
        }

        // 3. Launch the Rust Engine Strike
        serviceScope.launch {
            try {
                Log.i(TAG, "Handoff to Rust Engine: $runId")
                
                // This is the cross-language call into the Rust Engine logic
                val success = agent.startStrike(configJson, runId)
                
                if (success) {
                    Log.i(TAG, "Strike Logic Completed Successfully")
                    updateNotification("Strike Finished", "Operation was successful.")
                } else {
                    Log.w(TAG, "Strike Logic Finished without booking")
                    updateNotification("Strike Finished", "No availability found.")
                }
            } catch (e: EngineException) {
                Log.e(TAG, "Rust Engine Error: ${e.message}")
                updateNotification("Engine Error", e.message ?: "Unknown error")
            } catch (e: Exception) {
                Log.e(TAG, "System Error: ${e.message}")
            } finally {
                // Release hardware resources and stop service
                wakeLock?.let { if (it.isHeld) it.release() }
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        Log.i(TAG, "Booking Service Destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(title: String, content: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setForegroundServiceType(NotificationCompat.FOREGROUND_SERVICE_DATA_SYNC)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(title: String, content: String) {
        val notification = createNotification(title, content)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Appointment Agent Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }
}
