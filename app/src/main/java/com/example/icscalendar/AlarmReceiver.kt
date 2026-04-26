// Copyright (c) 2025 Daniel Monedero-Tortola
package com.example.icscalendar

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_EVENT_REMINDER = "com.example.icscalendar.ACTION_EVENT_REMINDER"
        const val EXTRA_EVENT_TITLE = "extra_event_title"
        const val EXTRA_EVENT_TIME = "extra_event_time"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d("AlarmReceiver", "onReceive action: ${intent?.action}")
        when (intent?.action) {
            ACTION_EVENT_REMINDER -> {
                val title = intent.getStringExtra(EXTRA_EVENT_TITLE) ?: "Upcoming Event"
                val time = intent.getStringExtra(EXTRA_EVENT_TIME) ?: ""
                Log.d("AlarmReceiver", "Received reminder for: $title at $time")
                
                val notification = createReminderNotification(context, title, time)
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                
                // Ensure unique ID
                val notificationId = Math.abs((title + time).hashCode())
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
