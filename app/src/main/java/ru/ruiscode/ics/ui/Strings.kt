package ru.ruiscode.ics.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import ru.ruiscode.ics.i18n.I18n
import ru.ruiscode.ics.i18n.StringResolver

/**
 * CompositionLocal с активным резолвером строк. Изменение языка обновляет
 * значение через [I18n.state] — все экраны перерисовываются реактивно.
 */
val LocalStrings = staticCompositionLocalOf<StringResolver> { StringResolver("en", emptyList()) }

@Composable
fun rememberStrings(): StringResolver {
    val state by I18n.state.collectAsState()
    return state?.resolver ?: StringResolver("en", emptyList())
}
