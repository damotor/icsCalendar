// Copyright (c) 2025 Daniel Monedero-Tortola
package com.example.icscalendar

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import biweekly.Biweekly
import java.io.File
import java.io.FileInputStream
import java.time.LocalDate

class CalendarService : Service() {

    companion object {
        const val ACTION_REFRESH = "com.example.icscalendar.ACTION_REFRESH"
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            // Trigger refresh on ANY screen activity. 
            // This forces the notification to reappear if it was dismissed.
            refreshEvents()
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("CalendarService", "Service Created")
        
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
        
        // Initial setup
        updateNotification(emptyList())
        refreshEvents()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        refreshEvents()
        return START_STICKY
    }

    private fun refreshEvents() {
        Thread {
            processIcsFile()
        }.start()
    }

    private fun processIcsFile() {
        try {
            val documentsFolder = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
            val file = File(documentsFolder, "Calendar.ics")
            
            val todayEventsWithTimes = if (file.exists() && file.canRead()) {
                FileInputStream(file).use { inputStream ->
                    val iCalList = Biweekly.parse(inputStream).all()
                    if (iCalList.isNotEmpty()) {
                        val events = iCalList.flatMap { it.events }
                        val today = LocalDate.now()
                        events.mapNotNull { event ->
                            event.getOccurrenceStart(today)?.let { startTime ->
                                Pair(event, startTime)
                            }
                        }
                    } else emptyList()
                }
            } else emptyList()

            updateNotification(todayEventsWithTimes)
            
        } catch (e: Exception) {
            Log.e("CalendarService", "Error processing file", e)
        }
    }

    private fun updateNotification(events: List<Pair<biweekly.component.VEvent, java.time.LocalDateTime>>) {
        val notification = createEventsNotification(this, events)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(105, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(105, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
