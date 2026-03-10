// Copyright (c) 2024 Daniel Monedero-Tortola
package com.example.icscalendar

import android.Manifest
import android.app.AlarmManager
import android.content.Context
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
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        createNotificationChannel(this)
        
        // Start the service immediately
        WorkScheduler.refreshEvents(this)
        WorkScheduler.scheduleDailyWork(this)
        
        setContent {
            ICSCalendarTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CalendarApp(
                        modifier = Modifier.padding(innerPadding),
                        intent = intent
                    )
                }
            }
        }
    }
}

@Composable
fun CalendarApp(modifier: Modifier = Modifier, intent: Intent) {
    var events by remember { mutableStateOf<List<VEvent>>(emptyList()) }
    var yearMonth by remember { mutableStateOf(YearMonth.now()) }
    val initialDate = remember {
        intent.getStringExtra("dateToShow")?.let { LocalDate.parse(it) }
    }
    var selectedDate by remember { mutableStateOf(initialDate) }
    var isLoading by remember { mutableStateOf(true) }
    val context = LocalContext.current

    // Check for permissions
    var storagePermissionGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    var notificationPermissionGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    var alarmPermissionGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        notificationPermissionGranted = isGranted
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        storagePermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
        
        alarmPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationPermissionGranted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        if (!storagePermissionGranted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = "package:${context.packageName}".toUri()
                settingsLauncher.launch(intent)
            } else {
                settingsLauncher.launch(Intent(Settings.ACTION_SETTINGS))
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmPermissionGranted) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            intent.data = "package:${context.packageName}".toUri()
            settingsLauncher.launch(intent)
        }
    }

    // Automatically try to load the file when permission is granted
    LaunchedEffect(storagePermissionGranted) {
        if (storagePermissionGranted) {
            isLoading = true
            try {
                val documentsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                val file = File(documentsFolder, "Calendar.ics")
                if (file.exists()) {
                    FileInputStream(file).use { inputStream ->
                        val iCal = Biweekly.parse(inputStream).first()
                        events = iCal.events
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        } else {
            isLoading = false
        }
    }

    LaunchedEffect(events, notificationPermissionGranted) {
        if (events.isNotEmpty() && notificationPermissionGranted) {
            // Tell the service to update its foreground notification
            WorkScheduler.refreshEvents(context)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.loading), color = Color.White)
            }
        } else if (!storagePermissionGranted || !notificationPermissionGranted || !alarmPermissionGranted) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "This app needs storage, notification, and alarm permissions to function correctly.",
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { requestPermissions() }) {
                    Text("Grant Permissions")
                }
            }
        } else if (selectedDate == null) {
            CalendarView(
                events = events,
                yearMonth = yearMonth,
                onMonthChange = { yearMonth = it },
                onDayClick = { selectedDate = it }
            )
        } else {
            DayView(
                date = selectedDate!!,
                events = events,
                onBack = { selectedDate = null },
                onDateChange = { selectedDate = it }
            )
        }
    }
}

@Composable
fun DayView(date: LocalDate, events: List<VEvent>, onBack: () -> Unit, onDateChange: (LocalDate) -> Unit) {
    BackHandler {
        onBack()
    }

    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val sortedEvents = events.getSortedEventsForDay(date)

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = { onDateChange(date.minusDays(1)) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.LightGray,
                    contentColor = Color.DarkGray
                )
            ) {
                Text(stringResource(R.string.previous_button))
            }
            Text(
                text = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.clickable { onDateChange(LocalDate.now()) }
            )
            Button(
                onClick = { onDateChange(date.plusDays(1)) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.LightGray,
                    contentColor = Color.DarkGray
                )
            ) {
                Text(stringResource(R.string.next_button))
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            items(sortedEvents) { event ->
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        val summary = event.summary?.value
                        val description = event.description?.value
                        val location = event.location?.value
                        val isAllDay = event.isAllDay()

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!isAllDay) {
                                val eventDateTime = event.getOccurrenceStart(date)
                                if (eventDateTime != null) {
                                    Text(
                                        text = "${eventDateTime.format(timeFormatter)} ",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            if (summary != null) {
                                Text(
                                    text = summary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        if (!location.isNullOrBlank()) {
                            Text(
                                text = stringResource(R.string.location_label, location),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

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
fun CalendarView(
    events: List<VEvent>,
    yearMonth: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    onDayClick: (LocalDate) -> Unit
) {
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
        Button(
            onClick = { onMonthChange(yearMonth.minusMonths(1)) },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.LightGray,
                contentColor = Color.DarkGray
            )
        ) {
            Text(stringResource(R.string.previous_button))
        }
        Text(
            text = "${yearMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${yearMonth.year}",
            modifier = Modifier
                .weight(1f)
                .clickable { onMonthChange(YearMonth.now()) },
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )
        Button(
            onClick = { onMonthChange(yearMonth.plusMonths(1)) },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.LightGray,
                contentColor = Color.DarkGray
            )
        ) {
            Text(stringResource(R.string.next_button))
        }
    }
}

@Composable
fun DaysOfWeek() {
    Row {
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
    val firstOfMonth = yearMonth.atDay(1)
    val gridStartDate = firstOfMonth.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    val lastOfMonth = yearMonth.atEndOfMonth()
    val gridEndDate = lastOfMonth.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))

    val dayCount = ChronoUnit.DAYS.between(gridStartDate, gridEndDate).toInt() + 1

    LazyVerticalGrid(columns = GridCells.Fixed(7)) {
        items(dayCount) { i ->
            val date = gridStartDate.plusDays(i.toLong())
            val isToday = date.isEqual(LocalDate.now())

            val sortedEvents = events.getSortedEventsForDay(date)
            val dayNumberColor = if (date.monthValue != yearMonth.monthValue) Color.Gray else Color.LightGray

            Column(
                modifier = Modifier
                    .height(120.dp)
                    .border(0.5.dp, Color.LightGray)
                    .clickable { onDayClick(date) }
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${date.dayOfMonth}",
                    modifier = if (isToday) {
                        Modifier
                            .background(Color.LightGray, CircleShape)
                            .padding(4.dp)
                    } else {
                        Modifier
                    },
                    color = if (isToday) Color.DarkGray else dayNumberColor,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                )

                LazyColumn {
                    items(sortedEvents) { event ->
                        val text = event.summary?.value ?: stringResource(R.string.no_summary)
                        Text(
                            text = text,
                            maxLines = 1,
                            color = dayNumberColor,
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
        CalendarApp(intent = Intent())
    }
}
