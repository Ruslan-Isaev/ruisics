package ru.ruiscode.ics.calendar

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import ru.ruiscode.ics.ics.ExportEvent
import ru.ruiscode.ics.ics.ParsedEvent

data class CalendarInfo(val id: Long, val name: String, val account: String)

/** Событие, найденное по критерию. */
data class EventMatch(val id: Long, val title: String, val startMillis: Long)

/** Спецификация события для записи в системный календарь. */
data class CalEventSpec(
    val uid: String,
    val title: String,
    val description: String = "",
    val location: String = "",
    val allDay: Boolean,
    val startMillis: Long,
    val endMillis: Long,
    val timeZoneId: String,
)

/** Проверяет, встречается ли [keyword] в названии, описании или месте события (без учёта регистра). */
fun eventContainsKeyword(title: String, description: String, location: String, keyword: String): Boolean =
    title.contains(keyword, ignoreCase = true) ||
        description.contains(keyword, ignoreCase = true) ||
        location.contains(keyword, ignoreCase = true)

/** Работа с календарём устройства через CalendarContract. */
object CalendarData {

    /** Календари, в которые можно писать (уровень доступа Contributor и выше). */
    fun getWritableCalendars(context: Context): List<CalendarInfo> {
        val result = mutableListOf<CalendarInfo>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
        )
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null,
            null,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME + " ASC",
        )?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
            val nameIdx = c.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            val accountIdx = c.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
            val accessIdx = c.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)
            while (c.moveToNext()) {
                if (c.getInt(accessIdx) < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) continue
                result.add(
                    CalendarInfo(
                        id = c.getLong(idIdx),
                        name = c.getString(nameIdx) ?: "Календарь",
                        account = c.getString(accountIdx) ?: "",
                    )
                )
            }
        }
        return result
    }

    /** Добавляет событие в календарь. Возвращает id события или -1 при ошибке. */
    fun insertEvent(resolver: ContentResolver, calendarId: Long, e: ParsedEvent): Long =
        insertSpec(
            resolver,
            calendarId,
            CalEventSpec(
                uid = e.uid,
                title = e.summary,
                description = e.description,
                location = e.location,
                allDay = e.allDay,
                startMillis = e.startMillis,
                endMillis = e.endMillis,
                timeZoneId = e.timeZoneId,
            ),
        )

    /**
     * Добавляет событие по спецификации. В UID_2445 записывается устойчивый
     * идентификатор — по нему импорт наборов праздников и смен распознаёт
     * уже существующие события (без опоры только на название и дату).
     */
    fun insertSpec(resolver: ContentResolver, calendarId: Long, e: CalEventSpec): Long {
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, e.title)
            put(CalendarContract.Events.DESCRIPTION, e.description)
            put(CalendarContract.Events.EVENT_LOCATION, e.location)
            put(CalendarContract.Events.DTSTART, e.startMillis)
            put(CalendarContract.Events.DTEND, e.endMillis)
            put(CalendarContract.Events.ALL_DAY, if (e.allDay) 1 else 0)
            put(CalendarContract.Events.EVENT_TIMEZONE, e.timeZoneId)
            put(CalendarContract.Events.UID_2445, e.uid)
        }
        val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: return -1L
        return ContentUris.parseId(uri)
    }

    /** Проверка на дубликат по UID_2445. */
    fun hasEventWithUid(resolver: ContentResolver, uid: String): Boolean =
        resolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID),
            "${CalendarContract.Events.UID_2445}=?",
            arrayOf(uid),
            null,
        )?.use { it.count > 0 } ?: false

    /** Проверка на дубликат: то же название и то же время в этом календаре. */
    fun hasSameEvent(resolver: ContentResolver, calendarId: Long, e: ParsedEvent): Boolean =
        hasSameSpec(
            resolver,
            calendarId,
            CalEventSpec(e.uid, e.summary, e.description, e.location, e.allDay, e.startMillis, e.endMillis, e.timeZoneId),
        )

    /** Проверка на дубликат спецификации: название и время в этом календаре. */
    fun hasSameSpec(resolver: ContentResolver, calendarId: Long, e: CalEventSpec): Boolean {
        val selection = "${CalendarContract.Events.CALENDAR_ID}=? AND " +
            "${CalendarContract.Events.TITLE}=? AND " +
            "${CalendarContract.Events.DTSTART}=? AND " +
            "${CalendarContract.Events.DTEND}=?"
        val args = arrayOf(
            calendarId.toString(),
            e.title,
            e.startMillis.toString(),
            e.endMillis.toString(),
        )
        resolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID),
            selection,
            args,
            null,
        )?.use { return it.count > 0 }
        return false
    }

    /**
     * События календаря, в названии, описании или месте которых встречается [keyword].
     * Поиск по таблице Events, поэтому повторяющаяся серия находится как одно событие.
     */
    fun findEventsByKeyword(resolver: ContentResolver, calendarId: Long, keyword: String): List<EventMatch> {
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DTSTART,
        )
        val result = mutableListOf<EventMatch>()
        resolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            "${CalendarContract.Events.CALENDAR_ID}=?",
            arrayOf(calendarId.toString()),
            CalendarContract.Events.DTSTART + " ASC",
        )?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(CalendarContract.Events._ID)
            val titleIdx = c.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
            val descIdx = c.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION)
            val locIdx = c.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)
            val startIdx = c.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
            while (c.moveToNext()) {
                val title = c.getString(titleIdx) ?: ""
                if (eventContainsKeyword(
                        title,
                        c.getString(descIdx) ?: "",
                        c.getString(locIdx) ?: "",
                        keyword,
                    )
                ) {
                    result.add(EventMatch(c.getLong(idIdx), title, c.getLong(startIdx)))
                }
            }
        }
        return result
    }

    /** События, начинающиеся в [fromMillis, toMillis) (таблица Events, серии — одной записью). */
    fun findEventsInRange(
        resolver: ContentResolver,
        calendarId: Long,
        fromMillis: Long,
        toMillis: Long,
    ): List<EventMatch> {
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
        )
        val result = mutableListOf<EventMatch>()
        resolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            "${CalendarContract.Events.CALENDAR_ID}=? AND " +
                "${CalendarContract.Events.DTSTART}>=? AND ${CalendarContract.Events.DTSTART}<?",
            arrayOf(calendarId.toString(), fromMillis.toString(), toMillis.toString()),
            CalendarContract.Events.DTSTART + " ASC",
        )?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(CalendarContract.Events._ID)
            val titleIdx = c.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
            val startIdx = c.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
            while (c.moveToNext()) {
                result.add(EventMatch(c.getLong(idIdx), c.getString(titleIdx) ?: "", c.getLong(startIdx)))
            }
        }
        return result
    }

    /** Число событий в календаре (таблица Events). */
    fun countEvents(resolver: ContentResolver, calendarId: Long): Int =
        resolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID),
            "${CalendarContract.Events.CALENDAR_ID}=?",
            arrayOf(calendarId.toString()),
            null,
        )?.use { it.count } ?: 0

    /** Id всех событий календаря. */
    fun getAllEventIds(resolver: ContentResolver, calendarId: Long): List<Long> {
        val result = mutableListOf<Long>()
        resolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID),
            "${CalendarContract.Events.CALENDAR_ID}=?",
            arrayOf(calendarId.toString()),
            null,
        )?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(CalendarContract.Events._ID)
            while (c.moveToNext()) result.add(c.getLong(idIdx))
        }
        return result
    }

    /** Удаляет события по id. Возвращает число удалённых событий. */
    fun deleteEvents(resolver: ContentResolver, eventIds: List<Long>): Int {
        if (eventIds.isEmpty()) return 0
        val placeholders = eventIds.joinToString(",") { "?" }
        return resolver.delete(
            CalendarContract.Events.CONTENT_URI,
            "${CalendarContract.Events._ID} IN ($placeholders)",
            eventIds.map { it.toString() }.toTypedArray(),
        )
    }

    /**
     * Все события (включая развёрнутые повторяющиеся) в диапазоне [fromMillis, toMillis),
     * по всем календарям.
     */
    fun getEvents(resolver: ContentResolver, fromMillis: Long, toMillis: Long): List<ExportEvent> {
        val projection = arrayOf(
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Events.UID_2445,
        )
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(fromMillis.toString())
            .appendPath(toMillis.toString())
            .build()
        val selection = "${CalendarContract.Instances.BEGIN} >= ? AND ${CalendarContract.Instances.BEGIN} < ?"
        val args = arrayOf(fromMillis.toString(), toMillis.toString())
        val result = mutableListOf<ExportEvent>()
        resolver.query(
            uri,
            projection,
            selection,
            args,
            CalendarContract.Instances.BEGIN + " ASC",
        )?.use { c ->
            val beginIdx = c.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            val endIdx = c.getColumnIndexOrThrow(CalendarContract.Instances.END)
            val titleIdx = c.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val descIdx = c.getColumnIndexOrThrow(CalendarContract.Instances.DESCRIPTION)
            val locIdx = c.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)
            val allDayIdx = c.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
            val eventIdIdx = c.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
            val uidIdx = c.getColumnIndexOrThrow(CalendarContract.Events.UID_2445)
            while (c.moveToNext()) {
                result.add(
                    ExportEvent(
                        uid = c.getString(uidIdx) ?: "event-${c.getLong(eventIdIdx)}-${c.getLong(beginIdx)}",
                        title = c.getString(titleIdx) ?: "",
                        description = c.getString(descIdx) ?: "",
                        location = c.getString(locIdx) ?: "",
                        allDay = c.getInt(allDayIdx) == 1,
                        startMillis = c.getLong(beginIdx),
                        endMillis = c.getLong(endIdx),
                    )
                )
            }
        }
        return result
    }
}
