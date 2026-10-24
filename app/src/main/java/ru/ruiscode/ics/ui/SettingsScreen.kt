package ru.ruiscode.ics.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.ruiscode.ics.i18n.I18n
import ru.ruiscode.ics.settings.AppSettings
import ru.ruiscode.ics.settings.ThemeMode

/** Настройки: язык (включая авто-выбор языка системы) и тема оформления. */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val s = LocalStrings.current
    val settings = AppSettings.instance
    val language by settings.language.collectAsState()
    val theme by settings.theme.collectAsState()
    val locales = I18n.availableLocales()

    ScreenScaffold(title = s.get("menu.settings"), onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SubsectionTitle(s.get("settings.language"))
            Spacer(Modifier.height(4.dp))
            LanguageRow(
                label = s.get("settings.language.auto"),
                selected = language == "auto",
                onClick = { I18n.setLanguage("auto") },
            )
            locales.forEach { meta ->
                LanguageRow(
                    label = meta.nativeName,
                    selected = language == meta.code,
                    onClick = { I18n.setLanguage(meta.code) },
                )
            }

            Spacer(Modifier.height(16.dp))
            SubsectionTitle(s.get("settings.theme"))
            Spacer(Modifier.height(4.dp))
            LanguageRow(
                label = s.get("settings.theme.system"),
                selected = theme == ThemeMode.SYSTEM,
                onClick = { settings.setTheme(ThemeMode.SYSTEM) },
            )
            LanguageRow(
                label = s.get("settings.theme.light"),
                selected = theme == ThemeMode.LIGHT,
                onClick = { settings.setTheme(ThemeMode.LIGHT) },
            )
            LanguageRow(
                label = s.get("settings.theme.dark"),
                selected = theme == ThemeMode.DARK,
                onClick = { settings.setTheme(ThemeMode.DARK) },
            )

            Spacer(Modifier.height(16.dp))
            SubsectionTitle(s.get("settings.storage.title"))
            Text(
                s.get("settings.storage.text"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun LanguageRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
    HorizontalDivider()
}
