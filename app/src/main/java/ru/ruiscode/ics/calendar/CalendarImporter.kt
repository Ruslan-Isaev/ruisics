package ru.ruiscode.ics.calendar

import android.content.ContentResolver

/**
 * Импорт набора спецификаций событий в системный календарь.
 *
 * Дедупликация по UID_2445 (устойчивая связь события с набором и
 * праздником/сменой), с резервной проверкой по названию и времени:
 * повторный импорт одного и того же набора не создаёт дубликатов.
 */
object CalendarImporter {

    data class ImportResult(
        val inserted: Int,
        val duplicates: Int,
        val failed: Int,
    )

    fun import(
        resolver: ContentResolver,
        calendarId: Long,
        specs: List<CalEventSpec>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ImportResult {
        var inserted = 0
        var duplicates = 0
        var failed = 0
        for ((index, spec) in specs.withIndex()) {
            when {
                CalendarData.hasEventWithUid(resolver, spec.uid) -> duplicates++
                CalendarData.hasSameSpec(resolver, calendarId, spec) -> duplicates++
                CalendarData.insertSpec(resolver, calendarId, spec) >= 0 -> inserted++
                else -> failed++
            }
            onProgress(index + 1, specs.size)
        }
        return ImportResult(inserted, duplicates, failed)
    }
}
