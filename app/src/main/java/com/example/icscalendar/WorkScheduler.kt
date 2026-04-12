// Copyright (c) 2025 Daniel Monedero-Tortola
package com.example.icscalendar

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
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

    fun scheduleEventReminders(context: Context, events: List<Pair<biweekly.component.VEvent, LocalDateTime>>) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = LocalDateTime.now()

        events.forEach { (event, startTime) ->
            if (event.isAllDay()) return@forEach

            val reminderTime = startTime.minusHours(1)
            if (reminderTime.isAfter(now)) {
                val intent = Intent(context, AlarmReceiver::class.java).apply {
                    action = AlarmReceiver.ACTION_EVENT_REMINDER
                    putExtra(AlarmReceiver.EXTRA_EVENT_TITLE, event.summary?.value ?: "No Title")
                    putExtra(AlarmReceiver.EXTRA_EVENT_TIME, startTime.format(timeFormatter))
                }
                
                // Use a unique requestCode for each event reminder to avoid overwriting
                val requestCode = (event.summary?.value?.hashCode() ?: 0) + startTime.hashCode()
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val triggerAtMillis = reminderTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
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
