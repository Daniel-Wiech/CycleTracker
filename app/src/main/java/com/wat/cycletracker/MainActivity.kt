
package com.wat.cycletracker

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BasalBodyTemperatureRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.wat.cycletracker.data.DatabaseProvider
import com.wat.cycletracker.data.PeriodEntry
import com.wat.cycletracker.data.TemperatureEntry
import com.wat.cycletracker.ui.theme.CycleTrackerTheme
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val polishLocale = Locale.forLanguageTag("pl-PL")

private val dateFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy")

private val dateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

private val monthFormatter =
    DateTimeFormatter.ofPattern("LLLL yyyy", polishLocale)

private fun LocalDate.toDatabaseMillis(): Long {
    return atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}

private fun Long.toLocalDateFromDatabase(): LocalDate {
    return Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CycleTrackerTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    CycleTrackerScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun CycleTrackerScreen(modifier: Modifier = Modifier) {

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val database = remember(context) {
        DatabaseProvider.getDatabase(context)
    }

    val temperatureDao = remember(database) {
        database.temperatureDao()
    }

    val periodDao = remember(database) {
        database.periodDao()
    }

    // Stan pomiarów BBT
    var temperatureInput by remember {
        mutableStateOf("")
    }

    var temperatureDate by remember {
        mutableStateOf(LocalDate.now())
    }

    var temperatureTime by remember {
        mutableStateOf(
            LocalTime.now().withSecond(0).withNano(0)
        )
    }

    var temperatureMessage by remember {
        mutableStateOf("")
    }

    var temperatures by remember {
        mutableStateOf<List<TemperatureEntry>>(emptyList())
    }

    // Stan kalendarza miesiączkowego
    var displayedMonth by remember {
        mutableStateOf(YearMonth.now())
    }

    var periodStart by remember {
        mutableStateOf<LocalDate?>(null)
    }

    var periodEnd by remember {
        mutableStateOf<LocalDate?>(null)
    }

    var periodNotes by remember {
        mutableStateOf("")
    }

    var periodMessage by remember {
        mutableStateOf("")
    }

    var periods by remember {
        mutableStateOf<List<PeriodEntry>>(emptyList())
    }

    // Stan Health Connect
    var healthConnectText by remember {
        mutableStateOf("Nie pobrano jeszcze danych.")
    }

    val healthConnectAvailable = remember(context) {
        HealthConnectClient.getSdkStatus(context) ==
                HealthConnectClient.SDK_AVAILABLE
    }

    val healthConnectClient = remember(
        context,
        healthConnectAvailable
    ) {
        if (healthConnectAvailable) {
            HealthConnectClient.getOrCreate(context)
        } else {
            null
        }
    }

    val permissions = remember {
        setOf(
            HealthPermission.getReadPermission(
                BasalBodyTemperatureRecord::class
            )
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController
            .createRequestPermissionResultContract()
    ) { granted ->

        if (granted.containsAll(permissions)) {
            coroutineScope.launch {
                val client = healthConnectClient

                healthConnectText = if (client != null) {
                    readTemperatureFromHealthConnect(client)
                } else {
                    "Health Connect jest niedostępny."
                }
            }
        } else {
            healthConnectText =
                "Odmówiono dostępu do Health Connect."
        }
    }

    // Wczytaj dane po otwarciu ekranu
    LaunchedEffect(temperatureDao, periodDao) {
        try {
            temperatures = temperatureDao.getAll()
            periods = periodDao.getAll()
        } catch (e: Exception) {
            periodMessage =
                "Błąd odczytu danych: ${e.localizedMessage}"
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = "CycleTracker",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        // =====================================
        // KALENDARZ MENSTRUACYJNY
        // =====================================

        Text(
            text = "Kalendarz menstruacyjny",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement =
                        Arrangement.SpaceBetween
                ) {

                    TextButton(
                        onClick = {
                            displayedMonth =
                                displayedMonth.minusMonths(1)
                        }
                    ) {
                        Text("‹")
                    }

                    Text(
                        text = displayedMonth
                            .atDay(1)
                            .format(monthFormatter)
                            .replaceFirstChar {
                                it.titlecase(polishLocale)
                            },
                        style = MaterialTheme.typography.titleMedium
                    )

                    TextButton(
                        onClick = {
                            displayedMonth =
                                displayedMonth.plusMonths(1)
                        }
                    ) {
                        Text("›")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                MenstrualCalendar(
                    displayedMonth = displayedMonth,
                    periods = periods,
                    selectedStart = periodStart,
                    selectedEnd = periodEnd,
                    onDayClick = { clickedDate ->

                        if (
                            periodStart == null ||
                            periodEnd != null ||
                            clickedDate.isBefore(periodStart)
                        ) {
                            periodStart = clickedDate
                            periodEnd = null
                        } else {
                            periodEnd = clickedDate
                        }

                        periodMessage = ""
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Dotknij pierwszego dnia miesiączki, " +
                    "a następnie ostatniego.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Początek: " +
                    (periodStart?.format(dateFormatter) ?: "nie wybrano")
        )

        Text(
            text = "Koniec: " +
                    (periodEnd?.format(dateFormatter) ?: "nie wybrano")
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = periodNotes,
            onValueChange = {
                periodNotes = it
            },
            label = {
                Text("Notatki (opcjonalnie)")
            },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                val start = periodStart
                val end = periodEnd

                when {
                    start == null || end == null -> {
                        periodMessage =
                            "Wybierz początek i koniec miesiączki."
                    }

                    end.isBefore(start) -> {
                        periodMessage =
                            "Data końca nie może być wcześniejsza."
                    }

                    periods.any { existing ->
                        val existingStart =
                            existing.startDate.toLocalDateFromDatabase()

                        val existingEnd =
                            (existing.endDate ?: existing.startDate)
                                .toLocalDateFromDatabase()

                        !end.isBefore(existingStart) &&
                                !start.isAfter(existingEnd)
                    } -> {
                        periodMessage =
                            "Ten zakres pokrywa się z zapisaną miesiączką."
                    }

                    else -> {
                        coroutineScope.launch {
                            try {
                                val entry = PeriodEntry(
                                    startDate = start.toDatabaseMillis(),
                                    endDate = end.toDatabaseMillis(),
                                    notes = periodNotes.trim()
                                )

                                periodDao.insert(entry)
                                periods = periodDao.getAll()

                                periodStart = null
                                periodEnd = null
                                periodNotes = ""

                                periodMessage =
                                    "Miesiączka została zapisana!"

                            } catch (e: Exception) {
                                periodMessage =
                                    "Błąd zapisu: ${e.localizedMessage}"
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Zapisz miesiączkę")
        }

        TextButton(
            onClick = {
                periodStart = null
                periodEnd = null
                periodMessage = ""
            }
        ) {
            Text("Wyczyść zaznaczenie")
        }

        if (periodMessage.isNotEmpty()) {
            Text(
                text = periodMessage,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Zapisane miesiączki",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (periods.isEmpty()) {
            Text("Brak zapisanych miesiączek.")
        } else {
            periods.forEach { entry ->

                val start =
                    entry.startDate.toLocalDateFromDatabase()

                val end =
                    (entry.endDate ?: entry.startDate)
                        .toLocalDateFromDatabase()

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {

                        Text(
                            text = "${start.format(dateFormatter)} – " +
                                    end.format(dateFormatter),
                            style = MaterialTheme.typography.titleSmall
                        )

                        if (entry.notes.isNotBlank()) {
                            Text(
                                text = entry.notes,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    try {
                                        periodDao.delete(entry)
                                        periods = periodDao.getAll()
                                        periodMessage =
                                            "Usunięto wpis."
                                    } catch (e: Exception) {
                                        periodMessage =
                                            "Błąd usuwania: " +
                                                    e.localizedMessage
                                    }
                                }
                            }
                        ) {
                            Text("Usuń")
                        }
                    }
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 24.dp)
        )

        // =====================================
        // RĘCZNY POMIAR BBT
        // =====================================

        Text(
            text = "Dodaj pomiar BBT",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = temperatureInput,
            onValueChange = {
                temperatureInput = it
            },
            label = {
                Text("Temperatura (°C)")
            },
            placeholder = {
                Text("np. 36,55")
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = {
                DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        temperatureDate =
                            LocalDate.of(year, month + 1, day)
                    },
                    temperatureDate.year,
                    temperatureDate.monthValue - 1,
                    temperatureDate.dayOfMonth
                ).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Data: ${temperatureDate.format(dateFormatter)}"
            )
        }

        OutlinedButton(
            onClick = {
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        temperatureTime =
                            LocalTime.of(hour, minute)
                    },
                    temperatureTime.hour,
                    temperatureTime.minute,
                    true
                ).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Godzina: " +
                        temperatureTime.format(
                            DateTimeFormatter.ofPattern("HH:mm")
                        )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                val temperature = temperatureInput
                    .trim()
                    .replace(',', '.')
                    .toDoubleOrNull()

                if (
                    temperature == null ||
                    !temperature.isFinite() ||
                    temperature !in 30.0..45.0
                ) {
                    temperatureMessage =
                        "Podaj poprawną temperaturę."
                } else {
                    coroutineScope.launch {
                        try {
                            val measuredAt =
                                LocalDateTime.of(
                                    temperatureDate,
                                    temperatureTime
                                )
                                    .atZone(ZoneId.systemDefault())
                                    .toInstant()
                                    .toEpochMilli()

                            temperatureDao.insert(
                                TemperatureEntry(
                                    measuredAt = measuredAt,
                                    temperatureCelsius = temperature,
                                    source = "MANUAL"
                                )
                            )

                            temperatures =
                                temperatureDao.getAll()

                            temperatureInput = ""
                            temperatureMessage =
                                "Pomiar BBT został zapisany!"

                        } catch (e: Exception) {
                            temperatureMessage =
                                "Błąd zapisu: ${e.localizedMessage}"
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Zapisz pomiar BBT")
        }

        if (temperatureMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(temperatureMessage)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Historia BBT (${temperatures.size})",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (temperatures.isEmpty()) {
            Text("Brak zapisanych pomiarów.")
        } else {
            temperatures.take(5).forEach { entry ->

                val dateTime = Instant
                    .ofEpochMilli(entry.measuredAt)
                    .atZone(ZoneId.systemDefault())
                    .format(dateTimeFormatter)

                val formattedTemp = String.format(
                    polishLocale,
                    "%.2f",
                    entry.temperatureCelsius
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "$formattedTemp °C",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text(dateTime)

                        Text(
                            text = "Źródło: ${entry.source}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 24.dp)
        )

        // =====================================
        // HEALTH CONNECT
        // =====================================

        Text(
            text = "Health Connect",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(healthConnectText)

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            enabled = healthConnectAvailable,
            onClick = {
                val client = healthConnectClient

                if (client != null) {
                    coroutineScope.launch {
                        try {
                            val granted =
                                client.permissionController
                                    .getGrantedPermissions()

                            if (granted.containsAll(permissions)) {
                                healthConnectText =
                                    readTemperatureFromHealthConnect(client)
                            } else {
                                permissionLauncher.launch(permissions)
                            }
                        } catch (e: Exception) {
                            healthConnectText =
                                "Błąd: ${e.localizedMessage}"
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Pobierz temperaturę z Health Connect")
        }

        if (!healthConnectAvailable) {
            Text(
                text = "Health Connect jest niedostępny.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun MenstrualCalendar(
    displayedMonth: YearMonth,
    periods: List<PeriodEntry>,
    selectedStart: LocalDate?,
    selectedEnd: LocalDate?,
    onDayClick: (LocalDate) -> Unit
) {

    val weekDays = listOf(
        "Pn", "Wt", "Śr", "Cz", "Pt", "So", "Nd"
    )

    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        weekDays.forEach { day ->
            Text(
                text = day,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    val firstDay = displayedMonth.atDay(1)
    val leadingEmptyDays = firstDay.dayOfWeek.value - 1
    val daysInMonth = displayedMonth.lengthOfMonth()

    val totalCells = leadingEmptyDays + daysInMonth
    val numberOfRows = (totalCells + 6) / 7

    for (week in 0 until numberOfRows) {

        Row(
            modifier = Modifier.fillMaxWidth()
        ) {

            for (dayOfWeek in 0..6) {

                val cellIndex = week * 7 + dayOfWeek
                val dayNumber =
                    cellIndex - leadingEmptyDays + 1

                if (dayNumber in 1..daysInMonth) {

                    val date = displayedMonth.atDay(dayNumber)

                    val isPeriodDay = periods.any { entry ->

                        val start =
                            entry.startDate.toLocalDateFromDatabase()

                        val end =
                            (entry.endDate ?: entry.startDate)
                                .toLocalDateFromDatabase()

                        !date.isBefore(start) &&
                                !date.isAfter(end)
                    }

                    val isSelected = when {
                        selectedStart == null -> false
                        selectedEnd == null ->
                            date == selectedStart
                        else ->
                            !date.isBefore(selectedStart) &&
                                    !date.isAfter(selectedEnd)
                    }

                    val backgroundColor = when {
                        isSelected ->
                            MaterialTheme.colorScheme.primaryContainer

                        isPeriodDay ->
                            MaterialTheme.colorScheme.tertiaryContainer

                        else ->
                            MaterialTheme.colorScheme.surface
                    }

                    Surface(
                        onClick = {
                            onDayClick(date)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(2.dp)
                            .aspectRatio(1f),
                        shape = RoundedCornerShape(8.dp),
                        color = backgroundColor
                    ) {
                        Box(
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dayNumber.toString(),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                } else {

                    Spacer(
                        modifier = Modifier
                            .weight(1f)
                            .padding(2.dp)
                            .aspectRatio(1f)
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            modifier = Modifier.size(16.dp)
        ) {}

        Text(
            text = "Miesiączka",
            style = MaterialTheme.typography.labelMedium
        )

        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(16.dp)
        ) {}

        Text(
            text = "Zaznaczenie",
            style = MaterialTheme.typography.labelMedium
        )
    }
}

suspend fun readTemperatureFromHealthConnect(
    client: HealthConnectClient
): String {

    return try {
        val endTime = Instant.now()
        val startTime =
            endTime.minus(7, ChronoUnit.DAYS)

        val response = client.readRecords(
            ReadRecordsRequest(
                recordType =
                    BasalBodyTemperatureRecord::class,
                timeRangeFilter =
                    TimeRangeFilter.between(
                        startTime,
                        endTime
                    )
            )
        )

        val latestRecord = response.records
            .maxByOrNull { it.time }

        if (latestRecord != null) {

            val formatted = String.format(
                polishLocale,
                "%.2f",
                latestRecord.temperature.inCelsius
            )

            "Ostatni pomiar BBT: $formatted °C"

        } else {
            "Brak pomiarów BBT w ostatnich 7 dniach."
        }

    } catch (e: Exception) {
        "Błąd odczytu: ${e.localizedMessage}"
    }
}
