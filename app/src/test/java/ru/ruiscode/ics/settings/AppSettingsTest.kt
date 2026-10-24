package ru.ruiscode.ics.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Проверка сохранения настроек (InMemoryStore) и их восстановления. */
class AppSettingsTest {

    @Test
    fun persistsLanguage() {
        val store = InMemoryStore()
        val settings = AppSettings(store)
        assertEquals("auto", settings.language.value)
        settings.setLanguage("ru")
        assertEquals("ru", settings.language.value)

        // Новый экземпляр на том же хранилище видит сохранённое значение.
        val reloaded = AppSettings(store)
        assertEquals("ru", reloaded.language.value)
    }

    @Test
    fun persistsTheme() {
        val store = InMemoryStore()
        val settings = AppSettings(store)
        assertEquals(ThemeMode.SYSTEM, settings.theme.value)
        settings.setTheme(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, AppSettings(store).theme.value)
        settings.setTheme(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, AppSettings(store).theme.value)
    }

    @Test
    fun persistsEnabledPacks() {
        val store = InMemoryStore()
        val settings = AppSettings(store)
        assertEquals(emptySet<String>(), settings.enabledPackIds.value)
        settings.setPackEnabled("world", true)
        settings.setPackEnabled("russia", true)
        settings.setPackEnabled("world", false)
        assertEquals(setOf("russia"), AppSettings(store).enabledPackIds.value)
    }

    @Test
    fun persistsSelectedCalendar() {
        val store = InMemoryStore()
        val settings = AppSettings(store)
        assertNull(settings.selectedCalendarId.value)
        settings.setSelectedCalendar(42L)
        assertEquals(42L, AppSettings(store).selectedCalendarId.value)
    }

    @Test
    fun persistsCustomShiftTemplates() {
        val store = InMemoryStore()
        val settings = AppSettings(store)
        assertNull(settings.customShiftTemplates.value)
        settings.setCustomShiftTemplates("""{"templates":[]}""")
        assertEquals("""{"templates":[]}""", AppSettings(store).customShiftTemplates.value)
        settings.setCustomShiftTemplates(null)
        assertNull(AppSettings(store).customShiftTemplates.value)
    }
}
