// Copyright (c) 2025 Daniel Monedero-Tortola
package com.example.icscalendar

import biweekly.Biweekly
import biweekly.ICalendar
import biweekly.component.VEvent
import biweekly.io.TimezoneInfo
import biweekly.util.ICalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.TimeZone

fun VEvent.isAllDay(): Boolean {
    val dtStart = dateStart ?: return false
    return !dtStart.value.hasTime()
}

private val windowsTzMapping = mapOf(
    "Eastern Standard Time" to "America/New_York",
    "Pacific Standard Time" to "America/Los_Angeles",
    "Central Standard Time" to "America/Chicago",
    "Mountain Standard Time" to "America/Denver",
    "W. Europe Standard Time" to "Europe/Berlin",
    "Central Europe Standard Time" to "Europe/Prague",
    "Romance Standard Time" to "Europe/Paris",
    "GMT Standard Time" to "Europe/London",
    "GTB Standard Time" to "Europe/Bucharest"
)

private val tzCache = java.util.concurrent.ConcurrentHashMap<String, TimeZone>()

private fun resolveTimeZone(tzid: String): TimeZone {
    return tzCache.getOrPut(tzid) {
        windowsTzMapping[tzid]?.let { return@getOrPut TimeZone.getTimeZone(it) }
        val tz = TimeZone.getTimeZone(tzid)
        if (tz.id == "GMT" && tzid != "GMT") TimeZone.getTimeZone("UTC") else tz
    }
}

private fun getResolvedStartInstant(dtStartValue: java.util.Date, eventTimeZone: TimeZone): Instant {
    if (dtStartValue is ICalDate && dtStartValue.rawComponents != null) {
        val rc = dtStartValue.rawComponents
        return try {
            val zoneId = eventTimeZone.toZoneId()
            LocalDateTime.of(rc.year, rc.month, rc.date, rc.hour, rc.minute, rc.second).atZone(zoneId).toInstant()
        } catch (e: Exception) {
            val cal = java.util.Calendar.getInstance(eventTimeZone)
            cal.set(rc.year, rc.month - 1, rc.date, rc.hour, rc.minute, rc.second)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            Instant.ofEpochMilli(cal.timeInMillis)
        }
    }
    return dtStartValue.toInstant()
}

private fun getEventDuration(event: VEvent, dtStartValue: java.util.Date): java.time.Duration {
    val durationVal = event.duration?.value
    val dateEndVal = event.dateEnd?.value
    return when {
        durationVal != null -> java.time.Duration.ofMillis(durationVal.toMillis())
        dateEndVal != null -> java.time.Duration.between(dtStartValue.toInstant(), dateEndVal.toInstant())
        event.isAllDay() -> java.time.Duration.ofDays(1)
        else -> java.time.Duration.ZERO
    }
}

