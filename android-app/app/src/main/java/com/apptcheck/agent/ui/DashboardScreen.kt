package com.apptcheck.agent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apptcheck.agent.ui.components.CredentialEditor
import com.apptcheck.agent.ui.components.CredentialUiModel
import kotlinx.serialization.json.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * DashboardScreen is the primary UI for the Appointment Agent.
 * It integrates Site configuration, Strike Scheduling, and Live Logs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: MainViewModel = viewModel()) {
    val configJson by viewModel.configJson.collectAsState()
    val engineStatus by viewModel.engineStatus.collectAsState()
    val logs = viewModel.liveLogs
    
    // Local UI state for Site selection
    var activeSite by remember { mutableStateOf("spl") }
    var selectedMuseum by remember { mutableStateOf("") }
    
    // Parse Config for UI display
    val configObj = remember(configJson) { 
        try { Json.parseToJsonElement(configJson).jsonObject } catch (e: Exception) { buildJsonObject {} }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appt Agent v3.2") },
                actions = {
                    IconButton(onClick = { /* Trigger Export via Intent */ }) {
                        Icon(Icons.Default.Share, contentDescription = "Export Backup")
                    }
                    IconButton(onClick = { /* Trigger Import via Intent */ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Import Backup")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { 
                    val runId = "manual-${System.currentTimeMillis()}"
                    // Trigger immediate strike (30s prep) via AlarmManager
                    viewModel.scheduleStrike(runId, System.currentTimeMillis() + 30000)
                },
                icon = { Icon(Icons.Default.PlayArrow, "Run") },
                text = { Text("Strike Now") },
                containerColor = MaterialTheme.colorScheme.primary
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // 1. Status Banner
            StatusBanner(engineStatus)

            // 2. Site Selector
            SiteSelector(activeSite) { activeSite = it }

            // 3. Museum Chips (Dynamic based on site)
            MuseumPills(activeSite, selectedMuseum) { selectedMuseum = it }

            // 4. Credential Manager (Integrated from File 18)
            CredentialEditor(
                credentials = emptyMap(), // Connect to ViewModel credentials map in real impl
                onSave = { /* Update VM */ },
                onDelete = { /* Update VM */ }
            )

            // 5. Strike Queue
            StrikeQueue(viewModel)

            // 6. Live Engine Logs (Terminal Style)
            LogViewer(logs)
            
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun StatusBanner(status: rust_engine.EngineStatus) {
    val color = if (status.isRunning) Color(0xFF4CAF50) else Color(0xFF607D8B)
    Surface(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        color = color,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (status.isRunning) Icons.Default.Sync else Icons.Default.Info,
                contentDescription = null,
                tint = Color.white
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = if (status.isRunning) "Engine Active" else "Engine Idle",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = status.lastLog,
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun SiteSelector(selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = { onSelect("spl") },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (selected == "spl") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                contentColor = if (selected == "spl") Color.White else MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) { Text("SPL") }
        
        Button(
            onClick = { onSelect("kcls") },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (selected == "kcls") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                contentColor = if (selected == "kcls") Color.White else MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) { Text("KCLS") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MuseumPills(site: String, selected: String, onSelect: (String) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text("Preferred Museum", style = MaterialTheme.typography.titleSmall)
        FlowRow(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Mock museums list - in full app, this pulls from AppConfig
            val museums = if (site == "spl") listOf("SAM", "Zoo", "MoM") else listOf("KidsQuest")
            museums.forEach { museum ->
                FilterChip(
                    selected = selected == museum,
                    onClick = { onSelect(museum) },
                    label = { Text(museum) }
                )
            }
        }
    }
}

@Composable
fun StrikeQueue(viewModel: MainViewModel) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Strike Queue", style = MaterialTheme.typography.titleSmall)
        // Dummy queue items
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DateRange, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Daily Strike (9:00 AM)", style = MaterialTheme.typography.bodyMedium)
                    Text("Mode: Booking | Site: SPL", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { /* Cancel */ }) {
                    Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun LogViewer(logs: List<String>) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.List, null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Engine Logs", style = MaterialTheme.typography.titleSmall)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .background(Color.Black, shape = MaterialTheme.shapes.small)
                .padding(8.dp)
        ) {
            LazyColumn {
                items(logs.asReversed()) { log ->
                    Text(
                        text = "> $log",
                        color = Color(0xFF00FF00),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}
