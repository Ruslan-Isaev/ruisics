package ru.ruiscode.ics.core

import java.security.MessageDigest

/**
 * Устойчивые идентификаторы событий (UID).
 *
 * Праздники: UID привязан к набору, празднику и году — повторный импорт
 * одного и того же набора не создаёт дубликатов. Смены и генератор:
 * UID вычисляется из содержимого события — одинаковое содержимое при
 * повторном импорте распознаётся как дубликат, изменённое создаёт новое
 * событие. Хеш нужен только для идентификации, криптографические
 * свойства не требуются.
 */
object Uids {

    private const val DOMAIN = "ruiscode.ru"

    /** UID праздника: набор + праздник + год. */
    fun holiday(packId: String, holidayId: String, year: Int): String =
        "ruisics-holiday-$packId-$holidayId-$year@$DOMAIN"

    /** UID по содержимому события (префикс + части, от которых зависит событие). */
    fun content(prefix: String, vararg parts: String): String {
        val digest = MessageDigest.getInstance("MD5")
            .digest(parts.joinToString("|").toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }.take(16)
        return "ruisics-$prefix-$hex@$DOMAIN"
    }
}
