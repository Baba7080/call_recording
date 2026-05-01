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
    
    val allCallLogs: Flow<List<CallLogEntity>> = database.logsDao().getAllCallLogs()
    
    val filteredCallLogs: Flow<List<CallLogEntity>> = snapshotFlow { searchQuery }.combine(allCallLogs) { query, logs ->
        if (query.isBlank()) logs
        else logs.filter { it.universityName.contains(query, ignoreCase = true) || it.ownerName.contains(query, ignoreCase = true) }
    }

    val allRecordings: Flow<List<RecordingEntity>> = database.logsDao().getAllRecordings()

    val callStats: Flow<CallStats> = filteredCallLogs.map { logs ->
        val hourlyMap = logs.groupBy { 
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = it.dateMillis }
            cal.get(java.util.Calendar.HOUR_OF_DAY)
        }.mapValues { it.value.size }

        CallStats(
            incomingCount = logs.count { it.callType == "INCOMING" },
            outgoingCount = logs.count { it.callType == "OUTGOING" },
            missedCount = logs.count { it.callType == "MISSED" },
            totalDurationSeconds = logs.sumOf { it.durationSeconds },
            hourlyCounts = hourlyMap
        )
    }

    fun isUserRegistered(): Boolean = prefs.getString("user_number", null) != null

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
            val url = java.net.URL("http://192.168.1.100:5001/api/logs/sync") // Updated to port 5001
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
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
                    "time": ${log.dateMillis}
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
    val hourlyCounts: Map<Int, Int> = emptyMap()
)
