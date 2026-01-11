// Copyright (c) 2025 Daniel Monedero-Tortola
package com.example.icscalendar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val receivedAction = intent?.action
        Log.d("BootReceiver", "Received action: $receivedAction")
        
        // Ensure channel exists
        createNotificationChannel(context)
        
        // Start the monitoring service - this is now the only way to show the persistent notification
        val serviceIntent = Intent(context, CalendarService::class.java).apply {
            action = CalendarService.ACTION_REFRESH
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        WorkScheduler.scheduleDailyWork(context)
    }
}
