package ru.ruiscode.ics.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Проверка устойчивых UID: детерминированность и уникальность. */
class UidsTest {

    @Test
    fun holidayUidIsStableAndDistinct() {
        val a = Uids.holiday("world", "new_year", 2026)
        val b = Uids.holiday("world", "new_year", 2026)
        assertEquals(a, b)
        assertNotEquals(a, Uids.holiday("world", "new_year", 2027))
        assertNotEquals(a, Uids.holiday("russia", "new_year", 2026))
        assertTrue(a.startsWith("ruisics-holiday-"))
        assertTrue(a.endsWith("@ruiscode.ru"))
    }

    @Test
    fun contentUidDependsOnContent() {
        val a = Uids.content("shift", "Смена", "start", "end")
        val b = Uids.content("shift", "Смена", "start", "end")
        assertEquals(a, b)
        assertNotEquals(a, Uids.content("shift", "Смена", "start", "end2"))
        assertNotEquals(a, Uids.content("gen", "Смена", "start", "end"))
    }

    @Test
    fun manyUidsAreUnique() {
        val uids = (1..500).map { Uids.content("gen", "title", "desc", "loc", it.toString(), "x") }.toSet()
        assertEquals(500, uids.size)
    }
}
