package ru.ruiscode.ics.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.ruiscode.ics.AppModules
import ru.ruiscode.ics.holidays.HolidayPack

/** Список наборов праздников: переход к деталям и импорту. */
@Composable
fun HolidaysScreen(onBack: () -> Unit, onOpenPack: (String) -> Unit) {
    val s = LocalStrings.current
    val packs = AppModules.holidayCatalog.packs

    ScreenScaffold(title = s.get("menu.holidays"), onBack = onBack) {
        Text(
            s.get("holidays.subtitle"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        if (packs.isEmpty()) {
            Text(s.get("holidays.pack.empty"), modifier = Modifier.padding(8.dp))
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(packs, key = { it.meta.id }) { pack ->
                    PackRow(pack = pack, onClick = { onOpenPack(pack.meta.id) })
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun PackRow(pack: HolidayPack, onClick: () -> Unit) {
    val s = LocalStrings.current
    val resolver = pack.resolver(s)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
        ) {
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
            Text(
                pack.description(resolver),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

internal fun categoryName(s: ru.ruiscode.ics.i18n.StringResolver, category: String): String =
    s.get("holidays.category.$category")
