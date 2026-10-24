package ru.ruiscode.ics.shifts

import java.time.LocalDate
import java.time.ZoneId

/**
 * Генератор расписания смен: разворачивает цикл шаблона на [lengthDays]
 * дней, начиная со [startDate]. Смены, переходящие через полночь,
 * заканчиваются на следующий календарный день (в часовом поясе [zone]).
 */
object ShiftGenerator {

    /** Событие смены в календарной системе. */
    fun generate(
        template: ShiftTemplate,
        startDate: LocalDate,
        lengthDays: Int,
        title: String,
        zone: ZoneId,
    ): List<ShiftOccurrence> {
        require(template.isValid()) { "invalid template" }
        require(lengthDays > 0) { "lengthDays must be positive" }

        val result = mutableListOf<ShiftOccurrence>()
        for (offset in 0 until lengthDays) {
            val date = startDate.plusDays(offset.toLong())
            val dayTypeId = template.cycle[offset % template.cycle.size]
            val dayType = template.dayType(dayTypeId) ?: continue
            val start = dayType.startTime?.let { date.atTime(it).atZone(zone) }
            val end = dayType.endTime?.let { time ->
                val endDate = if (time.isAfter(dayType.startTime!!)) date else date.plusDays(1)
                endDate.atTime(time).atZone(zone)
            }
            result.add(ShiftOccurrence(date, dayTypeId, start, end, title))
        }
        return result
    }
}
