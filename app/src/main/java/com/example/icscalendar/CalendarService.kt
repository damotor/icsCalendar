// Copyright (c) 2025 Daniel Monedero-Tortola
package com.example.icscalendar

import android.app.Service
import android.content.Context
import android.content.Intent
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

    override fun onCreate() {
        super.onCreate()
        Log.d("CalendarService", "Service Created")
        
        updateNotification(emptyList(), emptyList(), emptyList())
        refreshEvents()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        refreshEvents()
        return START_STICKY
    }

    private fun refreshEvents() {
        Thread {
            Thread.sleep(5000)
            processIcsFile()
        }.start()
    }

    private fun processIcsFile() {
        try {
            val documentsFolder = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
            val file = File(documentsFolder, "Calendar.ics")
            
            if (file.exists() && file.canRead()) {
                FileInputStream(file).use { inputStream ->
                    val iCalList = Biweekly.parse(inputStream).all()
                    if (iCalList.isNotEmpty()) {
                        val allEvents = iCalList.flatMap { it.events }
                        val today = LocalDate.now()
                        val tomorrow = today.plusDays(1)
                        val overmorrow = today.plusDays(2)

                        val todayEvents = allEvents.mapNotNull { event ->
                            event.getOccurrenceStart(today)?.let { Pair(event, it) }
                        }
                        
                        val tomorrowEvents = allEvents.mapNotNull { event ->
                            event.getOccurrenceStart(tomorrow)?.let { Pair(event, it) }
                        }

                        val overmorrowEvents = allEvents.mapNotNull { event ->
                            event.getOccurrenceStart(overmorrow)?.let { Pair(event, it) }
                        }

                        updateNotification(todayEvents, tomorrowEvents, overmorrowEvents)
                        WorkScheduler.scheduleEventReminders(this, todayEvents + tomorrowEvents + overmorrowEvents)
                    }
                }
            } else {
                updateNotification(emptyList(), emptyList(), emptyList())
            }
        } catch (e: Exception) {
            Log.e("CalendarService", "Error processing file", e)
        }
    }

    private fun updateNotification(
        todayEvents: List<Pair<biweekly.component.VEvent, java.time.LocalDateTime>>,
        tomorrowEvents: List<Pair<biweekly.component.VEvent, java.time.LocalDateTime>>,
        overmorrowEvents: List<Pair<biweekly.component.VEvent, java.time.LocalDateTime>>
    ) {
        val notification = createEventsNotification(this, todayEvents, tomorrowEvents, overmorrowEvents)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(105, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(105, notification)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
