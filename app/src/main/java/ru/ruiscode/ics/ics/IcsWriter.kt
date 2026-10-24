package ru.ruiscode.ics.ics

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class ExportEvent(
    val uid: String,
    val title: String,
    val description: String,
    val location: String,
    val allDay: Boolean,
    val startMillis: Long,
    val endMillis: Long,
)

/**
 * Генератор iCalendar (RFC 5545). Время событий пишется в UTC ("Z"),
 * события на весь день — как VALUE=DATE. Повторяющиеся события экспортируются
 * как отдельные вхождения. Строки экранируются и сворачиваются по 75 октетов.
 */
object IcsWriter {

    private val UTC_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
        .withZone(ZoneOffset.UTC)
    private val LOCAL_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE
        .withZone(ZoneOffset.UTC)

    /** Экспорт событий календаря (существующий формат, время — UTC). */
    fun build(events: List<ExportEvent>): String {
        val sb = StringBuilder()
        header(sb)
        for (e in events) writeExportEvent(sb, e)
        line(sb, "END:VCALENDAR")
        return sb.toString()
    }

    /**
     * Генерация ICS из спецификаций [IcsEventSpec]: поддержка TZID,
     * событий на весь день и RRULE.
     */
    fun buildSpecs(events: List<IcsEventSpec>): String {
        val sb = StringBuilder()
        header(sb)
        for (e in events) writeSpec(sb, e)
        line(sb, "END:VCALENDAR")
        return sb.toString()
    }

    private fun header(sb: StringBuilder) {
        line(sb, "BEGIN:VCALENDAR")
        line(sb, "VERSION:2.0")
        line(sb, "PRODID:-//RuisCode//RUIS ICS//RU")
        line(sb, "CALSCALE:GREGORIAN")
    }

    private fun writeExportEvent(sb: StringBuilder, e: ExportEvent) {
        line(sb, "BEGIN:VEVENT")
        line(sb, "UID:${escape(e.uid)}")
        line(sb, "DTSTAMP:${UTC_FORMAT.format(Instant.now())}")
        if (e.allDay) {
            line(sb, "DTSTART;VALUE=DATE:${DATE_FORMAT.format(Instant.ofEpochMilli(e.startMillis))}")
            line(sb, "DTEND;VALUE=DATE:${DATE_FORMAT.format(Instant.ofEpochMilli(e.endMillis))}")
        } else {
            line(sb, "DTSTART:${UTC_FORMAT.format(Instant.ofEpochMilli(e.startMillis))}")
            line(sb, "DTEND:${UTC_FORMAT.format(Instant.ofEpochMilli(e.endMillis))}")
        }
        writeCommonFields(sb, e.title, e.description, e.location)
        line(sb, "END:VEVENT")
    }

    private fun writeSpec(sb: StringBuilder, e: IcsEventSpec) {
        line(sb, "BEGIN:VEVENT")
        line(sb, "UID:${escape(e.uid)}")
        line(sb, "DTSTAMP:${UTC_FORMAT.format(Instant.now())}")
        when (val time = e.time) {
            is IcsTime.AllDay -> {
                line(sb, "DTSTART;VALUE=DATE:${DATE_FORMAT.format(time.startDate)}")
                line(sb, "DTEND;VALUE=DATE:${DATE_FORMAT.format(time.endDate)}")
            }
            is IcsTime.Timed -> {
                val zone = time.start.zone
                if (zone.id == "UTC" || (zone is ZoneOffset && zone.totalSeconds == 0)) {
                    line(sb, "DTSTART:${UTC_FORMAT.format(time.start.toInstant())}")
                    line(sb, "DTEND:${UTC_FORMAT.format(time.end.toInstant())}")
                } else {
                    line(sb, "DTSTART;TZID=${zone.id}:${LOCAL_FORMAT.format(time.start)}")
                    line(sb, "DTEND;TZID=${time.end.zone.id}:${LOCAL_FORMAT.format(time.end)}")
                }
                time.rrule?.let { line(sb, "RRULE:$it") }
            }
        }
        writeCommonFields(sb, e.title, e.description, e.location)
        line(sb, "END:VEVENT")
    }

    private fun writeCommonFields(sb: StringBuilder, title: String, description: String, location: String) {
        line(sb, "SUMMARY:${escape(title)}")
        if (description.isNotBlank()) line(sb, "DESCRIPTION:${escape(description)}")
        if (location.isNotBlank()) line(sb, "LOCATION:${escape(location)}")
    }

    /** Экранирование текста по RFC 5545. */
    fun escape(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace(";", "\\;")
        .replace(",", "\\,")

    /** Пишет строку со сворачиванием длинных строк (RFC 5545, максимум 75 октетов). */
    private fun line(sb: StringBuilder, content: String) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        if (bytes.size <= 75) {
            sb.append(content).append("\r\n")
            return
        }
        var start = 0
        var first = true
        while (start < content.length) {
            var end = start
            var byteCount = 0
            val limit = if (first) 75 else 74 // место под пробел-продолжение
            while (end < content.length) {
                val charBytes = content.substring(end, end + 1).toByteArray(Charsets.UTF_8).size
                if (byteCount + charBytes > limit) break
                byteCount += charBytes
                end++
            }
            if (first) {
                sb.append(content.substring(start, end))
                first = false
            } else {
                sb.append(' ').append(content.substring(start, end))
            }
            sb.append("\r\n")
            start = end
        }
    }
}
