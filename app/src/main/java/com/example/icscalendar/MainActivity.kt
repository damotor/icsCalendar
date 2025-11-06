package com.example.icscalendar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import biweekly.Biweekly
import biweekly.component.VEvent
import com.example.icscalendar.ui.theme.ICSCalendarTheme
import java.io.File
import java.io.FileInputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.TimeZone
import androidx.core.net.toUri

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ICSCalendarTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CalendarApp(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

fun VEvent.getOccurrenceStart(date: LocalDate): java.time.LocalDateTime? {val dtStartProp = dateStart ?: return null
    val systemZoneId = java.time.ZoneId.systemDefault()
    val isAllDay = dtStartProp.parameters.get("VALUE")?.contains("DATE") == true

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

        // *** THIS IS THE CORRECTED LOGIC ***
        val isInRange = if (!isAllDay) {
            !date.isBefore(occurrenceStartDate) && !date.isAfter(occurrenceEnd)
        } else {
            // For recurring all-day events, the end date is exclusive.
            // If the duration is null, it's a single day. If not, calculate the end.
            val endForAllDay = if (eventDuration != null) occurrenceEnd else occurrenceStartDate.plusDays(1)
            !date.isBefore(occurrenceStartDate) && date.isBefore(endForAllDay)
        }

        if (isInRange) {
            return occurrenceStartDateTime.toLocalDateTime()
        }
    }

    return null
}


