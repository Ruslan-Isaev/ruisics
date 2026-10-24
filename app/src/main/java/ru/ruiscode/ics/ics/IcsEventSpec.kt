package ru.ruiscode.ics.ics

import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Время события для генератора ICS.
 */
sealed class IcsTime {
    /** Событие с временем и часовым поясом; [rrule] — правило повторения RFC 5545. */
    data class Timed(
        val start: ZonedDateTime,
        val end: ZonedDateTime,
        val rrule: String? = null,
    ) : IcsTime()

    /** Событие на весь день; [endDate] — исключительная граница. */
    data class AllDay(
        val startDate: LocalDate,
        val endDate: LocalDate,
    ) : IcsTime()
}

/**
 * Общая модель события для генерации ICS: используется планировщиком смен,
 * наборами праздников и генератором ICS (общий механизм формирования).
 */
data class IcsEventSpec(
    val uid: String,
    val title: String,
    val description: String = "",
    val location: String = "",
    val time: IcsTime,
)
