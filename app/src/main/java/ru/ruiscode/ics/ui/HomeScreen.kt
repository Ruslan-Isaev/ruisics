package ru.ruiscode.ics.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.ruiscode.ics.core.ExternalLinks
import ru.ruiscode.ics.core.OpenResult

/**
 * Главный экран: функции сгруппированы по смыслу, опасные операции
 * (удаление) визуально отделены.
 */
@Composable
fun HomeScreen(onOpen: (Screen) -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    var donateError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = s.get("app.name"),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = s.get("app.tagline"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        SectionHeader(s.get("home.section.calendar"))
        MenuCard(
            title = s.get("menu.ics"),
            subtitle = s.get("menu.ics.desc"),
            icon = Icons.Filled.DateRange,
            onClick = { onOpen(Screen.Ics) },
        )
        Spacer(Modifier.height(8.dp))
        MenuCard(
            title = s.get("menu.events"),
            subtitle = s.get("menu.events.desc"),
            icon = Icons.Filled.Delete,
            onClick = { onOpen(Screen.Events) },
            danger = true,
        )

        SectionHeader(s.get("home.section.tools"))
        MenuCard(
            title = s.get("menu.holidays"),
            subtitle = s.get("menu.holidays.desc"),
            icon = Icons.Filled.DateRange,
            onClick = { onOpen(Screen.Holidays) },
        )
        Spacer(Modifier.height(8.dp))
        MenuCard(
            title = s.get("menu.shifts"),
            subtitle = s.get("menu.shifts.desc"),
            icon = Icons.Filled.DateRange,
            onClick = { onOpen(Screen.Shifts) },
        )
        Spacer(Modifier.height(8.dp))
        MenuCard(
            title = s.get("menu.generator"),
            subtitle = s.get("menu.generator.desc"),
            icon = Icons.Filled.DateRange,
            onClick = { onOpen(Screen.Generator) },
        )

        SectionHeader(s.get("home.section.app"))
        MenuCard(
            title = s.get("menu.settings"),
            subtitle = s.get("settings.storage.text"),
            icon = Icons.Filled.Settings,
            onClick = { onOpen(Screen.Settings) },
        )
        Spacer(Modifier.height(8.dp))
        MenuCard(
            title = s.get("menu.about"),
            subtitle = s.get("about.version"),
            icon = Icons.Filled.Info,
            onClick = { onOpen(Screen.About) },
        )
        Spacer(Modifier.height(8.dp))
        MenuCard(
            title = s.get("menu.donate"),
            subtitle = s.get("menu.donate.desc"),
            icon = Icons.Filled.Favorite,
            onClick = {
                when (val result = ExternalLinks.open(context, ExternalLinks.DONATE_URL)) {
                    OpenResult.Opened -> donateError = null
                    is OpenResult.Failed -> donateError = when (result.reason) {
                        OpenResult.Reason.NO_BROWSER -> s.get("error.no_browser")
                        OpenResult.Reason.ERROR -> s.format("error.open_link", ExternalLinks.DONATE_URL)
                    }
                }
            },
        )
        ErrorText(donateError)
        Spacer(Modifier.height(24.dp))
    }
}