fun VEvent.getOccurrenceStart(date: LocalDate, timezoneInfo: TimezoneInfo? = null): LocalDateTime? {
    val dtStartProp = dateStart ?: return null
    val systemZoneId = ZoneId.systemDefault()
    val dtStartValue = dtStartProp.value
    val tzid = dtStartProp.parameters.getTimezoneId()
    val eventTimeZone = when {
        dtStartValue is ICalDate && dtStartValue.rawComponents?.isUtc == true -> TimeZone.getTimeZone("UTC")
        else -> (timezoneInfo?.getTimezone(dtStartProp) ?: tzid?.let { timezoneInfo?.getTimezoneById(it) })?.timeZone
            ?: tzid?.let { resolveTimeZone(it) } ?: TimeZone.getTimeZone(systemZoneId.id)
    }
    val isRecurring = recurrenceRule != null
    
    if (!isRecurring) {
        val approxStart = dtStartValue.toInstant().atZone(systemZoneId).toLocalDate()
        if (ChronoUnit.DAYS.between(approxStart, date) !in -2L..2L) return null
        
        val startInstant = getResolvedStartInstant(dtStartValue, eventTimeZone)
        val startDS = startInstant.atZone(systemZoneId)
        val duration = getEventDuration(this, dtStartValue)
        val endDS = startDS.plus(duration)
        
        val occurs = if (!isAllDay()) !date.isBefore(startDS.toLocalDate()) && !date.isAfter(endDS.toLocalDate())
                     else !date.isBefore(startDS.toLocalDate()) && date.isBefore(endDS.toLocalDate())
        return if (occurs) startDS.toLocalDateTime() else null
    }

    val startInstant = getResolvedStartInstant(dtStartValue, eventTimeZone)
    val seed = java.util.Date.from(startInstant)
    val iterator = recurrenceRule.value.getDateIterator(seed, eventTimeZone)
    iterator.advanceTo(java.util.Date.from(date.atStartOfDay(systemZoneId).toInstant()))

    if (iterator.hasNext()) {
        val next = iterator.next().toInstant().atZone(systemZoneId)
        if (next.toLocalDate().isEqual(date)) return next.toLocalDateTime()
    }
    
    val duration = getEventDuration(this, dtStartValue)
    if (duration.toDays() >= 1 || !isAllDay()) {
        val longIterator = recurrenceRule.value.getDateIterator(seed, eventTimeZone)
        longIterator.advanceTo(java.util.Date.from(date.minusDays(duration.toDays() + 1).atStartOfDay(systemZoneId).toInstant()))
        var count = 0
        while (longIterator.hasNext() && count++ < 10) {
            val occStart = longIterator.next().toInstant().atZone(systemZoneId)
            if (occStart.toLocalDate().isAfter(date)) break
            val occEnd = occStart.plus(duration)
            val inRange = if (!isAllDay()) !date.isBefore(occStart.toLocalDate()) && !date.isAfter(occEnd.toLocalDate())
                          else !date.isBefore(occStart.toLocalDate()) && date.isBefore(occEnd.toLocalDate())
            if (inRange) return occStart.toLocalDateTime()
        }
    }
    return null
}

suspend fun List<VEvent>.getEventsByDayInRange(startDate: LocalDate, endDate: LocalDate, timezoneInfo: TimezoneInfo?): Map<LocalDate, List<VEvent>> = coroutineScope {
    val chunkSize = (this@getEventsByDayInRange.size / Runtime.getRuntime().availableProcessors()).coerceAtLeast(100)
    val chunks = this@getEventsByDayInRange.chunked(chunkSize)
    
    val deferredResults = chunks.map { chunk ->
        async(Dispatchers.Default) {
            processChunk(chunk, startDate, endDate, timezoneInfo)
        }
    }

    val results = deferredResults.awaitAll()
    val finalMap = mutableMapOf<LocalDate, MutableList<Pair<VEvent, LocalDateTime>>>()
    
    results.forEach { chunkResult ->
        chunkResult.forEach { (date, events) ->
            finalMap.getOrPut(date) { mutableListOf() }.addAll(events)
        }
    }

    finalMap.mapValues { entry ->
        val (allDay, timed) = entry.value.partition { it.first.isAllDay() }
        allDay.map { it.first } + timed.sortedBy { it.second }.map { it.first }
    }
}

