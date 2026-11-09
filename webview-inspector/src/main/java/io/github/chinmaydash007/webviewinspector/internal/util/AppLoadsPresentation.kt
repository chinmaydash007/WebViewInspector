package io.github.chinmaydash007.webviewinspector.internal.util

import io.github.chinmaydash007.webviewinspector.internal.state.AppLoadRecord
import io.github.chinmaydash007.webviewinspector.internal.state.AppLoadsSnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Formats stored inputs only. Never decodes, sorts, trims or normalizes header/URL strings. */
internal object AppLoadsPresentation {
    fun headers(record: AppLoadRecord): String = record.headers.joinToString("\n") { "${it.name}: ${it.value}" }

    fun export(
        snapshot: AppLoadsSnapshot,
        number: Int,
    ): String =
        buildString {
            appendLine("App loads · WebView $number")
            appendLine("App-supplied inputs; not verified network headers.")
            appendLine("Discarded app loads: ${snapshot.discardedCount}")
            snapshot.records.asReversed().forEach { record ->
                appendLine()
                appendLine(SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(Date(record.atMillis)))
                appendLine(record.rawUrl)
                appendLine("Headers: ${record.headers.size}")
                if (record.headers.isNotEmpty()) appendLine(headers(record))
            }
        }
}
