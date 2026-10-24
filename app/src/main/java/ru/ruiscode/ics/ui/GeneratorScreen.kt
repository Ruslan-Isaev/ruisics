package ru.ruiscode.ics.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.ruiscode.ics.calendar.CalEventSpec
import ru.ruiscode.ics.calendar.CalendarImporter
import ru.ruiscode.ics.core.Uids
import ru.ruiscode.ics.ics.IcsEventSpec
import ru.ruiscode.ics.ics.IcsTime
import ru.ruiscode.ics.ics.IcsWriter
import ru.ruiscode.ics.ics.SeriesGenerator
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** Часовые пояса для генератора: системный и распространённые. */
private val TIMEZONES = listOf(
    "UTC",
    "Europe/Kaliningrad", "Europe/Moscow", "Europe/Samara",
    "Asia/Yekaterinburg", "Asia/Omsk", "Asia/Krasnoyarsk", "Asia/Irkutsk",
    "Asia/Yakutsk", "Asia/Vladivostok", "Asia/Magadan", "Asia/Kamchatka",
    "Asia/Almaty", "Asia/Tashkent", "Asia/Bishkek",
    "Europe/London", "Europe/Berlin",
    "America/New_York", "America/Los_Angeles",
)

private data class GeneratedEvent(
    val title: String,
    val description: String,
    val location: String,
    val allDay: Boolean,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    val timeZoneId: String,
)

