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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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
import ru.ruiscode.ics.calendar.CalEventSpec
import ru.ruiscode.ics.calendar.CalendarImporter
import ru.ruiscode.ics.core.Uids
import ru.ruiscode.ics.ics.IcsEventSpec
import ru.ruiscode.ics.ics.IcsTime
import ru.ruiscode.ics.ics.IcsWriter
import ru.ruiscode.ics.settings.AppSettings
import ru.ruiscode.ics.shifts.CustomTemplateStore
import ru.ruiscode.ics.shifts.DayTypeSpec
import ru.ruiscode.ics.shifts.ShiftGenerator
import ru.ruiscode.ics.shifts.ShiftOccurrence
import ru.ruiscode.ics.shifts.ShiftTemplate
import ru.ruiscode.ics.shifts.ShiftTemplateCatalog
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private const val CUSTOM_TEMPLATE = "__custom__"
private const val PREVIEW_LIMIT = 200

/**
 * Планировщик смен: шаблоны из JSON (обнаруживаются автоматически),
 * произвольные циклы, смены через полночь, предпросмотр, экспорт в ICS
 * и импорт в календарь.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftsScreen(env: CalendarEnv, onBack: () -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val builtIn = AppModules.shiftCatalog.templates.filter { !it.isCustom }
    val customTemplates = AppModules.shiftCatalog.templates.filter { it.isCustom }

    var templateKey by remember { mutableStateOf(builtIn.firstOrNull()?.id ?: CUSTOM_TEMPLATE) }
    var startDateText by remember { mutableStateOf(LocalDate.now().toString()) }
    var lengthText by remember { mutableStateOf("30") }
    var titleText by remember { mutableStateOf(s.get("shifts.event_title.default")) }
    var busy by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<List<ShiftOccurrence>?>(null) }
    var confirmImport by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<CalendarImporter.ImportResult?>(null) }
    var exportStatus by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var customName by remember { mutableStateOf("") }
    var showCustomSave by remember { mutableStateOf(false) }

    // Произвольный цикл: типы дней.
    val customCycle = remember { mutableStateListOf<String>() }
    // Редактируемое время типов дней: ключ "templateKey|typeId" → (start, end).
    val timeEdits = remember { mutableStateMapOf<String, Pair<String, String>>() }

    fun isCustomMode() = templateKey == CUSTOM_TEMPLATE

    /** Имя выбранного шаблона для поля выбора. */
    fun displayName(): String {
        if (isCustomMode()) return s.get("shifts.template.custom")
        val template = AppModules.shiftCatalog.template(templateKey) ?: return s.get("shifts.template.custom")
        return template.name(template.resolver(s))
    }

    /** Эффективный шаблон из текущего состояния UI. */
    fun effectiveTemplate(): ShiftTemplate? {
        val base = if (isCustomMode()) {
            ShiftTemplate(
                id = CUSTOM_TEMPLATE,
                nameKey = "shifts.template.custom",
                descriptionKey = "shifts.custom.description",
                cycle = customCycle.toList(),
                dayTypes = mapOf(
                    "work" to DayTypeSpec("work", "shift.daytype.work.name", null, null),
                    "rest" to DayTypeSpec("rest", "shift.daytype.rest.name", null, null),
                    "recovery" to DayTypeSpec("recovery", "shift.daytype.recovery.name", null, null),
                ),
                translations = emptyMap(),
                isCustom = true,
            )
        } else {
            AppModules.shiftCatalog.template(templateKey) ?: return null
        }
        return ShiftTemplate(
            id = base.id,
            nameKey = base.nameKey,
            descriptionKey = base.descriptionKey,
            cycle = base.cycle,
            dayTypes = base.dayTypes.mapValues { (typeId, spec) ->
                val edits = timeEdits["${base.id}|$typeId"]
                val startText = edits?.first ?: spec.startTime?.toString() ?: ""
                val endText = edits?.second ?: spec.endTime?.toString() ?: ""
                if (spec.hasEvent || (base.id == CUSTOM_TEMPLATE && typeId == "work")) {
                    DayTypeSpec(typeId, spec.nameKey, parseTime(startText), parseTime(endText))
                } else {
                    spec
                }
            },
            translations = base.translations,
            isCustom = base.isCustom,
        )
    }

    fun validate(): ShiftTemplate? {
        val template = effectiveTemplate()
        val start = parseDate(startDateText)
        val length = lengthText.trim().toIntOrNull()
        validationError = when {
            titleText.isBlank() -> s.get("shifts.title.empty")
            template == null || !template.isValid() -> s.get("shifts.cycle.empty")
            start == null -> s.get("generator.validation.date")
            length == null || length !in 1..ShiftTemplateCatalog.MAX_LENGTH_DAYS ->
                s.format("shifts.length.invalid", "min" to 1, "max" to ShiftTemplateCatalog.MAX_LENGTH_DAYS)
            else -> {
                // Проверяем время типов дней: непустые поля должны разбираться.
                val badType = template.dayTypes.values.firstOrNull { spec ->
                    val edits = timeEdits["${template.id}|${spec.id}"]
                    val startOk = edits?.first.isNullOrBlank() || parseTime(edits!!.first) != null
                    val endOk = edits?.second.isNullOrBlank() || parseTime(edits!!.second) != null
                    !startOk || !endOk
                }
                if (badType != null) s.get("shifts.time.invalid") else null
            }
        }
        return if (validationError == null) template else null
    }

    fun generate() {
        val template = validate() ?: return
        val start = parseDate(startDateText)!!
        val length = lengthText.trim().toInt()!!
        val zone = ZoneId.systemDefault()
        preview = ShiftGenerator.generate(template, start, length, titleText.trim(), zone)
    }

    fun occurrencesToSpecs(occurrences: List<ShiftOccurrence>): List<CalEventSpec> =
        occurrences.filter { it.isWorking }.map { o ->
            CalEventSpec(
                uid = Uids.content("shift", o.title, o.start!!.toString(), o.end!!.toString()),
                title = o.title,
                description = "",
                location = "",
                allDay = false,
                startMillis = o.start.toInstant().toEpochMilli(),
                endMillis = o.end.toInstant().toEpochMilli(),
                timeZoneId = o.start.zone.id,
            )
        }

    fun occurrencesToIcs(occurrences: List<ShiftOccurrence>): List<IcsEventSpec> =
        occurrences.filter { it.isWorking }.map { o ->
            IcsEventSpec(
                uid = Uids.content("shift", o.title, o.start!!.toString(), o.end!!.toString()),
                title = o.title,
                time = IcsTime.Timed(o.start, o.end!!),
            )
        }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/calendar")
    ) { uri ->
        val occurrences = preview
        if (uri != null && occurrences != null) {
            scope.launch {
                busy = true
                exportStatus = withContext(Dispatchers.IO) {
                    try {
                        val ics = IcsWriter.buildSpecs(occurrencesToIcs(occurrences))
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.bufferedWriter(Charsets.UTF_8).use { it.write(ics) }
                        } ?: throw IllegalStateException(s.get("ics.file.create.error"))
                        s.format("shifts.export.result", "count" to occurrences.count { it.isWorking })
                    } catch (e: Exception) {
                        s.format("ics.export.error", "message" to (e.message ?: s.get("error.generic")))
                    }
                }
                busy = false
            }
        }
    }

    fun saveCustomTemplate() {
        val name = customName.trim()
        if (name.isEmpty() || customCycle.isEmpty()) return
        val id = ShiftTemplateCatalog.CUSTOM_TEMPLATE_ID_PREFIX + System.currentTimeMillis()
        val workStart = timeEdits["${CUSTOM_TEMPLATE}|work"]?.first ?: "08:00"
        val workEnd = timeEdits["${CUSTOM_TEMPLATE}|work"]?.second ?: "20:00"
        val template = CustomTemplateStore.buildTemplate(id, name, customCycle.toList(), workStart, workEnd)
        val existing = CustomTemplateStore.parse(AppSettings.instance.customShiftTemplates.value)
        AppSettings.instance.setCustomShiftTemplates(CustomTemplateStore.toJson(existing + template))
        AppModules.reloadShifts(context)
        customName = ""
        showCustomSave = false
        templateKey = id
    }

    fun deleteCustomTemplate(id: String) {
        val existing = CustomTemplateStore.parse(AppSettings.instance.customShiftTemplates.value)
        AppSettings.instance.setCustomShiftTemplates(CustomTemplateStore.toJson(existing.filterNot { it.str("id") == id }))
        AppModules.reloadShifts(context)
        if (templateKey == id) templateKey = builtIn.firstOrNull()?.id ?: CUSTOM_TEMPLATE
    }

    ScreenScaffold(title = s.get("menu.shifts"), onBack = onBack) {
        val occurrences = preview
        val template = effectiveTemplate()
        // Резолвер с переводами шаблона: имена типов дней (dayType.nameKey)
        // есть только в translations шаблона, а не в глобальных локалях.
        val resolver = template?.resolver(s) ?: s

        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                Text(
                    s.get("shifts.subtitle"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                if (builtIn.isEmpty() && customTemplates.isEmpty()) {
                    Text(s.get("shifts.template.empty"))
                }
            }

            item {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = displayName(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(s.get("shifts.template.label")) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        builtIn.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t.name(t.resolver(s))) },
                                onClick = { templateKey = t.id; expanded = false },
                            )
                        }
                        customTemplates.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t.name(t.resolver(s))) },
                                onClick = { templateKey = t.id; expanded = false },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(s.get("shifts.template.custom")) },
                            onClick = { templateKey = CUSTOM_TEMPLATE; expanded = false },
                        )
                    }
                }
            }

            if (template != null) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        template.description(template.resolver(s)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }

            // Редактор цикла для произвольного шаблона.
            if (isCustomMode()) {
                item {
                    SubsectionTitle(s.get("shifts.cycle.editor.title"))
                    Text(
                        customCycle.joinToString(" ") { s.get("shift.daytype.$it.name") }
                            .ifBlank { s.get("shifts.cycle.empty") },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row {
                        TextButton(onClick = {
                            if (customCycle.size < ShiftTemplateCatalog.MAX_CYCLE_LENGTH) customCycle.add("work")
                        }) { Text(s.get("shift.daytype.work.name")) }
                        TextButton(onClick = {
                            if (customCycle.size < ShiftTemplateCatalog.MAX_CYCLE_LENGTH) customCycle.add("rest")
                        }) { Text(s.get("shift.daytype.rest.name")) }
                        TextButton(onClick = {
                            if (customCycle.size < ShiftTemplateCatalog.MAX_CYCLE_LENGTH) customCycle.add("recovery")
                        }) { Text(s.get("shift.daytype.recovery.name")) }
                    }
                    Row {
                        TextButton(
                            onClick = { if (customCycle.isNotEmpty()) customCycle.removeAt(customCycle.lastIndex) }
                        ) { Text(s.get("shifts.cycle.remove_last")) }
                        TextButton(onClick = { customCycle.clear() }) { Text(s.get("shifts.cycle.clear")) }
                    }
                }
            }

            // Время типов дней.
            template?.dayTypes?.values?.forEach { dayType ->
                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        s.format("shifts.daytype.times", "type" to resolver.get(dayType.nameKey)),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    if (dayType.hasEvent) {
                        val edits = timeEdits["${template.id}|${dayType.id}"]
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = edits?.first ?: dayType.startTime?.toString() ?: "",
                                onValueChange = { v ->
                                    timeEdits["${template.id}|${dayType.id}"] = v to (edits?.second ?: dayType.endTime?.toString() ?: "")
                                },
                                label = { Text(s.get("shifts.daytype.start")) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.padding(horizontal = 4.dp))
                            OutlinedTextField(
                                value = edits?.second ?: dayType.endTime?.toString() ?: "",
                                onValueChange = { v ->
                                    timeEdits["${template.id}|${dayType.id}"] = (edits?.first ?: dayType.startTime?.toString() ?: "") to v
                                },
                                label = { Text(s.get("shifts.daytype.end")) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        Text(
                            s.get("shifts.daytype.no_event"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = startDateText,
                    onValueChange = { startDateText = it },
                    label = { Text(s.get("shifts.start_date.label")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = lengthText,
                    onValueChange = { lengthText = it.filter { ch -> ch.isDigit() }.take(3) },
                    label = { Text(s.get("shifts.length.label")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text(s.get("shifts.event_title.label")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (template != null && template.dayTypes.values.any { it.crossesMidnight }) {
                    Text(
                        s.get("shifts.overnight.note"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { generate() },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(s.get("action.generate"))
                }
                ErrorText(validationError)

                if (isCustomMode()) {
                    Spacer(Modifier.height(8.dp))
                    if (!showCustomSave) {
                        OutlinedButton(
                            onClick = { showCustomSave = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(s.get("shifts.custom.save")) }
                    } else {
                        OutlinedTextField(
                            value = customName,
                            onValueChange = { customName = it },
                            label = { Text(s.get("shifts.custom.name.label")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = { saveCustomTemplate() },
                            enabled = customName.isNotBlank() && customCycle.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(s.get("action.save")) }
                    }
                }
                customTemplates.forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            t.name(t.resolver(s)),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        IconButton(onClick = { deleteCustomTemplate(t.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = s.get("shifts.custom.delete"))
                        }
                    }
                }
            }

            // Предпросмотр.
            if (occurrences != null) {
                item {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    SubsectionTitle(s.get("shifts.preview.title"))
                    Text(
                        s.plural("shifts.preview.count", occurrences.count { it.isWorking }),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                items(occurrences.take(PREVIEW_LIMIT), key = { "${it.date}|${it.dayTypeId}" }) { o ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            s.formatDate(o.date),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            if (o.isWorking) {
                                "${s.formatTime(o.start!!.toLocalTime())}–${s.formatTime(o.end!!.toLocalTime())}"
                            } else {
                                resolver.get("shift.daytype.${o.dayTypeId}.name")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (occurrences.size > PREVIEW_LIMIT) {
                    item {
                        Text(
                            s.format("shifts.preview.more", "n" to (occurrences.size - PREVIEW_LIMIT)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    if (!env.permissionsGranted) {
                        PermissionCard(onRequest = env.onRequestPermissions)
                    } else {
                        OutlinedButton(
                            onClick = { exportLauncher.launch("shifts-${LocalDate.now()}.ics") },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(s.get("shifts.export")) }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { confirmImport = true },
                            enabled = !busy && env.selectedCalendarId != null,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(s.get("shifts.import")) }
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
                title = s.get("shifts.import"),
                text = {
                    Text(
                        s.plural(
                            "import.confirm",
                            list.count { it.isWorking },
                            mapOf("calendar" to calendarName)
                        )
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
                                specs = occurrencesToSpecs(list),
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
