// Copyright (c) 2025 Daniel Monedero-Tortola
package com.example.icscalendar

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class EventWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        Log.d("EventWorker", "Worker triggered, routing to service")
        
        // Always route work to the service to maintain notification stickiness
        val serviceIntent = Intent(applicationContext, CalendarService::class.java).apply {
            action = CalendarService.ACTION_REFRESH
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            applicationContext.startForegroundService(serviceIntent)
        } else {
            applicationContext.startService(serviceIntent)
        }

        // Reschedule for the next day if this was the midnight job
        if (tags.contains("MidnightJob")) {
            WorkScheduler.scheduleMidnightWork(applicationContext)
        }

        return Result.success()
    }
}
