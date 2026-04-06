
package com.apptcheck.agent

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope // Added for 2026 scope management
import com.apptcheck.agent.scheduler.StrikeReceiver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rust_engine.* 

class MainActivity : ComponentActivity() {
    private val agent = BookingAgent()
    private val logs = mutableStateListOf<String>()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Permissions required for background operation", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        checkAndRequestPermissions()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    DashboardScreen(agent, logs) { runId, configJson, delayMs ->
                        scheduleStrike(runId, configJson, delayMs)
                    }
                }
            }
        }

        lifecycleScope.launch {
            while (true) {
                val status = agent.getStatus()
                if (status.isRunning) {
                    if (logs.lastOrNull() != status.lastLog) {
                        logs.add("${System.currentTimeMillis()}: ${status.lastLog}")
                    }
                }
                delay(1000)
            }
        }
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                startActivity(intent)
            }
        }

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }

    private fun scheduleStrike(runId: String, configJson: String, delayMs: Long) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, StrikeReceiver::class.java).apply {
            action = "com.apptcheck.ACTION_STRIKE_ALARM"
            putExtra("RUN_ID", runId)
            putExtra("CONFIG_JSON", configJson)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this, runId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = System.currentTimeMillis() + delayMs
        
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerTime,
            pendingIntent
        )
        
        Toast.makeText(this, "Strike scheduled in ${delayMs / 1000}s", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun DashboardScreen(agent: BookingAgent, logs: List<String>, onSchedule: (String, String, Long) -> Unit) {
    var configText by remember { mutableStateOf("{\"mode\":\"alert\",\"scheduled_runs\":[]}") }
    
    Column(modifier = Modifier.padding(16.dp)) {
        Text("ApptCheck Agent Dashboard", style = MaterialTheme.typography.headlineMedium)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Engine Controls", style = MaterialTheme.typography.titleMedium)
                Button(
                    onClick = { 
                        val dummyRunId = "run-${System.currentTimeMillis()}"
                        onSchedule(dummyRunId, configText, 30000L) 
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Test Strike (30s delay)")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Live Engine Logs", style = MaterialTheme.typography.titleMedium)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(Color.Black)
                .padding(8.dp)
        ) {
            LazyColumn {
                items(logs.reversed()) { log ->
                    Text(
                        text = log,
                        color = Color.Green,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
