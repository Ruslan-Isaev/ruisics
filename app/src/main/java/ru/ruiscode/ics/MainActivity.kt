package ru.ruiscode.ics

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import ru.ruiscode.ics.core.readAsset
import ru.ruiscode.ics.i18n.I18n
import ru.ruiscode.ics.settings.AppSettings
import ru.ruiscode.ics.ui.RuisIcsApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.init(this)
        I18n.init(
            load = { path -> readAsset(path) },
            settings = AppSettings.instance,
            warn = { message -> if (BuildConfig.DEBUG) Log.w("RUISIcs", message) },
        )
        AppModules.init(this)
        setContent {
            RuisIcsApp()
        }
    }
}
