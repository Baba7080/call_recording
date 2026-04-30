package com.caall.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.caall.app.data.local.entity.CallLogEntity
import com.caall.app.data.local.entity.RecordingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LogsDao {
    @Insert
    suspend fun insertCallLog(callLog: CallLogEntity): Long

    @Insert
    suspend fun insertRecording(recording: RecordingEntity): Long

    @Query("SELECT * FROM call_logs ORDER BY dateMillis DESC")
    fun getAllCallLogs(): Flow<List<CallLogEntity>>

    @Query("SELECT * FROM recordings ORDER BY recordedAtMillis DESC")
    fun getAllRecordings(): Flow<List<RecordingEntity>>
}
