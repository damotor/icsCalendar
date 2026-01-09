// Copyright (c) 2025 Daniel Monedero-Tortola
package com.example.icscalendar

import android.content.Context
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
            .setContentText("Worker processing...")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .build()
        return ForegroundInfo(101, notification)
    }

    override suspend fun doWork(): Result {
        Log.d("EventWorker", "Worker started")
        
        // If this worker was triggered as the daily midnight job, schedule the next one
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
                            showEventsNotification(applicationContext, todayEventsWithTimes)
                        } else {
                            showSimpleNotification(applicationContext, "ICS Calendar", "No events for today")
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e("EventWorker", "Critical error in worker", e)
            return Result.retry()
        }
        return Result.success()
    }
}
