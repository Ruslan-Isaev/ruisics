package ru.ruiscode.ics.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.ruiscode.ics.AppModules
import ru.ruiscode.ics.holidays.HolidayPack
import ru.ruiscode.ics.settings.AppSettings

/** Список наборов праздников: включение/отключение и переход к деталям. */
@Composable
fun HolidaysScreen(env: CalendarEnv, onBack: () -> Unit, onOpenPack: (String) -> Unit) {
    val s = LocalStrings.current
    val enabled by AppSettings.instance.enabledPackIds.collectAsState()
    val packs = AppModules.holidayCatalog.packs

    ScreenScaffold(title = s.get("menu.holidays"), onBack = onBack) {
        Text(
            s.get("holidays.subtitle"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            s.get("holidays.pack.enabled.note"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        if (packs.isEmpty()) {
            Text(s.get("holidays.pack.empty"), modifier = Modifier.padding(8.dp))
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(packs, key = { it.meta.id }) { pack ->
                    PackRow(
                        pack = pack,
                        enabled = pack.meta.id in enabled,
                        onToggle = { value ->
                            AppSettings.instance.setPackEnabled(pack.meta.id, value)
                        },
                        onClick = { onOpenPack(pack.meta.id) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun PackRow(
    pack: HolidayPack,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val s = LocalStrings.current
    val resolver = pack.resolver(s)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        pack.name(resolver),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        s.get("holidays.pack.category") + ": " + categoryName(s, pack.meta.category),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        s.format("holidays.pack.languages", "languages" to pack.meta.availableLocales.joinToString(", ")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            Text(
                pack.description(resolver),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                if (enabled) s.get("holidays.enabled") else s.get("holidays.disabled"),
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

internal fun categoryName(s: ru.ruiscode.ics.i18n.StringResolver, category: String): String =
    s.get("holidays.category.$category")
