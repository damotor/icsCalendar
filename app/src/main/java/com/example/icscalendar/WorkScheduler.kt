// Copyright (c) 2025 Daniel Monedero-Tortola
package com.example.icscalendar

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object WorkScheduler {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun scheduleDailyWork(context: Context) {
        scheduleMidnightWork(context)
    }

    fun scheduleMidnightWork(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val now = LocalDateTime.now()
        // Refresh precisely at 00:01
        var nextRun = LocalDateTime.of(now.toLocalDate(), LocalTime.of(0, 1))
        
        if (now.isAfter(nextRun)) {
            nextRun = nextRun.plusDays(1)
        }

        val zonedDateTime = nextRun.atZone(ZoneId.systemDefault())
        val triggerAtMillis = zonedDateTime.toInstant().toEpochMilli()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, pendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun showReminder(context: Context, title: String, startTime: LocalDateTime) {
        val timeStr = startTime.format(timeFormatter)
        val notification = createReminderNotification(context, title, timeStr)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = Math.abs((title + startTime.toString()).hashCode())
        notificationManager.notify(notificationId, notification)
        Log.d("WorkScheduler", "Notification displayed immediately for '$title' starting at $timeStr")
    }

    fun scheduleEventReminders(context: Context, events: List<Pair<biweekly.component.VEvent, LocalDateTime>>) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = LocalDateTime.now()
        val limit24h = now.plusHours(24)
        val limit72h = now.plusDays(3)

        Log.d("WorkScheduler", "Refreshing event reminders. Current time: $now")

        events.forEach { (event, startTime) ->
            if (event.isAllDay()) return@forEach

            val title = event.summary?.value ?: "No Title"
            
            // If the event is in the next 24 hours, show it immediately
            if (startTime.isAfter(now) && startTime.isBefore(limit24h)) {
                showReminder(context, title, startTime)
            } 
            // If the event is between 24h and 72h away, schedule it to appear when it's 24h away
            else if (startTime.isAfter(limit24h) && startTime.isBefore(limit72h)) {
                val triggerTime = startTime.minusHours(24)
                
                Log.d("WorkScheduler", "Scheduling future reminder for '$title' to appear at $triggerTime")
                
                val intent = Intent(context, AlarmReceiver::class.java).apply {
                    action = AlarmReceiver.ACTION_EVENT_REMINDER
                    putExtra(AlarmReceiver.EXTRA_EVENT_TITLE, title)
                    putExtra(AlarmReceiver.EXTRA_EVENT_TIME, startTime.format(timeFormatter))
                }
                
                val requestCode = Math.abs((title + startTime.toString()).hashCode())
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val triggerAtMillis = triggerTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                    } else {
                        alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                    }
                } catch (e: SecurityException) {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            }
        }
    }

    fun refreshEvents(context: Context) {
        val serviceIntent = Intent(context, CalendarService::class.java).apply {
            action = CalendarService.ACTION_REFRESH
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    @Deprecated("Use refreshEvents(context) instead", ReplaceWith("refreshEvents(context)"))
    fun runNow(context: Context) = refreshEvents(context)
}
