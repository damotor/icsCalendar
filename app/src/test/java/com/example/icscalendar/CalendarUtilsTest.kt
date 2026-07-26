package com.example.icscalendar

import biweekly.Biweekly
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.TimeZone

class CalendarUtilsTest {

    @Test
    fun testGetOccurrenceStart_withTimezone() {
        // Save current default timezone
        val originalDefault = TimeZone.getDefault()
        try {
            // Set system timezone to PST (UTC-8)
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            
            val ics = """
                BEGIN:VCALENDAR
                VERSION:2.0
                BEGIN:VTIMEZONE
                TZID:Eastern Standard Time
                BEGIN:STANDARD
                DTSTART:19701101T020000
                RRULE:FREQ=YEARLY;BYMONTH=11;BYDAY=1SU
                TZOFFSETFROM:-0400
                TZOFFSETTO:-0500
                END:STANDARD
                BEGIN:DAYLIGHT
                DTSTART:19700308T020000
                RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=2SU
                TZOFFSETFROM:-0500
                TZOFFSETTO:-0400
                END:DAYLIGHT
                END:VTIMEZONE
                BEGIN:VEVENT
                SUMMARY:Test Event
                DTSTART;TZID=Eastern Standard Time:20260716T120000
                DTEND;TZID=Eastern Standard Time:20260716T123000
                END:VEVENT
                END:VCALENDAR
            """.trimIndent()

            val ical = Biweekly.parse(ics).first()
            val event = ical.events[0]
            val date = LocalDate.of(2026, 7, 16)

            val occurrenceStart = event.getOccurrenceStart(date, ical.timezoneInfo)

            // 12:00 PM Eastern on July 16 (Daylight Savings) is 16:00 UTC.
            // 16:00 UTC in Pacific Time (Daylight Savings) is 09:00 AM.
            
            val expected = LocalDateTime.of(2026, 7, 16, 9, 0)
            assertEquals("Event should start at 09:00 PST", expected, occurrenceStart)
        } finally {
            TimeZone.setDefault(originalDefault)
        }
    }

    @Test
    fun testGetOccurrenceStart_WindowsTimezone_NoVTimezone() {
        val originalDefault = TimeZone.getDefault()
        try {
            // Set system timezone to PST (UTC-8)
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            
            val ics = """
                BEGIN:VCALENDAR
                VERSION:2.0
                BEGIN:VEVENT
                SUMMARY:Test Event
                DTSTART;TZID=Eastern Standard Time:20260716T120000
                DTEND;TZID=Eastern Standard Time:20260716T123000
                END:VEVENT
                END:VCALENDAR
            """.trimIndent()

            val ical = Biweekly.parse(ics).first()
            val event = ical.events[0]
            val date = LocalDate.of(2026, 7, 16)

            // Here we DON'T provide the VTIMEZONE definition in the file, 
            // but our code should resolve "Eastern Standard Time" via fallback.
            val occurrenceStart = event.getOccurrenceStart(date, ical.timezoneInfo)

            // 12:00 PM Eastern on July 16 (Daylight Savings) is 09:00 AM PST.
            val expected = LocalDateTime.of(2026, 7, 16, 9, 0)
            assertEquals("Event should start at 09:00 PST even without VTIMEZONE", expected, occurrenceStart)
        } finally {
            TimeZone.setDefault(originalDefault)
        }
    }

    @Test
    fun testGetOccurrenceStart_UtcTime() {
        val originalDefault = TimeZone.getDefault()
        try {
            // Set system timezone to PDT (UTC-7)
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            
            val ics = """
                BEGIN:VCALENDAR
                VERSION:2.0
                BEGIN:VEVENT
                SUMMARY:Your Additional: Virtual - Round Robin at Palo Alto Networks
                DTSTART:20260728T180000Z
                DTEND:20260728T185000Z
                END:VEVENT
                END:VCALENDAR
            """.trimIndent()

            val ical = Biweekly.parse(ics).first()
            val event = ical.events[0]
            val date = LocalDate.of(2026, 7, 28)

            val occurrenceStart = event.getOccurrenceStart(date, ical.timezoneInfo)

            // 18:00 UTC in PDT (UTC-7) is 11:00 AM.
            val expected = LocalDateTime.of(2026, 7, 28, 11, 0)
            assertEquals("18:00 UTC should be 11:00 AM PDT", expected, occurrenceStart)
        } finally {
            TimeZone.setDefault(originalDefault)
        }
    }
}
