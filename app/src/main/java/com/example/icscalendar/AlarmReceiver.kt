// Copyright (c) 2025 Daniel Monedero-Tortola
package com.example.icscalendar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.d("AlarmReceiver", "Midnight alarm triggered")
        
        WorkScheduler.refreshEvents(context)
        
        // Schedule next day's alarm
        WorkScheduler.scheduleMidnightWork(context)
    }
}
