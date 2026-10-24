package ru.ruiscode.ics.ics

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Разворачивание повторяющихся событий (период генерации) и формирование
 * RRULE по RFC 5545. Используется генератором ICS: календарь получает
 * развёрнутые вхождения, ICS-файл — одно событие с RRULE.
 */
object SeriesGenerator {

    const val MAX_OCCURRENCES = 1000

    enum class Freq { DAILY, WEEKLY, MONTHLY, YEARLY }

    data class Recurrence(
        val freq: Freq,
        val interval: Int = 1,
        /** Число повторений; null — без ограничения по числу. */
        val count: Int? = null,
        /** Верхняя граница последнего начала; null — без ограничения по дате. */
        val until: ZonedDateTime? = null,
    ) {
        init {
            require(interval >= 1) { "interval must be >= 1" }
        }
    }

    /** Разворачивает серию в пары (начало, конец); длительность вхождений сохраняется. */
    fun expand(
        start: ZonedDateTime,
        end: ZonedDateTime,
        recurrence: Recurrence,
        maxOccurrences: Int = MAX_OCCURRENCES,
    ): List<Pair<ZonedDateTime, ZonedDateTime>> {
        require(end.isAfter(start)) { "end must be after start" }
        val duration = java.time.Duration.between(start, end)
        val result = mutableListOf<Pair<ZonedDateTime, ZonedDateTime>>()
        var current = start
        var occurrence = 0
        while (occurrence < maxOccurrences) {
            occurrence++
            result.add(current to current.plus(duration))
            if (recurrence.count != null && occurrence >= recurrence.count) break
            val next = advance(current, recurrence)
            if (next == current) break // дата больше не меняется (напр., 31-е в коротких месяцах)
            if (recurrence.until != null && next.isAfter(recurrence.until)) break
            current = next
        }
        return result
    }

    private fun advance(start: ZonedDateTime, recurrence: Recurrence): ZonedDateTime =
        when (recurrence.freq) {
            Freq.DAILY -> start.plusDays(recurrence.interval.toLong())
            Freq.WEEKLY -> start.plusWeeks(recurrence.interval.toLong())
            Freq.MONTHLY -> start.plusMonths(recurrence.interval.toLong())
            Freq.YEARLY -> start.plusYears(recurrence.interval.toLong())
        }

    private val UTC_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
        .withZone(java.time.ZoneOffset.UTC)

    /** Строка RRULE для записи в ICS; UNTIL приводится к UTC. */
    fun rruleText(recurrence: Recurrence): String {
        val sb = StringBuilder("FREQ=").append(recurrence.freq.name)
        if (recurrence.interval > 1) sb.append(";INTERVAL=").append(recurrence.interval)
        recurrence.count?.let { sb.append(";COUNT=").append(it) }
        recurrence.until?.let {
            sb.append(";UNTIL=").append(UTC_FORMAT.format(it.toInstant()))
        }
        return sb.toString()
    }
}
