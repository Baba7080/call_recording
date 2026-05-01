package com.caall.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "call_logs",
    indices = [Index(value = ["nativeLogId"], unique = true)]
)
data class CallLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val nativeLogId: String? = null,
    val fromNumber: String,
    val toNumber: String,
    val callType: String, // INCOMING, OUTGOING, MISSED
    val durationSeconds: Long,
    val dateMillis: Long,
    val simUsed: String, // SIM 1, SIM 2
    val universityName: String = "",
    val ownerName: String = "",
    val registeredNumber: String = ""
)
