package ru.ruiscode.ics.holidays

import java.time.LocalDate

/** Результат вычисления дат праздников набора за год. */
data class DateResolution(
    /** id праздника → дата; null означает «праздник пропущен в этом году» (исключение). */
    val dates: Map<String, LocalDate?>,
    /** id праздников, дату которых вычислить не удалось (неподдерживаемое или неверное правило). */
    val unresolved: List<String>,
)

/**
 * Вычисление дат праздников по правилам [DateRule].
 *
 * Неизвестные алгоритмы не подменяются приблизительными вычислениями —
 * праздник попадает в [DateResolution.unresolved], а UI сообщает об ошибке.
 * Новые вычислители добавляются в [compute] без изменения формата наборов.
 */
object HolidayDateCalculator {

    const val CALENDAR_GREGORIAN_EASTER = "gregorian_easter"

    /**
     * Вычисляет даты всех праздников набора за [year].
     * Правила со смещением резолвятся итеративно — поддерживаются цепочки
     * зависимостей; циклические зависимости попадают в unresolved.
     */
    fun resolve(pack: HolidayPack, year: Int): DateResolution {
        val dates = mutableMapOf<String, LocalDate?>()
        val pending = pack.holidays.toMutableList()
        var progressed = true
        while (progressed) {
            progressed = false
            val iterator = pending.iterator()
            while (iterator.hasNext()) {
                val holiday = iterator.next()
                val rule = holiday.exceptions[year] ?: holiday.dateRule
                when {
                    // Праздник явно пропущен в этом году.
                    rule.type == "skip" -> {
                        dates[holiday.id] = null
                        iterator.remove()
                        progressed = true
                    }
                    // Смещение от уже вычисленной даты; пропуск зависимости распространяется.
                    rule.type == "offset" && rule.relativeTo != null && rule.relativeTo in dates -> {
                        val base = dates[rule.relativeTo]
                        val offset = rule.offsetDays
                        dates[holiday.id] = if (base != null && offset != null) base.plusDays(offset.toLong()) else null
                        iterator.remove()
                        progressed = true
                    }
                    else -> {
                        val date = compute(rule, year, dates)
                        if (date != null) {
                            dates[holiday.id] = date
                            iterator.remove()
                            progressed = true
                        }
                    }
                }
            }
        }
        return DateResolution(dates, pending.map { it.id })
    }

    /** Вычисляет дату по правилу; null — правило невычислимо в текущий момент. */
    fun compute(rule: DateRule, year: Int, dependencies: Map<String, LocalDate?>): LocalDate? {
        return try {
            when (rule.type) {
                "fixed" -> {
                    if (rule.year != null && rule.year != year) return null
                    LocalDate.of(year, requireMonth(rule), requireDay(rule))
                }
                "nth_weekday" -> {
                    val month = requireMonth(rule)
                    val weekday = rule.weekday?.takeIf { it in 1..7 } ?: return null
                    val n = rule.n ?: return null
                    val length = LocalDate.of(year, month, 1).lengthOfMonth()
                    val candidates = (1..length)
                        .map { LocalDate.of(year, month, it) }
                        .filter { it.dayOfWeek.value == weekday }
                    when {
                        n in 1..5 && candidates.size >= n -> candidates[n - 1]
                        n == -1 && candidates.isNotEmpty() -> candidates.last()
                        else -> null
                    }
                }
                "offset" -> {
                    val relativeTo = rule.relativeTo ?: return null
                    val offset = rule.offsetDays ?: return null
                    if (relativeTo !in dependencies) return null // зависимость ещё не вычислена
                    dependencies[relativeTo]?.plusDays(offset.toLong()) // null = пропуск
                }
                "calculated" -> when (rule.calendar) {
                    CALENDAR_GREGORIAN_EASTER -> gregorianEaster(year)
                    else -> null
                }
                else -> null
            }
        } catch (e: Exception) {
            null // неверная дата (например, 30 февраля)
        }
    }

    fun isSupported(rule: DateRule): Boolean = when (rule.type) {
        "fixed" -> rule.month != null && rule.day != null
        "nth_weekday" -> rule.month != null && rule.weekday in 1..7 && rule.n != null
        "offset" -> rule.relativeTo != null && rule.offsetDays != null
        "calculated" -> rule.calendar == CALENDAR_GREGORIAN_EASTER
        "skip" -> true
        else -> false
    }

    private fun requireMonth(rule: DateRule): Int = rule.month?.takeIf { it in 1..12 }
        ?: throw IllegalArgumentException("invalid month")

    private fun requireDay(rule: DateRule): Int = rule.day?.takeIf { it in 1..31 }
        ?: throw IllegalArgumentException("invalid day")

    /**
     * Пасха по григорианскому календарю (анонимный григорианский алгоритм
     * Миуса/Джонса/Батчера). Проверочные значения: 2024 → 31 марта,
     * 2025 → 20 апреля, 2026 → 5 апреля, 2027 → 28 марта.
     */
    fun gregorianEaster(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day)
    }
}
