package ru.ruiscode.ics.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.ruiscode.ics.calendar.CalendarData
import ru.ruiscode.ics.calendar.CalendarInfo
import ru.ruiscode.ics.settings.AppSettings

/** Экраны приложения; навигация без отдельной библиотеки. */
sealed interface Screen {
    data object Home : Screen
    data object Ics : Screen
    data object Events : Screen
    data object Holidays : Screen
    data class HolidayDetail(val packId: String) : Screen
    data object Shifts : Screen
    data object Generator : Screen
    data object Settings : Screen
    data object About : Screen
}

@Composable
fun AppNav() {
    val context = LocalContext.current
    val settings = AppSettings.instance
    var backStack by remember { mutableStateOf(listOf<Screen>(Screen.Home)) }
    var permissionsGranted by remember { mutableStateOf(hasCalendarPermissions(context)) }
    var calendars by remember { mutableStateOf<List<CalendarInfo>>(emptyList()) }
    var selectedCalendarId by remember { mutableStateOf(settings.selectedCalendarId.value) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionsGranted = result.values.all { it }
    }

    fun requestPermissions() {
        permissionLauncher.launch(
            arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        )
    }

    LaunchedEffect(Unit) {
        if (!permissionsGranted) requestPermissions()
    }

    // Загружаем список календарей после получения разрешения.
    LaunchedEffect(permissionsGranted) {
        if (permissionsGranted && calendars.isEmpty()) {
            val loaded = withContext(Dispatchers.IO) { CalendarData.getWritableCalendars(context) }
            calendars = loaded
            val saved = settings.selectedCalendarId.value
            selectedCalendarId = if (loaded.any { it.id == saved }) saved else loaded.firstOrNull()?.id
            selectedCalendarId?.let { settings.setSelectedCalendar(it) }
        }
    }

    BackHandler(enabled = backStack.size > 1) { backStack = backStack.dropLast(1) }
    fun push(screen: Screen) { backStack = backStack + screen }
    fun pop() { backStack = backStack.dropLast(1) }

    val env = CalendarEnv(
        calendars = calendars,
        selectedCalendarId = selectedCalendarId,
        permissionsGranted = permissionsGranted,
        onSelectCalendar = { id ->
            selectedCalendarId = id
            settings.setSelectedCalendar(id)
        },
        onRequestPermissions = { requestPermissions() },
    )

    when (val screen = backStack.last()) {
        Screen.Home -> HomeScreen(onOpen = ::push)
        Screen.Ics -> IcsScreen(env, onBack = ::pop)
        Screen.Events -> EventsScreen(env, onBack = ::pop)
        Screen.Holidays -> HolidaysScreen(onBack = ::pop, onOpenPack = { push(Screen.HolidayDetail(it)) })
        is Screen.HolidayDetail -> HolidayDetailScreen(screen.packId, env, onBack = ::pop)
        Screen.Shifts -> ShiftsScreen(env, onBack = ::pop)
        Screen.Generator -> GeneratorScreen(env, onBack = ::pop)
        Screen.Settings -> SettingsScreen(onBack = ::pop)
        Screen.About -> AboutScreen(onBack = ::pop)
    }
}
