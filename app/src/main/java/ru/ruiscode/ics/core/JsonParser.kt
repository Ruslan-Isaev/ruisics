package ru.ruiscode.ics.core

/**
 * Минимальный JSON-парсер без внешних зависимостей.
 *
 * Используется для чтения встроенных JSON-ресурсов приложения (локали,
 * наборы праздников, шаблоны смен). Поддерживает объекты, массивы, строки
 * с экранированием и \uXXXX, числа, true/false/null. При повреждённом входе
 * бросает [JsonException] с позицией ошибки — вызывающий код решает,
 * пропустить ресурс или сообщить об ошибке.
 */
object JsonParser {

    class JsonException(message: String) : Exception(message)

    fun parse(text: String): JsonValue {
        val reader = Reader(text.removePrefix("\uFEFF")) // BOM
        reader.skipWhitespace()
        val value = reader.parseValue()
        reader.skipWhitespace()
        if (!reader.atEnd()) {
            throw JsonException("Unexpected content at position ${reader.pos}")
        }
        return value
    }

    private class Reader(private val s: String) {
        var pos = 0

        fun atEnd(): Boolean = pos >= s.length

        fun skipWhitespace() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        fun expect(c: Char) {
            skipWhitespace()
            if (pos >= s.length || s[pos] != c) {
                throw JsonException("Expected '$c' at position $pos")
            }
            pos++
        }

        fun parseValue(): JsonValue {
            skipWhitespace()
            if (atEnd()) throw JsonException("Unexpected end of input at position $pos")
            return when (s[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> JsonValue.JsonStr(parseString())
                't' -> { expectWord("true"); JsonValue.JsonBool(true) }
                'f' -> { expectWord("false"); JsonValue.JsonBool(false) }
                'n' -> { expectWord("null"); JsonValue.JsonNull }
                else -> parseNumber()
            }
        }

        private fun expectWord(word: String) {
            if (!s.startsWith(word, pos)) {
                throw JsonException("Invalid literal at position $pos")
            }
            pos += word.length
        }

        private fun parseObject(): JsonValue.JsonObj {
            pos++ // '{'
            val fields = LinkedHashMap<String, JsonValue>()
            skipWhitespace()
            if (peekIs('}')) { pos++; return JsonValue.JsonObj(fields) }
            while (true) {
                skipWhitespace()
                if (pos >= s.length || s[pos] != '"') {
                    throw JsonException("Expected string key at position $pos")
                }
                val key = parseString()
                expect(':')
                fields[key] = parseValue()
                skipWhitespace()
                if (peekIs('}')) { pos++; return JsonValue.JsonObj(fields) }
                expect(',')
            }
        }

        private fun parseArray(): JsonValue.JsonArr {
            pos++ // '['
            val items = mutableListOf<JsonValue>()
            skipWhitespace()
            if (peekIs(']')) { pos++; return JsonValue.JsonArr(items) }
            while (true) {
                items.add(parseValue())
                skipWhitespace()
                if (peekIs(']')) { pos++; return JsonValue.JsonArr(items) }
                expect(',')
            }
        }

        private fun peekIs(c: Char): Boolean {
            skipWhitespace()
            return pos < s.length && s[pos] == c
        }

        private fun parseString(): String {
            pos++ // '"'
            val sb = StringBuilder()
            while (true) {
                if (pos >= s.length) throw JsonException("Unterminated string at position $pos")
                when (val c = s[pos]) {
                    '"' -> { pos++; return sb.toString() }
                    '\\' -> {
                        pos++
                        if (pos >= s.length) throw JsonException("Unterminated escape at position $pos")
                        when (val e = s[pos]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 >= s.length) {
                                    throw JsonException("Unterminated \\u escape at position $pos")
                                }
                                val hex = s.substring(pos + 1, pos + 5)
                                val code = hex.toIntOrNull(16)
                                    ?: throw JsonException("Invalid \\u escape at position $pos")
                                sb.append(code.toChar())
                                pos += 4
                            }
                            else -> throw JsonException("Invalid escape '\\$e' at position $pos")
                        }
                        pos++
                    }
                    else -> {
                        if (c.code < 0x20) {
                            throw JsonException("Unescaped control character at position $pos")
                        }
                        sb.append(c)
                        pos++
                    }
                }
            }
        }

        private fun parseNumber(): JsonValue {
            val start = pos
            if (pos < s.length && s[pos] == '-') pos++
            if (pos >= s.length) throw JsonException("Invalid number at position $start")
            if (s[pos] == '0') {
                pos++
            } else if (s[pos] in '1'..'9') {
                while (pos < s.length && s[pos] in '0'..'9') pos++
            } else {
                throw JsonException("Invalid number at position $start")
            }
            if (pos < s.length && s[pos] == '.') {
                pos++
                if (pos >= s.length || s[pos] !in '0'..'9') {
                    throw JsonException("Invalid number at position $start")
                }
                while (pos < s.length && s[pos] in '0'..'9') pos++
            }
            if (pos < s.length && (s[pos] == 'e' || s[pos] == 'E')) {
                pos++
                if (pos < s.length && (s[pos] == '+' || s[pos] == '-')) pos++
                if (pos >= s.length || s[pos] !in '0'..'9') {
                    throw JsonException("Invalid number at position $start")
                }
                while (pos < s.length && s[pos] in '0'..'9') pos++
            }
            val literal = s.substring(start, pos)
            val double = literal.toDoubleOrNull()
                ?: throw JsonException("Invalid number at position $start")
            return JsonValue.JsonNum(double)
        }
    }
}

sealed class JsonValue {
    object JsonNull : JsonValue()
    data class JsonBool(val value: Boolean) : JsonValue()
    data class JsonNum(val value: Double) : JsonValue()
    data class JsonStr(val value: String) : JsonValue()
    data class JsonArr(val items: List<JsonValue>) : JsonValue()
    data class JsonObj(val fields: Map<String, JsonValue>) : JsonValue()

    fun asString(): String? = (this as? JsonStr)?.value
    fun asBool(): Boolean? = (this as? JsonBool)?.value
    fun asObject(): JsonObj? = this as? JsonObj
    fun asArray(): JsonArr? = this as? JsonArr

    /** Целое число; дробные значения возвращают null. */
    fun asInt(): Int? {
        val d = (this as? JsonNum)?.value ?: return null
        if (d != Math.floor(d) || d < Int.MIN_VALUE || d > Int.MAX_VALUE) return null
        return d.toInt()
    }

    fun get(key: String): JsonValue? = asObject()?.fields?.get(key)

    fun str(key: String): String? = get(key)?.asString()
    fun bool(key: String): Boolean? = get(key)?.asBool()
    fun int(key: String): Int? = get(key)?.asInt()
    fun obj(key: String): JsonObj? = get(key)?.asObject()
    fun arr(key: String): JsonArr? = get(key)?.asArray()

    fun asStringList(): List<String>? = asArray()?.items?.mapNotNull { it.asString() }
        ?.takeIf { it.size == asArray()!!.items.size }

    /** Сериализация обратно в JSON (для сохранения пользовательских данных). */
    fun toJson(): String = when (this) {
        is JsonNull -> "null"
        is JsonBool -> value.toString()
        is JsonNum -> value.toString()
        is JsonStr -> "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r") + "\""
        is JsonArr -> "[" + items.joinToString(",") { it.toJson() } + "]"
        is JsonObj -> "{" + fields.entries.joinToString(",") { (k, v) -> "\"$k\":" + v.toJson() } + "}"
    }
}
