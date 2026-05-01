package com.caall.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Embedded
import androidx.room.Relation
import com.caall.app.data.local.entity.CallLogEntity
import com.caall.app.data.local.entity.RecordingEntity
import kotlinx.coroutines.flow.Flow

data class RecordingWithLog(
    @Embedded val recording: RecordingEntity,
    @Relation(
        parentColumn = "callLogId",
        entityColumn = "id"
    )
    val callLog: CallLogEntity
)

@Dao
interface LogsDao {
    @Insert
    suspend fun insertCallLog(callLog: CallLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCallLogs(callLogs: List<CallLogEntity>)

    @Insert
    suspend fun insertRecording(recording: RecordingEntity): Long

    @Query("SELECT * FROM call_logs ORDER BY dateMillis DESC")
    fun getAllCallLogs(): Flow<List<CallLogEntity>>

    @Query("SELECT * FROM call_logs WHERE isSynced = 0")
    suspend fun getUnsyncedLogs(): List<CallLogEntity>

    @Query("UPDATE call_logs SET isSynced = 1 WHERE id IN (:logIds)")
    suspend fun markLogsAsSynced(logIds: List<Long>)

    @Query("SELECT * FROM recordings ORDER BY recordedAtMillis DESC")
    fun getAllRecordings(): Flow<List<RecordingEntity>>

    @Transaction
    @Query("SELECT * FROM recordings ORDER BY recordedAtMillis DESC")
    fun getRecordingsWithLogs(): Flow<List<RecordingWithLog>>
}
