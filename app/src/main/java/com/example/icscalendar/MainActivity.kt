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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import biweekly.ICalendar
import biweekly.component.VEvent
import com.example.icscalendar.ui.theme.ICSCalendarTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    var calendar by remember { mutableStateOf<ICalendar?>(null) }
    var yearMonth by remember { mutableStateOf(YearMonth.now()) }
    val initialDate = remember {
        intent.getStringExtra("dateToShow")?.let { LocalDate.parse(it) }
    }
    var selectedDate by remember { mutableStateOf(initialDate) }
    var isLoadingFile by remember { mutableStateOf(true) }
    val context = LocalContext.current

    // Permissions
    var storagePermissionGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
            else ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        )
    }
    var notificationPermissionGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            else true
        )
    }
    var alarmPermissionGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
            else true
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notificationPermissionGranted = it }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        storagePermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        alarmPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms() else true
    }

    fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationPermissionGranted) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        if (!storagePermissionGranted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply { data = "package:${context.packageName}".toUri() }
                settingsLauncher.launch(intent)
            } else settingsLauncher.launch(Intent(Settings.ACTION_SETTINGS))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmPermissionGranted) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply { data = "package:${context.packageName}".toUri() }
            settingsLauncher.launch(intent)
        }
    }

    LaunchedEffect(storagePermissionGranted) {
        if (storagePermissionGranted) {
            isLoadingFile = true
            try {
                val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Calendar.ics")
                if (file.exists()) {
                    calendar = parseIcsParallel(file)
                }
            } catch (e: Exception) { e.printStackTrace() }
            finally { isLoadingFile = false }
        } else isLoadingFile = false
    }

    LaunchedEffect(calendar, notificationPermissionGranted) {
        if (calendar != null && notificationPermissionGranted) WorkScheduler.refreshEvents(context)
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (isLoadingFile) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.loading), color = Color.White)
                }
            }
        } else if (!storagePermissionGranted || !notificationPermissionGranted || !alarmPermissionGranted) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("This app needs storage, notification, and alarm permissions to function correctly.", textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { requestPermissions() }) { Text("Grant Permissions") }
            }
        } else if (selectedDate == null) {
            CalendarView(calendar = calendar, yearMonth = yearMonth, onMonthChange = { yearMonth = it }, onDayClick = { selectedDate = it })
        } else {
            DayView(date = selectedDate!!, calendar = calendar, onBack = { selectedDate = null }, onDateChange = { selectedDate = it })
        }
    }
}

@Composable
fun DayView(date: LocalDate, calendar: ICalendar?, onBack: () -> Unit, onDateChange: (LocalDate) -> Unit) {
    BackHandler { onBack() }
    
    val initialDate = remember { date }
    val middlePage = Int.MAX_VALUE / 2
    val pagerState = rememberPagerState(
        initialPage = middlePage + ChronoUnit.DAYS.between(initialDate, date).toInt(),
        pageCount = { Int.MAX_VALUE }
    )

    LaunchedEffect(pagerState.currentPage) {
        val newDate = initialDate.plusDays((pagerState.currentPage - middlePage).toLong())
        if (newDate != date) {
            onDateChange(newDate)
        }
    }

    LaunchedEffect(date) {
        val targetPage = middlePage + ChronoUnit.DAYS.between(initialDate, date).toInt()
        if (pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    Column {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = { onDateChange(date.minusDays(1)) }, colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray, contentColor = Color.DarkGray)) { Text(stringResource(R.string.previous_button)) }
            Text(text = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.clickable { onDateChange(LocalDate.now()) })
            Button(onClick = { onDateChange(date.plusDays(1)) }, colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray, contentColor = Color.DarkGray)) { Text(stringResource(R.string.next_button)) }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            val pageDate = initialDate.plusDays((pageIndex - middlePage).toLong())
            DayContent(date = pageDate, calendar = calendar)
        }
    }
}

@Composable
fun DayContent(date: LocalDate, calendar: ICalendar?) {
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    var sortedEvents by remember { mutableStateOf<List<VEvent>>(emptyList()) }

    LaunchedEffect(date, calendar) {
        sortedEvents = calendar?.events?.getSortedEventsForDay(date, calendar.timezoneInfo) ?: emptyList()
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        items(sortedEvents) { event ->
            SelectionContainer {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    val isAllDay = event.isAllDay()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!isAllDay) {
                            val eventDateTime = event.getOccurrenceStart(date, calendar?.timezoneInfo)
                            if (eventDateTime != null) Text(text = "${eventDateTime.format(timeFormatter)} ", fontWeight = FontWeight.Bold)
                        }
                        event.summary?.value?.let { Text(text = it, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)) }
                    }
                    event.location?.value?.let { if (it.isNotBlank()) Text(text = stringResource(R.string.location_label, it), modifier = Modifier.padding(top = 4.dp)) }
                    event.description?.value?.let { if (it.isNotBlank()) Text(text = it, modifier = Modifier.padding(top = 4.dp)) }
                }
            }
        }
    }
}

