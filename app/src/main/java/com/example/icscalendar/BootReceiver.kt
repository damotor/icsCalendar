// Copyright (c) 2025 Daniel Monedero-Tortola
package com.example.icscalendar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        Log.d("BootReceiver", "Received action: $action")
        
        // Ensure channel exists
        createNotificationChannel(context)
        
        // Visual confirmation that receiver is triggered
        //showSimpleNotification(context, "ICS Calendar", "Boot event: $action", 100)
        
        // Direct execution without WorkManager for the boot event to bypass its constraints
        // We use a Thread to avoid Blocking the main thread, though for a quick file read it's usually fine.
        val pendingResult = goAsync()
        Thread {
            try {
                processIcsFile(context)
            } finally {
                pendingResult.finish()
            }
        }.start()

        // Also schedule the periodic work
        WorkScheduler.scheduleDailyWork(context)
    }

    private fun processIcsFile(context: Context) {
        try {
            // Short sleep to allow system to settle
            Thread.sleep(5000)
            
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
            } else if (!file.exists()) {
                showSimpleNotification(context, "ICS Calendar", "File not found at boot", 102)
            } else {
                showSimpleNotification(context, "ICS Calendar", "Permission denied at boot", 102)
            }
        } catch (e: Exception) {
            showSimpleNotification(context, "ICS Calendar Error", "Boot error: ${e.message}", 102)
        }
    }
}
