package ru.ruiscode.ics.holidays

import ru.ruiscode.ics.core.JsonParser
import ru.ruiscode.ics.core.JsonValue
import ru.ruiscode.ics.i18n.AssetLoader

/**
 * Каталог наборов праздников: обнаружение по манифесту `holiday_packs/manifest.json`,
 * загрузка и валидация данных и переводов.
 *
 * Новый набор добавляется файлами данных (директория + запись в манифесте)
 * без изменения Kotlin-кода. Повреждённые наборы пропускаются и попадают
 * в [problems]; наборы с дублирующимися id отклоняются.
 */
class HolidayCatalog private constructor(
    val packs: List<HolidayPack>,
    val problems: List<String>,
) {
    fun pack(id: String): HolidayPack? = packs.firstOrNull { it.meta.id == id }

    companion object {
        const val MANIFEST_PATH = "holiday_packs/manifest.json"
        const val SCHEMA_VERSION = 1
        const val PACKS_DIR = "holiday_packs"

        fun load(load: AssetLoader): HolidayCatalog {
            val problems = mutableListOf<String>()
            val manifestText = load(MANIFEST_PATH)
            if (manifestText == null) {
                problems.add("Missing asset: $MANIFEST_PATH")
                return HolidayCatalog(emptyList(), problems)
            }
            val manifest = try {
                JsonParser.parse(manifestText)
            } catch (e: JsonParser.JsonException) {
                problems.add("Broken JSON in $MANIFEST_PATH: ${e.message}")
                return HolidayCatalog(emptyList(), problems)
            }
            val entries = manifest.arr("packs")?.items
            if (entries == null) {
                problems.add("$MANIFEST_PATH: missing 'packs' array")
                return HolidayCatalog(emptyList(), problems)
            }

            val packs = mutableListOf<HolidayPack>()
            val seenIds = mutableSetOf<String>()
            for ((index, entry) in entries.withIndex()) {
                val id = entry.str("id")
                val directory = entry.str("directory")
                val where = "$MANIFEST_PATH: packs[$index]"
                when {
                    id == null -> { problems.add("$where: missing 'id'"); continue }
                    directory == null -> { problems.add("$where: missing 'directory'"); continue }
                    id in seenIds -> { problems.add("$where: duplicate pack id '$id'"); continue }
                }
                seenIds.add(id)
                loadPack(load, id, directory, where, problems)?.let { packs.add(it) }
            }
            return HolidayCatalog(packs, problems)
        }

        private fun loadPack(
            load: AssetLoader,
            id: String,
            directory: String,
            where: String,
            problems: MutableList<String>,
        ): HolidayPack? {
            val base = "$PACKS_DIR/$directory"
            val packJson = parseObject(load("$base/pack.json"), "$where: $base/pack.json", problems)
                ?: return null

            val nameKey = packJson.str("nameKey")
            val descriptionKey = packJson.str("descriptionKey")
            if (nameKey == null || descriptionKey == null) {
                problems.add("$where: missing 'nameKey' or 'descriptionKey' in pack.json")
                return null
            }
            val dataFile = packJson.str("dataFile") ?: "holidays.json"
            val translationsDir = packJson.str("translationsDirectory") ?: "translations"
            val availableLocales = packJson.arr("availableLocales")?.asStringList() ?: emptyList()

            val holidaysJson = parseObject(load("$base/$dataFile"), "$where: $base/$dataFile", problems)
                ?: return null
            val holidays = parseHolidays(holidaysJson, "$where: $base/$dataFile", problems)
            if (holidays == null) return null

            val translations = mutableMapOf<String, Map<String, JsonValue>>()
            for (locale in availableLocales) {
                val text = load("$base/$translationsDir/$locale.json")
                if (text == null) {
                    problems.add("$where: missing translation file for '$locale'")
                    continue
                }
                val parsed = try {
                    JsonParser.parse(text)
                } catch (e: JsonParser.JsonException) {
                    problems.add("$where: broken translation '$locale': ${e.message}")
                    continue
                }
                val strings = parsed.obj("strings")?.fields
                if (strings == null) {
                    problems.add("$where: translation '$locale' has no 'strings' object")
                    continue
                }
                translations[locale] = strings
            }

            val source = packJson.obj("source")
            val license = packJson.obj("license")
            return HolidayPack(
                meta = HolidayPackMeta(
                    id = id,
                    directory = directory,
                    nameKey = nameKey,
                    descriptionKey = descriptionKey,
                    category = packJson.str("category") ?: "other",
                    schemaVersion = packJson.int("schemaVersion") ?: 1,
                    version = packJson.str("version") ?: "1.0.0",
                    dataFile = dataFile,
                    translationsDirectory = translationsDir,
                    availableLocales = availableLocales,
                    sourceName = source?.str("name"),
                    sourceUrl = source?.str("url"),
                    licenseSpdxId = license?.str("spdxId"),
                    licenseName = license?.str("name"),
                    licenseUrl = license?.str("url"),
                ),
                holidays = holidays,
                translations = translations,
            )
        }

        private fun parseHolidays(
            json: JsonValue.JsonObj,
            where: String,
            problems: MutableList<String>,
        ): List<Holiday>? {
            val entries = json.arr("holidays")?.items
            if (entries == null) {
                problems.add("$where: missing 'holidays' array")
                return null
            }
            val result = mutableListOf<Holiday>()
            val seenIds = mutableSetOf<String>()
            for ((index, entry) in entries.withIndex()) {
                val hWhere = "$where: holidays[$index]"
                val id = entry.str("id")
                if (id == null || id in seenIds) {
                    problems.add("$hWhere: missing or duplicate holiday id")
                    continue
                }
                seenIds.add(id)
                val nameKey = entry.str("nameKey")
                val descriptionKey = entry.str("descriptionKey")
                if (nameKey == null || descriptionKey == null) {
                    problems.add("$hWhere: missing 'nameKey' or 'descriptionKey'")
                    continue
                }
                val rule = parseDateRule(entry.obj("dateRule"), hWhere, problems) ?: continue
                val exceptions = parseExceptions(entry.obj("exceptions"), hWhere, problems)
                result.add(
                    Holiday(
                        id = id,
                        nameKey = nameKey,
                        descriptionKey = descriptionKey,
                        dateRule = rule,
                        durationDays = (entry.int("durationDays") ?: 1).coerceAtLeast(1),
                        tags = entry.arr("tags")?.asStringList() ?: emptyList(),
                        enabledByDefault = entry.bool("enabledByDefault") ?: false,
                        exceptions = exceptions,
                    )
                )
            }
            if (result.isEmpty()) {
                problems.add("$where: no valid holidays")
                return null
            }
            return result
        }

        private fun parseDateRule(
            json: JsonValue.JsonObj?,
            where: String,
            problems: MutableList<String>,
        ): DateRule? {
            if (json == null) {
                problems.add("$where: missing 'dateRule'")
                return null
            }
            val rule = DateRule(
                type = json.str("type") ?: return null.also { problems.add("$where: missing dateRule.type") },
                year = json.int("year"),
                month = json.int("month"),
                day = json.int("day"),
                weekday = json.int("weekday"),
                n = json.int("n"),
                relativeTo = json.str("relativeTo"),
                offsetDays = json.int("offsetDays"),
                calendar = json.str("calendar"),
            )
            if (!HolidayDateCalculator.isSupported(rule)) {
                problems.add("$where: unsupported or incomplete date rule '${rule.type}'")
                return null
            }
            return rule
        }

        private fun parseExceptions(
            json: JsonValue.JsonObj?,
            where: String,
            problems: MutableList<String>,
        ): Map<Int, DateRule> {
            if (json == null) return emptyMap()
            val result = mutableMapOf<Int, DateRule>()
            for ((year, value) in json.fields) {
                val y = year.toIntOrNull()
                val ruleObj = value.asObject()
                if (y == null || ruleObj == null) {
                    problems.add("$where: invalid exception for year '$year'")
                    continue
                }
                val rule = parseDateRule(ruleObj, "$where: exceptions[$year]", problems)
                if (rule != null) result[y] = rule
            }
            return result
        }

        private fun parseObject(
            text: String?,
            where: String,
            problems: MutableList<String>,
        ): JsonValue.JsonObj? {
            if (text == null) {
                problems.add("Missing asset: $where")
                return null
            }
            return try {
                JsonParser.parse(text).asObject()
                    ?: run { problems.add("$where: not a JSON object"); null }
            } catch (e: JsonParser.JsonException) {
                problems.add("Broken JSON in $where: ${e.message}")
                null
            }
        }
    }
}
