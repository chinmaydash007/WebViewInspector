package com.webviewinspector.internal.actions

import android.content.ClipData
import android.content.Context
import android.webkit.WebView
import com.webviewinspector.R

/**
 * Copies a freshly read URL from the exact selected instance on the main thread.
 * The session rescans and resolves selection first. This action validates visibility again and
 * never uses the sheet's display text, decoded query parameters, or a cached URL.
 */
internal class UrlClipboardCopier(
    private val context: Context,
    private val isVisible: (WebView) -> Boolean,
) {
    fun copy(
        webView: WebView,
        number: Int,
    ): Boolean {
        if (!isVisible(webView)) return false
        val rawUrl = runCatching { webView.url }.getOrNull() ?: return false
        val label = context.getString(R.string.wvi_view_number, number)
        return context.writeInspectorClip(ClipData.newPlainText(label, rawUrl))
    }
}
