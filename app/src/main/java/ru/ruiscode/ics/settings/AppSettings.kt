package ru.ruiscode.ics.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Простое хранилище «ключ-значение». Используется как точка вариации,
 * чтобы настройки можно было тестировать без Android-фреймворка
 * (реализации: [SharedPrefsStore] в приложении, [InMemoryStore] в тестах).
 */
interface KeyValueStore {
    fun getString(key: String, def: String?): String?
    fun putString(key: String, value: String)
    fun getLong(key: String, def: Long): Long
    fun putLong(key: String, value: Long)
}

class SharedPrefsStore(private val prefs: SharedPreferences) : KeyValueStore {
    override fun getString(key: String, def: String?): String? = prefs.getString(key, def)
    override fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
    override fun getLong(key: String, def: Long): Long = prefs.getLong(key, def)
    override fun putLong(key: String, value: Long) = prefs.edit().putLong(key, value).apply()
}

class InMemoryStore : KeyValueStore {
    private val strings = mutableMapOf<String, String>()
    private val longs = mutableMapOf<String, Long>()
    override fun getString(key: String, def: String?): String? = strings[key] ?: def
    override fun putString(key: String, value: String) { strings[key] = value }
    override fun getLong(key: String, def: Long): Long = longs[key] ?: def
    override fun putLong(key: String, value: Long) { longs[key] = value }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Настройки приложения. Хранятся в [KeyValueStore] отдельно от встроенных
 * файлов наборов: встроенные ресурсы никогда не изменяются. Реактивность
 * обеспечивается StateFlow — Compose-экраны обновляются при изменении.
 */
class AppSettings(private val store: KeyValueStore) {

    private val _language = MutableStateFlow(store.getString(KEY_LANGUAGE, "auto") ?: "auto")
    val language: StateFlow<String> = _language

    private val _theme = MutableStateFlow(parseTheme(store.getString(KEY_THEME, null)))
    val theme: StateFlow<ThemeMode> = _theme

    private val _selectedCalendarId = MutableStateFlow(store.getLong(KEY_CALENDAR_ID, -1L).takeIf { it >= 0 })
    val selectedCalendarId: StateFlow<Long?> = _selectedCalendarId

    /** Пользовательские шаблоны смен в JSON (схема — см. ShiftTemplateCatalog). */
    private val _customShiftTemplates = MutableStateFlow(
        store.getString(KEY_CUSTOM_SHIFT_TEMPLATES, null)?.takeIf { it.isNotBlank() }
    )
    val customShiftTemplates: StateFlow<String?> = _customShiftTemplates

    fun setLanguage(code: String) {
        _language.value = code
        store.putString(KEY_LANGUAGE, code)
    }

    fun setTheme(mode: ThemeMode) {
        _theme.value = mode
        store.putString(KEY_THEME, mode.name.lowercase())
    }

    fun setSelectedCalendar(id: Long) {
        _selectedCalendarId.value = id
        store.putLong(KEY_CALENDAR_ID, id)
    }

    fun setCustomShiftTemplates(json: String?) {
        _customShiftTemplates.value = json
        if (json == null) {
            store.putString(KEY_CUSTOM_SHIFT_TEMPLATES, "")
        } else {
            store.putString(KEY_CUSTOM_SHIFT_TEMPLATES, json)
        }
    }

    private fun parseTheme(raw: String?): ThemeMode = when (raw) {
        "light" -> ThemeMode.LIGHT
        "dark" -> ThemeMode.DARK
        else -> ThemeMode.SYSTEM
    }

    companion object {
        const val PREF_NAME = "ruis_ics_settings"
        const val KEY_LANGUAGE = "language"
        const val KEY_THEME = "theme"
        const val KEY_CALENDAR_ID = "selected_calendar_id"
        const val KEY_CUSTOM_SHIFT_TEMPLATES = "custom_shift_templates"

        lateinit var instance: AppSettings
            private set

        fun init(context: Context): AppSettings {
            val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            instance = AppSettings(SharedPrefsStore(prefs))
            return instance
        }
    }
}
