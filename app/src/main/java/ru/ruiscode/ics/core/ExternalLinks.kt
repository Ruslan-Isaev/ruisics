package ru.ruiscode.ics.core

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Результат открытия внешней ссылки. */
sealed class OpenResult {
    object Opened : OpenResult()
    data class Failed(val reason: Reason) : OpenResult()

    enum class Reason { NO_BROWSER, ERROR }
}

/**
 * Единый обработчик внешних ссылок: открывает HTTP(S)-ссылки штатным
 * механизмом Android (ACTION_VIEW во внешнем браузере), без WebView.
 */
object ExternalLinks {

    /** Страница добровольной поддержки разработчика. */
    const val DONATE_URL = "https://ruiscode.ru/donate.html"

    fun open(context: Context, url: String): OpenResult {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            if (intent.resolveActivity(context.packageManager) == null) {
                OpenResult.Failed(OpenResult.Reason.NO_BROWSER)
            } else {
                context.startActivity(intent)
                OpenResult.Opened
            }
        } catch (e: Exception) {
            OpenResult.Failed(OpenResult.Reason.ERROR)
        }
    }
}
