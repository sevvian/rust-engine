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
import com.apptcheck.agent.data.models.*
import com.apptcheck.agent.scheduler.StrikeReceiver
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import rust_engine.* // Native UniFFI bindings

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "MainViewModel"
    private val storage = StorageManager(application)
    private val agent = BookingAgent()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // 1. RAW STATE: The encrypted JSON string from storage
    private val _configJsonRaw = MutableStateFlow(storage.loadConfigRaw())
    val configJsonRaw = _configJsonRaw.asStateFlow()

    // 2. PARSED STATE: Reactive AppConfig object
    val appConfig = _configJsonRaw.map { raw ->
        try { json.decodeFromString<AppConfig>(raw) } 
        catch (e: Exception) { AppConfig() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppConfig())

    // 3. UI STATE: Which site is the user currently LOOKING at (not necessarily the active strike site)
    private val _uiSelectedSiteId = MutableStateFlow("spl")
    val uiSelectedSiteId = _uiSelectedSiteId.asStateFlow()

    // 4. DERIVED STATE: Automatically filtered lists for the UI
    val filteredMuseums = combine(appConfig, _uiSelectedSiteId) { config, siteId ->
        config.sites[siteId]?.museums?.values?.toList() ?: emptyList()
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val filteredCredentials = combine(appConfig, _uiSelectedSiteId) { config, siteId ->
        config.credentials.values.filter { it.site == siteId }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // 5. ENGINE STATE: Status from the Rust Core
    private val _engineStatus = MutableStateFlow(EngineStatus(false, "Idle", null))
    val engineStatus = _engineStatus.asStateFlow()
    val liveLogs = mutableStateListOf<String>()

    init {
        startStatusPolling()
    }

    private fun startStatusPolling() {
        viewModelScope.launch {
            while (true) {
                try {
                    val status = agent.getStatus()
                    _engineStatus.value = status
                    if (status.isRunning && status.lastLog.isNotEmpty()) {
                        if (liveLogs.isEmpty() || liveLogs.last() != status.lastLog) {
                            liveLogs.add(status.lastLog)
                            if (liveLogs.size > 500) liveLogs.removeAt(0)
                        }
                    }
                } catch (e: Exception) { Log.e(TAG, "Polling error: ${e.message}") }
                delay(1000)
            }
        }
    }

    // --- UI Actions ---

    fun selectUiSite(siteId: String) {
        _uiSelectedSiteId.value = siteId
    }

    fun updatePreferredMuseum(siteId: String, museumSlug: String) {
        val current = appConfig.value
        val site = current.sites[siteId] ?: return
        val updatedSite = site.copy(preferredslug = museumSlug)
        val updatedSites = current.sites.toMutableMap().apply { put(siteId, updatedSite) }
        saveFullConfig(current.copy(sites = updatedSites))
    }

    fun saveCredential(cred: Credential) {
        val current = appConfig.value
        val updatedCreds = current.credentials.toMutableMap().apply { put(cred.id, cred) }
        saveFullConfig(current.copy(credentials = updatedCreds))
    }

    fun deleteCredential(id: String) {
        val current = appConfig.value
        val updatedCreds = current.credentials.toMutableMap().apply { remove(id) }
        saveFullConfig(current.copy(credentials = updatedCreds))
    }

    private fun saveFullConfig(config: AppConfig) {
        val raw = json.encodeToString(config)
        storage.saveConfig(raw)
        _configJsonRaw.value = raw
    }

    fun restoreBackup(rawJson: String) {
        storage.restoreBackup(rawJson)
        _configJsonRaw.value = rawJson
    }

    fun scheduleStrike(runId: String, delayMs: Long) {
        val context = getApplication<Application>().applicationContext
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, StrikeReceiver::class.java).apply {
            action = "com.apptcheck.ACTION_STRIKE_ALARM"
            putExtra("RUN_ID", runId)
            putExtra("CONFIG_JSON", _configJsonRaw.value)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, runId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delayMs, pendingIntent)
    }
}
