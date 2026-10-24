package ru.ruiscode.ics.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Проверка минимального JSON-парсера: корректный и повреждённый вход. */
class JsonParserTest {

    @Test
    fun parsesObjectsArraysAndScalars() {
        val json = JsonParser.parse(
            """
            {
              "name": "Test",
              "count": 3,
              "ratio": 0.5,
              "flag": true,
              "nothing": null,
              "list": [1, "two", false],
              "nested": { "key": "value" }
            }
            """.trimIndent()
        )
        assertEquals("Test", json.str("name"))
        assertEquals(3, json.int("count"))
        assertTrue(json.bool("flag")!!)
        assertNull(json.str("nothing"))
        assertEquals(listOf(1.0, 2.0, 3.0).size, json.arr("list")!!.items.size)
        assertEquals("value", json.obj("nested")!!.str("key"))
    }

    @Test
    fun parsesEscapesAndUnicode() {
        val json = JsonParser.parse("""{"text": "line1\nline2\t\"quoted\" \\ Привет"}""")
        assertEquals("line1\nline2\t\"quoted\" \\ Привет", json.str("text"))
    }

    @Test
    fun handlesBom() {
        assertEquals("x", JsonParser.parse("﻿{\"k\": \"x\"}").str("k"))
    }

    @Test(expected = JsonParser.JsonException::class)
    fun rejectsBrokenJson() {
        JsonParser.parse("""{"k": "unterminated""")
    }

    @Test(expected = JsonParser.JsonException::class)
    fun rejectsTrailingContent() {
        JsonParser.parse("""{"k": 1} garbage""")
    }

    @Test(expected = JsonParser.JsonException::class)
    fun rejectsSingleQuotes() {
        JsonParser.parse("{'k': 1}")
    }

    @Test(expected = JsonParser.JsonException::class)
    fun rejectsTrailingComma() {
        JsonParser.parse("""{"k": 1,}""")
    }

    @Test
    fun asIntRejectsFractions() {
        assertNull(JsonValue.JsonNum(1.5).asInt())
        assertEquals(2, JsonValue.JsonNum(2.0).asInt())
    }

    @Test
    fun roundTripsToJson() {
        val parsed = JsonParser.parse("""{"a": [1, "два", null, true], "b": {"c": "x"}}""")
        val reParsed = JsonParser.parse(parsed.toJson())
        assertEquals(parsed.toJson(), reParsed.toJson())
    }
}
