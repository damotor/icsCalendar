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

private const val CHANNEL_ID = "events_channel_locked_v3"
private const val REMINDER_CHANNEL_ID = "event_reminders_channel"
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Summary Channel
        val name = context.getString(R.string.notification_channel_name)
        val descriptionText = context.getString(R.string.notification_channel_description)
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
            enableVibration(false)
            setSound(null, null)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        notificationManager.createNotificationChannel(channel)

        // Reminder Channel
        val reminderName = "Event Reminders"
        val reminderDescription = "Notifications sent before events start"
        val reminderImportance = NotificationManager.IMPORTANCE_DEFAULT
        val reminderChannel = NotificationChannel(REMINDER_CHANNEL_ID, reminderName, reminderImportance).apply {
            description = reminderDescription
            enableVibration(true)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        notificationManager.createNotificationChannel(reminderChannel)
    }
}

private fun String.truncate(limit: Int): String {
    return if (this.length > limit) {
        this.substring(0, limit - 3) + "..."
    } else {
        this
    }
}

private fun appendEventsSection(
    context: Context,
    bodyBuilder: StringBuilder,
    events: List<Pair<VEvent, LocalDateTime>>,
    bodyHeaderResId: Int
) {
    if (events.isNotEmpty()) {
        bodyBuilder.append(context.getString(bodyHeaderResId, events.size))
        events.sortedBy { it.second }.forEach { (event, startTime) ->
            val summary = event.summary?.value?.replace("\n", " ")?.replace("\r", "")
                ?: context.getString(R.string.no_title)
            
            val timePrefix = if (event.isAllDay()) "" else "${startTime.format(timeFormatter)} "
            bodyBuilder.append(timePrefix).append(summary).append("\n")
        }
    }
}

fun createEventsNotification(
    context: Context, 
    todayEvents: List<Pair<VEvent, LocalDateTime>>,
    tomorrowEvents: List<Pair<VEvent, LocalDateTime>>,
    overmorrowEvents: List<Pair<VEvent, LocalDateTime>>
): Notification {
    val intent = Intent(context, MainActivity::class.java).apply {
        putExtra("dateToShow", LocalDate.now().toString())
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }

    val pendingIntent: PendingIntent = PendingIntent.getActivity(
        context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val deleteIntent = Intent(context, CalendarService::class.java).apply {
        action = CalendarService.ACTION_REFRESH
    }
    val deletePendingIntent = PendingIntent.getService(
        context, 1, deleteIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    var title = context.getString(R.string.no_events_title)
    var body = ""

    val allEvents = (todayEvents + tomorrowEvents + overmorrowEvents).sortedBy { it.second }

    if (allEvents.isNotEmpty()) {
        val (firstEvent, firstStartTime) = allEvents.first()
        val summary = firstEvent.summary?.value?.replace("\n", " ")?.replace("\r", "") ?: context.getString(R.string.no_title)
        val timePrefix = if (firstEvent.isAllDay()) "" else "${firstStartTime.format(timeFormatter)} "

        val today = LocalDate.now()
        val eventDate = firstStartTime.toLocalDate()
        val dayPrefix = when (eventDate) {
            today -> context.getString(R.string.today_prefix)
            today.plusDays(1) -> context.getString(R.string.tomorrow_prefix)
            today.plusDays(2) -> context.getString(R.string.overmorrow_prefix)
            else -> ""
        }

        title = "$dayPrefix $timePrefix $summary"

        val bodyBuilder = StringBuilder()
        appendEventsSection(
            context, bodyBuilder, todayEvents,
            R.string.today_body_header
        )
        appendEventsSection(
            context, bodyBuilder, tomorrowEvents,
            R.string.tomorrow_body_header
        )
        appendEventsSection(
            context, bodyBuilder, overmorrowEvents,
            R.string.overmorrow_body_header
        )
        body = bodyBuilder.toString().trim().truncate(265)
    }

    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.icon)
        .setColor(ContextCompat.getColor(context, R.color.black))
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setVibrate(longArrayOf(0))
        .setSound(null)
        .setOnlyAlertOnce(true)
        .setContentIntent(pendingIntent)
        .setDeleteIntent(deletePendingIntent)
        .setOngoing(true) 
        .setAutoCancel(false)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setCategory(NotificationCompat.CATEGORY_EVENT)

    val notification = builder.build()
    notification.flags = notification.flags or Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT

    return notification
}

fun createReminderNotification(
    context: Context,
    eventTitle: String,
    eventTime: String,
    eventStart: LocalDateTime
): Notification {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val pendingIntent: PendingIntent = PendingIntent.getActivity(
        context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val today = LocalDate.now()
    val eventDate = eventStart.toLocalDate()
    val prefix = when (eventDate) {
        today -> context.getString(R.string.today_prefix)
        today.plusDays(1) -> context.getString(R.string.tomorrow_prefix)
        today.plusDays(2) -> context.getString(R.string.overmorrow_prefix)
        else -> ""
    }

    return NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
        .setSmallIcon(R.drawable.icon)
        .setContentTitle("$prefix $eventTime $eventTitle")
        .setContentText("$prefix $eventTime $eventTitle")
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setCategory(NotificationCompat.CATEGORY_EVENT)
        .build()
}
