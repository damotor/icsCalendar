// Copyright (c) 2025 Daniel Monedero-Tortola
package com.example.icscalendar

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_EVENT_REMINDER = "com.example.icscalendar.ACTION_EVENT_REMINDER"
        const val EXTRA_EVENT_TITLE = "extra_event_title"
        const val EXTRA_EVENT_TIME = "extra_event_time"
        const val EXTRA_EVENT_START = "extra_event_start"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d("AlarmReceiver", "onReceive action: ${intent?.action}")
        when (intent?.action) {
            ACTION_EVENT_REMINDER -> {
                val time = intent.getStringExtra(EXTRA_EVENT_TIME) ?: ""
                val startStr = intent.getStringExtra(EXTRA_EVENT_START)
                val startTime = try {
                    if (startStr != null) LocalDateTime.parse(startStr) else LocalDateTime.now()
                } catch (_: Exception) {
                    LocalDateTime.now()
                }
                val dayOfWeek = startTime.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
                val eventTitle = intent.getStringExtra(EXTRA_EVENT_TITLE)
                val title = if (eventTitle.isNullOrBlank()) dayOfWeek else eventTitle

                Log.d("AlarmReceiver", "Received reminder for: $title at $time (Start: $startTime)")
                
                val notification = createReminderNotification(context, title, time, startTime)
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                
                // Ensure unique ID per event
                val notificationId = abs((title + startTime.toString()).hashCode())
                notificationManager.notify(notificationId, notification)
            }
            else -> {
                Log.d("AlarmReceiver", "Midnight alarm triggered")
                WorkScheduler.refreshEvents(context)
                // Schedule next day's alarm
                WorkScheduler.scheduleMidnightWork(context)
            }
        }
    }
}
