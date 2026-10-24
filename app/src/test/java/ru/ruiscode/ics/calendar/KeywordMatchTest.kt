package ru.ruiscode.ics.calendar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Проверка поиска событий по ключевому слову. */
class KeywordMatchTest {

    @Test
    fun matchesAnyFieldCaseInsensitive() {
        assertTrue(eventContainsKeyword("Тренировка по футболу", "", "", "тренировка"))
        assertTrue(eventContainsKeyword("ТРЕНИРОВКА", "", "", "тренировка"))
        assertTrue(eventContainsKeyword("", "Тренировка в зале", "", "ТРЕНИРОВКА"))
        assertTrue(eventContainsKeyword("", "", "Спортзал «Тренировка»", "тренировка"))
    }

    @Test
    fun doesNotMatchAbsentKeyword() {
        assertFalse(eventContainsKeyword("Встреча с друзьями", "", "", "спорт"))
        assertFalse(eventContainsKeyword("", "", "", "спорт"))
    }

    @Test
    fun partialWordIsAMatch() {
        assertTrue(eventContainsKeyword("Тренировка", "", "", "трен"))
        assertFalse(eventContainsKeyword("Тренер", "", "", "тренеру")) // ключевое слово длиннее поля — не вхождение
    }
}
