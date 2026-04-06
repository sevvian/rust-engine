package com.apptcheck.agent.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import com.apptcheck.agent.data.models.AppConfig // We will define this model next

/**
 * StorageManager provides encrypted persistence for the agent's configuration.
 * It ensures credentials remain safe at rest.
 */
class StorageManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "agent_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }

    private val CONFIG_KEY = "app_config_v3"

    /**
     * Saves the entire AppConfig as an encrypted JSON string.
     */
    fun saveConfig(config: String) {
        sharedPreferences.edit().putString(CONFIG_KEY, config).apply()
    }

    /**
     * Loads the AppConfig. Returns a default JSON if none exists.
     */
    fun loadConfigRaw(): String {
        return sharedPreferences.getString(CONFIG_KEY, null) ?: "{}"
    }

    /**
     * Complete Backup: Returns the encrypted string for export.
     */
    fun getBackupData(): String = loadConfigRaw()

    /**
     * Restore: Overwrites current config with imported data.
     */
    fun restoreBackup(data: String) {
        saveConfig(data)
    }
}
