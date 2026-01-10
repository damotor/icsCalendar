// Copyright (c) 2025 Daniel Monedero-Tortola
package com.example.icscalendar

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object WorkScheduler {
    private const val WORK_NAME = "EventNotificationWork"

    /**
     * Entry point to ensure the daily schedule is set up.
     */
    fun scheduleDailyWork(context: Context) {
        scheduleMidnightWork(context)
    }

    /**
     * Schedules an exact alarm to run at 00:01.
     * Using AlarmClock info ensures it's treated as a high-priority alarm by the system.
     */
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
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    /**
     * Runs the event check immediately (used for boot or manual refresh).
     */
    fun runNow(context: Context) {
        val workRequest = OneTimeWorkRequestBuilder<EventWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME + "_NOW",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }
}
