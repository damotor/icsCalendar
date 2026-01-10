// Copyright (c) 2025 Daniel Monedero-Tortola
package com.example.icscalendar

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.util.Log

class CalendarService : Service() {

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            val action = intent?.action
            Log.d("CalendarService", "Received screen action: $action")
            
            // Trigger update on unlock or screen on
            if (action == Intent.ACTION_USER_PRESENT || action == Intent.ACTION_SCREEN_ON) {
                Thread {
                    processIcsFile()
                }.start()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("CalendarService", "Service Created")
        
        // Register for screen and unlock events dynamically
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)
        
        // Promote to foreground so the system doesn't kill it
        startForegroundService()
    }

    private fun startForegroundService() {
        createNotificationChannel(this)
        val notification = androidx.core.app.NotificationCompat.Builder(this, "events_channel_silent")
            .setSmallIcon(R.drawable.icon)
            .setContentTitle("ICS Calendar")
            .setContentText("Monitoring calendar events")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MIN) // Low priority so it's not intrusive
            .build()
        
        startForeground(105, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY // Stay alive
    }

    private fun processIcsFile() {
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
                            showEventsNotification(this, todayEventsWithTimes)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("CalendarService", "Error processing file", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenReceiver)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