private fun processChunk(chunk: List<VEvent>, startDate: LocalDate, endDate: LocalDate, timezoneInfo: TimezoneInfo?): Map<LocalDate, List<Pair<VEvent, LocalDateTime>>> {
    val result = mutableMapOf<LocalDate, MutableList<Pair<VEvent, LocalDateTime>>>()
    val systemZoneId = ZoneId.systemDefault()
    val startBoundary = startDate.atStartOfDay(systemZoneId).toInstant()
    val endBoundary = endDate.plusDays(1).atStartOfDay(systemZoneId).toInstant()

    val tzMapping = mutableMapOf<String, TimeZone>()
    timezoneInfo?.timezones?.forEach { assignment ->
        val id = assignment.globalId ?: assignment.component?.timezoneId?.value
        if (id != null) tzMapping[id] = assignment.timeZone
    }

    chunk.forEach { event ->
        val dtStartProp = event.dateStart ?: return@forEach
        val tzid = dtStartProp.parameters.getTimezoneId()
        
        val dtStartValue = dtStartProp.value
        val eventTimeZone = when {
            dtStartValue is ICalDate && dtStartValue.rawComponents?.isUtc == true -> TimeZone.getTimeZone("UTC")
            else -> timezoneInfo?.getTimezone(dtStartProp)?.timeZone
                ?: tzid?.let { tzMapping[it] ?: resolveTimeZone(it) }
                ?: TimeZone.getTimeZone(systemZoneId.id)
        }
        val isAllDay = event.isAllDay()

        if (event.recurrenceRule == null) {
            val approxStart = dtStartValue.toInstant()
            val approxEnd = event.dateEnd?.value?.toInstant() ?: approxStart.plus(java.time.Duration.ofDays(1))
            
            if (approxStart.isAfter(endBoundary.plus(java.time.Duration.ofDays(1)))) return@forEach
            if (approxEnd.isBefore(startBoundary.minus(java.time.Duration.ofDays(1)))) return@forEach
            
            val startInstant = getResolvedStartInstant(dtStartValue, eventTimeZone)
            val duration = getEventDuration(event, dtStartValue)
            val startDS = startInstant.atZone(systemZoneId)
            val endDS = startDS.plus(duration)
            
            if (startDS.toInstant().isBefore(endBoundary) && endDS.toInstant().isAfter(startBoundary)) {
                val firstDate = if (startDS.toLocalDate().isBefore(startDate)) startDate else startDS.toLocalDate()
                val lastDate = run {
                    val d = if (isAllDay) endDS.toLocalDate().minusDays(1) else endDS.toLocalDate()
                    if (d.isAfter(endDate)) endDate else d
                }
                var d = firstDate
                while (!d.isAfter(lastDate)) {
                    result.getOrPut(d) { mutableListOf() }.add(event to startDS.toLocalDateTime())
                    d = d.plusDays(1)
                }
            }
        } else {
            val startInstant = getResolvedStartInstant(dtStartValue, eventTimeZone)
            val duration = getEventDuration(event, dtStartValue)
            val seed = java.util.Date.from(startInstant)
            val iterator = event.recurrenceRule.value.getDateIterator(seed, eventTimeZone)
            
            iterator.advanceTo(java.util.Date.from(startDate.minusDays(duration.toDays() + 1).atStartOfDay(systemZoneId).toInstant()))
            
            var count = 0
            while (iterator.hasNext() && count++ < 1000) {
                val occStart = iterator.next().toInstant().atZone(systemZoneId)
                if (occStart.toInstant().isAfter(endBoundary)) break
                val occEnd = occStart.plus(duration)
                if (occEnd.toInstant().isAfter(startBoundary)) {
                    val firstDate = if (occStart.toLocalDate().isBefore(startDate)) startDate else occStart.toLocalDate()
                    val lastDate = run {
                        val d = if (isAllDay) occEnd.toLocalDate().minusDays(1) else occEnd.toLocalDate()
                        if (d.isAfter(endDate)) endDate else d
                    }
                    var d = firstDate
                    while (!d.isAfter(lastDate)) {
                        result.getOrPut(d) { mutableListOf() }.add(event to occStart.toLocalDateTime())
                        d = d.plusDays(1)
                    }
                }
            }
        }
    }
    return result
}

suspend fun parseIcsParallel(file: File): ICalendar? = withContext(Dispatchers.IO) {
    try {
        val content = file.readText()
        if (!content.contains("BEGIN:VEVENT")) {
            return@withContext Biweekly.parse(content).first()
        }

        val eventDelimiter = "BEGIN:VEVENT"
        val parts = content.split(eventDelimiter)
        val header = parts[0]
        val footer = "END:VCALENDAR" // Simplified assumption for splitting
        
        val eventBlocks = parts.drop(1).map { eventDelimiter + it }
        
        // Parse the header to get general calendar info (timezones, etc.)
        val baseCalendar = Biweekly.parse(header + footer).first() ?: return@withContext null
        
        val batchSize = (eventBlocks.size / Runtime.getRuntime().availableProcessors()).coerceAtLeast(50)
        val batches = eventBlocks.chunked(batchSize)
        
        val deferredEvents = batches.map { batch ->
            async(Dispatchers.Default) {
                val batchContent = "BEGIN:VCALENDAR\r\n" + batch.joinToString("") + "END:VCALENDAR"
                Biweekly.parse(batchContent).first()?.events ?: emptyList()
            }
        }
        
        val allEvents = deferredEvents.awaitAll().flatten()
        allEvents.forEach { baseCalendar.addEvent(it) }
        
        baseCalendar
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

suspend fun List<VEvent>.getSortedEventsForDay(date: LocalDate, timezoneInfo: TimezoneInfo? = null): List<VEvent> {
    return getEventsByDayInRange(date, date, timezoneInfo)[date] ?: emptyList()
}
