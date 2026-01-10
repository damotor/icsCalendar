// Copyright (c) 2025 Daniel Monedero-Tortola
package com.example.icscalendar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.d("AlarmReceiver", "Alarm triggered")
        
        // Ensure channel exists
        createNotificationChannel(context)
        
        // Show immediate debug notification
        // showSimpleNotification(context, "ICS Calendar", "Daily update triggered", 103)
        
        // Direct execution using goAsync
        val pendingResult = goAsync()
        Thread {
            try {
                processIcsFile(context)
            } finally {
                // Schedule next alarm
                WorkScheduler.scheduleMidnightWork(context)
                pendingResult.finish()
            }
        }.start()
    }

    private fun processIcsFile(context: Context) {
        try {
            val documentsFolder = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
            val file = java.io.File(documentsFolder, "Calendar.ics")
            
            if (file.exists() && file.canRead()) {
                java.io.FileInputStream(file).use { inputStream ->
                    val iCalList = biweekly.Biweekly.parse(inputStream).all()
                    if (iCalList.isNotEmpty()) {
                        val events = iCalList.flatMap { it.events }
                        val today = java.time.LocalDate.now()
                        val todayEventsWithTimes = events.mapNotNull { event ->
                            event.getOccurrenceStart(today)?.let { startTime ->
                                Pair(event, startTime)
                            }
                        }
                        
                        if (todayEventsWithTimes.isNotEmpty()) {
                            showEventsNotification(context, todayEventsWithTimes)
                        } else {
                            showSimpleNotification(context, "ICS Calendar", "No events for today", 102)
                        }
                    }
                }
            } else {
                showSimpleNotification(context, "ICS Calendar", "Calendar.ics not found", 102)
            }
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Error processing file", e)
        }
    }
}
