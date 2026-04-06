package com.apptcheck.agent.ui

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apptcheck.agent.data.StorageManager
import com.apptcheck.agent.scheduler.StrikeReceiver
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import rust_engine.* // Import UniFFI Rust bindings

/**
 * MainViewModel manages the UI state and orchestrates the interaction
 * between the StorageManager and the native Rust Engine.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "MainViewModel"
    private val storage = StorageManager(application)
    private val agent = BookingAgent()

    // Reactive UI State
    private val _configJson = MutableStateFlow(storage.loadConfigRaw())
    val configJson = _configJson.asStateFlow()

    private val _engineStatus = MutableStateFlow(EngineStatus(false, "Idle", null))
    val engineStatus = _engineStatus.asStateFlow()

    val liveLogs = mutableStateListOf<String>()

    init {
        startStatusPolling()
    }

    /**
     * Periodically polls the Rust Engine for status and logs.
     * This replaces the WebSocket-based log viewer from the Go version.
     */
    private fun startStatusPolling() {
        viewModelScope.launch {
            while (true) {
                try {
                    val status = agent.getStatus()
                    _engineStatus.value = status
                    
                    if (status.isRunning && status.lastLog.isNotEmpty()) {
                        if (liveLogs.isEmpty() || liveLogs.last() != status.lastLog) {
                            liveLogs.add(status.lastLog)
                            // Keep logs manageable (last 500 lines)
                            if (liveLogs.size > 500) liveLogs.removeAt(0)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error polling engine status: ${e.message}")
                }
                delay(1000) // Poll every second
            }
        }
    }

    /**
     * Updates and persists the configuration.
     */
    fun updateConfig(newJson: String) {
        _configJson.value = newJson
        storage.saveConfig(newJson)
    }

    /**
     * Schedules a strike using the Android AlarmManager.
     * calculates the trigger time based on the pre-warm offset (Go parity).
     */
    fun scheduleStrike(runId: String, triggerTimeMillis: Long) {
        val context = getApplication<Application>().applicationContext
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val intent = Intent(context, StrikeReceiver::class.java).apply {
            action = "com.apptcheck.ACTION_STRIKE_ALARM"
            putExtra("RUN_ID", runId)
            putExtra("CONFIG_JSON", _configJson.value)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context, 
            runId.hashCode(), 
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Exact scheduling even in Doze mode
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerTimeMillis,
            pendingIntent
        )
        
        Log.i(TAG, "Strike $runId scheduled for timestamp: $triggerTimeMillis")
    }

    /**
     * Immediate cancellation of a run (both scheduled and active).
     */
    fun cancelRun(runId: String) {
        val context = getApplication<Application>().applicationContext
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        // Cancel the alarm
        val intent = Intent(context, StrikeReceiver::class.java).apply {
            action = "com.apptcheck.ACTION_STRIKE_ALARM"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, runId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)

        // Stop the Rust Engine if it's currently running this ID
        viewModelScope.launch {
            if (_engineStatus.value.currentRunId == runId) {
                agent.stop()
            }
        }
    }

    /**
     * Export functionality for complete backup.
     */
    fun getBackupPayload(): String = storage.getBackupData()

    /**
     * Restore functionality for configuration import.
     */
    fun restoreFromBackup(payload: String) {
        storage.restoreBackup(payload)
        _configJson.value = payload
    }
}
