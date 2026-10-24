package ru.ruiscode.ics.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import ru.ruiscode.ics.AppModules
import ru.ruiscode.ics.calendar.CalendarImporter
import ru.ruiscode.ics.holidays.HolidayDateCalculator
import ru.ruiscode.ics.holidays.HolidayOccurrence
import ru.ruiscode.ics.holidays.HolidayPlanner
import java.time.LocalDate

/**
 * Детали набора праздников: просмотр за выбранный год, выбор праздников
 * и импорт в календарь с подтверждением. Повторный импорт того же набора
 * не создаёт дубликатов (UID = набор + праздник + год).
 */
@Composable
fun HolidayDetailScreen(packId: String, env: CalendarEnv, onBack: () -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pack = AppModules.holidayCatalog.pack(packId)

    var yearText by remember { mutableStateOf(LocalDate.now().year.toString()) }
    var busy by remember { mutableStateOf(false) }
    var confirmImport by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<CalendarImporter.ImportResult?>(null) }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var localCalendarId by remember { mutableStateOf(env.selectedCalendarId) }

    val year = yearText.trim().toIntOrNull()?.takeIf { it in 1900..2199 }

    ScreenScaffold(
        title = pack?.name(pack?.resolver(s) ?: s) ?: s.get("holidays.title"),
        onBack = onBack,
    ) {
        if (pack == null) {
            Text(s.get("holidays.pack.empty"))
            return@ScreenScaffold
        }
        val resolver = pack.resolver(s)

        val resolution = if (year != null) remember(pack, year) {
            HolidayDateCalculator.resolve(pack, year)
        } else null
        val occurrences: List<HolidayOccurrence> = if (year != null) remember(pack, year) {
            HolidayPlanner.occurrencesForYear(pack, year)
        } else emptyList()
        val selected = remember(pack, year) {
            mutableStateListOf<String>().apply {
                addAll(occurrences.map { it.holiday.id }.filter { id ->
                    pack.holiday(id)?.enabledByDefault == true
                })
            }
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                Text(
                    pack.description(resolver),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    s.get("holidays.pack.category") + ": " + categoryName(s, pack.meta.category),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    s.format("holidays.pack.version", "version" to pack.meta.version),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!pack.meta.sourceName.isNullOrBlank()) {
                    Text(
                        s.get("holidays.pack.source") + ": " + pack.meta.sourceName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val license = pack.meta.licenseName
                    ?: pack.meta.licenseSpdxId
                    ?: s.get("about.unverified")
                Text(
                    s.get("holidays.pack.license") + ": " + license,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = yearText,
                    onValueChange = { yearText = it.filter { ch -> ch.isDigit() }.take(4) },
                    label = { Text(s.get("holidays.year.label")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ErrorText(if (year == null) s.get("events.date.invalid") else null)

                Spacer(Modifier.height(16.dp))
                Text(
                    s.format("holidays.preview.title", "year" to (year ?: yearText)),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (resolution != null) {
                    resolution.unresolved.forEach { id ->
                        ErrorText(s.format("holidays.date.error", "holiday" to pack.holidayName(resolver, id), "year" to year))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    s.plural("holidays.preview.count", selected.size),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (occurrences.isEmpty()) {
                    Text(
                        s.get("holidays.preview.empty"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            items(occurrences, key = { it.holiday.id }) { occurrence ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Checkbox(
                        checked = occurrence.holiday.id in selected,
                        onCheckedChange = { checked ->
                            if (checked) selected.add(occurrence.holiday.id)
                            else selected.remove(occurrence.holiday.id)
                        },
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            resolver.get(occurrence.holiday.nameKey),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            resolver.formatDate(occurrence.startDate) +
                                if (occurrence.durationDays > 1) " (" + s.plural("plural.days", occurrence.durationDays) + ")" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            resolver.get(occurrence.holiday.descriptionKey),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
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
        BusyLine(busy)

        if (!env.permissionsGranted) {
            PermissionCard(onRequest = env.onRequestPermissions)
        } else {
            // Выбор календаря назначения для этого импорта.
            CalendarSelector(
                calendars = env.calendars,
                selectedId = localCalendarId,
                onSelect = { localCalendarId = it },
                enabled = !busy,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { confirmImport = true },
                enabled = !busy && localCalendarId != null && selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(s.get("action.import.calendar"))
            }
            if (selected.isEmpty()) {
                Text(
                    s.get("holidays.import.none"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        // Подтверждение импорта.
        if (confirmImport && year != null) {
            val calendarName = env.calendars.firstOrNull { it.id == localCalendarId }?.name ?: ""
            ConfirmDialog(
                title = s.get("holidays.import.select"),
                text = {
                    Text(
                        s.plural(
                            "holidays.import.confirm",
                            selected.size,
                            mapOf(
                                "pack" to pack.name(resolver),
                                "year" to year,
                                "calendar" to calendarName,
                            )
                        )
                    )
                },
                confirmLabel = s.get("action.import"),
                confirmDanger = false,
                onConfirm = {
                    confirmImport = false
                    val calendarId = localCalendarId ?: return@ConfirmDialog
                    scope.launch {
                        busy = true
                        progress = null
                        val result = withContext(Dispatchers.IO) {
                            CalendarImporter.import(
                                resolver = context.contentResolver,
                                calendarId = calendarId,
                                specs = HolidayPlanner.toSpecs(pack, year, selected.toSet(), s),
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