@Composable
fun CalendarApp(modifier: Modifier = Modifier) {
    var events by remember { mutableStateOf<List<VEvent>>(emptyList()) }
    var yearMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    val context = LocalContext.current

    // Check for MANAGE_EXTERNAL_STORAGE permission
    var permissionGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                // On older versions, the legacy permission is sufficient
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    // Launcher to take the user to the settings screen
    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // After returning from settings, re-check the permission
        permissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            // This part is for older Android versions, just in case
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    // Function to request permission
    fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = "package:${context.packageName}".toUri()
            settingsLauncher.launch(intent)
        }
        // Note: For simplicity, this example omits the logic for older permissions.
        // The original READ_EXTERNAL_STORAGE request would go here in an 'else' block
        // if you needed to support Android 10 and below with the same flow.
    }

    // Automatically try to load the file when permission is granted
    LaunchedEffect(permissionGranted) {
        if (permissionGranted) {
            try {
                val file = File(Environment.getExternalStorageDirectory(), "Calendar.ics")
                if (file.exists()) {
                    FileInputStream(file).use { inputStream ->
                        val ical = Biweekly.parse(inputStream).first()
                        events = ical.events
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (selectedDate == null) {
            if (permissionGranted) {
                CalendarView(
                    events = events,
                    yearMonth = yearMonth,
                    onMonthChange = { yearMonth = it },
                    onDayClick = { selectedDate = it }
                )
            } else {
                // Show a screen to explain why the permission is needed
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "This app needs 'All Files Access' to read the Calendar.ics file from your device's storage. Please grant the permission on the next screen.",
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { requestPermission() }) {
                        Text("Grant Permission")
                    }
                }
            }
        } else {
            DayView(
                date = selectedDate!!,
                events = events,
                onBack = { selectedDate = null }
            )
        }
    }
}

@Composable
fun DayView(date: LocalDate, events: List<VEvent>, onBack: () -> Unit) {
    // *** Add this BackHandler to intercept the system back button press ***
    BackHandler {
        onBack()
    }

    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    // Use getOccurrenceStart and filter out nulls
    val eventsForDay = events.mapNotNull { event ->
        event.getOccurrenceStart(date)?.let { startTime ->
            Pair(event, startTime)
        }
    }

    val (allDayEvents, timedEvents) = eventsForDay.partition { (event, _) ->
        event.dateStart.parameters.get("VALUE")?.contains("DATE") == true
    }
    // Sort the timed events by their specific start time for that day
    val sortedEvents = allDayEvents.map { it.first } + timedEvents.sortedBy { it.second }.map { it.first }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)),
                modifier = Modifier.padding(16.dp)
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            items(sortedEvents) { event ->
                // *** Wrap the event details in a SelectionContainer ***
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        val summary = event.summary?.value
                        val description = event.description?.value
                        val location = event.location?.value
                        val isAllDay = event.dateStart.parameters.get("VALUE")?.contains("DATE") == true

                        // --- Event Header (Time and Summary) ---
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!isAllDay) {
                                val eventDateTime = event.dateStart.value.toInstant().atZone(java.time.ZoneId.systemDefault())
                                Text(
                                    text = "${eventDateTime.format(timeFormatter)} ",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (summary != null) {
                                Text(
                                    text = summary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // --- Location (No copy button needed) ---
                        if (!location.isNullOrBlank()) {
                            Text(
                                text = "Location: $location",
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        // --- Description (No copy button needed) ---
                        if (!description.isNullOrBlank()) {
                            Text(
                                text = description,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun CalendarView(events: List<VEvent>, yearMonth: YearMonth, onMonthChange: (YearMonth) -> Unit, onDayClick: (LocalDate) -> Unit) {
    Column {
        MonthHeader(yearMonth = yearMonth, onMonthChange = onMonthChange)
        DaysOfWeek()
        MonthGrid(yearMonth = yearMonth, events = events, onDayClick = onDayClick)
    }
}

@Composable
fun MonthHeader(yearMonth: YearMonth, onMonthChange: (YearMonth) -> Unit) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(onClick = { onMonthChange(yearMonth.minusMonths(1)) },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.LightGray,
                contentColor = Color.DarkGray
            )) {
            Text("<")
        }
        Text(
            text = "${yearMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${yearMonth.year}",
            modifier = Modifier
                .weight(1f)
                .clickable { onMonthChange(YearMonth.now()) },
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )
        Button(onClick = { onMonthChange(yearMonth.plusMonths(1)) },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.LightGray,
                contentColor = Color.DarkGray
            )) {
            Text(">")
        }
    }
}

@Composable
fun DaysOfWeek() {
    Row {
        // DayOfWeek enum starts with MONDAY (index 0) and ends with SUNDAY (index 6).
        // To start the week on Monday, we can just use the natural order of the enum values.
        val days = DayOfWeek.entries.toTypedArray()

        for (day in days) {
            Text(
                text = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
        }
    }
}



@Composable
fun MonthGrid(yearMonth: YearMonth, events: List<VEvent>, onDayClick: (LocalDate) -> Unit) {
    // Determine the start date: the Monday of the week containing the 1st of the month.
    val firstOfMonth = yearMonth.atDay(1)
    val gridStartDate = firstOfMonth.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    // Determine the end date: the Sunday of the week containing the last day of the month.
    val lastOfMonth = yearMonth.atEndOfMonth()
    val gridEndDate = lastOfMonth.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))

    val dayCount = ChronoUnit.DAYS.between(gridStartDate, gridEndDate).toInt() + 1

    LazyVerticalGrid(columns = GridCells.Fixed(7)) {
        items(dayCount) { i ->
            val date = gridStartDate.plusDays(i.toLong())
            val isToday = date.isEqual(LocalDate.now())

            // Get events with their specific start times for the current day
            val eventsForDay = events.mapNotNull { event ->
                event.getOccurrenceStart(date)?.let { startTime ->
                    Pair(event, startTime)
                }
            }

            // Separate all-day from timed events
            val (allDayEvents, timedEvents) = eventsForDay.partition { (event, _) ->
                event.dateStart.parameters.get("VALUE")?.contains("DATE") == true
            }

            // Sort timed events by their start time, then combine with all-day events
            val sortedEvents = allDayEvents.map { it.first } + timedEvents.sortedBy { it.second }.map { it.first }

            // Determine color for text: gray for days outside the current month
            val dayNumberColor = if (date.monthValue != yearMonth.monthValue) Color.Gray else Color.LightGray

            Column(
                modifier = Modifier
                    .height(120.dp)
                    .border(0.5.dp, Color.LightGray)
                    .clickable { onDayClick(date) }
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally // Center the day number
            ) {
                // Day number with highlighting for today
                Text(
                    text = "${date.dayOfMonth}",
                    modifier = if (isToday) {
                        Modifier
                            .background(Color.LightGray, CircleShape)
                            .padding(4.dp) // Padding inside the circle
                    } else {
                        Modifier
                    },
                    // Use white text for today's date to make it readable on the red background
                    color = if (isToday) Color.DarkGray else dayNumberColor,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                )

                // Events list
                LazyColumn {
                    items(sortedEvents) { event ->
                        val text = event.summary?.value ?: "-"
                        Text(
                            text = text,
                            maxLines = 1,
                            color = dayNumberColor, // Event text color matches the day's month status
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}



@Preview(showBackground = true)
@Composable
fun CalendarAppPreview() {
    ICSCalendarTheme {
        CalendarApp()
    }
}
