package com.caall.app.logs

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.caall.app.data.local.LogsDatabase
import com.caall.app.data.local.entity.CallLogEntity
import com.caall.app.data.local.entity.RecordingEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CallService : Service() {

    private val CHANNEL_ID = "CallServiceChannel"
    private lateinit var callRecorder: CallRecorder
    private lateinit var database: LogsDatabase
    private val scope = CoroutineScope(Dispatchers.IO)
    
    private var activeCallNumber: String? = null
    private var activeCallType: String = ""
    private var callStartTime: Long = 0
    private var currentRecordingPath: String? = null

    override fun onCreate() {
        super.onCreate()
        callRecorder = CallRecorder(this)
        database = LogsDatabase.getDatabase(this)
        createNotificationChannel()
        startForeground(1, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val state = intent?.getStringExtra("CALL_STATE")
        val number = intent?.getStringExtra("INCOMING_NUMBER")

        state?.let {
            handleCallState(it, number)
        }

        return START_STICKY
    }

    private fun handleCallState(state: String, number: String?) {
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                Log.d("CallService", "Ringing: $number")
                activeCallNumber = number
                activeCallType = "INCOMING"
                // Usually we don't start recording until OFFHOOK, but some apps start immediately.
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                Log.d("CallService", "Offhook (Call Answered or Dialing): $number")
                if (activeCallNumber == null) {
                    activeCallNumber = number
                    activeCallType = "OUTGOING" // Assuming if not ringing, it's outgoing
                }
                callStartTime = System.currentTimeMillis()
                currentRecordingPath = callRecorder.startRecording(activeCallNumber)
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                Log.d("CallService", "Idle (Call Ended)")
                if (activeCallNumber != null && callStartTime > 0) {
                    callRecorder.stopRecording()
                    val callDuration = (System.currentTimeMillis() - callStartTime) / 1000
                    saveCallData(activeCallType, activeCallNumber!!, callDuration, currentRecordingPath)
                } else if (activeCallNumber != null && callStartTime == 0L) {
                    // Missed Call scenario
                    saveCallData("MISSED", activeCallNumber!!, 0, null)
                }
                
                // Reset states
                activeCallNumber = null
                callStartTime = 0
                currentRecordingPath = null
                activeCallType = ""
            }
        }
    }

    private fun saveCallData(type: String, number: String, duration: Long, recPath: String?) {
        scope.launch {
            val prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
            val university = prefs.getString("university", "") ?: ""
            val owner = prefs.getString("owner", "") ?: ""
            val registeredNumber = prefs.getString("user_number", "") ?: ""

            val logEntity = CallLogEntity(
                fromNumber = if (type == "INCOMING" || type == "MISSED") number else registeredNumber,
                toNumber = if (type == "OUTGOING") number else registeredNumber,
                callType = type,
                durationSeconds = duration,
                dateMillis = System.currentTimeMillis(),
                simUsed = "Unknown",
                universityName = university,
                ownerName = owner,
                registeredNumber = registeredNumber
            )
            val logId = database.logsDao().insertCallLog(logEntity)
            Log.d("CallService", "Saved Call Log ID: $logId")

            if (recPath != null) {
                val recEntity = RecordingEntity(
                    callLogId = logId,
                    filePath = recPath,
                    durationSeconds = duration,
                    recordedAtMillis = System.currentTimeMillis()
                )
                database.logsDao().insertRecording(recEntity)
                Log.d("CallService", "Saved Recording for Log ID: $logId")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // Not bound
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Call Recording Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Call Recorder Active")
        .setContentText("Running in background to capture calls.")
        .setSmallIcon(android.R.drawable.ic_btn_speak_now) // placeholder icon
        .build()
}
