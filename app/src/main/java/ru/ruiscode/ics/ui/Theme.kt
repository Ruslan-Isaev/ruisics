package ru.ruiscode.ics.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Централизованная тема: одна пара схем, никакого дублирования экранов.
private val LightColors = lightColorScheme(
    primary = Color(0xFF1565C0),
    secondaryContainer = Color(0xFFD6E3F8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF90CAF9),
    secondaryContainer = Color(0xFF1E3A5F),
)

@Composable
fun RuisIcsTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
