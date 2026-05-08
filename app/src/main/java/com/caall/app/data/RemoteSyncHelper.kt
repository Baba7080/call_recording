package com.caall.app.data

import android.content.Context
import android.util.Log
import com.caall.app.data.local.entity.CallLogEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object RemoteSyncHelper {

    private const val TAG = "RemoteSyncHelper"
    private var cachedApiKey: String? = null

    private suspend fun fetchApiKey(context: Context): String {
        if (cachedApiKey != null) return cachedApiKey!!
        
        return try {
            // Try to fetch from the local server first (emulator setup)
            val url = URL("http://10.0.2.2:3000/api/get-key")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "GET"
            
            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader().readText()
                val key = JSONObject(text).getString("apiKey")
                cachedApiKey = key
                key
            } else {
                "wl_GlKP0jTOnKQb8NBJeqAdvRRCsMgY5qoW" // Fallback
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching API key: ${e.message}")
            "wl_GlKP0jTOnKQb8NBJeqAdvRRCsMgY5qoW" // Fallback
        }
    }

    suspend fun syncLogsToRemote(context: Context, logs: List<CallLogEntity>, onSynced: suspend (List<Long>) -> Unit) {
        if (logs.isEmpty()) return

        try {
            val key = fetchApiKey(context)
            val url = URL("https://demo.bytelinkup.com/api/logs/call")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("X-API-Key", key)
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val logsArray = JSONArray()
            logs.forEach { log ->
                val logObj = JSONObject().apply {
                    put("callId", log.nativeLogId ?: "app_${log.id}")
                    put("from", log.fromNumber)
                    put("to", log.toNumber)
                    put("duration", log.durationSeconds)
                    put("type", log.callType)
                    put("university", log.universityName)
                    put("owner", log.ownerName)
                    put("time", log.dateMillis)
                    put("status", if (log.callType == "MISSED") "missed" else "completed")
                }
                logsArray.put(logObj)
            }

            val root = JSONObject()
            root.put("logs", logsArray)

            val body = root.toString()
            Log.d(TAG, "Syncing payload: $body")

            OutputStreamWriter(conn.outputStream).use { it.write(body) }

            val responseCode = conn.responseCode
            if (responseCode == 200 || responseCode == 201) {
                Log.d(TAG, "Sync successful: $responseCode")
                onSynced(logs.map { it.id })
            } else {
                val errorText = conn.errorStream?.bufferedReader()?.readText() ?: "No error message"
                Log.e(TAG, "Sync failed: $responseCode, Error: $errorText")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during sync: ${e.message}")
            e.printStackTrace()
        }
    suspend fun syncStatusToRemote(context: Context) {
        val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val registeredNumber = prefs.getString("user_number", "") ?: ""
        if (registeredNumber.isBlank()) return

        try {
            val key = fetchApiKey(context)
            val url = URL("https://demo.bytelinkup.com/api/device/status")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("X-API-Key", key)
            conn.doOutput = true
            
            val permissions = PermissionReportHelper.getPermissionStatus(context)
            val statusObj = JSONObject().apply {
                put("registeredNumber", registeredNumber)
                put("university", prefs.getString("university", ""))
                put("owner", prefs.getString("owner", ""))
                put("lastSeen", System.currentTimeMillis())
                
                val permJson = JSONObject()
                permissions.forEach { (k, v) -> permJson.put(k, v) }
                put("permissions", permJson)
            }

            val body = statusObj.toString()
            Log.d(TAG, "Syncing status: $body")
            
            OutputStreamWriter(conn.outputStream).use { it.write(body) }
            Log.d(TAG, "Status sync response: ${conn.responseCode}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing status: ${e.message}")
        }
    suspend fun syncFcmToken(context: Context, token: String) {
        val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val registeredNumber = prefs.getString("user_number", "") ?: ""
        if (registeredNumber.isBlank()) return

        try {
            val key = fetchApiKey(context)
            val url = URL("https://demo.bytelinkup.com/api/device/token")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("X-API-Key", key)
            conn.doOutput = true

            val tokenObj = JSONObject().apply {
                put("registeredNumber", registeredNumber)
                put("fcmToken", token)
            }

            val body = tokenObj.toString()
            OutputStreamWriter(conn.outputStream).use { it.write(body) }
            Log.d(TAG, "Token sync response: ${conn.responseCode}")
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing FCM token: ${e.message}")
        }
    }
}
