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
        when (intent?.action) {
            ACTION_EVENT_REMINDER -> {
                val title = intent.getStringExtra(EXTRA_EVENT_TITLE) ?: "Upcoming Event"
                val time = intent.getStringExtra(EXTRA_EVENT_TIME) ?: ""
                
                val notification = createReminderNotification(context, title, time)
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                // Use a unique ID for each reminder based on title/time hash if possible, 
                // or just a common one if we only want one reminder at a time.
                // For now, let's use a hash of the title to allow multiple distinct reminders.
                notificationManager.notify(title.hashCode(), notification)
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
