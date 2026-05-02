package com.caall.app.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.caall.app.data.local.LogsDatabase
import com.caall.app.data.local.entity.CallLogEntity
import com.caall.app.data.local.entity.RecordingEntity
import com.caall.app.logs.NativeCallLogHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = LogsDatabase.getDatabase(application)
    private val prefs = application.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    var searchQuery by mutableStateOf("")
    var selectedDate by mutableStateOf(java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis)
    
    val allCallLogs: Flow<List<CallLogEntity>> = database.logsDao().getAllCallLogs()
    
    val filteredCallLogs: Flow<List<CallLogEntity>> = snapshotFlow { searchQuery }.combine(allCallLogs) { query, logs ->
        if (query.isBlank()) logs
        else logs.filter { it.universityName.contains(query, ignoreCase = true) || it.ownerName.contains(query, ignoreCase = true) }
    }

    val allRecordings: Flow<List<RecordingEntity>> = database.logsDao().getAllRecordings()
    val recordingsWithDetails: Flow<List<com.caall.app.data.local.dao.RecordingWithLog>> = database.logsDao().getRecordingsWithLogs()

    val userNumber: String get() = prefs.getString("user_number", "") ?: ""

    val callStats: Flow<CallStats> = allCallLogs.map { logs ->
        // Filter by user and selected date
        val currentUserLogs = logs.filter { it.registeredNumber == userNumber }
        
        val filteredByDate = currentUserLogs.filter { log ->
            val logCal = java.util.Calendar.getInstance().apply { timeInMillis = log.dateMillis }
            val selectedCal = java.util.Calendar.getInstance().apply { timeInMillis = selectedDate }
            logCal.get(java.util.Calendar.YEAR) == selectedCal.get(java.util.Calendar.YEAR) &&
            logCal.get(java.util.Calendar.DAY_OF_YEAR) == selectedCal.get(java.util.Calendar.DAY_OF_YEAR)
        }
        
        val hourlyCountsMap = filteredByDate.groupBy { 
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = it.dateMillis }
            cal.get(java.util.Calendar.HOUR_OF_DAY)
        }.mapValues { it.value.size }

        val hourlyDurationsMap = filteredByDate.groupBy { 
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = it.dateMillis }
            cal.get(java.util.Calendar.HOUR_OF_DAY)
        }.mapValues { it.value.sumOf { log -> log.durationSeconds } }

        CallStats(
            incomingCount = filteredByDate.count { it.callType == "INCOMING" },
            outgoingCount = filteredByDate.count { it.callType == "OUTGOING" },
            missedCount = filteredByDate.count { it.callType == "MISSED" },
            totalDurationSeconds = filteredByDate.sumOf { it.durationSeconds },
            hourlyCounts = hourlyCountsMap,
            hourlyDurations = hourlyDurationsMap
        )
    }

    fun isUserRegistered(): Boolean = prefs.getString("user_number", null) != null

    private var cachedApiKey: String? = null

    private suspend fun fetchApiKey(): String? {
        if (cachedApiKey != null) return cachedApiKey
        return try {
            val url = java.net.URL("http://10.0.2.2:3000/api/get-key") // Emulator access to localhost
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            val text = conn.inputStream.bufferedReader().readText()
            val key = org.json.JSONObject(text).getString("apiKey")
            cachedApiKey = key
            key
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun registerUser(number: String, university: String, owner: String) {
        prefs.edit().apply {
            putString("user_number", number)
            putString("university", university)
            putString("owner", owner)
            apply()
        }
    }

    fun syncCallLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val nativeLogs = NativeCallLogHelper.fetchNativeCallLogs(context)
            if (nativeLogs.isNotEmpty()) {
                database.logsDao().insertCallLogs(nativeLogs)
                syncLogsToRemote()
            }
        }
    }

    private suspend fun syncLogsToRemote() {
        val unsynced = database.logsDao().getUnsyncedLogs()
        if (unsynced.isEmpty()) return

        try {
            val key = fetchApiKey() ?: "wl_GlKP0jTOnKQb8NBJeqAdvRRCsMgY5qoW" // Fallback if service is down
            val url = java.net.URL("https://demo.bytelinkup.com/api/logs/call")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("X-API-Key", key)
            conn.doOutput = true

            val jsonLogs = unsynced.joinToString(",") { log ->
                """
                {
                    "callId": "${log.nativeLogId}",
                    "from": "${log.fromNumber}",
                    "to": "${log.toNumber}",
                    "duration": ${log.durationSeconds},
                    "type": "${log.callType}",
                    "university": "${log.universityName}",
                    "owner": "${log.ownerName}",
                    "time": ${log.dateMillis},
                    "status": "${if(log.callType == "MISSED") "missed" else "completed"}"
                }
                """.trimIndent()
            }
            val body = """{"logs": [$jsonLogs]}"""

            conn.outputStream.use { it.write(body.toByteArray()) }

            if (conn.responseCode == 200) {
                database.logsDao().markLogsAsSynced(unsynced.map { it.id })
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

data class CallStats(
    val incomingCount: Int = 0,
    val outgoingCount: Int = 0,
    val missedCount: Int = 0,
    val totalDurationSeconds: Long = 0,
    val hourlyCounts: Map<Int, Int> = emptyMap(),
    val hourlyDurations: Map<Int, Long> = emptyMap()
)
