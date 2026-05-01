package com.caall.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.caall.app.data.local.LogsDatabase
import com.caall.app.data.local.entity.CallLogEntity
import com.caall.app.data.local.entity.RecordingEntity
import com.caall.app.logs.NativeCallLogHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = LogsDatabase.getDatabase(application)

    val allCallLogs: Flow<List<CallLogEntity>> = database.logsDao().getAllCallLogs()
    val allRecordings: Flow<List<RecordingEntity>> = database.logsDao().getAllRecordings()

    val callStats: Flow<CallStats> = allCallLogs.map { logs ->
        CallStats(
            incomingCount = logs.count { it.callType == "INCOMING" },
            outgoingCount = logs.count { it.callType == "OUTGOING" },
            missedCount = logs.count { it.callType == "MISSED" },
            totalDurationSeconds = logs.sumOf { it.durationSeconds }
        )
    }

    fun syncCallLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val nativeLogs = NativeCallLogHelper.fetchNativeCallLogs(context)
            if (nativeLogs.isNotEmpty()) {
                database.logsDao().insertCallLogs(nativeLogs)
            }
        }
    }
}

data class CallStats(
    val incomingCount: Int = 0,
    val outgoingCount: Int = 0,
    val missedCount: Int = 0,
    val totalDurationSeconds: Long = 0
)
