package ru.ruiscode.ics.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.ruiscode.ics.testAssetLoader

/** Проверка обнаружения и валидации языковых модулей. */
class LocaleCatalogTest {

    @Test
    fun loadsRealLocaleAssets() {
        val problems = mutableListOf<String>()
        val packs = LocaleCatalog.load(testAssetLoader(), problems)
        assertEquals(emptyList<String>(), problems)
        assertEquals(2, packs.size)
        assertEquals(setOf("en", "ru"), packs.map { it.meta.code }.toSet())
        val en = packs.first { it.meta.code == "en" }
        assertEquals("ltr", en.meta.direction)
        assertEquals("RUIS ICS", en.strings["app.name"]?.asString())
    }

    @Test
    fun skipsBrokenAndDuplicateEntries() {
        val assets = mapOf(
            "locales/manifest.json" to """
                {
                  "schemaVersion": 1,
                  "locales": [
                    { "code": "en", "name": "English", "nativeName": "English", "direction": "ltr", "fileName": "en.json" },
                    { "code": "en", "name": "Dup", "nativeName": "Dup", "direction": "ltr", "fileName": "en.json" },
                    { "code": "xx", "name": "X", "nativeName": "X", "direction": "ltr", "fileName": "xx.json" },
                    { "code": "br", "name": "B", "nativeName": "B", "direction": "ltr", "fileName": "broken.json" },
                    { "code": "nf", "name": "N", "nativeName": "N", "direction": "ltr", "fileName": "missing.json" }
                  ]
                }
            """.trimIndent(),
            "locales/en.json" to """{ "meta": { "code": "en" }, "strings": { "a": "b" } }""",
            "locales/xx.json" to """{ "meta": { "code": "xx" }, "strings": { } }""",
            "locales/broken.json" to """{ "meta": { "code": "broken" }, "strings": broken""",
        )
        val problems = mutableListOf<String>()
        val packs = LocaleCatalog.load({ assets[it] }, problems)
        assertEquals(2, packs.size)
        assertTrue(problems.any { it.contains("duplicate") })
        assertTrue(problems.any { it.contains("broken JSON") })
        assertTrue(problems.any { it.contains("not found") })
    }

    @Test
    fun missingManifestYieldsEmptyCatalog() {
        val problems = mutableListOf<String>()
        val packs = LocaleCatalog.load({ null }, problems)
        assertTrue(packs.isEmpty())
        assertTrue(problems.isNotEmpty())
    }
}
