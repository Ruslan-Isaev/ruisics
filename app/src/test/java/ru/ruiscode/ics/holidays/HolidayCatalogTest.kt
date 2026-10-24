package ru.ruiscode.ics.holidays

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.ruiscode.ics.testAssetLoader

/** Проверка каталога наборов праздников на реальных assets. */
class HolidayCatalogTest {

    @Test
    fun loadsAllRealPacks() {
        val catalog = HolidayCatalog.load(testAssetLoader())
        assertEquals(emptyList<String>(), catalog.problems)
        assertEquals(
            setOf("world", "russia", "united_states", "example_religious"),
            catalog.packs.map { it.meta.id }.toSet(),
        )
    }

    @Test
    fun packStructureIsComplete() {
        val catalog = HolidayCatalog.load(testAssetLoader())
        val pack = catalog.pack("united_states")!!
        assertEquals("country", pack.meta.category)
        assertEquals(listOf("ru", "en"), pack.meta.availableLocales)
        assertTrue(pack.holidays.size >= 10)
        // Каждый праздник имеет имя и правило.
        pack.holidays.forEach { h ->
            assertTrue(h.nameKey.isNotBlank())
            assertTrue(HolidayDateCalculator.isSupported(h.dateRule))
        }
    }

    @Test
    fun realPackDatesResolve() {
        val catalog = HolidayCatalog.load(testAssetLoader())
        val us = catalog.pack("united_states")!!
        val resolution = HolidayDateCalculator.resolve(us, 2026)
        assertTrue(resolution.unresolved.isEmpty())
        assertEquals(java.time.LocalDate.of(2026, 11, 26), resolution.dates["thanksgiving"])

        val religious = catalog.pack("example_religious")!!
        val religiousResolution = HolidayDateCalculator.resolve(religious, 2026)
        assertTrue(religiousResolution.unresolved.isEmpty())
        assertEquals(java.time.LocalDate.of(2026, 4, 5), religiousResolution.dates["easter"])
    }

    @Test
    fun duplicatePackIdsAreRejected() {
        val assets = mapOf(
            "holiday_packs/manifest.json" to """
                {
                  "schemaVersion": 1,
                  "packs": [
                    { "id": "a", "directory": "a" },
                    { "id": "a", "directory": "a" }
                  ]
                }
            """.trimIndent(),
            "holiday_packs/a/pack.json" to """
                {
                  "schemaVersion": 1,
                  "id": "a",
                  "nameKey": "pack.a.name",
                  "descriptionKey": "pack.a.description",
                  "category": "other",
                  "version": "1.0.0",
                  "dataFile": "holidays.json",
                  "translationsDirectory": "translations",
                  "availableLocales": []
                }
            """.trimIndent(),
            "holiday_packs/a/holidays.json" to """
                {
                  "schemaVersion": 1,
                  "holidays": [
                    { "id": "h1", "nameKey": "n", "descriptionKey": "d",
                      "dateRule": { "type": "fixed", "month": 1, "day": 1 } }
                  ]
                }
            """.trimIndent(),
        )
        val catalog = HolidayCatalog.load({ assets[it] })
        assertEquals(1, catalog.packs.size)
        assertTrue(catalog.problems.any { it.contains("duplicate") })
    }

    @Test
    fun brokenPackIsSkippedWithoutCrash() {
        val assets = mapOf(
            "holiday_packs/manifest.json" to """
                { "schemaVersion": 1, "packs": [ { "id": "good", "directory": "good" }, { "id": "bad", "directory": "bad" } ] }
            """.trimIndent(),
            "holiday_packs/good/pack.json" to """
                { "schemaVersion": 1, "id": "good", "nameKey": "p", "descriptionKey": "p",
                  "category": "other", "version": "1.0.0", "dataFile": "holidays.json",
                  "translationsDirectory": "translations", "availableLocales": [] }
            """.trimIndent(),
            "holiday_packs/good/holidays.json" to """
                { "schemaVersion": 1, "holidays": [ { "id": "h", "nameKey": "n", "descriptionKey": "d",
                  "dateRule": { "type": "fixed", "month": 1, "day": 1 } } ] }
            """.trimIndent(),
            "holiday_packs/bad/pack.json" to "{ broken json",
        )
        val catalog = HolidayCatalog.load({ assets[it] })
        assertEquals(1, catalog.packs.size)
        assertEquals("good", catalog.packs[0].meta.id)
        assertTrue(catalog.problems.isNotEmpty())
    }

    @Test
    fun missingTranslationsDoNotBreakPack() {
        // В наборе заявлен язык без файла перевода — набор загружается без него.
        val assets = mapOf(
            "holiday_packs/manifest.json" to """
                { "schemaVersion": 1, "packs": [ { "id": "p", "directory": "p" } ] }
            """.trimIndent(),
            "holiday_packs/p/pack.json" to """
                { "schemaVersion": 1, "id": "p", "nameKey": "p.name", "descriptionKey": "p.desc",
                  "category": "other", "version": "1.0.0", "dataFile": "holidays.json",
                  "translationsDirectory": "translations", "availableLocales": ["ru", "en"] }
            """.trimIndent(),
            "holiday_packs/p/holidays.json" to """
                { "schemaVersion": 1, "holidays": [ { "id": "h", "nameKey": "n", "descriptionKey": "d",
                  "dateRule": { "type": "fixed", "month": 1, "day": 1 } } ] }
            """.trimIndent(),
        )
        val catalog = HolidayCatalog.load({ assets[it] })
        assertEquals(1, catalog.packs.size)
        val pack = catalog.packs[0]
        // Резолвер набора не падает при отсутствии переводов — возвращается
        // безопасное отображение ключа.
        val name = pack.name(ru.ruiscode.ics.i18n.StringResolver("en", emptyList()))
        assertNotNull(name)
        assertTrue(catalog.problems.any { it.contains("missing translation") })
    }
}
