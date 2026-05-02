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
    }
}
