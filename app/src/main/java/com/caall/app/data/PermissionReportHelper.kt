package com.caall.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat

object PermissionReportHelper {

    fun getPermissionStatus(context: Context): Map<String, Boolean> {
        val status = mutableMapOf<String, Boolean>()

        status["phone_state"] = isGranted(context, Manifest.permission.READ_PHONE_STATE)
        status["call_log"] = isGranted(context, Manifest.permission.READ_CALL_LOG)
        status["microphone"] = isGranted(context, Manifest.permission.RECORD_AUDIO)
        status["contacts"] = isGranted(context, Manifest.permission.READ_CONTACTS)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            status["notifications"] = isGranted(context, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            status["notifications"] = true // Not required below Tiramisu
        }

        // Check Battery Optimization
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        status["battery_optimization_ignored"] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }

        return status
    }

    private fun isGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
