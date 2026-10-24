package ru.ruiscode.ics.holidays

import ru.ruiscode.ics.core.JsonValue
import ru.ruiscode.ics.i18n.StringResolver

/**
 * Метаданные набора праздников (из `pack.json`).
 * Идентификатором набора служит [id] — отображаемое имя хранится
 * отдельно через [nameKey] и резолвится системой локализации.
 */
data class HolidayPackMeta(
    val id: String,
    val directory: String,
    val nameKey: String,
    val descriptionKey: String,
    val category: String,
    val schemaVersion: Int,
    val version: String,
    val dataFile: String,
    val translationsDirectory: String,
    val availableLocales: List<String>,
    val sourceName: String?,
    val sourceUrl: String?,
    val licenseSpdxId: String?,
    val licenseName: String?,
    val licenseUrl: String?,
)

/**
 * Правило вычисления даты праздника.
 *
 * Поддерживаемые типы (архитектура допускает добавление новых вычислителей
 * без изменения формата существующих наборов):
 * - `fixed` — фиксированная дата [month]/[day]; при заданном [year] — разовый праздник;
 * - `nth_weekday` — N-й день недели [weekday] (1=Пн..7=Вс) месяца [month]; n=-1 — последний;
 * - `offset` — смещение на [offsetDays] от праздника [relativeTo] того же набора;
 * - `calculated` — вычисляемая дата по алгоритму [calendar] (например, gregorian_easter);
 * - `skip` — пропустить праздник (используется в исключениях конкретных лет).
 */
data class DateRule(
    val type: String,
    val year: Int? = null,
    val month: Int? = null,
    val day: Int? = null,
    val weekday: Int? = null,
    val n: Int? = null,
    val relativeTo: String? = null,
    val offsetDays: Int? = null,
    val calendar: String? = null,
)

data class Holiday(
    val id: String,
    val nameKey: String,
    val descriptionKey: String,
    val dateRule: DateRule,
    val durationDays: Int,
    val tags: List<String>,
    val enabledByDefault: Boolean,
    /** Переопределение правила для конкретных лет (например, перенос даты). */
    val exceptions: Map<Int, DateRule>,
)

/**
 * Набор праздников: метаданные, данные и переводы отделены друг от друга.
 * Переводы резолвятся единым механизмом локализации (см. [resolver]).
 */
class HolidayPack(
    val meta: HolidayPackMeta,
    val holidays: List<Holiday>,
    private val translations: Map<String, Map<String, JsonValue>>,
) {
    fun holiday(id: String): Holiday? = holidays.firstOrNull { it.id == id }

    /** Резолвер: переводы набора поверх глобальных; отсутствие перевода не падает. */
    fun resolver(global: StringResolver): StringResolver {
        val layers = buildList {
            translations[global.code]?.let { add(it) }
            if (global.code != "en") translations["en"]?.let { add(it) }
        }
        return global.withLayers(layers)
    }

    fun name(resolver: StringResolver): String = resolver.get(meta.nameKey)

    fun description(resolver: StringResolver): String = resolver.get(meta.descriptionKey)

    fun holidayName(resolver: StringResolver, holidayId: String): String =
        holiday(holidayId)?.let { resolver.get(it.nameKey) } ?: resolver.get(holidayId)
}

/** Одно вхождение праздника в конкретном году. */
data class HolidayOccurrence(
    val holiday: Holiday,
    val year: Int,
    val startDate: java.time.LocalDate,
    val durationDays: Int,
)
