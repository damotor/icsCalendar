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

object WorkScheduler {

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

    fun runNow(context: Context) {
        val serviceIntent = Intent(context, CalendarService::class.java).apply {
            action = CalendarService.ACTION_REFRESH
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
