package com.caall.app.logs

import android.annotation.SuppressLint
import android.content.Context
import android.provider.CallLog
import android.util.Log
import com.caall.app.data.local.entity.CallLogEntity

object NativeCallLogHelper {

    @SuppressLint("Range")
    fun fetchNativeCallLogs(context: Context): List<CallLogEntity> {
        val callLogs = mutableListOf<CallLogEntity>()
        
        try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                null,
                null,
                null,
                CallLog.Calls.DATE + " DESC"
            )

            cursor?.use {
                val idIndex = it.getColumnIndex(CallLog.Calls._ID)
                val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
                val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)
                val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)
                val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)

                val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                val university = prefs.getString("university", "") ?: ""
                val owner = prefs.getString("owner", "") ?: ""
                val registeredNumber = prefs.getString("user_number", "") ?: ""

                while (it.moveToNext()) {
                    val id = it.getString(idIndex)
                    val number = it.getString(numberIndex) ?: "Unknown"
                    val type = it.getInt(typeIndex)
                    val dateMillis = it.getLong(dateIndex)
                    val durationSeconds = it.getLong(durationIndex)
                    
                    val callTypeStr = when (type) {
                        CallLog.Calls.INCOMING_TYPE -> "INCOMING"
                        CallLog.Calls.OUTGOING_TYPE -> "OUTGOING"
                        CallLog.Calls.MISSED_TYPE -> "MISSED"
                        CallLog.Calls.REJECTED_TYPE -> "MISSED"
                        else -> "UNKNOWN"
                    }

                    val fromNumber = if (callTypeStr == "INCOMING" || callTypeStr == "MISSED") number else registeredNumber
                    val toNumber = if (callTypeStr == "OUTGOING") number else registeredNumber

                    callLogs.add(
                        CallLogEntity(
                            nativeLogId = id,
                            fromNumber = fromNumber,
                            toNumber = toNumber,
                            callType = callTypeStr,
                            durationSeconds = durationSeconds,
                            dateMillis = dateMillis,
                            simUsed = "Unknown",
                            universityName = university,
                            ownerName = owner,
                            registeredNumber = registeredNumber
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.e("NativeCallLogHelper", "Missing READ_CALL_LOG permission", e)
        } catch (e: Exception) {
            Log.e("NativeCallLogHelper", "Error fetching call logs", e)
        }

        return callLogs
    }
}
