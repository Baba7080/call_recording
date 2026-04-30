package com.caall.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_logs")
data class CallLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fromNumber: String,
    val toNumber: String,
    val callType: String, // INCOMING, OUTGOING, MISSED
    val durationSeconds: Long,
    val dateMillis: Long,
    val simUsed: String // SIM 1, SIM 2
)
