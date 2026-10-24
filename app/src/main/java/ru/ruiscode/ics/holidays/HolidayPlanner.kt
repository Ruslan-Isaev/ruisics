package ru.ruiscode.ics.holidays

import ru.ruiscode.ics.calendar.CalEventSpec
import ru.ruiscode.ics.core.Uids
import ru.ruiscode.ics.i18n.StringResolver
import java.time.ZoneOffset

/**
 * Связывает наборы праздников с системным календарём: раскладывает
 * праздники набора на годы и формирует спецификации all-day событий.
 */
object HolidayPlanner {

    /** Все вхождения праздников набора за год (праздники с пропуском года исключены). */
    fun occurrencesForYear(pack: HolidayPack, year: Int): List<HolidayOccurrence> {
        val resolution = HolidayDateCalculator.resolve(pack, year)
        return pack.holidays.mapNotNull { holiday ->
            val date = resolution.dates[holiday.id] ?: return@mapNotNull null
            HolidayOccurrence(holiday, year, date, holiday.durationDays)
        }
    }

    /**
     * Спецификации all-day событий для импорта выбранных праздников.
     * Название и описание берутся из переводов набора в активном языке.
     */
    fun toSpecs(
        pack: HolidayPack,
        year: Int,
        holidayIds: Set<String>,
        globalResolver: StringResolver,
    ): List<CalEventSpec> {
        val resolver = pack.resolver(globalResolver)
        val resolution = HolidayDateCalculator.resolve(pack, year)
        return pack.holidays
            .filter { it.id in holidayIds }
            .mapNotNull { holiday ->
                val start = resolution.dates[holiday.id] ?: return@mapNotNull null
                CalEventSpec(
                    uid = Uids.holiday(pack.meta.id, holiday.id, year),
                    title = resolver.get(holiday.nameKey),
                    description = resolver.get(holiday.descriptionKey),
                    location = "",
                    allDay = true,
                    startMillis = start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                    endMillis = start.plusDays(holiday.durationDays.toLong())
                        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                    timeZoneId = "UTC",
                )
            }
    }
}
