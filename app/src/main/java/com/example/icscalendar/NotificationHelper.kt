// Copyright (c) 2025 Daniel Monedero-Tortola
package com.example.icscalendar

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import biweekly.component.VEvent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val name = "Events"
        val descriptionText = "Notifications for calendar events"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel("events_channel", name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}

fun showEventsNotification(context: Context, eventsWithTimes: List<Pair<VEvent, LocalDateTime>>) {
    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val intent = Intent(context, MainActivity::class.java).apply {
        putExtra("dateToShow", LocalDate.now().toString())
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }

    val pendingIntent: PendingIntent = PendingIntent.getActivity(
        context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    val detailedText = eventsWithTimes
        .sortedBy { it.second }
        .joinToString("\n") { (event, startTime) ->
            val summary = event.summary?.value ?: "Event"
            val isAllDay = event.dateStart.parameters.get("VALUE")?.contains("DATE") == true
            if (isAllDay) {
                summary
            } else {
                val time = startTime.format(timeFormatter)
                "$time $summary"
            }
        }

    val contentText = if (eventsWithTimes.size == 1) {
        detailedText
    } else {
        "${eventsWithTimes.size} events today"
    }

    val style = NotificationCompat.BigTextStyle().bigText(detailedText)

    val notification = NotificationCompat.Builder(context, "events_channel")
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("Today's Events")
        .setContentText(contentText)
        .setStyle(style)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()

    notificationManager.notify(1, notification)
}
