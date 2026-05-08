package com.caall.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import android.media.MediaPlayer
import android.app.DatePickerDialog
import java.io.File
import java.util.*

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    permissionsState: MultiplePermissionsState,
    viewModel: MainViewModel = viewModel()
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Dashboard", "Call Logs", "Recordings")

    var showRegistration by remember { mutableStateOf(!viewModel.isUserRegistered()) }

    if (showRegistration) {
        RegistrationScreen { number, university, owner ->
            viewModel.registerUser(number, university, owner)
            showRegistration = false
        }
    } else {
        val context = LocalContext.current
        LaunchedEffect(permissionsState.allPermissionsGranted) {
            if (permissionsState.allPermissionsGranted) {
                viewModel.syncCallLogs()
            }
            // Always sync status so admin knows which permissions are missing
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.caall.app.data.RemoteSyncHelper.syncStatusToRemote(context)
            }
        }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("ByteDail", fontWeight = FontWeight.Bold) }
                )
            },
            bottomBar = {
                NavigationBar {
                    tabs.forEachIndexed { index, title ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = {},
                            label = { Text(title) }
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                when (selectedTab) {
                    0 -> DashboardScreen(permissionsState, viewModel)
                    1 -> CallLogsScreen(viewModel)
                    2 -> RecordingsScreen(viewModel)
                }
            }
        }
    }
}
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun DashboardScreen(permissionsState: MultiplePermissionsState, viewModel: MainViewModel = viewModel()) {
    val stats by viewModel.callStats.collectAsState(initial = CallStats())
    val context = LocalContext.current
    val calendar = Calendar.getInstance().apply { timeInMillis = viewModel.selectedDate }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Stats for ${java.text.SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(viewModel.selectedDate))}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = {
                DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        val cal = Calendar.getInstance().apply {
                            set(year, month, dayOfMonth, 0, 0, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        viewModel.selectedDate = cal.timeInMillis
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                ).show()
            }) {
                Icon(Icons.Default.DateRange, contentDescription = "Select Date", tint = MaterialTheme.colorScheme.primary)
            }
        }

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
        Text("Hourly Duration Report (24h)", style = MaterialTheme.typography.titleLarge, modifier = Modifier.align(Alignment.Start))
        Spacer(modifier = Modifier.height(8.dp))
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                (0..23).forEach { hour ->
                    val duration = stats.hourlyDurations[hour] ?: 0L
                    val count = stats.hourlyCounts[hour] ?: 0
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "%02d:00".format(hour), style = MaterialTheme.typography.bodyMedium, color = if (count > 0) MaterialTheme.colorScheme.primary else Color.Gray)
                        Text(
                            text = "$count calls | ${formatDuration(duration)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (count > 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (count > 0) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (permissionsState.allPermissionsGranted) {
            Text("Service Status: Active", style = MaterialTheme.typography.bodyLarge, color = Color.Green)
        } else {
            Button(onClick = { permissionsState.launchMultiplePermissionRequest() }) {
                Text("Grant Permissions")
            }
        }
    }
}

@Composable
fun RegistrationScreen(onRegister: (String, String, String) -> Unit) {
    var number by remember { mutableStateOf("") }
    var university by remember { mutableStateOf("") }
    var owner by remember { mutableStateOf("") }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Register Device", style = MaterialTheme.typography.headlineLarge)

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(number, { number = it }, label = { Text("Your Number") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(university, { university = it }, label = { Text("University/Code") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(owner, { owner = it }, label = { Text("Owner Name") }, modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (number.isNotBlank() && university.isNotBlank() && owner.isNotBlank()) {
                        onRegister(number, university, owner)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start Logging")
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
            Text(value, style = MaterialTheme.typography.headlineSmall, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun CallLogsScreen(viewModel: MainViewModel) {
    val callLogs by viewModel.filteredCallLogs.collectAsState(initial = emptyList())
    
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = viewModel.searchQuery,
            onValueChange = { viewModel.searchQuery = it },
            label = { Text("Search by University or Owner") },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") }
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(callLogs) { log ->
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(
                                    text = log.callType,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = when(log.callType) {
                                        "INCOMING" -> Color(0xFF4CAF50)
                                        "OUTGOING" -> Color(0xFF2196F3)
                                        else -> Color(0xFFF44336)
                                    }
                                )
                                Text(
                                    text = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(log.dateMillis)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                            Text(
                                text = formatDuration(log.durationSeconds),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("From", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                Text(log.fromNumber, style = MaterialTheme.typography.bodyMedium)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("To", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                Text(log.toNumber, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Owner", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                Text(log.ownerName.ifBlank { "N/A" }, style = MaterialTheme.typography.bodyMedium)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("University Code", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                Text(log.universityName.ifBlank { "N/A" }, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecordingsScreen(viewModel: MainViewModel) {
    val recordings by viewModel.recordingsWithDetails.collectAsState(initial = emptyList())

    LazyColumn {
        items(recordings) { item ->
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(modifier = Modifier.padding(16.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Duration: ${formatDuration(item.recording.durationSeconds)}")
                    }
                    RecordingPlayer(item.recording.filePath)
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
        }
    }

    IconButton(onClick = {
        if (isPlaying) {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            isPlaying = false
        } else {
            val file = File(filePath)
            if (file.exists()) {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(filePath)
                    prepare()
                    start()
                    setOnCompletionListener {
                        isPlaying = false
                        release()
                    }
                }
                isPlaying = true
            }
        }
    }) {
        Icon(
            imageVector = if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
            contentDescription = null
        )
    }
}

fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "$m:$s"
}