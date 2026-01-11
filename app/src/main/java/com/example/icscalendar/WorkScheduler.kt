// Copyright (c) 2025 Daniel Monedero-Tortola
package com.example.icscalendar

import android.content.Context
import android.content.Intent
import android.os.Build

object WorkScheduler {

    fun scheduleDailyWork(context: Context) {
        // Daily refresh functionality has been removed.
    }

    fun runNow(context: Context) {
        val serviceIntent = Intent(context, CalendarService::class.java).apply {
            action = CalendarService.ACTION_REFRESH
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
