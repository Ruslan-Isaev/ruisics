package ru.ruiscode.ics.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import ru.ruiscode.ics.calendar.CalendarInfo

/**
 * Общее окружение экранов: выбранный календарь, разрешения и колбэки.
 * Загружается один раз на уровне навигации.
 */
class CalendarEnv(
    val calendars: List<CalendarInfo>,
    val selectedCalendarId: Long?,
    val permissionsGranted: Boolean,
    val onSelectCalendar: (Long) -> Unit,
    val onRequestPermissions: () -> Unit,
) {
    val selectedCalendar: CalendarInfo?
        get() = calendars.firstOrNull { it.id == selectedCalendarId }
}

fun hasCalendarPermissions(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
        PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
        PackageManager.PERMISSION_GRANTED
