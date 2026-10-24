package ru.ruiscode.ics.ics

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

data class ParsedEvent(
    val uid: String,
    val summary: String,
    val description: String,
    val location: String,
    val allDay: Boolean,
    val startMillis: Long,
    val endMillis: Long,
    val timeZoneId: String,
)

data class ParseResult(
    val events: List<ParsedEvent>,
    val skipped: Int,
)

/**
 * Простой парсер iCalendar (RFC 5545): только VEVENT и базовые поля.
 * Повторяющиеся события (RRULE) при импорте не раскрываются.
 */
object IcsParser {

    // withZone нужен: ZonedDateTime.parse требует зону, а литерал 'Z' её не задаёт
    private val UTC_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
        .withZone(ZoneOffset.UTC)
    private val LOCAL_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE
    private val ALL_DAY_VALUE = Regex("\\d{8}")
    private val ESCAPED_CHAR = Regex("\\\\([\\\\,;nN])")

    private data class Prop(val name: String, val params: Map<String, String>, val value: String)

    private data class DateTimeValue(val millis: Long, val allDay: Boolean, val timeZoneId: String) {
        fun plus(duration: Duration): DateTimeValue =
            DateTimeValue(millis + duration.toMillis(), allDay, timeZoneId)

        fun plusDefault(day: Boolean): DateTimeValue =
            DateTimeValue(millis + if (day) 24L * 3600_000 else 3600_000, allDay, timeZoneId)
    }

    fun parse(text: String): ParseResult {
        val normalized = text
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .removePrefix("\uFEFF") // BOM

        val events = mutableListOf<ParsedEvent>()
        var skipped = 0
        var inEvent = false
        var props = mutableListOf<Prop>()

        for (rawLine in unfold(normalized).lineSequence()) {
            // Обрезаем только ведущие пробелы: конечные могут быть частью значения.
            val line = rawLine.trimStart()
            if (line.isEmpty()) continue
            when {
                line.startsWith("BEGIN:VEVENT", ignoreCase = true) -> {
                    inEvent = true
                    props = mutableListOf()
                }
                line.startsWith("END:VEVENT", ignoreCase = true) -> {
                    inEvent = false
                    val event = toEvent(props)
                    if (event != null) events.add(event) else skipped++
                }
                inEvent -> parseProp(line)?.let { props.add(it) }
            }
        }
        return ParseResult(events, skipped)
    }

    /** Сворачивает строки-продолжения (начинающиеся с пробела или таба). */
    private fun unfold(text: String): String {
        val sb = StringBuilder(text.length)
        var first = true
        for (line in text.lineSequence()) {
            val isContinuation = line.isNotEmpty() && (line[0] == ' ' || line[0] == '\t')
            if (isContinuation && !first) {
                sb.append(line.substring(1))
            } else {
                if (!first) sb.append('\n')
                sb.append(line)
            }
            first = false
        }
        return sb.toString()
    }

    private fun parseProp(line: String): Prop? {
        val colon = line.indexOf(':')
        if (colon < 0) return null
        val left = line.substring(0, colon)
        val value = line.substring(colon + 1)
        val parts = left.split(';')
        val params = mutableMapOf<String, String>()
        for (p in parts.drop(1)) {
            val eq = p.indexOf('=')
            if (eq > 0) {
                params[p.substring(0, eq).uppercase()] = p.substring(eq + 1).trim('"')
            }
        }
        return Prop(parts[0].uppercase(), params, value)
    }

    private fun toEvent(props: List<Prop>): ParsedEvent? {
        val byName = props.associateBy({ it.name }, { it })
        val dtStart = byName["DTSTART"] ?: return null

        val start = parseDateTime(dtStart) ?: return null
        val allDay = start.allDay

        var end: DateTimeValue = when {
            byName["DTEND"] != null -> {
                val parsedEnd = parseDateTime(byName["DTEND"]!!) ?: return null
                if (parsedEnd.allDay != allDay) start.plusDefault(allDay) else parsedEnd
            }
            byName["DURATION"] != null -> {
                val duration = try {
                    Duration.parse(byName["DURATION"]!!.value)
                } catch (e: Exception) {
                    return null
                }
                start.plus(duration)
            }
            else -> start.plusDefault(allDay)
        }

        if (end.millis <= start.millis) end = start.plusDefault(allDay)

        return ParsedEvent(
            uid = byName["UID"]?.let { unescape(it.value) }
                ?: "import-${System.currentTimeMillis()}-${kotlin.random.Random.nextInt()}",
            summary = byName["SUMMARY"]?.let { unescape(it.value) } ?: "",
            description = byName["DESCRIPTION"]?.let { unescape(it.value) } ?: "",
            location = byName["LOCATION"]?.let { unescape(it.value) } ?: "",
            allDay = allDay,
            startMillis = start.millis,
            endMillis = end.millis,
            timeZoneId = start.timeZoneId,
        )
    }

    private fun parseDateTime(prop: Prop): DateTimeValue? {
        val value = prop.value
        val tzid = prop.params["TZID"]
        return try {
            when {
                prop.params["VALUE"] == "DATE" || ALL_DAY_VALUE.matches(value) -> {
                    // Событие на весь день: храним как полночь UTC
                    val date = LocalDate.parse(value, DATE_FORMAT)
                    DateTimeValue(
                        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                        true,
                        "UTC",
                    )
                }
                value.endsWith("Z") -> {
                    val instant = ZonedDateTime.parse(value, UTC_FORMAT).toInstant()
                    DateTimeValue(instant.toEpochMilli(), false, tzid ?: "UTC")
                }
                tzid != null -> {
                    val zone = zoneOf(tzid)
                    val zdt = LocalDateTime.parse(value, LOCAL_FORMAT).atZone(zone)
                    DateTimeValue(zdt.toInstant().toEpochMilli(), false, zone.id)
                }
                else -> {
                    val zone = ZoneId.systemDefault()
                    val zdt = LocalDateTime.parse(value, LOCAL_FORMAT).atZone(zone)
                    DateTimeValue(zdt.toInstant().toEpochMilli(), false, zone.id)
                }
            }
        } catch (e: DateTimeParseException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun zoneOf(tzid: String): ZoneId = try {
        ZoneId.of(tzid)
    } catch (e: Exception) {
        ZoneId.systemDefault()
    }

    private fun unescape(s: String): String = s.replace(ESCAPED_CHAR) { m ->
        when (m.groupValues[1]) {
            "n", "N" -> "\n"
            else -> m.groupValues[1]
        }
    }
}
