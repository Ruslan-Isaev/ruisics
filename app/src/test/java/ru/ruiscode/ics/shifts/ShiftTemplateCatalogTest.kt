package ru.ruiscode.ics.shifts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.ruiscode.ics.testAssetLoader

/** Проверка каталога шаблонов смен: встроенные JSON, пользовательские, валидация. */
class ShiftTemplateCatalogTest {

    @Test
    fun loadsAllBuiltInTemplates() {
        val catalog = ShiftTemplateCatalog.load(testAssetLoader(), null)
        assertEquals(emptyList<String>(), catalog.problems)
        assertEquals(
            setOf("two_two", "one_three", "three_three", "day_night", "five_two"),
            catalog.templates.map { it.id }.toSet(),
        )
        catalog.templates.forEach { assertTrue(it.isValid()) }
    }

    @Test
    fun dayNightTemplateHasOvernightShift() {
        val catalog = ShiftTemplateCatalog.load(testAssetLoader(), null)
        val template = catalog.template("day_night")!!
        assertTrue(template.dayType("night")!!.crossesMidnight)
        assertTrue(!template.dayType("day")!!.crossesMidnight)
        assertTrue(!template.dayType("recovery")!!.hasEvent)
    }

    @Test
    fun loadsCustomTemplatesFromSettings() {
        val customJson = """
            {
              "templates": [
                {
                  "schemaVersion": 1,
                  "id": "custom_1",
                  "nameKey": "shift.template.custom_1.name",
                  "descriptionKey": "shift.template.custom_1.description",
                  "cycle": ["work", "rest"],
                  "dayTypes": {
                    "work": { "nameKey": "shift.daytype.work.name", "startTime": "10:00", "endTime": "22:00" },
                    "rest": { "nameKey": "shift.daytype.rest.name", "startTime": null, "endTime": null }
                  },
                  "isCustom": true,
                  "translations": {
                    "en": { "strings": {
                      "shift.template.custom_1.name": "My shift",
                      "shift.template.custom_1.description": "Custom",
                      "shift.daytype.work.name": "Work day",
                      "shift.daytype.rest.name": "Day off"
                    } }
                  }
                }
              ]
            }
        """.trimIndent()
        val catalog = ShiftTemplateCatalog.load(testAssetLoader(), customJson)
        val custom = catalog.template("custom_1")
        assertTrue(custom != null)
        assertTrue(custom!!.isCustom)
        assertTrue(custom.isValid())
        val resolver = custom.resolver(ru.ruiscode.ics.i18n.StringResolver("en", emptyList()))
        assertEquals("My shift", custom.name(resolver))
    }

    @Test
    fun brokenCustomJsonIsReportedButNotFatal() {
        val catalog = ShiftTemplateCatalog.load(testAssetLoader(), "{ not json")
        assertTrue(catalog.problems.any { it.contains("Broken JSON") })
        assertTrue(catalog.templates.isNotEmpty()) // встроенные остались
    }

    @Test
    fun invalidTemplateIsSkipped() {
        val assets = mapOf(
            "shift_templates/manifest.json" to """
                { "schemaVersion": 1, "templates": [ { "fileName": "bad.json" } ] }
            """.trimIndent(),
            "shift_templates/templates/bad.json" to """
                {
                  "schemaVersion": 1,
                  "id": "bad",
                  "nameKey": "n",
                  "descriptionKey": "d",
                  "cycle": ["work", "ghost"],
                  "dayTypes": {
                    "work": { "nameKey": "w", "startTime": "09:00", "endTime": "18:00" }
                  },
                  "translations": {}
                }
            """.trimIndent(),
        )
        val catalog = ShiftTemplateCatalog.load({ assets[it] }, null)
        assertTrue(catalog.templates.isEmpty())
        assertTrue(catalog.problems.any { it.contains("unknown day types") })
    }

    @Test
    fun dayTypeTimesMustBePaired() {
        val assets = mapOf(
            "shift_templates/manifest.json" to """
                { "schemaVersion": 1, "templates": [ { "fileName": "bad.json" } ] }
            """.trimIndent(),
            "shift_templates/templates/bad.json" to """
                {
                  "schemaVersion": 1,
                  "id": "bad",
                  "nameKey": "n",
                  "descriptionKey": "d",
                  "cycle": ["work"],
                  "dayTypes": {
                    "work": { "nameKey": "w", "startTime": "09:00", "endTime": null }
                  },
                  "translations": {}
                }
            """.trimIndent(),
        )
        val catalog = ShiftTemplateCatalog.load({ assets[it] }, null)
        assertTrue(catalog.templates.isEmpty())
        assertTrue(catalog.problems.any { it.contains("both startTime and endTime") })
    }
}
