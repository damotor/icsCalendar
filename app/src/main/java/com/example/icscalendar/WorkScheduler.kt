// Copyright (c) 2025 Daniel Monedero-Tortola
package com.example.icscalendar

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object WorkScheduler {
    private const val WORK_NAME = "EventNotificationWork"
    private const val MIDNIGHT_WORK_NAME = "MidnightEventNotificationWork"

    /**
     * Entry point to ensure the midnight schedule is set up.
     */
    fun scheduleDailyWork(context: Context) {
        scheduleMidnightWork(context)
    }

    /**
     * Schedules a one-time worker to run at 00:01 the next day.
     */
    fun scheduleMidnightWork(context: Context) {
        val now = LocalDateTime.now()
        // Target 00:01 of the next day
        val nextMidnight = LocalDateTime.of(now.toLocalDate().plusDays(1), LocalTime.of(0, 1))
        val initialDelay = Duration.between(now, nextMidnight)

        val workRequest = OneTimeWorkRequestBuilder<EventWorker>()
            .setInitialDelay(initialDelay.toMinutes(), TimeUnit.MINUTES)
            .addTag("MidnightJob")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            MIDNIGHT_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
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
