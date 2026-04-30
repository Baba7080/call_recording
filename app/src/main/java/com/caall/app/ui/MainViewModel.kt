package com.caall.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.caall.app.data.local.LogsDatabase
import com.caall.app.data.local.entity.CallLogEntity
import com.caall.app.data.local.entity.RecordingEntity
import kotlinx.coroutines.flow.Flow

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = LogsDatabase.getDatabase(application)

    val allCallLogs: Flow<List<CallLogEntity>> = database.logsDao().getAllCallLogs()
    val allRecordings: Flow<List<RecordingEntity>> = database.logsDao().getAllRecordings()
}
