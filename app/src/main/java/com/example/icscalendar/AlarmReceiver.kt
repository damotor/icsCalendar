// Copyright (c) 2025 Daniel Monedero-Tortola
package com.example.icscalendar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.d("AlarmReceiver", "Midnight alarm triggered")
        
        val serviceIntent = Intent(context, CalendarService::class.java).apply {
            action = CalendarService.ACTION_REFRESH
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
        
        // Schedule next day's alarm
        WorkScheduler.scheduleMidnightWork(context)
    }
}
