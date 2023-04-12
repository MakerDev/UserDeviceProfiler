package com.example.userdeviceprofiler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Start your service here
            val serviceIntent = Intent(context, ProfilerService::class.java)
            serviceIntent.action = "START"
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}