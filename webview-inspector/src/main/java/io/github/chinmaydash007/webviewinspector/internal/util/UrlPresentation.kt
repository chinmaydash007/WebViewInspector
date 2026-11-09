package io.github.chinmaydash007.webviewinspector.internal.util

import android.net.Uri

/** Display-only URL formatting. Clipboard actions must use the original WebView URL instead. */
internal object UrlPresentation {
    internal data class QueryParameter(
        val name: String,
        val value: String,
    )

    fun domain(rawUrl: String?): String =
        parse(rawUrl)?.host
            ?: rawUrl?.substringBefore(':')
            ?: "—"

    fun path(rawUrl: String?): String =
        parse(rawUrl)
            ?.encodedPath
            ?.takeIf { it.isNotEmpty() }
            ?: "/"

    /** Preserves entry order and duplicates for presentation; decoding is never used for copying. */
    fun queryParameters(rawUrl: String?): List<QueryParameter> {
        val encodedQuery = parse(rawUrl)?.encodedQuery
        val entries = encodedQuery?.takeIf { it.isNotEmpty() }?.split('&').orEmpty()
        return entries.map { entry ->
            QueryParameter(
                name = Uri.decode(entry.substringBefore('=')),
                value = Uri.decode(entry.substringAfter('=', missingDelimiterValue = "")),
            )
        }
    }

    private fun parse(rawUrl: String?): Uri? =
        rawUrl?.let {
            runCatching { Uri.parse(it) }.getOrNull()
        }
}
