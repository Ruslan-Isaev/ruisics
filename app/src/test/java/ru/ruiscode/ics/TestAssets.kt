package ru.ruiscode.ics

import ru.ruiscode.ics.i18n.AssetLoader
import java.io.File

/**
 * Загрузчик реальных файлов assets для JVM-тестов: тесты читают те же
 * JSON-файлы, что и приложение, поэтому проверяют и данные, и обнаружение.
 */
fun testAssetLoader(): AssetLoader = { path ->
    val file = File("src/main/assets", path)
    if (file.isFile) file.readText(Charsets.UTF_8) else null
}
