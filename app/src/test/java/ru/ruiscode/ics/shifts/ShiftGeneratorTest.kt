package ru.ruiscode.ics.shifts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Проверка генерации расписания смен, включая переходы через полночь. */
class ShiftGeneratorTest {

    private val zone = ZoneId.of("Europe/Moscow")

    private fun template(
        cycle: List<String>,
        dayTypes: Map<String, DayTypeSpec>,
    ) = ShiftTemplate("t", "n", "d", cycle, dayTypes, emptyMap())

    private fun dayType(id: String, start: String?, end: String?): DayTypeSpec =
        DayTypeSpec(id, "$id.name", start?.let { LocalTime.parse(it) }, end?.let { LocalTime.parse(it) })

    @Test
    fun expandsTwoTwoCycle() {
        val template = template(
            listOf("work", "work", "rest", "rest"),
            mapOf(
                "work" to dayType("work", "08:00", "20:00"),
                "rest" to dayType("rest", null, null),
            ),
        )
        val occurrences = ShiftGenerator.generate(
            template, LocalDate.of(2026, 1, 1), 6, "Смена", zone
        )
        assertEquals(6, occurrences.size)
        assertEquals("work", occurrences[0].dayTypeId)
        assertEquals("work", occurrences[1].dayTypeId)
        assertEquals("rest", occurrences[2].dayTypeId)
        assertEquals("rest", occurrences[3].dayTypeId)
        assertEquals("work", occurrences[4].dayTypeId)
        assertEquals(LocalDate.of(2026, 1, 5), occurrences[4].date)
        assertFalse(occurrences[2].isWorking)
        assertEquals(LocalTime.of(8, 0), occurrences[0].start!!.toLocalTime())
        assertEquals(LocalTime.of(20, 0), occurrences[0].end!!.toLocalTime())
    }

    @Test
    fun overnightShiftEndsNextDay() {
        val template = template(
            listOf("night"),
            mapOf("night" to dayType("night", "20:00", "08:00")),
        )
        val occurrences = ShiftGenerator.generate(
            template, LocalDate.of(2026, 1, 1), 1, "Ночь", zone
        )
        val night = occurrences[0]
        assertEquals(LocalDate.of(2026, 1, 1), night.start!!.toLocalDate())
        assertEquals(LocalDate.of(2026, 1, 2), night.end!!.toLocalDate())
        assertEquals(LocalTime.of(8, 0), night.end!!.toLocalTime())
    }

    @Test
    fun fullDayShiftEndsNextDay() {
        val template = template(
            listOf("work"),
            mapOf("work" to dayType("work", "08:00", "08:00")),
        )
        val occurrences = ShiftGenerator.generate(
            template, LocalDate.of(2026, 1, 1), 1, "Сутки", zone
        )
        assertEquals(LocalDate.of(2026, 1, 2), occurrences[0].end!!.toLocalDate())
    }

    @Test
    fun yearTransitionAcrossMidnight() {
        val template = template(
            listOf("night"),
            mapOf("night" to dayType("night", "20:00", "08:00")),
        )
        val occurrences = ShiftGenerator.generate(
            template, LocalDate.of(2026, 12, 31), 1, "Ночь", zone
        )
        val night = occurrences[0]
        assertEquals(LocalDate.of(2026, 12, 31), night.start!!.toLocalDate())
        assertEquals(LocalDate.of(2027, 1, 1), night.end!!.toLocalDate())
    }

    @Test
    fun cycleWrapsForLongSchedules() {
        val template = template(
            listOf("work", "rest"),
            mapOf(
                "work" to dayType("work", "09:00", "18:00"),
                "rest" to dayType("rest", null, null),
            ),
        )
        val occurrences = ShiftGenerator.generate(
            template, LocalDate.of(2026, 1, 1), 5, "Смена", zone
        )
        assertEquals(listOf("work", "rest", "work", "rest", "work"), occurrences.map { it.dayTypeId })
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidTemplate() {
        val template = template(listOf("unknown"), mapOf("work" to dayType("work", "09:00", "18:00")))
        ShiftGenerator.generate(template, LocalDate.of(2026, 1, 1), 1, "Смена", zone)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveLength() {
        val template = template(listOf("work"), mapOf("work" to dayType("work", "09:00", "18:00")))
        ShiftGenerator.generate(template, LocalDate.of(2026, 1, 1), 0, "Смена", zone)
    }

    @Test
    fun recoveryDayHasNoEvent() {
        val template = template(
            listOf("night", "recovery"),
            mapOf(
                "night" to dayType("night", "20:00", "08:00"),
                "recovery" to dayType("recovery", null, null),
            ),
        )
        val occurrences = ShiftGenerator.generate(
            template, LocalDate.of(2026, 1, 1), 2, "Смена", zone
        )
        assertTrue(occurrences[0].isWorking)
        assertFalse(occurrences[1].isWorking)
    }
}
