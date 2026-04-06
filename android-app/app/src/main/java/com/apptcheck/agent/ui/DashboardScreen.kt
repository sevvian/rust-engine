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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apptcheck.agent.data.models.Museum
import com.apptcheck.agent.ui.components.CredentialEditor
import com.apptcheck.agent.ui.components.CredentialUiModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(viewModel: MainViewModel = viewModel()) {
    val uiSiteId by viewModel.uiSelectedSiteId.collectAsState()
    val config by viewModel.appConfig.collectAsState()
    val museums by viewModel.filteredMuseums.collectAsState()
    val credentials by viewModel.filteredCredentials.collectAsState()
    val engineStatus by viewModel.engineStatus.collectAsState()
    
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appt Agent v3.2") },
                actions = {
                    IconButton(onClick = { /* Export Logic */ }) { Icon(Icons.Default.Share, "Export") }
                    IconButton(onClick = { /* Import Logic */ }) { Icon(Icons.Default.Refresh, "Import") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.scheduleStrike("manual-${System.currentTimeMillis()}", 30000L) }) {
                Icon(Icons.Default.PlayArrow, "Strike Now")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // 1. Status Banner
            EngineStatusBanner(engineStatus)

            // 2. Site Tabs
            SiteTabs(selectedSite = uiSiteId, onSelect = { viewModel.selectUiSite(it) })

            // 3. Museums List (Updates automatically based on uiSiteId)
            MuseumSection(
                museums = museums, 
                preferredSlug = config.sites[uiSiteId]?.preferredslug ?: "",
                onSelect = { viewModel.updatePreferredMuseum(uiSiteId, it) }
            )

            // 4. Credential List (Updates automatically based on uiSiteId)
            CredentialSection(
                credentials = credentials,
                onSave = { viewModel.saveCredential(it) },
                onDelete = { viewModel.deleteCredential(it) }
            )

            // 5. Strike Queue
            StrikeQueueSection(config.scheduled_runs)

            // 6. Live Logs
            LogConsole(viewModel.liveLogs)
            
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
fun EngineStatusBanner(status: rust_engine.EngineStatus) {
    val bgColor = if (status.isRunning) Color(0xFF2E7D32) else Color(0xFF455A64)
    Surface(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        color = bgColor,
        shape = MaterialTheme.shapes.medium,
        shadowElevation = 4.dp
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = if (status.isRunning) Color.White else Color.Transparent,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(if (status.isRunning) "STRIKE ACTIVE" else "ENGINE IDLE", color = Color.White, style = MaterialTheme.typography.titleSmall)
                Text(status.lastLog, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
    }
}

@Composable
fun SiteTabs(selectedSite: String, onSelect: (String) -> Unit) {
    TabRow(selectedTabIndex = if (selectedSite == "spl") 0 else 1) {
        Tab(selected = selectedSite == "spl", onClick = { onSelect("spl") }, text = { Text("SPL") })
        Tab(selected = selectedSite == "kcls", onClick = { onSelect("kcls") }, text = { Text("KCLS") })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MuseumSection(museums: List<Museum>, preferredSlug: String, onSelect: (String) -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Preferred Museum", style = MaterialTheme.typography.labelLarge)
        if (museums.isEmpty()) {
            Text("No museums configured for this site.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        } else {
            FlowRow(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                museums.forEach { museum ->
                    FilterChip(
                        selected = museum.slug == preferredSlug,
                        onClick = { onSelect(museum.slug) },
                        label = { Text(museum.name) }
                    )
                }
            }
        }
    }
}

@Composable
fun LogConsole(logs: List<String>) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Engine Logs", style = MaterialTheme.typography.labelLarge)
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
                    Text("> $log", color = Color.Green, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                }
            }
        }
    }
}

// Note: CredentialSection and StrikeQueueSection would follow the same pattern, 
// calling into existing UI components provided in File 18.
