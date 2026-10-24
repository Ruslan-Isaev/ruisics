package ru.ruiscode.ics.ics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.ruiscode.ics.ics.SeriesGenerator.Freq
import ru.ruiscode.ics.ics.SeriesGenerator.Recurrence
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Проверка разворачивания повторений и формирования RRULE. */
class SeriesGeneratorTest {

    private val zone = ZoneId.of("Europe/Moscow")
    private fun zdt(date: LocalDate, hour: Int) = date.atTime(hour, 0).atZone(zone)

    @Test
    fun dailyCountExpansion() {
        val start = zdt(LocalDate.of(2026, 1, 1), 9)
        val end = start.plusHours(1)
        val expanded = SeriesGenerator.expand(start, end, Recurrence(Freq.DAILY, count = 3))
        assertEquals(3, expanded.size)
        assertEquals(LocalDate.of(2026, 1, 3), expanded[2].first.toLocalDate())
    }

    @Test
    fun weeklyIntervalExpansion() {
        val start = zdt(LocalDate.of(2026, 1, 5), 9) // понедельник
        val end = start.plusHours(2)
        val expanded = SeriesGenerator.expand(start, end, Recurrence(Freq.WEEKLY, interval = 2, count = 3))
        assertEquals(3, expanded.size)
        assertEquals(LocalDate.of(2026, 1, 19), expanded[1].first.toLocalDate())
        assertEquals(LocalDate.of(2026, 2, 2), expanded[2].first.toLocalDate())
    }

    @Test
    fun monthlyClampsShortMonths() {
        val start = zdt(LocalDate.of(2026, 1, 31), 9)
        val end = start.plusHours(1)
        val expanded = SeriesGenerator.expand(start, end, Recurrence(Freq.MONTHLY, count = 2))
        assertEquals(LocalDate.of(2026, 2, 28), expanded[1].first.toLocalDate())
    }

    @Test
    fun yearlyLeapDayClamps() {
        val start = zdt(LocalDate.of(2024, 2, 29), 9)
        val end = start.plusHours(1)
        val expanded = SeriesGenerator.expand(start, end, Recurrence(Freq.YEARLY, count = 2))
        assertEquals(LocalDate.of(2025, 2, 28), expanded[1].first.toLocalDate())
    }

    @Test
    fun untilBoundsExpansion() {
        val start = zdt(LocalDate.of(2026, 1, 1), 9)
        val end = start.plusHours(1)
        val until = zdt(LocalDate.of(2026, 1, 5), 0)
        val expanded = SeriesGenerator.expand(start, end, Recurrence(Freq.DAILY, until = until))
        assertEquals(4, expanded.size) // 1..4 января; 5-е выходит за границу
    }

    @Test
    fun maxOccurrencesCapsExpansion() {
        val start = zdt(LocalDate.of(2026, 1, 1), 9)
        val end = start.plusHours(1)
        val expanded = SeriesGenerator.expand(start, end, Recurrence(Freq.DAILY), maxOccurrences = 7)
        assertEquals(7, expanded.size)
    }

    @Test
    fun preservesDuration() {
        val start = zdt(LocalDate.of(2026, 1, 1), 9)
        val end = start.plusMinutes(90)
        val expanded = SeriesGenerator.expand(start, end, Recurrence(Freq.DAILY, count = 2))
        assertEquals(90 * 60_000L, java.time.Duration.between(expanded[1].first, expanded[1].second).toMillis())
    }

    @Test
    fun rruleTextFormats() {
        assertEquals("FREQ=DAILY;COUNT=5", SeriesGenerator.rruleText(Recurrence(Freq.DAILY, count = 5)))
        assertEquals("FREQ=WEEKLY;INTERVAL=2", SeriesGenerator.rruleText(Recurrence(Freq.WEEKLY, interval = 2)))
        val until = zdt(LocalDate.of(2026, 12, 31), 23)
        val text = SeriesGenerator.rruleText(Recurrence(Freq.MONTHLY, until = until))
        assertTrue(text.startsWith("FREQ=MONTHLY;UNTIL=20261231T"))
    }
}