@Composable
fun CalendarView(calendar: ICalendar?, yearMonth: YearMonth, onMonthChange: (YearMonth) -> Unit, onDayClick: (LocalDate) -> Unit) {
    val initialYearMonth = remember { yearMonth }
    val middlePage = Int.MAX_VALUE / 2
    val pagerState = rememberPagerState(
        initialPage = middlePage + ChronoUnit.MONTHS.between(initialYearMonth.atDay(1), yearMonth.atDay(1)).toInt(),
        pageCount = { Int.MAX_VALUE }
    )

    LaunchedEffect(pagerState.currentPage) {
        val newYearMonth = initialYearMonth.plusMonths((pagerState.currentPage - middlePage).toLong())
        if (newYearMonth != yearMonth) {
            onMonthChange(newYearMonth)
        }
    }

    LaunchedEffect(yearMonth) {
        val targetPage = middlePage + ChronoUnit.MONTHS.between(initialYearMonth.atDay(1), yearMonth.atDay(1)).toInt()
        if (pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    Column {
        MonthHeader(yearMonth = yearMonth, onMonthChange = onMonthChange)
        DaysOfWeek()
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            val pageYearMonth = initialYearMonth.plusMonths((pageIndex - middlePage).toLong())
            MonthGrid(yearMonth = pageYearMonth, calendar = calendar, onDayClick = onDayClick)
        }
    }
}

@Composable
fun MonthHeader(yearMonth: YearMonth, onMonthChange: (YearMonth) -> Unit) {
    Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = { onMonthChange(yearMonth.minusMonths(1)) }, colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray, contentColor = Color.DarkGray)) { Text(stringResource(R.string.previous_button)) }
        Text(text = "${yearMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${yearMonth.year}", modifier = Modifier.weight(1f).clickable { onMonthChange(YearMonth.now()) }, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
        Button(onClick = { onMonthChange(yearMonth.plusMonths(1)) }, colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray, contentColor = Color.DarkGray)) { Text(stringResource(R.string.next_button)) }
    }
}

@Composable
fun DaysOfWeek() {
    Row {
        DayOfWeek.entries.forEach { day ->
            Text(text = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()), modifier = Modifier.weight(1f).padding(vertical = 4.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun MonthGrid(yearMonth: YearMonth, calendar: ICalendar?, onDayClick: (LocalDate) -> Unit) {
    val firstOfMonth = yearMonth.atDay(1)
    val gridStartDate = firstOfMonth.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val gridEndDate = yearMonth.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    val dayCount = ChronoUnit.DAYS.between(gridStartDate, gridEndDate).toInt() + 1

    var eventsByDay by remember { mutableStateOf<Map<LocalDate, List<VEvent>>>(emptyMap()) }
    var isCalculating by remember { mutableStateOf(false) }

    LaunchedEffect(yearMonth, calendar) {
        if (calendar == null) return@LaunchedEffect
        isCalculating = true
        val result = calendar.events.getEventsByDayInRange(gridStartDate, gridEndDate, calendar.timezoneInfo)
        eventsByDay = result
        isCalculating = false
    }

    Box {
        LazyVerticalGrid(columns = GridCells.Fixed(7)) {
            items(dayCount) { i ->
                val date = gridStartDate.plusDays(i.toLong())
                val isToday = date.isEqual(LocalDate.now())
                val sortedEvents = eventsByDay[date] ?: emptyList()
                val dayNumberColor = if (date.monthValue != yearMonth.monthValue) Color.Gray else Color.LightGray

                Column(modifier = Modifier.height(120.dp).border(0.5.dp, Color.LightGray).clickable { onDayClick(date) }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "${date.dayOfMonth}", modifier = if (isToday) Modifier.background(Color.LightGray, CircleShape).padding(4.dp) else Modifier, color = if (isToday) Color.DarkGray else dayNumberColor, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
                    LazyColumn {
                        items(sortedEvents) { event ->
                            Text(text = event.summary?.value ?: stringResource(R.string.no_summary), maxLines = 1, color = dayNumberColor, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        if (isCalculating) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}
