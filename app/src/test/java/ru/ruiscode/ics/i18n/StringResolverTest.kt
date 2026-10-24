package ru.ruiscode.ics.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.ruiscode.ics.core.JsonParser

/** Проверка резолвера строк: резервные переводы, параметры, формы множественного числа. */
class StringResolverTest {

    private fun resolver(): StringResolver {
        val ru = JsonParser.parse(
            """
            {
              "strings": {
                "only.en": "English only",
                "hello": "Привет, {name}!",
                "positional": "{0} из {1}",
                "plural.events": { "one": "{count} событие", "few": "{count} события", "many": "{count} событий" },
                "num": "Всего: {count}"
              }
            }
            """.trimIndent()
        ).obj("strings")!!.fields
        val en = JsonParser.parse(
            """
            {
              "strings": {
                "only.en": "English only",
                "hello": "Hello, {name}!",
                "positional": "{0} of {1}",
                "plural.events": { "one": "{count} event", "other": "{count} events" },
                "num": "Total: {count}"
              }
            }
            """.trimIndent()
        ).obj("strings")!!.fields
        return StringResolver("ru", listOf(ru, en))
    }

    @Test
    fun resolvesActiveThenEnglish() {
        val s = resolver()
        assertEquals("Привет, Мир!", s.format("hello", mapOf("name" to "Мир")))
    }

    @Test
    fun fallsBackToEnglishForMissingKey() {
        val s = resolver()
        // Ключ есть в en, отсутствует в ru (совпадает с only.en).
        assertEquals("English only", s.get("only.en"))
    }

    @Test
    fun missingKeyReturnsSafeDisplayAndWarns() {
        val warnings = mutableListOf<String>()
        val s = StringResolver("ru", emptyList()) { warnings.add(it) }
        assertEquals("[no.such.key]", s.get("no.such.key"))
        assertTrue(warnings.any { it.contains("no.such.key") })
    }

    @Test
    fun substitutesPositionalParams() {
        val s = resolver()
        assertEquals("5 из 10", s.format("positional", 5, 10))
    }

    @Test
    fun russianPluralForms() {
        val s = resolver()
        assertEquals("1 событие", s.plural("plural.events", 1))
        assertEquals("3 события", s.plural("plural.events", 3))
        assertEquals("7 событий", s.plural("plural.events", 7))
        assertEquals("21 событие", s.plural("plural.events", 21))
        assertEquals("115 событий", s.plural("plural.events", 115))
    }

    @Test
    fun englishPluralForms() {
        val s = StringResolver("en", listOf(resolverEnOnly()))
        assertEquals("1 event", s.plural("plural.events", 1))
        assertEquals("5 events", s.plural("plural.events", 5))
    }

    private fun resolverEnOnly(): Map<String, ru.ruiscode.ics.core.JsonValue> =
        JsonParser.parse(
            """{"strings": {"plural.events": {"one": "{count} event", "other": "{count} events"}}}"""
        ).obj("strings")!!.fields

    @Test
    fun missingPluralFormFallsBackToOther() {
        // В ru отсутствует форма many для ключа только с one/other — берём other.
        val layers = listOf(
            JsonParser.parse("""{"strings": {"plural.x": {"one": "один", "other": "{count} штук"}}}""")
                .obj("strings")!!.fields
        )
        val s = StringResolver("ru", layers)
        assertEquals("5 штук", s.plural("plural.x", 5))
    }

    @Test
    fun lookupReturnsNullForMissing() {
        assertNull(resolver().lookup("absent.key"))
    }

    @Test
    fun overlayLayersWin() {
        val s = resolver()
        val overlay = JsonParser.parse("""{"strings": {"hello": "Привет из набора, {name}!"}}""")
            .obj("strings")!!.fields
        assertEquals("Привет из набора, Мир!", s.withLayers(listOf(overlay)).format("hello", mapOf("name" to "Мир")))
    }
}
