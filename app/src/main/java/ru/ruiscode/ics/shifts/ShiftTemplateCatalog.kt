package ru.ruiscode.ics.shifts

import ru.ruiscode.ics.core.JsonParser
import ru.ruiscode.ics.core.JsonValue
import ru.ruiscode.ics.i18n.AssetLoader
import java.time.LocalTime

/**
 * Каталог шаблонов смен: встроенные шаблоны загружаются из
 * `assets/shift_templates` по манифесту, пользовательские — из настроек.
 * Готовые графики не зашиты в Kotlin-код.
 */
class ShiftTemplateCatalog private constructor(
    val templates: List<ShiftTemplate>,
    val problems: List<String>,
) {
    fun template(id: String): ShiftTemplate? = templates.firstOrNull { it.id == id }

    companion object {
        const val MANIFEST_PATH = "shift_templates/manifest.json"
        const val TEMPLATES_DIR = "shift_templates/templates"
        const val CUSTOM_TEMPLATE_ID_PREFIX = "custom_"
        const val MAX_CYCLE_LENGTH = 60
        const val MAX_LENGTH_DAYS = 730

        fun load(load: AssetLoader, customTemplatesJson: String?): ShiftTemplateCatalog {
            val problems = mutableListOf<String>()
            val templates = mutableListOf<ShiftTemplate>()

            val manifestText = load(MANIFEST_PATH)
            if (manifestText == null) {
                problems.add("Missing asset: $MANIFEST_PATH")
            } else {
                try {
                    val manifest = JsonParser.parse(manifestText)
                    val entries = manifest.arr("templates")?.items
                    if (entries == null) {
                        problems.add("$MANIFEST_PATH: missing 'templates' array")
                    } else {
                        val seenIds = mutableSetOf<String>()
                        for ((index, entry) in entries.withIndex()) {
                            val fileName = entry.str("fileName")
                            val where = "$MANIFEST_PATH: templates[$index]"
                            if (fileName == null) {
                                problems.add("$where: missing 'fileName'")
                                continue
                            }
                            val text = load("$TEMPLATES_DIR/$fileName")
                            if (text == null) {
                                problems.add("$where: file '$TEMPLATES_DIR/$fileName' not found")
                                continue
                            }
                            val template = parseTemplate(text, where, problems) ?: continue
                            if (template.id in seenIds) {
                                problems.add("$where: duplicate template id '${template.id}'")
                                continue
                            }
                            seenIds.add(template.id)
                            templates.add(template)
                        }
                    }
                } catch (e: JsonParser.JsonException) {
                    problems.add("Broken JSON in $MANIFEST_PATH: ${e.message}")
                }
            }

            // Пользовательские шаблоны (отдельное хранилище — настройки).
            if (!customTemplatesJson.isNullOrBlank()) {
                try {
                    val parsed = JsonParser.parse(customTemplatesJson)
                    val items = parsed.arr("templates")?.items
                    if (items != null) {
                        for ((index, entry) in items.withIndex()) {
                            val template = parseTemplateValue(entry, "custom templates[$index]", problems)
                            if (template != null && template.id !in templates.map { it.id }) {
                                templates.add(template)
                            }
                        }
                    }
                } catch (e: JsonParser.JsonException) {
                    problems.add("Broken JSON in custom shift templates: ${e.message}")
                }
            }

            return ShiftTemplateCatalog(templates, problems)
        }

        /** Парсит JSON шаблона (встроенный файл или пользовательская запись). */
        private fun parseTemplate(text: String, where: String, problems: MutableList<String>): ShiftTemplate? {
            val json = try {
                JsonParser.parse(text)
            } catch (e: JsonParser.JsonException) {
                problems.add("Broken JSON in $where: ${e.message}")
                return null
            }
            return parseTemplateValue(json, where, problems)
        }

        private fun parseTemplateValue(json: JsonValue, where: String, problems: MutableList<String>): ShiftTemplate? {
            val obj = json.asObject()
            if (obj == null) {
                problems.add("$where: not a JSON object")
                return null
            }
            val id = obj.str("id")
            val nameKey = obj.str("nameKey")
            val descriptionKey = obj.str("descriptionKey")
            if (id == null || nameKey == null || descriptionKey == null) {
                problems.add("$where: missing 'id', 'nameKey' or 'descriptionKey'")
                return null
            }
            val cycle = obj.arr("cycle")?.asStringList()
            if (cycle == null || cycle.isEmpty()) {
                problems.add("$where: missing or empty 'cycle'")
                return null
            }
            if (cycle.size > MAX_CYCLE_LENGTH) {
                problems.add("$where: cycle longer than $MAX_CYCLE_LENGTH")
                return null
            }
            val dayTypesObj = obj.obj("dayTypes")
            if (dayTypesObj == null) {
                problems.add("$where: missing 'dayTypes'")
                return null
            }
            val dayTypes = mutableMapOf<String, DayTypeSpec>()
            for ((typeId, typeValue) in dayTypesObj.fields) {
                val typeObj = typeValue.asObject() ?: continue
                val typeNameKey = typeObj.str("nameKey") ?: continue
                val start = typeObj.str("startTime")?.let { parseTime(it) }
                val end = typeObj.str("endTime")?.let { parseTime(it) }
                if ((start == null) != (end == null)) {
                    problems.add("$where: day type '$typeId' must have both startTime and endTime or neither")
                    continue
                }
                dayTypes[typeId] = DayTypeSpec(typeId, typeNameKey, start, end)
            }
            val translations = mutableMapOf<String, Map<String, JsonValue>>()
            obj.obj("translations")?.fields?.forEach { (locale, value) ->
                value.obj("strings")?.fields?.let { translations[locale] = it }
            }
            val template = ShiftTemplate(
                id = id,
                nameKey = nameKey,
                descriptionKey = descriptionKey,
                cycle = cycle,
                dayTypes = dayTypes,
                translations = translations,
                isCustom = obj.bool("isCustom") ?: false,
            )
            if (!template.isValid()) {
                problems.add("$where: cycle references unknown day types")
                return null
            }
            return template
        }

        private fun parseTime(raw: String): LocalTime? = try {
            val parts = raw.split(':')
            require(parts.size == 2)
            LocalTime.of(parts[0].toInt(), parts[1].toInt())
        } catch (e: Exception) {
            null
        }
    }
}
