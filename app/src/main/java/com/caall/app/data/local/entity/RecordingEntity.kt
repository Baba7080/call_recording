package com.caall.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

@Entity(
    tableName = "recordings",
    foreignKeys = [
        ForeignKey(
            entity = CallLogEntity::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("callLogId"),
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val callLogId: Long,
    val filePath: String,
    val durationSeconds: Long,
    val recordedAtMillis: Long
)
