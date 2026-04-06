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
import com.apptcheck.agent.data.models.ScheduledRun
import com.apptcheck.agent.ui.components.*
import java.time.format.DateTimeFormatter
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(viewModel: MainViewModel = viewModel()) {
    val uiSiteId by viewModel.uiSelectedSiteId.collectAsState()
    val config by viewModel.appConfig.collectAsState()
    val museums by viewModel.filteredMuseums.collectAsState()
    val credentials by viewModel.filteredCredentials.collectAsState()
    val engineStatus by viewModel.engineStatus.collectAsState()
    
    var showScheduleDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appt Agent v3.3") },
                actions = {
                    IconButton(onClick = { /* Export Logic via Intent */ }) { Icon(Icons.Default.Share, "Export") }
                    IconButton(onClick = { /* Import Logic via Intent */ }) { Icon(Icons.Default.Refresh, "Import") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                SmallFloatingActionButton(
                    onClick = { showScheduleDialog = true },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) { Icon(Icons.Default.DateRange, "Schedule") }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                FloatingActionButton(
                    onClick = { viewModel.scheduleStrike("manual-${System.currentTimeMillis()}", 30000L) },
                    containerColor = MaterialTheme.colorScheme.primary
                ) { Icon(Icons.Default.PlayArrow, "Strike Now") }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            EngineStatusBanner(engineStatus)
            
            SiteTabs(selectedSite = uiSiteId, onSelect = { viewModel.selectUiSite(it) })
            
            MuseumSection(
                museums = museums, 
                preferredSlug = config.sites[uiSiteId]?.preferredslug ?: "",
                onSelect = { viewModel.updatePreferredMuseum(uiSiteId, it) }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            CredentialSection(
                credentials = credentials.map { 
                    CredentialUiModel(it.id, it.name, it.username, it.password, it.email, it.site) 
                }.associateBy { it.id },
                onSave = { uiModel -> 
                    viewModel.saveCredential(
                        com.apptcheck.agent.data.models.Credential(
                            uiModel.id, uiModel.name, uiModel.username, uiModel.password, uiModel.email, uiModel.site
                        )
                    )
                },
                onDelete = { id -> viewModel.deleteCredential(id) }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            StrikeQueueSection(
                runs = config.scheduled_runs.filter { it.sitekey == uiSiteId },
                onCancel = { id -> viewModel.cancelRun(id) }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            LogConsole(viewModel.liveLogs)
            
            Spacer(modifier = Modifier.height(120.dp))
        }
    }

    if (showScheduleDialog) {
        ScheduleStrikeDialog(
            onDismiss = { showScheduleDialog = false },
            onConfirm = { ldt, zone, mode ->
                viewModel.createScheduledRun(uiSiteId, config.sites[uiSiteId]?.preferredslug ?: "", ldt, zone, mode)
                showScheduleDialog = false
            }
        )
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
            if (status.isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Default.Info, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = if (status.isRunning) "STRIKE ACTIVE" else "ENGINE IDLE", 
                    color = Color.White, 
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = status.lastLog, 
                    color = Color.White.copy(alpha = 0.7f), 
                    style = MaterialTheme.typography.bodySmall, 
                    maxLines = 1
                )
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
        Text("Preferred Museum", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        if (museums.isEmpty()) {
            Text(
                "No museums configured for this site.", 
                style = MaterialTheme.typography.bodySmall, 
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            FlowRow(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                museums.forEach { museum ->
                    FilterChip(
                        selected = museum.slug == preferredSlug,
                        onClick = { onSelect(museum.slug) },
                        label = { Text(museum.name) },
                        leadingIcon = if (museum.slug == preferredSlug) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }
        }
    }
}

@Composable
fun CredentialSection(
    credentials: Map<String, CredentialUiModel>,
    onSave: (CredentialUiModel) -> Unit,
    onDelete: (String) -> Unit
) {
    CredentialEditor(
        credentials = credentials,
        onSave = onSave,
        onDelete = onDelete
    )
}

@Composable
fun StrikeQueueSection(runs: List<ScheduledRun>, onCancel: (String) -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Upcoming Strikes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        if (runs.isEmpty()) {
            Text("No strikes scheduled.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        } else {
            runs.forEach { run ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(run.museumslug, style = MaterialTheme.typography.titleSmall)
                            val instant = java.time.Instant.parse(run.droptime)
                            val local = instant.atZone(java.time.ZoneId.systemDefault())
                            Text(
                                "Drop: ${local.format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, HH:mm"))}", 
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        IconButton(onClick = { onCancel(run.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LogConsole(logs: List<String>) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Engine Logs", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
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
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}
