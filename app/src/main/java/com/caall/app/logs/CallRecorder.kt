package com.caall.app.logs

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class CallRecorder(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var currentFilePath: String? = null

    fun startRecording(incomingNumber: String?): String? {
        if (isRecording) stopRecording()

        try {
            val fileName = "CALL_REC_${System.currentTimeMillis()}_${incomingNumber ?: "UNKNOWN"}.mp3"
            val file = File(context.filesDir, fileName)
            currentFilePath = file.absolutePath

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder?.apply {
                // VOICE_COMMUNICATION or VOICE_RECOGNITION are standard sources for calls.
                // Note: Android 9+ heavily restricts this and may result in silent files. 
                setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(currentFilePath)
                prepare()
                start()
            }
            isRecording = true
            Log.d("CallRecorder", "Started recording to: $currentFilePath")
            return currentFilePath
        } catch (e: Exception) {
            Log.e("CallRecorder", "Error starting recording", e)
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
            return null
        }
    }

    fun stopRecording() {
        if (isRecording) {
            try {
                mediaRecorder?.stop()
            } catch (e: Exception) {
                Log.e("CallRecorder", "Error stopping recording", e)
            } finally {
                mediaRecorder?.release()
                mediaRecorder = null
                isRecording = false
            }
        }
    }
}
