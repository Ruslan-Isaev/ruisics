package ru.ruiscode.ics.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.ruiscode.ics.calendar.CalendarData
import ru.ruiscode.ics.ics.IcsParser
import ru.ruiscode.ics.ics.IcsWriter
import java.time.LocalDate
import java.time.ZoneId

/** Импорт событий из .ics и экспорт календаря в .ics. */
@Composable
fun IcsScreen(env: CalendarEnv, onBack: () -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val status = remember { mutableStateListOf<String>() }

    fun addStatus(msg: String) {
        status.add(0, msg)
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val calendarId = env.selectedCalendarId
        if (uri != null && calendarId != null) {
            scope.launch {
                busy = true
                withContext(Dispatchers.IO) {
                    try {
                        val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                            stream.bufferedReader(Charsets.UTF_8).readText()
                        } ?: throw IllegalStateException(s.get("ics.file.open.error"))

                        val parsed = IcsParser.parse(text)
                        var inserted = 0
                        var duplicates = 0
                        var failed = 0
                        for (event in parsed.events) {
                            when {
                                CalendarData.hasSameEvent(context.contentResolver, calendarId, event) -> duplicates++
                                CalendarData.insertEvent(context.contentResolver, calendarId, event) >= 0 -> inserted++
                                else -> failed++
                            }
                        }
                        val skipped = parsed.skipped + duplicates + failed
                        addStatus(
                            s.format(
                                "ics.import.result",
                                mapOf(
                                    "total" to parsed.events.size,
                                    "inserted" to inserted,
                                    "skipped" to skipped,
                                    "parseErrors" to parsed.skipped,
                                    "duplicates" to duplicates,
                                    "failed" to failed,
                                )
                            )
                        )
                    } catch (e: Exception) {
                        addStatus(s.format("ics.import.error", "message" to (e.message ?: s.get("error.generic"))))
                    }
                }
                busy = false
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/calendar")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                withContext(Dispatchers.IO) {
                    try {
                        val zone = ZoneId.systemDefault()
                        // С начала текущего года до конца следующего (исключительно)
                        val from = LocalDate.now().withDayOfYear(1)
                            .atStartOfDay(zone).toInstant().toEpochMilli()
                        val to = LocalDate.now().plusYears(2)
                            .withMonth(12).withDayOfMonth(31).plusDays(1)
                            .atStartOfDay(zone).toInstant().toEpochMilli()

                        val events = CalendarData.getEvents(context.contentResolver, from, to)
                        if (events.isEmpty()) {
                            addStatus(s.get("ics.export.empty"))
                            return@withContext
                        }
                        val ics = IcsWriter.build(events)
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.bufferedWriter(Charsets.UTF_8).use { it.write(ics) }
                        } ?: throw IllegalStateException(s.get("ics.file.create.error"))
                        addStatus(s.format("ics.export.result", "count" to events.size))
                    } catch (e: Exception) {
                        addStatus(s.format("ics.export.error", "message" to (e.message ?: s.get("error.generic"))))
                    }
                }
                busy = false
            }
        }
    }

    ScreenScaffold(title = s.get("menu.ics"), onBack = onBack) {
        if (!env.permissionsGranted) {
            PermissionCard(onRequest = env.onRequestPermissions)
        } else {
            CalendarSelector(
                calendars = env.calendars,
                selectedId = env.selectedCalendarId,
                onSelect = env.onSelectCalendar,
                enabled = !busy,
            )
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    importLauncher.launch(
                        arrayOf("text/calendar", "application/ics", "application/octet-stream")
                    )
                },
                enabled = !busy && env.selectedCalendarId != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(s.get("ics.import.title"))
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = { exportLauncher.launch("calendar-${LocalDate.now()}.ics") },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(s.get("ics.export.title"))
            }

            Text(
                text = s.get("ics.export.hint"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        BusyLine(busy)

        Spacer(Modifier.height(16.dp))
        StatusLog(messages = status, modifier = Modifier.weight(1f))
    }
}
