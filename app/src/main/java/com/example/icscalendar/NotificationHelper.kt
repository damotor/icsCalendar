// Copyright (c) 2025 Daniel Monedero-Tortola
package com.example.icscalendar

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import biweekly.component.VEvent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val CHANNEL_ID = "events_channel_locked_v2"

fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val name = "Daily Events Summary"
        val descriptionText = "Pinned calendar event notifications"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
            enableVibration(false)
            setSound(null, null)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}

fun createEventsNotification(context: Context, eventsWithTimes: List<Pair<VEvent, LocalDateTime>>): Notification {
    val intent = Intent(context, MainActivity::class.java).apply {
        putExtra("dateToShow", LocalDate.now().toString())
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }

    val pendingIntent: PendingIntent = PendingIntent.getActivity(
        context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // Delete intent to re-trigger the notification if swiped away (Android 14+ behavior)
    val deleteIntent = Intent(context, CalendarService::class.java).apply {
        action = CalendarService.ACTION_REFRESH
    }
    val deletePendingIntent = PendingIntent.getService(
        context, 1, deleteIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    val detailedText = if (eventsWithTimes.isEmpty()) {
        "No events for today"
    } else {
        eventsWithTimes
            .sortedBy { it.second }
            .joinToString("\n") { (event, startTime) ->
                val summary = event.summary?.value ?: "Event"
                val isAllDay = event.dateStart?.parameters?.get("VALUE")?.contains("DATE") == true
                if (isAllDay) {
                    summary
                } else {
                    val time = startTime.format(timeFormatter)
                    "$time $summary"
                }
            }
    }

    val contentText = if (eventsWithTimes.isEmpty()) {
        "No events for today"
    } else if (eventsWithTimes.size == 1) {
        detailedText
    } else {
        "${eventsWithTimes.size} events today"
    }

    val style = NotificationCompat.BigTextStyle().bigText(detailedText)

    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.icon)
        .setColor(ContextCompat.getColor(context, R.color.black))
        .setContentTitle("Today's Events")
        .setContentText(contentText)
        .setStyle(style)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setVibrate(longArrayOf(0))
        .setSound(null)
        .setOnlyAlertOnce(false)
        .setContentIntent(pendingIntent)
        .setDeleteIntent(deletePendingIntent) // Re-trigger if swiped
        .setOngoing(true) 
        .setAutoCancel(false)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setCategory(NotificationCompat.CATEGORY_EVENT)

    val notification = builder.build()
    notification.flags = notification.flags or Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT

    return notification
}
