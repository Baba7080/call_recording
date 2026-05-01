package com.caall.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import android.media.MediaPlayer
import java.io.File

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
fun DashboardScreen(permissionsState: MultiplePermissionsState, viewModel: MainViewModel = viewModel()) {
    val stats by viewModel.callStats.collectAsState(initial = CallStats())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text("Call Statistics", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 16.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatCard("Incoming", stats.incomingCount.toString(), Color(0xFF4CAF50))
            StatCard("Outgoing", stats.outgoingCount.toString(), Color(0xFF2196F3))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatCard("Missed", stats.missedCount.toString(), Color(0xFFF44336))
            StatCard("Total Duration", formatDuration(stats.totalDurationSeconds), Color(0xFF9C27B0))
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (permissionsState.allPermissionsGranted) {
            Text("Status: Active", style = MaterialTheme.typography.bodyLarge, color = Color.Green)
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
fun StatCard(label: String, value: String, color: Color) {
    Card(
        modifier = Modifier.width(160.dp).padding(4.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = color)
            Text(value, style = MaterialTheme.typography.headlineSmall, color = color)
        }
    }
}

fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%dh %dm %ds".format(h, m, s) else "%dm %ds".format(m, s)
}

@Composable
fun CallLogsScreen(viewModel: MainViewModel) {
    val callLogs by viewModel.allCallLogs.collectAsState(initial = emptyList())
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(callLogs) { log ->
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = log.callType,
                            style = MaterialTheme.typography.labelLarge,
                            color = when(log.callType) {
                                "INCOMING" -> Color(0xFF4CAF50)
                                "OUTGOING" -> Color(0xFF2196F3)
                                else -> Color(0xFFF44336)
                            }
                        )
                        Text(text = formatDuration(log.durationSeconds), style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("From: ${log.fromNumber}", style = MaterialTheme.typography.titleMedium)
                    Text("To: ${log.toNumber}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(log.dateMillis)),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
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
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Log ID: ${rec.callLogId}", style = MaterialTheme.typography.titleMedium)
                            Text("Duration: ${formatDuration(rec.durationSeconds)}", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(rec.recordedAtMillis)),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                        RecordingPlayer(rec.filePath)
                    }
                }
            }
        }
    }
}

@Composable
fun RecordingPlayer(filePath: String) {
    var isPlaying by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    IconButton(
        onClick = {
            if (isPlaying) {
                mediaPlayer?.stop()
                mediaPlayer?.release()
                mediaPlayer = null
                isPlaying = false
            } else {
                val file = File(filePath)
                if (file.exists()) {
                    try {
                        mediaPlayer = MediaPlayer().apply {
                            setDataSource(filePath)
                            prepare()
                            start()
                            setOnCompletionListener {
                                isPlaying = false
                                release()
                                mediaPlayer = null
                            }
                        }
                        isPlaying = true
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Stop" else "Play",
            tint = MaterialTheme.colorScheme.primary
        )
    }
}
