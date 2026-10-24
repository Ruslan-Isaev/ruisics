package ru.ruiscode.ics.ics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Проверка логики генерации и разбора .ics (round-trip и краевые случаи). */
class IcsRoundTripTest {

    @Test
    fun roundTripTimedEvent() {
        val start = LocalDate.of(2026, 10, 17).atTime(14, 30).atZone(ZoneId.of("Europe/Moscow")).toInstant().toEpochMilli()
        val end = start + 2 * 3600_000L
        val events = listOf(
            ExportEvent(
                uid = "test-1",
                title = "Встреча; с, запятой \\ и переносом\nстроки",
                description = "Описание",
                location = "Москва",
                allDay = false,
                startMillis = start,
                endMillis = end,
            )
        )
        val ics = IcsWriter.build(events)
        val parsed = IcsParser.parse(ics)

        assertEquals(1, parsed.events.size)
        val e = parsed.events[0]
        assertEquals("test-1", e.uid)
        assertEquals("Встреча; с, запятой \\ и переносом\nстроки", e.summary)
        assertEquals("Описание", e.description)
        assertEquals("Москва", e.location)
        assertEquals(false, e.allDay)
        assertEquals(start, e.startMillis)
        assertEquals(end, e.endMillis)
    }

    @Test
    fun roundTripAllDayEvent() {
        val start = LocalDate.of(2026, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val end = LocalDate.of(2026, 1, 2).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val events = listOf(
            ExportEvent("a-1", "Праздник", "", "", true, start, end)
        )
        val parsed = IcsParser.parse(IcsWriter.build(events))

        assertEquals(1, parsed.events.size)
        assertEquals(true, parsed.events[0].allDay)
        assertEquals(start, parsed.events[0].startMillis)
        assertEquals(end, parsed.events[0].endMillis)
    }

    @Test
    fun parseTzidAndUtcFormats() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:tz-1
            SUMMARY:Московское время
            DTSTART;TZID=Europe/Moscow:20261017T140000
            DTEND;TZID=Europe/Moscow:20261017T150000
            END:VEVENT
            BEGIN:VEVENT
            UID:utc-1
            SUMMARY:UTC
            DTSTART:20261017T100000Z
            DTEND:20261017T110000Z
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val parsed = IcsParser.parse(ics)
        assertEquals(2, parsed.events.size)

        val msk = parsed.events[0]
        val utc = parsed.events[1]
        // 14:00 МСК == 11:00 UTC
        assertEquals(utc.startMillis + 3600_000L, msk.startMillis)
        assertEquals("Europe/Moscow", msk.timeZoneId)
    }

    @Test
    fun parseDurationAndFoldedLines() {
        val longSummary = "Очень длинное описание события " + "текст ".repeat(50)
        val folded = IcsWriter.build(
            listOf(
                ExportEvent("f-1", longSummary, "", "", false, 0L, 0L)
            )
        )
        // Строки свёрнуты корректно (не более 75 октетов на физическую строку)
        for (line in folded.lines()) {
            if (line.isNotEmpty() && !line[0].isWhitespace()) {
                assertTrue(
                    "Строка длиннее 75 октетов: ${line.length}",
                    line.toByteArray(Charsets.UTF_8).size <= 75
                )
            }
        }

        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:dur-1
            SUMMARY:Событие с длительностью
            DTSTART:20261017T090000Z
            DURATION:PT1H30M
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val parsed = IcsParser.parse(ics)
        assertEquals(1, parsed.events.size)
        val e = parsed.events[0]
        assertEquals(
            e.startMillis + (90 * 60_000L),
            e.endMillis
        )
    }

    @Test
    fun skipsBrokenEvents() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:bad-1
            SUMMARY:Без даты
            END:VEVENT
            BEGIN:VEVENT
            UID:good-1
            SUMMARY:Хорошее
            DTSTART:20261017T090000Z
            DTEND:20261017T100000Z
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val parsed = IcsParser.parse(ics)
        assertEquals(1, parsed.events.size)
        assertEquals(1, parsed.skipped)
        assertEquals("Хорошее", parsed.events[0].summary)
    }
}
