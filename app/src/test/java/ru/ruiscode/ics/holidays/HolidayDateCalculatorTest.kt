package ru.ruiscode.ics.holidays

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Проверка вычисления дат праздников: все типы правил и краевые случаи. */
class HolidayDateCalculatorTest {

    private fun holiday(id: String, rule: DateRule, exceptions: Map<Int, DateRule> = emptyMap()) =
        Holiday(id, "$id.name", "$id.desc", rule, 1, emptyList(), false, exceptions)

    private fun pack(holidays: List<Holiday>) = HolidayPack(
        meta = HolidayPackMeta(
            id = "t", directory = "t", nameKey = "p", descriptionKey = "p",
            category = "other", schemaVersion = 1, version = "1", dataFile = "d",
            translationsDirectory = "tr", availableLocales = emptyList(),
            sourceName = null, sourceUrl = null,
            licenseSpdxId = null, licenseName = null, licenseUrl = null,
        ),
        holidays = holidays,
        translations = emptyMap(),
    )

    @Test
    fun fixedDates() {
        val resolution = HolidayDateCalculator.resolve(
            pack(listOf(holiday("a", DateRule("fixed", month = 1, day = 1)))), 2026
        )
        assertEquals(LocalDate.of(2026, 1, 1), resolution.dates["a"])
    }

    @Test
    fun leapYearFebruary() {
        val resolution = HolidayDateCalculator.resolve(
            pack(listOf(holiday("a", DateRule("fixed", month = 2, day = 29)))), 2024
        )
        assertEquals(LocalDate.of(2024, 2, 29), resolution.dates["a"])
        // 2025 не високосный — правило невычислимо.
        val bad = HolidayDateCalculator.resolve(
            pack(listOf(holiday("a", DateRule("fixed", month = 2, day = 29)))), 2025
        )
        assertTrue("a" in bad.unresolved)
    }

    @Test
    fun yearTransitions() {
        val resolution = HolidayDateCalculator.resolve(
            pack(
                listOf(
                    holiday("last", DateRule("fixed", month = 12, day = 31)),
                    holiday("first", DateRule("fixed", month = 1, day = 1)),
                )
            ), 2026
        )
        assertEquals(LocalDate.of(2026, 12, 31), resolution.dates["last"])
        assertEquals(LocalDate.of(2026, 1, 1), resolution.dates["first"])
    }

    @Test
    fun nthWeekdayRules() {
        // Третий понедельник января 2026 = 19 января.
        val mlk = HolidayDateCalculator.compute(
            DateRule("nth_weekday", month = 1, weekday = 1, n = 3), 2026, emptyMap()
        )
        assertEquals(LocalDate.of(2026, 1, 19), mlk)

        // Последний понедельник мая 2026 = 25 мая.
        val memorial = HolidayDateCalculator.compute(
            DateRule("nth_weekday", month = 5, weekday = 1, n = -1), 2026, emptyMap()
        )
        assertEquals(LocalDate.of(2026, 5, 25), memorial)

        // Четвёртый четверг ноября 2026 = 26 ноября.
        val thanksgiving = HolidayDateCalculator.compute(
            DateRule("nth_weekday", month = 11, weekday = 4, n = 4), 2026, emptyMap()
        )
        assertEquals(LocalDate.of(2026, 11, 26), thanksgiving)
    }

    @Test
    fun gregorianEasterKnownYears() {
        assertEquals(LocalDate.of(2024, 3, 31), HolidayDateCalculator.gregorianEaster(2024))
        assertEquals(LocalDate.of(2025, 4, 20), HolidayDateCalculator.gregorianEaster(2025))
        assertEquals(LocalDate.of(2026, 4, 5), HolidayDateCalculator.gregorianEaster(2026))
        assertEquals(LocalDate.of(2027, 3, 28), HolidayDateCalculator.gregorianEaster(2027))
    }

    @Test
    fun offsetChainsFromEaster() {
        val resolution = HolidayDateCalculator.resolve(
            pack(
                listOf(
                    holiday("easter", DateRule("calculated", calendar = "gregorian_easter")),
                    holiday("good_friday", DateRule("offset", relativeTo = "easter", offsetDays = -2)),
                    holiday("easter_monday", DateRule("offset", relativeTo = "easter", offsetDays = 1)),
                    holiday("pentecost", DateRule("offset", relativeTo = "easter", offsetDays = 49)),
                )
            ), 2026
        )
        assertEquals(LocalDate.of(2026, 4, 5), resolution.dates["easter"])
        assertEquals(LocalDate.of(2026, 4, 3), resolution.dates["good_friday"])
        assertEquals(LocalDate.of(2026, 4, 6), resolution.dates["easter_monday"])
        assertEquals(LocalDate.of(2026, 5, 24), resolution.dates["pentecost"])
    }

    @Test
    fun oneOffHolidayWithYear() {
        val oneOff = DateRule("fixed", year = 2026, month = 5, day = 30)
        assertEquals(LocalDate.of(2026, 5, 30), HolidayDateCalculator.compute(oneOff, 2026, emptyMap()))
        assertNull(HolidayDateCalculator.compute(oneOff, 2027, emptyMap()))
    }

    @Test
    fun yearlyExceptionOverridesDate() {
        val resolution = HolidayDateCalculator.resolve(
            pack(
                listOf(
                    holiday(
                        "h",
                        DateRule("fixed", month = 5, day = 9),
                        exceptions = mapOf(2026 to DateRule("fixed", month = 5, day = 10)),
                    )
                )
            ), 2026
        )
        assertEquals(LocalDate.of(2026, 5, 10), resolution.dates["h"])
    }

    @Test
    fun skipExceptionRemovesHoliday() {
        val resolution = HolidayDateCalculator.resolve(
            pack(
                listOf(
                    holiday(
                        "h",
                        DateRule("fixed", month = 5, day = 9),
                        exceptions = mapOf(2026 to DateRule("skip")),
                    )
                )
            ), 2026
        )
        assertNull(resolution.dates["h"])
        assertTrue(resolution.unresolved.isEmpty())
    }

    @Test
    fun unknownCalculatedAlgorithmIsUnresolved() {
        val resolution = HolidayDateCalculator.resolve(
            pack(listOf(holiday("h", DateRule("calculated", calendar = "mayan_calendar")))), 2026
        )
        assertTrue("h" in resolution.unresolved)
        assertTrue(resolution.dates["h"] == null)
    }

    @Test
    fun cyclicOffsetDependenciesAreUnresolved() {
        val resolution = HolidayDateCalculator.resolve(
            pack(
                listOf(
                    holiday("a", DateRule("offset", relativeTo = "b", offsetDays = 1)),
                    holiday("b", DateRule("offset", relativeTo = "a", offsetDays = 1)),
                )
            ), 2026
        )
        assertTrue(setOf("a", "b") == resolution.unresolved.toSet())
    }
}
