package ru.ruiscode.ics.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import ru.ruiscode.ics.i18n.I18n
import ru.ruiscode.ics.settings.AppSettings
import ru.ruiscode.ics.settings.ThemeMode

/**
 * Корень приложения: тема (светлая/тёмная/системная), активная локализация
 * и навигация. Выбор темы и языка сохраняется и применяется реактивно.
 */
@Composable
fun RuisIcsApp() {
    val settings = AppSettings.instance
    val themeMode by settings.theme.collectAsState()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    RuisIcsTheme(darkTheme) {
        val state by I18n.state.collectAsState()
        val resolver = state?.resolver ?: I18n.resolverOrNull() ?: ru.ruiscode.ics.i18n.StringResolver("en", emptyList())
        val direction = if (state?.direction == "rtl") LayoutDirection.Rtl else LayoutDirection.Ltr
        CompositionLocalProvider(
            LocalStrings provides resolver,
            LocalLayoutDirection provides direction,
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                AppNav()
            }
        }
    }
}
