package ru.ruiscode.ics.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import ru.ruiscode.ics.AppModules
import ru.ruiscode.ics.core.ExternalLinks
import ru.ruiscode.ics.core.OpenResult

/** Информация о приложении: версия, лицензии, источники данных, поддержка. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    var donateError by remember { mutableStateOf<String?>(null) }

    ScreenScaffold(title = s.get("menu.about"), onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                s.get("app.name"),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                s.get("about.version"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(s.get("about.description"), style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            SubsectionTitle(s.get("about.license.title"))
            Text(
                s.get("about.license.project"),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )

            SubsectionTitle(s.get("about.third_party.title"))
            Text(
                s.get("about.third_party.text"),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )

            SubsectionTitle(s.get("about.data.title"))
            Text(
                s.get("about.data.text"),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            AppModules.holidayCatalog.packs.forEach { pack ->
                val resolver = pack.resolver(s)
                Text(
                    "• " + pack.name(resolver),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
                val license = pack.meta.licenseName
                    ?: pack.meta.licenseSpdxId
                    ?: s.get("about.unverified")
                Text(
                    s.get("holidays.pack.license") + ": " + license,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            SubsectionTitle(s.get("donate.title"))
            Text(
                s.get("donate.text"),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    when (val result = ExternalLinks.open(context, ExternalLinks.DONATE_URL)) {
                        OpenResult.Opened -> donateError = null
                        is OpenResult.Failed -> donateError = when (result.reason) {
                            OpenResult.Reason.NO_BROWSER -> s.get("error.no_browser")
                            OpenResult.Reason.ERROR -> s.format("error.open_link", ExternalLinks.DONATE_URL)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Favorite, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(s.get("donate.open"))
            }
            ErrorText(donateError)
            Spacer(Modifier.height(24.dp))
        }
    }
}
