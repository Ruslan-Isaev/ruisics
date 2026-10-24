package ru.ruiscode.ics.i18n

import ru.ruiscode.ics.core.JsonParser
import ru.ruiscode.ics.core.JsonValue

/** Загрузчик файла ресурса по пути; null, если файл недоступен. */
typealias AssetLoader = (path: String) -> String?

/** Метаданные языкового модуля (из манифеста локалей). */
data class LocaleMeta(
    val code: String,
    val name: String,
    val nativeName: String,
    val direction: String,
    val fileName: String,
)

/** Загруженный языковой модуль: метаданные + карта ключей. */
data class LocalePack(
    val meta: LocaleMeta,
    val strings: Map<String, JsonValue>,
)

/**
 * Обнаружение и загрузка языковых модулей из `assets/locales`.
 *
 * Ресурсы в assets не обнаруживаются автоматически, поэтому используется
 * явный манифест `locales/manifest.json` со списком файлов. Повреждённые
 * записи пропускаются и попадают в [problems] — приложение продолжает
 * работать с оставшимися языками.
 */
object LocaleCatalog {

    const val MANIFEST_PATH = "locales/manifest.json"
    const val SCHEMA_VERSION = 1

    private val CODE_REGEX = Regex("^[a-z]{2,3}(-[A-Za-z0-9]{2,8})*$")

    fun load(load: AssetLoader, problems: MutableList<String>? = null): List<LocalePack> {
        val manifestText = load(MANIFEST_PATH)
        if (manifestText == null) {
            problems?.add("Missing asset: $MANIFEST_PATH")
            return emptyList()
        }
        val manifest = try {
            JsonParser.parse(manifestText)
        } catch (e: JsonParser.JsonException) {
            problems?.add("Broken JSON in $MANIFEST_PATH: ${e.message}")
            return emptyList()
        }
        val entries = manifest.arr("locales")?.items
        if (entries == null) {
            problems?.add("$MANIFEST_PATH: missing 'locales' array")
            return emptyList()
        }

        val packs = mutableListOf<LocalePack>()
        val seenCodes = mutableSetOf<String>()
        for ((index, entry) in entries.withIndex()) {
            val code = entry.str("code")
            val fileName = entry.str("fileName")
            val where = "$MANIFEST_PATH: locales[$index]"
            when {
                code == null || !CODE_REGEX.matches(code) -> {
                    problems?.add("$where: invalid or missing 'code'")
                    continue
                }
                fileName == null -> {
                    problems?.add("$where: missing 'fileName'")
                    continue
                }
                code in seenCodes -> {
                    problems?.add("$where: duplicate locale code '$code'")
                    continue
                }
            }
            seenCodes.add(code)
            val text = load("locales/$fileName")
            if (text == null) {
                problems?.add("$where: file 'locales/$fileName' not found")
                continue
            }
            val parsed = try {
                JsonParser.parse(text)
            } catch (e: JsonParser.JsonException) {
                problems?.add("$where: broken JSON: ${e.message}")
                continue
            }
            val strings = parsed.obj("strings")?.fields
            if (strings == null) {
                problems?.add("$where: missing 'strings' object")
                continue
            }
            packs.add(
                LocalePack(
                    meta = LocaleMeta(
                        code = code,
                        name = entry.str("name") ?: code,
                        nativeName = entry.str("nativeName") ?: code,
                        direction = (entry.str("direction") ?: "ltr").lowercase().takeIf { it in setOf("ltr", "rtl") } ?: "ltr",
                        fileName = fileName,
                    ),
                    strings = strings,
                )
            )
        }
        return packs
    }
}
