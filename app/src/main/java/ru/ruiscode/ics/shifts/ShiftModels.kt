package ru.ruiscode.ics.shifts

import ru.ruiscode.ics.core.JsonValue
import ru.ruiscode.ics.i18n.StringResolver
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Тип дня цикла смен. [startTime]/[endTime] == null означает день без события
 * (выходной, отсыпной). Если endTime <= startTime, смена переходит через
 * полночь и заканчивается на следующий день.
 */
data class DayTypeSpec(
    val id: String,
    val nameKey: String,
    val startTime: LocalTime?,
    val endTime: LocalTime?,
) {
    val hasEvent: Boolean get() = startTime != null && endTime != null

    /** true, если смена заканчивается на следующий день. */
    val crossesMidnight: Boolean get() = hasEvent && !endTime!!.isAfter(startTime!!)
}

/**
 * Шаблон графика смен. Готовые шаблоны хранятся в JSON-файлах и
 * обнаруживаются через манифест; пользовательские — в настройках.
 */
data class ShiftTemplate(
    val id: String,
    val nameKey: String,
    val descriptionKey: String,
    val cycle: List<String>,
    val dayTypes: Map<String, DayTypeSpec>,
    /** Переводы шаблона: locale → ключи. */
    val translations: Map<String, Map<String, JsonValue>>,
    /** true для шаблонов, созданных пользователем и сохранённых в настройках. */
    val isCustom: Boolean = false,
) {
    fun dayType(id: String): DayTypeSpec? = dayTypes[id]

    /** Проверка целостности: цикл не пуст и ссылается только на известные типы дней. */
    fun isValid(): Boolean =
        cycle.isNotEmpty() && cycle.all { it in dayTypes }

    fun resolver(global: StringResolver): StringResolver {
        val layers = buildList {
            translations[global.code]?.let { add(it) }
            if (global.code != "en") translations["en"]?.let { add(it) }
        }
        return global.withLayers(layers)
    }

    fun name(resolver: StringResolver): String = resolver.get(nameKey)

    fun description(resolver: StringResolver): String = resolver.get(descriptionKey)

    fun dayTypeName(resolver: StringResolver, typeId: String): String =
        dayType(typeId)?.let { resolver.get(it.nameKey) } ?: resolver.get(typeId)
}

/** Одно событие смены. */
data class ShiftOccurrence(
    val date: LocalDate,
    val dayTypeId: String,
    val start: ZonedDateTime?,
    val end: ZonedDateTime?,
    val title: String,
) {
    val isWorking: Boolean get() = start != null && end != null
}
