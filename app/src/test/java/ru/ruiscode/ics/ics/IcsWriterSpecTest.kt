package ru.ruiscode.ics.ics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** Проверка генератора ICS: экранирование, переносы, TZID, RRULE, UID. */
class IcsWriterSpecTest {

    @Test
    fun escapesSpecialCharacters() {
        val start = ZonedDateTime.of(2026, 1, 1, 9, 0, 0, 0, ZoneOffset.UTC)
        val ics = IcsWriter.buildSpecs(
            listOf(
                IcsEventSpec(
                    uid = "u1",
                    title = "Встреча; с, запятой \\ и переносом\nстроки",
                    description = "Описание; тоже\\текст",
                    location = "Москва, центр",
                    time = IcsTime.Timed(start, start.plusHours(1)),
                )
            )
        )
        val parsed = IcsParser.parse(ics)
        assertEquals(1, parsed.events.size)
        assertEquals("Встреча; с, запятой \\ и переносом\nстроки", parsed.events[0].summary)
        assertEquals("Описание; тоже\\текст", parsed.events[0].description)
        assertEquals("Москва, центр", parsed.events[0].location)
        assertEquals("u1", parsed.events[0].uid)
    }

    @Test
    fun foldsLongLinesTo75Octets() {
        val start = ZonedDateTime.of(2026, 1, 1, 9, 0, 0, 0, ZoneOffset.UTC)
        val longTitle = "Длинный заголовок события " + "текст ".repeat(50)
        val ics = IcsWriter.buildSpecs(
            listOf(IcsEventSpec("f1", longTitle, "", "", IcsTime.Timed(start, start.plusHours(1))))
        )
        for (line in ics.lines()) {
            if (line.isNotEmpty() && !line[0].isWhitespace()) {
                assertTrue(
                    "Физическая строка длиннее 75 октетов: ${line.length}",
                    line.toByteArray(Charsets.UTF_8).size <= 75
                )
            }
        }
        // Разворачивается обратно в исходный заголовок.
        assertEquals(longTitle, IcsParser.parse(ics).events[0].summary)
    }

    @Test
    fun writesTzidForNonUtcZones() {
        val start = ZonedDateTime.of(2026, 1, 1, 9, 0, 0, 0, ZoneId.of("Europe/Moscow"))
        val ics = IcsWriter.buildSpecs(
            listOf(IcsEventSpec("tz1", "Москва", "", "", IcsTime.Timed(start, start.plusHours(1))))
        )
        assertTrue(ics.contains("DTSTART;TZID=Europe/Moscow:20260101T090000"))
        val parsed = IcsParser.parse(ics)
        assertEquals(start.toInstant().toEpochMilli(), parsed.events[0].startMillis)
        assertEquals("Europe/Moscow", parsed.events[0].timeZoneId)
    }

    @Test
    fun writesAllDayAsValueDate() {
        val ics = IcsWriter.buildSpecs(
            listOf(
                IcsEventSpec(
                    "ad1", "Праздник", "", "",
                    IcsTime.AllDay(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2)),
                )
            )
        )
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20260101"))
        assertTrue(ics.contains("DTEND;VALUE=DATE:20260102"))
        assertTrue(IcsParser.parse(ics).events[0].allDay)
    }

    @Test
    fun writesRrule() {
        val start = ZonedDateTime.of(2026, 1, 1, 9, 0, 0, 0, ZoneOffset.UTC)
        val ics = IcsWriter.buildSpecs(
            listOf(
                IcsEventSpec(
                    "r1", "Повтор", "", "",
                    IcsTime.Timed(start, start.plusHours(1), rrule = "FREQ=DAILY;COUNT=5"),
                )
            )
        )
        assertTrue(ics.contains("RRULE:FREQ=DAILY;COUNT=5"))
    }

    @Test
    fun generatedSeriesUidsAreUnique() {
        val start = ZonedDateTime.of(2026, 1, 1, 9, 0, 0, 0, ZoneOffset.UTC)
        val events = (1..10).map { i ->
            IcsEventSpec("uid-$i", "Событие $i", "", "", IcsTime.Timed(start.plusDays(i.toLong()), start.plusDays(i.toLong()).plusHours(1)))
        }
        val ics = IcsWriter.buildSpecs(events)
        val uids = Regex("UID:(.+)").findAll(ics).map { it.groupValues[1] }.toList()
        assertEquals(events.size, uids.size)
        assertEquals(events.size, uids.toSet().size) // уникальность
    }

    @Test
    fun roundTripSpecWithSpecialTitle() {
        val start = ZonedDateTime.of(2026, 3, 8, 14, 30, 0, 0, ZoneId.of("Europe/Moscow"))
        val ics = IcsWriter.buildSpecs(
            listOf(
                IcsEventSpec(
                    "rt1",
                    "Смена; ночь \\ 20:00→08:00",
                    "Описание с переносом\nстроки",
                    "",
                    IcsTime.Timed(start, start.plusHours(12)),
                )
            )
        )
        val parsed = IcsParser.parse(ics)
        assertEquals(1, parsed.events.size)
        assertEquals("Смена; ночь \\ 20:00→08:00", parsed.events[0].summary)
        assertEquals("Описание с переносом\nстроки", parsed.events[0].description)
        assertEquals(start.toInstant().toEpochMilli(), parsed.events[0].startMillis)
    }
}
