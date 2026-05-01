package com.caall.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(
    permissionsState: MultiplePermissionsState,
    viewModel: MainViewModel = viewModel()
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Dashboard", "Call Logs", "Recordings")

    LaunchedEffect(permissionsState.allPermissionsGranted) {
        if (permissionsState.allPermissionsGranted) {
            viewModel.syncCallLogs()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { /* Add Icons here if needed */ },
                        label = { Text(title) }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab) {
                0 -> DashboardScreen(permissionsState)
                1 -> CallLogsScreen(viewModel)
                2 -> RecordingsScreen(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun DashboardScreen(permissionsState: MultiplePermissionsState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (permissionsState.allPermissionsGranted) {
            Text("Ready to Record", style = MaterialTheme.typography.headlineMedium, color = Color.Green)
            Text("Ready to Fetch Logs", style = MaterialTheme.typography.bodyLarge)
            Text("Background Running", style = MaterialTheme.typography.bodyLarge)
        } else {
            Text("Permission Missing", style = MaterialTheme.typography.headlineMedium, color = Color.Red)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { permissionsState.launchMultiplePermissionRequest() }) {
                Text("Grant Permissions")
            }
        }
    }
}

@Composable
fun CallLogsScreen(viewModel: MainViewModel) {
    val callLogs by viewModel.allCallLogs.collectAsState(initial = emptyList())
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(callLogs) { log ->
            Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Type: ${log.callType}", style = MaterialTheme.typography.titleMedium)
                    Text("From: ${log.fromNumber} - To: ${log.toNumber}")
                    Text("Duration: ${log.durationSeconds}s")
                }
            }
        }
    }
}

@Composable
fun RecordingsScreen(viewModel: MainViewModel) {
    val recordings by viewModel.allRecordings.collectAsState(initial = emptyList())
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(recordings) { rec ->
            Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Log ID: ${rec.callLogId}", style = MaterialTheme.typography.titleMedium)
                    Text("Duration: ${rec.durationSeconds}s")
                    Text("File: ${rec.filePath}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
