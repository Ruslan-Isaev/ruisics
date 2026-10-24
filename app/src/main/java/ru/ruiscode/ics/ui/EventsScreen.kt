package ru.ruiscode.ics.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import ru.ruiscode.ics.calendar.EventDeleter
import ru.ruiscode.ics.calendar.EventMatch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException

private enum class DeleteCriteria { KEYWORD, RANGE, ALL }

/**
 * Управление событиями: пакетное удаление по ключевому слову и диапазону
 * дат, полная очистка календаря. Удаление идёт пакетами с прогрессом,
 * частичные ошибки можно повторить. Опасные действия требуют явного
 * подтверждения.
 */
@Composable
fun EventsScreen(env: CalendarEnv, onBack: () -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var criteria by remember { mutableStateOf(DeleteCriteria.KEYWORD) }
    var keyword by remember { mutableStateOf("") }
    var fromText by remember { mutableStateOf("") }
    var toText by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf<List<EventMatch>?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var failedIds by remember { mutableStateOf<List<Long>?>(null) }
    var clearDialog by remember { mutableStateOf(false) }
    var clearCount by remember { mutableStateOf<Int?>(null) }
    val status = remember { mutableStateListOf<String>() }

    fun addStatus(msg: String) {
        status.add(0, msg)
    }

    fun deleteIds(ids: List<Long>) {
        scope.launch {
            busy = true
            progress = null
            failedIds = null
            val result = withContext(Dispatchers.IO) {
                val deleter = EventDeleter()
                deleter.delete(
                    ids = ids,
                    doDelete = { batch -> CalendarData.deleteEvents(context.contentResolver, batch) },
                    onProgress = { done, total -> progress = done to total },
                )
            }
            busy = false
            progress = null
            addStatus(s.plural("events.delete.result", result.deleted))
            if (result.failedIds.isNotEmpty()) {
                failedIds = result.failedIds
                addStatus(s.plural("events.delete.partial", result.failedIds.size))
                addStatus(s.get("events.delete.retry.hint"))
            }
        }
    }

    fun parseDate(text: String): LocalDate? = try {
        LocalDate.parse(text.trim())
    } catch (e: DateTimeParseException) {
        null
    }

    fun findInRange() {
        val calendarId = env.selectedCalendarId ?: return
        val from = parseDate(fromText)
        val to = parseDate(toText)
        when {
            from == null || to == null -> addStatus(s.get("events.date.invalid"))
            from.isAfter(to) -> addStatus(s.get("events.range.invalid"))
            else -> scope.launch {
                busy = true
                val zone = ZoneId.systemDefault()
                val fromMillis = from.atStartOfDay(zone).toInstant().toEpochMilli()
                val toMillis = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val found = withContext(Dispatchers.IO) {
                    CalendarData.findEventsInRange(context.contentResolver, calendarId, fromMillis, toMillis)
                }
                busy = false
                matches = found
                if (found.isEmpty()) addStatus(s.get("events.range.none"))
            }
        }
    }

    fun findByKeyword() {
        val calendarId = env.selectedCalendarId ?: return
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        scope.launch {
            busy = true
            val found = withContext(Dispatchers.IO) {
                CalendarData.findEventsByKeyword(context.contentResolver, calendarId, trimmed)
            }
            busy = false
            matches = found
            if (found.isEmpty()) addStatus(s.format("events.find.none", "keyword" to trimmed))
        }
    }

    ScreenScaffold(title = s.get("menu.events"), onBack = onBack) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            if (!env.permissionsGranted) {
                PermissionCard(onRequest = env.onRequestPermissions)
                return@ScreenScaffold
            }

            CalendarSelector(
                calendars = env.calendars,
                selectedId = env.selectedCalendarId,
                onSelect = env.onSelectCalendar,
                enabled = !busy,
            )
            Spacer(Modifier.height(16.dp))

            Text(
                s.get("events.criteria.label"),
                style = MaterialTheme.typography.titleSmall,
            )
            Row {
                FilterChip(
                    selected = criteria == DeleteCriteria.KEYWORD,
                    onClick = { criteria = DeleteCriteria.KEYWORD; matches = null },
                    label = { Text(s.get("events.criteria.keyword")) },
                )
                Spacer(Modifier.padding(horizontal = 4.dp))
                FilterChip(
                    selected = criteria == DeleteCriteria.RANGE,
                    onClick = { criteria = DeleteCriteria.RANGE; matches = null },
                    label = { Text(s.get("events.criteria.range")) },
                )
            }
            Spacer(Modifier.height(8.dp))

            when (criteria) {
                DeleteCriteria.KEYWORD -> {
                    OutlinedTextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        label = { Text(s.get("events.keyword.label")) },
                        placeholder = { Text(s.get("events.keyword.placeholder")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        s.get("events.keyword.hint"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { findByKeyword() },
                        enabled = !busy && env.selectedCalendarId != null && keyword.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(s.get("action.find"))
                    }
                }
                DeleteCriteria.RANGE -> {
                    OutlinedTextField(
                        value = fromText,
                        onValueChange = { fromText = it },
                        label = { Text(s.get("events.range.from")) },
                        placeholder = { Text(LocalDate.now().toString()) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = toText,
                        onValueChange = { toText = it },
                        label = { Text(s.get("events.range.to")) },
                        placeholder = { Text(LocalDate.now().toString()) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        s.get("events.range.hint"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { findInRange() },
                        enabled = !busy && env.selectedCalendarId != null && fromText.isNotBlank() && toText.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(s.get("action.find"))
                    }
                }
                DeleteCriteria.ALL -> Unit
            }

            // Результат поиска: предварительный список и количество.
            val found = matches
            if (found != null && found.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            s.plural("plural.events", found.size),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(4.dp))
                        found.take(5).forEach { m ->
                            Text(
                                "• ${m.title}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (found.size > 5) {
                            Text(
                                s.format("events.preview.showing", "shown" to 5, "total" to found.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            s.get("events.series.note"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { confirmDelete = true },
                            enabled = !busy,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(s.get("action.delete"))
                        }
                    }
                }
            }

            // Опасная зона: полная очистка календаря.
            Spacer(Modifier.height(24.dp))
            SubsectionTitle(s.get("events.clear.title"))
            Text(
                s.get("events.clear.description"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val calendarId = env.selectedCalendarId ?: return@launch
                        clearCount = withContext(Dispatchers.IO) {
                            CalendarData.countEvents(context.contentResolver, calendarId)
                        }
                        clearDialog = true
                    }
                },
                enabled = !busy && env.selectedCalendarId != null,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(s.get("events.clear.title"))
            }

            progress?.let { (done, total) ->
                Spacer(Modifier.height(8.dp))
                Text(
                    s.format("events.deleting.progress", "done" to done, "total" to total),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            BusyLine(busy)

            failedIds?.let { failed ->
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { deleteIds(failed) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(s.get("action.retry"))
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        // Подтверждение удаления найденных событий.
        if (confirmDelete) {
            val found = matches ?: emptyList()
            val calendarName = env.selectedCalendar?.name ?: ""
            val trimmedKeyword = keyword.trim()
            ConfirmDialog(
                title = s.get("events.confirm.title"),
                text = {
                    Column {
                        Text(s.format("events.confirm.text", "count" to found.size, "calendar" to calendarName))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            when (criteria) {
                                DeleteCriteria.KEYWORD -> s.format("events.confirm.criteria.keyword", "keyword" to trimmedKeyword)
                                DeleteCriteria.RANGE -> s.format("events.confirm.criteria.range", "from" to fromText, "to" to toText)
                                DeleteCriteria.ALL -> s.get("events.confirm.criteria.all")
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                        found.take(5).forEach { m ->
                            Text("• ${m.title}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (found.size > 5) {
                            Text(
                                s.format("events.confirm.more", "n" to (found.size - 5)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            s.get("events.confirm.irreversible"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                confirmLabel = s.get("action.delete"),
                onConfirm = {
                    confirmDelete = false
                    matches = null
                    deleteIds(found.map { it.id })
                },
                onDismiss = { confirmDelete = false },
            )
        }

        // Подтверждение полной очистки календаря.
        if (clearDialog) {
            val calendarName = env.selectedCalendar?.name ?: ""
            val count = clearCount ?: 0
            ConfirmDialog(
                title = s.get("events.clear.title"),
                text = {
                    Column {
                        Text(s.format("events.clear.warning", "count" to count, "calendar" to calendarName))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            s.get("events.series.note"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmLabel = s.get("events.clear.confirm"),
                onConfirm = {
                    clearDialog = false
                    val calendarId = env.selectedCalendarId ?: return@ConfirmDialog
                    scope.launch {
                        busy = true
                        val ids = withContext(Dispatchers.IO) {
                            CalendarData.getAllEventIds(context.contentResolver, calendarId)
                        }
                        val result = withContext(Dispatchers.IO) {
                            EventDeleter().delete(
                                ids = ids,
                                doDelete = { batch -> CalendarData.deleteEvents(context.contentResolver, batch) },
                                onProgress = { done, total -> progress = done to total },
                            )
                        }
                        busy = false
                        progress = null
                        addStatus(s.plural("events.clear.done", result.deleted))
                        if (result.failedIds.isNotEmpty()) {
                            failedIds = result.failedIds
                            addStatus(s.plural("events.delete.partial", result.failedIds.size))
                        }
                    }
                },
                onDismiss = { clearDialog = false },
            )
        }
    }
}
