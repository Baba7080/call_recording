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
        LaunchedEffect(permissionsState.allPermissionsGranted) {
            if (permissionsState.allPermissionsGranted) {
                viewModel.syncCallLogs()
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
fun DashboardScreen(
    permissionsState: MultiplePermissionsState,
    viewModel: MainViewModel = viewModel()
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Dashboard Screen")
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

            OutlinedTextField(number, { number = it }, label = { Text("Your Number") })
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(university, { university = it }, label = { Text("University/Code") })
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(owner, { owner = it }, label = { Text("Owner Name") })

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
fun CallLogsScreen(viewModel: MainViewModel) {
    val callLogs by viewModel.filteredCallLogs.collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = viewModel.searchQuery,
            onValueChange = { viewModel.searchQuery = it },
            label = { Text("Search by University or Owner") },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = "Search")
            }
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(callLogs) { log ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(log.callType, fontWeight = FontWeight.Bold)
                        Text(formatDuration(log.durationSeconds))
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