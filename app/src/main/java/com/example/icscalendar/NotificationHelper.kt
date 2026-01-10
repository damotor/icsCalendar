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

private const val CHANNEL_ID = "events_channel_silent"

fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val name = "Calendar Events (Silent)"
        val descriptionText = "Silent daily calendar event notifications"
        // IMPORTANCE_DEFAULT is necessary for it to show but not necessarily pop up/vibrate
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
            enableVibration(false)
            vibrationPattern = longArrayOf(0) // Explicit zero vibration
            setSound(null, null) // Explicitly no sound
            enableLights(true)
            lightColor = Color.RED
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}

fun showSimpleNotification(context: Context, title: String, text: String, id: Int = 99) {
    createNotificationChannel(context)
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.icon)
        .setColor(ContextCompat.getColor(context, R.color.black))
        .setContentTitle(title)
        .setContentText(text)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setVibrate(longArrayOf(0)) // Overriding builder default
        .setSound(null) // Overriding builder default
        .setOnlyAlertOnce(true)
        .setAutoCancel(true)
        .build()
        
    notificationManager.notify(id, notification)
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
            val isAllDay = event.dateStart?.parameters?.get("VALUE")?.contains("DATE") == true
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

    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.icon)
        .setColor(ContextCompat.getColor(context, R.color.black))
        .setContentTitle("Today's Events")
        .setContentText(contentText)
        .setStyle(style)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setVibrate(longArrayOf(0)) // Force no vibration
        .setSound(null) // Force no sound
        .setOnlyAlertOnce(true)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .build()

    notificationManager.notify(1, notification)
}