/**
 * Генератор ICS: событие с параметрами, повторением и периодом генерации.
 * Предпросмотр показывает развёрнутые вхождения; экспорт пишет RRULE,
 * импорт в календарь добавляет развёрнутые события.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratorScreen(env: CalendarEnv, onBack: () -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var allDay by remember { mutableStateOf(false) }
    var startDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var startTime by remember { mutableStateOf("09:00") }
    var endDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var endTime by remember { mutableStateOf("10:00") }
    var timeZoneKey by remember { mutableStateOf(SYSTEM_TZ) }
    var freqKey by remember { mutableStateOf("none") }
    var intervalText by remember { mutableStateOf("1") }
    var endModeCount by remember { mutableStateOf(true) }
    var countText by remember { mutableStateOf("10") }
    var untilText by remember { mutableStateOf(LocalDate.now().plusMonths(3).toString()) }

    var busy by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<List<GeneratedEvent>?>(null) }
    var confirmImport by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<CalendarImporter.ImportResult?>(null) }
    var exportStatus by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    fun zone(): ZoneId? = when (timeZoneKey) {
        SYSTEM_TZ -> ZoneId.systemDefault()
        else -> try { ZoneId.of(timeZoneKey) } catch (e: Exception) { null }
    }

    fun recurrence(): SeriesGenerator.Recurrence? {
        val freq = when (freqKey) {
            "daily" -> SeriesGenerator.Freq.DAILY
            "weekly" -> SeriesGenerator.Freq.WEEKLY
            "monthly" -> SeriesGenerator.Freq.MONTHLY
            "yearly" -> SeriesGenerator.Freq.YEARLY
            else -> return null
        }
        val interval = intervalText.trim().toIntOrNull()?.takeIf { it >= 1 } ?: return null
        val count = if (endModeCount) countText.trim().toIntOrNull() else null
        val until = if (!endModeCount) parseDate(untilText)?.atTime(23, 59, 59)?.atZone(zone() ?: return null) else null
        return SeriesGenerator.Recurrence(freq = freq, interval = interval, count = count, until = until)
    }

    fun buildEvent(): GeneratedEvent? {
        val z = zone() ?: return null
        val start = try {
            if (allDay) {
                parseDate(startDate)!!.atStartOfDay(z)
            } else {
                LocalDate.parse(startDate.trim()).atTime(parseTime(startTime)!!).atZone(z)
            }
        } catch (e: Exception) {
            return null
        }
        val end = try {
            if (allDay) {
                parseDate(endDate)!!.plusDays(1).atStartOfDay(z)
            } else {
                LocalDate.parse(endDate.trim()).atTime(parseTime(endTime)!!).atZone(z)
            }
        } catch (e: Exception) {
            return null
        }
        if (!end.isAfter(start)) return null
        return GeneratedEvent(title.trim(), description.trim(), location.trim(), allDay, start, end, z.id)
    }

    fun validateAndPreview() {
        validationError = when {
            title.isBlank() -> s.get("generator.validation.title")
            zone() == null -> s.get("generator.validation.timezone")
            parseDate(startDate) == null || parseDate(endDate) == null -> s.get("generator.validation.date")
            !allDay && (parseTime(startTime) == null || parseTime(endTime) == null) -> s.get("generator.validation.time")
            else -> null
        }
        if (validationError != null) {
            preview = null
            return
        }
        val base = buildEvent()
        val rec = recurrence()
        if (base == null) {
            validationError = s.get("generator.validation.start_before_end")
            preview = null
            return
        }
        if (freqKey != "none" && rec == null) {
            validationError = if (intervalText.trim().toIntOrNull()?.takeIf { it >= 1 } == null) {
                s.get("generator.validation.interval")
            } else {
                s.format(
                    "generator.validation.count",
                    "min" to 1,
                    "max" to SeriesGenerator.MAX_OCCURRENCES,
                )
            }
            preview = null
            return
        }
        if (rec != null && rec.count != null && rec.count !in 1..SeriesGenerator.MAX_OCCURRENCES) {
            validationError = s.format(
                "generator.validation.count",
                "min" to 1,
                "max" to SeriesGenerator.MAX_OCCURRENCES,
            )
            preview = null
            return
        }
        val pairs = if (rec == null) {
            listOf(base.start to base.end)
        } else {
            SeriesGenerator.expand(base.start, base.end, rec)
        }
        preview = pairs.map { (st, en) -> base.copy(start = st, end = en) }
    }

    fun toSpecs(events: List<GeneratedEvent>): List<CalEventSpec> =
        events.mapIndexed { index, e ->
            CalEventSpec(
                uid = Uids.content("gen", e.title, e.description, e.location, e.start.toString(), e.end.toString(), index.toString()),
                title = e.title,
                description = e.description,
                location = e.location,
                allDay = e.allDay,
                startMillis = e.start.toInstant().toEpochMilli(),
                endMillis = e.end.toInstant().toEpochMilli(),
                timeZoneId = e.timeZoneId,
            )
        }

    fun toIcs(events: List<GeneratedEvent>): List<IcsEventSpec> {
        val base = events.firstOrNull() ?: return emptyList()
        val rec = recurrence()
        return listOf(
            IcsEventSpec(
                uid = Uids.content("gen", base.title, base.description, base.location, base.start.toString(), base.end.toString()),
                title = base.title,
                description = base.description,
                location = base.location,
                time = if (base.allDay) {
                    IcsTime.AllDay(base.start.toLocalDate(), base.end.toLocalDate())
                } else {
                    IcsTime.Timed(base.start, base.end, rec?.let { SeriesGenerator.rruleText(it) })
                },
            )
        )
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/calendar")
    ) { uri ->
        val events = preview
        if (uri != null && events != null) {
            scope.launch {
                busy = true
                exportStatus = withContext(Dispatchers.IO) {
                    try {
                        val ics = IcsWriter.buildSpecs(toIcs(events))
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.bufferedWriter(Charsets.UTF_8).use { it.write(ics) }
                        } ?: throw IllegalStateException(s.get("ics.file.create.error"))
                        s.format("generator.export.result", "count" to events.size)
                    } catch (e: Exception) {
                        s.format("ics.export.error", "message" to (e.message ?: s.get("error.generic")))
                    }
                }
                busy = false
            }
        }
    }

    ScreenScaffold(title = s.get("menu.generator"), onBack = onBack) {
        val events = preview

        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                Text(
                    s.get("generator.subtitle"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(s.get("generator.event.title")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(s.get("generator.event.description")) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text(s.get("generator.event.location")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.get("generator.all_day"), modifier = Modifier.weight(1f))
                    Switch(checked = allDay, onCheckedChange = { allDay = it })
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = startDate,
                    onValueChange = { startDate = it },
                    label = { Text(s.get("generator.start")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!allDay) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = { startTime = it },
                        label = { Text(s.get("shifts.daytype.start")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = endDate,
                    onValueChange = { endDate = it },
                    label = { Text(s.get("generator.end")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!allDay) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = { endTime = it },
                        label = { Text(s.get("shifts.daytype.end")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(8.dp))

                var tzExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = tzExpanded, onExpandedChange = { tzExpanded = it }) {
                    OutlinedTextField(
                        value = if (timeZoneKey == SYSTEM_TZ) s.get("generator.timezone.system")
                        else timeZoneKey,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(s.get("generator.timezone")) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tzExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = tzExpanded, onDismissRequest = { tzExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(s.get("generator.timezone.system")) },
                            onClick = { timeZoneKey = SYSTEM_TZ; tzExpanded = false },
                        )
                        TIMEZONES.forEach { tz ->
                            DropdownMenuItem(
                                text = { Text(tz) },
                                onClick = { timeZoneKey = tz; tzExpanded = false },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                var freqExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = freqExpanded, onExpandedChange = { freqExpanded = it }) {
                    OutlinedTextField(
                        value = s.get("generator.repeat.$freqKey"),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(s.get("generator.repeat")) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = freqExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = freqExpanded, onDismissRequest = { freqExpanded = false }) {
                        listOf("none", "daily", "weekly", "monthly", "yearly").forEach { key ->
                            DropdownMenuItem(
                                text = { Text(s.get("generator.repeat.$key")) },
                                onClick = { freqKey = key; freqExpanded = false },
                            )
                        }
                    }
                }

                if (freqKey != "none") {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = intervalText,
                        onValueChange = { intervalText = it.filter { ch -> ch.isDigit() }.take(3) },
                        label = { Text(s.get("generator.repeat.interval")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.get("generator.period"), modifier = Modifier.weight(1f))
                    }
                    Row {
                        Button(
                            onClick = { endModeCount = true },
                            enabled = endModeCount,
                            modifier = Modifier.weight(1f),
                        ) { Text(s.get("generator.period.count")) }
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        Button(
                            onClick = { endModeCount = false },
                            enabled = !endModeCount,
                            modifier = Modifier.weight(1f),
                        ) { Text(s.get("generator.period.until")) }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (endModeCount) {
                        OutlinedTextField(
                            value = countText,
                            onValueChange = { countText = it.filter { ch -> ch.isDigit() }.take(4) },
                            label = { Text(s.get("generator.period.count")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        OutlinedTextField(
                            value = untilText,
                            onValueChange = { untilText = it },
                            label = { Text(s.get("generator.period.until")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { validateAndPreview() },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(s.get("action.preview")) }
                ErrorText(validationError)
            }

            // Предпросмотр.
            if (events != null) {
                item {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Text(
                        s.format("generator.preview.count", "count" to events.size),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                item {
                    Text(
                        s.get("generator.preview.first") + ": " + formatOccurrence(s, events.first()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                item {
                    Text(
                        s.get("generator.preview.last") + ": " + formatOccurrence(s, events.last()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    if (!env.permissionsGranted) {
                        PermissionCard(onRequest = env.onRequestPermissions)
                    } else {
                        OutlinedButton(
                            onClick = { exportLauncher.launch("generated-${LocalDate.now()}.ics") },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(s.get("generator.export")) }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { confirmImport = true },
                            enabled = !busy && env.selectedCalendarId != null,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(s.get("generator.import")) }
                    }
                }
            }

            item {
                progress?.let { (done, total) ->
                    Text(
                        s.format("import.progress", "done" to done, "total" to total),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                lastResult?.let { result ->
                    Text(
                        s.format(
                            "import.result",
                            mapOf(
                                "inserted" to result.inserted,
                                "duplicates" to result.duplicates,
                                "failed" to result.failed,
                            )
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                exportStatus?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                BusyLine(busy)
                Spacer(Modifier.height(16.dp))
            }
        }

        if (confirmImport) {
            val list = preview ?: emptyList()
            val calendarName = env.selectedCalendar?.name ?: ""
            ConfirmDialog(
                title = s.get("generator.import"),
                text = {
                    Text(
                        s.plural("import.confirm", list.size, mapOf("calendar" to calendarName))
                    )
                },
                confirmLabel = s.get("action.import"),
                confirmDanger = false,
                onConfirm = {
                    confirmImport = false
                    val calendarId = env.selectedCalendarId ?: return@ConfirmDialog
                    scope.launch {
                        busy = true
                        progress = null
                        val result = withContext(Dispatchers.IO) {
                            CalendarImporter.import(
                                resolver = context.contentResolver,
                                calendarId = calendarId,
                                specs = toSpecs(list),
                                onProgress = { done, total -> progress = done to total },
                            )
                        }
                        busy = false
                        progress = null
                        lastResult = result
                    }
                },
                onDismiss = { confirmImport = false },
            )
        }
    }
}

private const val SYSTEM_TZ = "__system__"

private fun formatOccurrence(
    s: ru.ruiscode.ics.i18n.StringResolver,
    event: GeneratedEvent,
): String = if (event.allDay) {
    s.formatDate(event.start.toLocalDate())
} else {
    s.formatDateTime(event.start)
}

private fun parseTime(text: String): LocalTime? = try {
    val parts = text.trim().split(':')
    require(parts.size == 2)
    LocalTime.of(parts[0].toInt(), parts[1].toInt())
} catch (e: Exception) {
    null
}

private fun parseDate(text: String): LocalDate? = try {
    LocalDate.parse(text.trim())
} catch (e: Exception) {
    null
}
