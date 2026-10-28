package ru.ruiscode.ics.i18n

import ru.ruiscode.ics.core.JsonValue
import java.text.DateFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date
import java.util.Locale

/**
 * Разрешение строк по ключам с резервной цепочкой слоёв.
 *
 * Слои проверяются по порядку (первое совпадение побеждает): обычно это
 * [активный язык, английский]. Для переводов наборов праздников и шаблонов
 * смен поверх глобальных слоёв добавляются собственные переводы
 * (см. [withLayers]).
 *
 * Отсутствующий ключ возвращает безопасное отображение `[key]` и сообщает
 * о проблеме через [warn] — приложение при этом не падает.
 */
class StringResolver(
    val code: String,
    private val layers: List<Map<String, JsonValue>>,
    private val warn: (String) -> Unit = {},
) {
    val locale: Locale = localeFor(code)

    /** Добавляет слои с более высоким приоритетом поверх текущих. */
    fun withLayers(prepend: List<Map<String, JsonValue>>, logger: (String) -> Unit = warn): StringResolver =
        StringResolver(code, prepend + layers, logger)

    /** Возвращает строку по ключу; при отсутствии — безопасное отображение ключа. */
    fun get(key: String): String = lookup(key) ?: safeDisplay(key)

    /** Возвращает перевод ключа или null, если он отсутствует во всех слоях. */
    fun lookup(key: String): String? {
        for (layer in layers) {
            when (val value = layer[key]) {
                is JsonValue.JsonStr -> return value.value
                is JsonValue.JsonNum -> return formatNumber(value.value)
                is JsonValue.JsonObj -> return value.fields["other"]?.asString()
                    ?: value.fields.values.firstOrNull()?.asString()
                else -> {}
            }
        }
        return null
    }

    /** Подстановка именованных параметров: `format("a.b", mapOf("count" to 3))` для `"{count}"`. */
    fun format(key: String, params: Map<String, Any?>): String =
        substitute(get(key), params)

    /** Подстановка именованных параметров парами: `format("a.b", "count" to 3)`. */
    fun format(key: String, vararg named: Pair<String, Any?>): String =
        format(key, named.toMap())

    /** Подстановка позиционных параметров: `format("a.b", x, y)` для `"{0}"`, `"{1}"`. */
    fun format(key: String, vararg positional: Any?): String =
        format(key, positional.mapIndexed { i, v -> i.toString() to v }.toMap())

    /**
     * Склоняемая форма: значение ключа `key` — объект с формами
     * `one`/`few`/`many`/`other` по правилам CLDR для активного языка.
     * В строки автоматически подставляются `{count}` и `{n}`.
     */
    fun plural(key: String, count: Int, params: Map<String, Any?> = emptyMap()): String {
        val form = pluralForm(count)
        val text = lookupPlural(key, form) ?: return safeDisplay("$key.$form")
        return substitute(text, params + mapOf("count" to count, "n" to count))
    }

    /**
     * Ищет форму множественного числа: значение ключа — объект с формами
     * `one`/`few`/`many`/`other` (или простая строка). Форма выбирается
     * внутри первого слоя, где ключ найден; резерв: other → one → первая форма.
     */
    private fun lookupPlural(key: String, form: String): String? {
        for (layer in layers) {
            when (val value = layer[key]) {
                is JsonValue.JsonObj -> {
                    value.fields[form]?.asString()?.let { return it }
                    value.fields["other"]?.asString()?.let { return it }
                    value.fields["one"]?.asString()?.let { return it }
                    value.fields.values.firstOrNull()?.asString()?.let { return it }
                }
                is JsonValue.JsonStr -> return value.value
                else -> {}
            }
        }
        return null
    }

    fun plural(key: String, count: Int, vararg positional: Any?): String =
        plural(key, count, positional.mapIndexed { i, v -> i.toString() to v }.toMap())

    fun formatDate(date: LocalDate): String =
        DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant()))

    fun formatDateTime(dateTime: ZonedDateTime): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date.from(dateTime.toInstant()))

    fun formatTime(time: LocalTime): String =
        DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(Date.from(time.atDate(LocalDate.of(1970, 1, 1)).atZone(ZoneId.systemDefault()).toInstant()))

    fun formatNumber(number: Number): String =
        java.text.NumberFormat.getIntegerInstance(locale).format(number)

    /** Форма множественного числа по правилам CLDR для активного языка. */
    fun pluralForm(count: Int): String {
        val mod10 = count % 10
        val mod100 = count % 100
        return when {
            language() == "ru" -> when {
                mod10 == 1 && mod100 != 11 -> "one"
                mod10 in 2..4 && mod100 !in 12..14 -> "few"
                else -> "many"
            }
            else -> if (count == 1) "one" else "other"
        }
    }

    fun language(): String = code.substringBefore('-')

    /** Безопасное отображение отсутствующего ключа. */
    private fun safeDisplay(key: String): String {
        warn("Missing translation for key: $key")
        return "[$key]"
    }

    private fun substitute(template: String, params: Map<String, Any?>): String {
        val sb = StringBuilder(template.length)
        var i = 0
        while (i < template.length) {
            val c = template[i]
            if (c == '{') {
                if (i + 1 < template.length && template[i + 1] == '{') {
                    sb.append('{')
                    i += 2
                    continue
                }
                val end = template.indexOf('}', i)
                if (end < 0) {
                    sb.append(c)
                    i++
                    continue
                }
                val name = template.substring(i + 1, end)
                if (params.containsKey(name)) {
                    sb.append(formatValue(params[name]))
                } else {
                    sb.append('{').append(name).append('}')
                }
                i = end + 1
            } else if (c == '}' && i + 1 < template.length && template[i + 1] == '}') {
                sb.append('}')
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    private fun formatValue(value: Any?): String = when (value) {
        null -> ""
        is Number -> formatNumber(value)
        else -> value.toString()
    }

    companion object {
        private fun localeFor(code: String): Locale = try {
            Locale.forLanguageTag(code)
        } catch (e: Exception) {
            Locale(code.substringBefore('-'), "")
        }
    }
}
