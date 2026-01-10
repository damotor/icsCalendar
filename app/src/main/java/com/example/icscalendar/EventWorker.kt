// Copyright (c) 2025 Daniel Monedero-Tortola
package com.example.icscalendar

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import biweekly.Biweekly
import java.io.File
import java.io.FileInputStream
import java.time.LocalDate

class EventWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    
    override suspend fun getForegroundInfo(): ForegroundInfo {
        createNotificationChannel(applicationContext)
        val notification = androidx.core.app.NotificationCompat.Builder(applicationContext, "events_channel")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("ICS Calendar")
            .setContentText("Updating daily events...")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .build()
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(101, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(101, notification)
        }
    }

    override suspend fun doWork(): Result {
        Log.d("EventWorker", "Worker started")
        
        // Show immediate debug notification to confirm the worker is running
        showSimpleNotification(applicationContext, "ICS Calendar", "Worker started processing...", 101)
        
        // Attempt to run as foreground to prevent being killed during execution
        try {
            setForeground(getForegroundInfo())
        } catch (e: Exception) {
            Log.e("EventWorker", "Failed to set foreground", e)
        }

        // Reschedule for the next day if this is the midnight job
        if (tags.contains("MidnightJob")) {
            WorkScheduler.scheduleMidnightWork(applicationContext)
        }

        try {
            val documentsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val file = File(documentsFolder, "Calendar.ics")
            
            if (file.exists() && file.canRead()) {
                FileInputStream(file).use { inputStream ->
                    val iCalList = Biweekly.parse(inputStream).all()
                    if (iCalList.isNotEmpty()) {
                        val events = iCalList.flatMap { it.events }
                        val today = LocalDate.now()
                        val todayEventsWithTimes = events.mapNotNull { event ->
                            event.getOccurrenceStart(today)?.let { startTime ->
                                Pair(event, startTime)
                            }
                        }
                        
                        createNotificationChannel(applicationContext)
                        if (todayEventsWithTimes.isNotEmpty()) {
                            Log.d("EventWorker", "Showing notification for ${todayEventsWithTimes.size} events")
                            showEventsNotification(applicationContext, todayEventsWithTimes)
                        } else {
                            showSimpleNotification(applicationContext, "ICS Calendar", "No events for today")
                        }
                    } else {
                        showSimpleNotification(applicationContext, "ICS Calendar", "Calendar file is empty")
                    }
                }
            } else {
                val reason = if (!file.exists()) "File not found" else "Cannot read file"
                showSimpleNotification(applicationContext, "ICS Calendar Error", "$reason at ${file.absolutePath}")
            }
        } catch (e: Throwable) {
            Log.e("EventWorker", "Critical error in worker", e)
            showSimpleNotification(applicationContext, "ICS Calendar Error", "Worker failed: ${e.message}")
            return Result.retry()
        }
        return Result.success()
    }
}
