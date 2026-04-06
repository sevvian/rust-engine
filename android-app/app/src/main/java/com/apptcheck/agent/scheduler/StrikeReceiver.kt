package com.apptcheck.agent.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.apptcheck.agent.service.BookingForegroundService

/**
 * StrikeReceiver is triggered by the AlarmManager.
 * It wakes the application up to begin the "Pre-warm" and "Strike" phases.
 */
class StrikeReceiver : BroadcastReceiver() {
    private val TAG = "StrikeReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        // 1. Verify the action matches our defined constant
        if (intent.action != "com.apptcheck.ACTION_STRIKE_ALARM") {
            Log.w(TAG, "Received unknown intent action: ${intent.action}")
            return
        }

        val runId = intent.getStringExtra("RUN_ID")
        val configJson = intent.getStringExtra("CONFIG_JSON")

        Log.i(TAG, "Alarm fired for Strike! Run ID: $runId")

        if (runId.isNullOrEmpty() || configJson.isNullOrEmpty()) {
            Log.e(TAG, "Strike metadata is missing. Cannot start engine.")
            return
        }

        // 2. Prepare the intent for the Foreground Service
        val serviceIntent = Intent(context, BookingForegroundService::class.java).apply {
            putExtra("RUN_ID", runId)
            putExtra("CONFIG_JSON", configJson)
        }

        // 3. Launch the Service
        // context.startForegroundService() is required for Android 8.0+ 
        // as the app is likely in the background when the alarm fires.
        try {
            context.startForegroundService(serviceIntent)
            Log.d(TAG, "BookingForegroundService handoff successful.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start BookingForegroundService: ${e.message}")
        }
    }
}
