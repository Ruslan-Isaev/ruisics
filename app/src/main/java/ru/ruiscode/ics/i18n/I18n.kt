package ru.ruiscode.ics.i18n

import ru.ruiscode.ics.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * Точка входа модульной системы языков.
 *
 * - обнаруживает языковые модули по манифесту (см. [LocaleCatalog]);
 * - кэширует их в памяти (загрузка происходит один раз);
 * - хранит активный язык; `"auto"` означает выбор языка системы;
 * - публикует [state] — реактивный источник для Compose: при смене языка
 *   интерфейс перерисовывается автоматически.
 */
object I18n {

    /** Активное состояние локализации. */
    data class LocaleState(
        val code: String,
        val isAuto: Boolean,
        val direction: String,
        val resolver: StringResolver,
        val locales: List<LocaleMeta>,
    )

    private val _state = MutableStateFlow<LocaleState?>(null)
    val state: StateFlow<LocaleState?> = _state

    private var catalog: List<LocalePack> = emptyList()
    private var settings: AppSettings? = null
    private var warn: (String) -> Unit = {}

    /** Проблемы, найденные при загрузке языковых модулей (для диагностики). */
    var problems: List<String> = emptyList()
        private set

    fun init(load: AssetLoader, settings: AppSettings, warn: (String) -> Unit = {}) {
        this.settings = settings
        this.warn = warn
        val found = mutableListOf<String>()
        catalog = LocaleCatalog.load(load, found)
        problems = found
        refresh()
    }

    /** Пересчитывает резолвер по текущей настройке языка. */
    fun refresh() {
        val pref = settings?.language?.value ?: "auto"
        val isAuto = pref == "auto" || catalog.none { it.meta.code == pref }
        val code = if (isAuto) detectSystemCode() else pref
        val layers = buildList {
            catalog.firstOrNull { it.meta.code == code }?.let { add(it.strings) }
            if (code != "en") catalog.firstOrNull { it.meta.code == "en" }?.let { add(it.strings) }
        }
        val meta = catalog.firstOrNull { it.meta.code == code }?.meta
        _state.value = LocaleState(
            code = code,
            isAuto = isAuto,
            direction = meta?.direction ?: "ltr",
            resolver = StringResolver(code, layers, warn),
            locales = catalog.map { it.meta },
        )
    }

    /** Устанавливает язык: `"auto"` — язык системы, иначе код из манифеста. */
    fun setLanguage(code: String) {
        settings?.setLanguage(code)
        refresh()
    }

    /** Код языка системы, если он доступен; иначе английский. */
    fun detectSystemCode(): String {
        val sys = Locale.getDefault().language
        return catalog.firstOrNull { it.meta.code == sys }?.meta?.code
            ?: catalog.firstOrNull { it.meta.code == "en" }?.meta?.code
            ?: catalog.firstOrNull()?.meta?.code
            ?: "en"
    }

    fun resolverOrNull(): StringResolver? = _state.value?.resolver

    fun availableLocales(): List<LocaleMeta> = catalog.map { it.meta }

    fun currentCode(): String = _state.value?.code ?: "en"

    fun isAuto(): Boolean = _state.value?.isAuto ?: true

    fun direction(): String = _state.value?.direction ?: "ltr"
}
