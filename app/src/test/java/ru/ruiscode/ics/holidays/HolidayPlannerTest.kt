package ru.ruiscode.ics.holidays

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.ruiscode.ics.core.Uids
import ru.ruiscode.ics.i18n.StringResolver
import ru.ruiscode.ics.testAssetLoader
import java.time.LocalDate
import java.time.ZoneOffset

/** Проверка планировщика праздников: вхождения, спецификации, устойчивые UID. */
class HolidayPlannerTest {

    private val catalog = HolidayCatalog.load(testAssetLoader())
    private val resolver = StringResolver("en", emptyList())

    @Test
    fun occurrencesForYearExpandDurations() {
        val pack = catalog.pack("world")!!
        val occurrences = HolidayPlanner.occurrencesForYear(pack, 2026)
        assertEquals(pack.holidays.size, occurrences.size)
        val newYear = occurrences.first { it.holiday.id == "new_year" }
        assertEquals(LocalDate.of(2026, 1, 1), newYear.startDate)
    }

    @Test
    fun specsUseStableUidsAndUtcAllDayTimes() {
        val pack = catalog.pack("russia")!!
        val specs = HolidayPlanner.toSpecs(pack, 2026, setOf("new_year"), resolver)
        assertEquals(1, specs.size)
        val spec = specs[0]
        assertEquals(Uids.holiday("russia", "new_year", 2026), spec.uid)
        assertTrue(spec.allDay)
        assertEquals(
            LocalDate.of(2026, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            spec.startMillis,
        )
        assertEquals("UTC", spec.timeZoneId)
        assertEquals("New Year's Day", spec.title)
    }

    @Test
    fun repeatedCallsProduceIdenticalUids() {
        val pack = catalog.pack("united_states")!!
        val ids = setOf("thanksgiving", "memorial_day")
        val first = HolidayPlanner.toSpecs(pack, 2026, ids, resolver)
        val second = HolidayPlanner.toSpecs(pack, 2026, ids, resolver)
        assertEquals(first.map { it.uid }, second.map { it.uid })
        // Разные годы — разные UID.
        val nextYear = HolidayPlanner.toSpecs(pack, 2027, ids, resolver)
        assertTrue(nextYear.map { it.uid }.none { uid -> first.any { it.uid == uid } })
    }

    @Test
    fun skippedHolidaysAreExcluded() {
        val pack = catalog.pack("example_religious")!!
        // У набора нет исключений — пропущенных не будет.
        val occurrences = HolidayPlanner.occurrencesForYear(pack, 2026)
        assertEquals(pack.holidays.size, occurrences.size)
        assertTrue(occurrences.all { it.startDate.year == 2026 })
    }
}
