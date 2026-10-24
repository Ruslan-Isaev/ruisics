package ru.ruiscode.ics.core

import android.content.Context

/** Читает файл из assets как UTF-8; null, если файл отсутствует или не читается. */
fun Context.readAsset(path: String): String? = try {
    assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
} catch (e: Exception) {
    null
}
