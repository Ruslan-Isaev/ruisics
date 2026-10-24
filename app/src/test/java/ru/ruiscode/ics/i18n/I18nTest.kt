package ru.ruiscode.ics.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.ruiscode.ics.settings.AppSettings
import ru.ruiscode.ics.settings.InMemoryStore
import ru.ruiscode.ics.testAssetLoader

/** Проверка I18n: инициализация, выбор языка, авто-режим, реактивность состояния. */
class I18nTest {

    @Test
    fun initLoadsCatalogAndSetsAutoLanguage() {
        I18n.init(testAssetLoader(), AppSettings(InMemoryStore()))
        assertTrue(I18n.availableLocales().any { it.code == "ru" })
        assertTrue(I18n.isAuto())
        assertEquals(2, I18n.availableLocales().size)
        assertEquals("RUIS ICS", I18n.resolverOrNull()!!.get("app.name"))
    }

    @Test
    fun setLanguageSwitchesResolver() {
        val settings = AppSettings(InMemoryStore())
        I18n.init(testAssetLoader(), settings)
        I18n.setLanguage("ru")
        assertFalse(I18n.isAuto())
        assertEquals("ru", I18n.currentCode())
        assertEquals("Настройки", I18n.resolverOrNull()!!.get("menu.settings"))

        I18n.setLanguage("en")
        assertEquals("en", I18n.currentCode())
        assertEquals("Settings", I18n.resolverOrNull()!!.get("menu.settings"))
        assertEquals("en", settings.language.value)
    }

    @Test
    fun unknownPreferenceFallsBackToSystemAuto() {
        val settings = AppSettings(InMemoryStore())
        settings.setLanguage("xx")
        I18n.init(testAssetLoader(), settings)
        assertTrue(I18n.isAuto())
        assertTrue(I18n.currentCode() in setOf("en", "ru"))
    }

    @Test
    fun stateFlowUpdatesOnLanguageChange() {
        val settings = AppSettings(InMemoryStore())
        I18n.init(testAssetLoader(), settings)
        I18n.setLanguage("en")
        assertEquals("en", I18n.state.value!!.code)
        I18n.setLanguage("ru")
        assertEquals("ru", I18n.state.value!!.code)
        assertEquals("ltr", I18n.direction())
    }
}
