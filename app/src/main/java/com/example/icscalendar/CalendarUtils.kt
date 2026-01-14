// Copyright (c) 2025 Daniel Monedero-Tortola
package com.example.icscalendar

import biweekly.component.VEvent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

fun VEvent.isAllDay(): Boolean {
    return dateStart?.parameters?.get("VALUE")?.contains("DATE") == true
}

fun VEvent.getOccurrenceStart(date: LocalDate): LocalDateTime? {
    val dtStartProp = dateStart ?: return null
    val systemZoneId = ZoneId.systemDefault()
    val isAllDay = isAllDay()

    val rruleValue = recurrenceRule?.value

    // If it's a non-recurring event
    if (rruleValue == null) {
        val eventStartDateTime = dtStartProp.value.toInstant().atZone(systemZoneId)
        val eventStartDate = eventStartDateTime.toLocalDate()
        val dtEndProp = dateEnd
        if (dtEndProp == null) {
            return if (eventStartDate == date) eventStartDateTime.toLocalDateTime() else null
        }
        val eventEndDateTime = dtEndProp.value.toInstant().atZone(systemZoneId)
        val eventEndDate = eventEndDateTime.toLocalDate()

        val occurs = if (!isAllDay) {
            !date.isBefore(eventStartDate) && !date.isAfter(eventEndDate)
        } else {
            // For all-day events, the range is exclusive of the end date
            !date.isBefore(eventStartDate) && date.isBefore(eventEndDate)
        }
        return if (occurs) eventStartDateTime.toLocalDateTime() else null
    }

    // It's a recurring event
    val seed = dtStartProp.value
    val timezone = TimeZone.getTimeZone(systemZoneId)
    val recurrenceIterator = rruleValue.getDateIterator(seed, timezone)

    val eventDuration = duration?.value?.let { java.time.Duration.ofMillis(it.toMillis()) }
        ?: dateEnd?.value?.let { java.time.Duration.between(dtStartProp.value.toInstant(), it.toInstant()) }

    val checkStartDate = date.minusWeeks(1)
    val checkStartDateAsDate = java.util.Date.from(checkStartDate.atStartOfDay(systemZoneId).toInstant())
    recurrenceIterator.advanceTo(checkStartDateAsDate)

    while (recurrenceIterator.hasNext()) {
        val nextOccurrence = recurrenceIterator.next()
        val occurrenceStartInstant = nextOccurrence.toInstant()
        val occurrenceStartDateTime = occurrenceStartInstant.atZone(systemZoneId)
        val occurrenceStartDate = occurrenceStartDateTime.toLocalDate()

        if (occurrenceStartDate.isAfter(date.plusDays(1))) {
            break
        }

        val occurrenceEnd: LocalDate
        if (eventDuration != null) {
            val occurrenceEndInstant = occurrenceStartInstant.plus(eventDuration)
            occurrenceEnd = occurrenceEndInstant.atZone(systemZoneId).toLocalDate()
        } else {
            occurrenceEnd = occurrenceStartDate
        }

        val isInRange = if (!isAllDay) {
            !date.isBefore(occurrenceStartDate) && !date.isAfter(occurrenceEnd)
        } else {
            val endForAllDay = if (eventDuration != null) occurrenceEnd else occurrenceStartDate.plusDays(1)
            !date.isBefore(occurrenceStartDate) && date.isBefore(endForAllDay)
        }

        if (isInRange) {
            return occurrenceStartDateTime.toLocalDateTime()
        }
    }

    return null
}

fun List<VEvent>.getSortedEventsForDay(date: LocalDate): List<VEvent> {
    val eventsForDay = this.mapNotNull { event ->
        event.getOccurrenceStart(date)?.let { Pair(event, it) }
    }
    val (allDayEvents, timedEvents) = eventsForDay.partition { it.first.isAllDay() }
    return allDayEvents.map { it.first } + timedEvents.sortedBy { it.second }.map { it.first }
}
