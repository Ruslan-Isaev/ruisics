package ru.ruiscode.ics.shifts

import ru.ruiscode.ics.core.JsonParser
import ru.ruiscode.ics.core.JsonValue

/**
 * Хранение пользовательских шаблонов смен: JSON-список в настройках
 * приложения (отдельное хранилище, встроенные файлы не изменяются).
 * Формат совместим с встроенными шаблонами, поэтому каталог загружает
 * их одним и тем же кодом.
 */
object CustomTemplateStore {

    /** Читает список пользовательских шаблонов. */
    fun parse(json: String?): List<JsonValue> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            JsonParser.parse(json).arr("templates")?.items ?: emptyList()
        } catch (e: JsonParser.JsonException) {
            emptyList()
        }
    }

    /** Сериализует список в строку для сохранения в настройках. */
    fun toJson(templates: List<JsonValue>): String =
        "{\"templates\":[" + templates.joinToString(",") { it.toJson() } + "]}"

    /** Собирает JSON нового пользовательского шаблона. */
    fun buildTemplate(
        id: String,
        name: String,
        cycle: List<String>,
        workStart: String,
        workEnd: String,
    ): JsonValue {
        val nameKey = "shift.template.$id.name"
        val descKey = "shift.template.$id.description"
        return JsonValue.JsonObj(
            linkedMapOf(
                "schemaVersion" to JsonValue.JsonNum(1.0),
                "id" to JsonValue.JsonStr(id),
                "nameKey" to JsonValue.JsonStr(nameKey),
                "descriptionKey" to JsonValue.JsonStr(descKey),
                "cycle" to JsonValue.JsonArr(cycle.map { JsonValue.JsonStr(it) }),
                "dayTypes" to JsonValue.JsonObj(
                    linkedMapOf(
                        "work" to JsonValue.JsonObj(
                            linkedMapOf(
                                "nameKey" to JsonValue.JsonStr("shift.daytype.work.name"),
                                "startTime" to JsonValue.JsonStr(workStart),
                                "endTime" to JsonValue.JsonStr(workEnd),
                            )
                        ),
                        "rest" to JsonValue.JsonObj(
                            linkedMapOf(
                                "nameKey" to JsonValue.JsonStr("shift.daytype.rest.name"),
                                "startTime" to JsonValue.JsonNull,
                                "endTime" to JsonValue.JsonNull,
                            )
                        ),
                        "recovery" to JsonValue.JsonObj(
                            linkedMapOf(
                                "nameKey" to JsonValue.JsonStr("shift.daytype.recovery.name"),
                                "startTime" to JsonValue.JsonNull,
                                "endTime" to JsonValue.JsonNull,
                            )
                        ),
                    )
                ),
                "isCustom" to JsonValue.JsonBool(true),
                "translations" to JsonValue.JsonObj(
                    linkedMapOf(
                        "en" to JsonValue.JsonObj(
                            linkedMapOf(
                                "strings" to JsonValue.JsonObj(
                                    linkedMapOf(
                                        nameKey to JsonValue.JsonStr(name),
                                        descKey to JsonValue.JsonStr(name),
                                        "shift.daytype.work.name" to JsonValue.JsonStr("Work day"),
                                        "shift.daytype.rest.name" to JsonValue.JsonStr("Day off"),
                                        "shift.daytype.recovery.name" to JsonValue.JsonStr("Recovery day"),
                                    )
                                )
                            )
                        ),
                        "ru" to JsonValue.JsonObj(
                            linkedMapOf(
                                "strings" to JsonValue.JsonObj(
                                    linkedMapOf(
                                        nameKey to JsonValue.JsonStr(name),
                                        descKey to JsonValue.JsonStr(name),
                                        "shift.daytype.work.name" to JsonValue.JsonStr("Рабочий день"),
                                        "shift.daytype.rest.name" to JsonValue.JsonStr("Выходной"),
                                        "shift.daytype.recovery.name" to JsonValue.JsonStr("Отсыпной"),
                                    )
                                )
                            )
                        ),
                    )
                ),
            )
        )
    }
}
