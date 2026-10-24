package ru.ruiscode.ics

import android.content.Context
import ru.ruiscode.ics.core.readAsset
import ru.ruiscode.ics.holidays.HolidayCatalog
import ru.ruiscode.ics.i18n.AssetLoader
import ru.ruiscode.ics.settings.AppSettings
import ru.ruiscode.ics.shifts.ShiftTemplateCatalog

/**
 * Загруженные один раз каталоги данных (наборы праздников, шаблоны смен).
 * Инициализируются в MainActivity; кэшируются в памяти.
 */
object AppModules {

    lateinit var holidayCatalog: HolidayCatalog
        private set

    lateinit var shiftCatalog: ShiftTemplateCatalog
        private set

    fun init(context: Context) {
        val loader: AssetLoader = { path -> context.readAsset(path) }
        holidayCatalog = HolidayCatalog.load(loader)
        shiftCatalog = loadShifts(loader)
    }

    /** Перезагружает каталог смен (после сохранения пользовательского шаблона). */
    fun reloadShifts(context: Context) {
        shiftCatalog = loadShifts({ path -> context.readAsset(path) })
    }

    private fun loadShifts(loader: AssetLoader): ShiftTemplateCatalog =
        ShiftTemplateCatalog.load(loader, AppSettings.instance.customShiftTemplates.value)
}
