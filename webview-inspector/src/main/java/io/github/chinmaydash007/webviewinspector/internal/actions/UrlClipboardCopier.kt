package io.github.chinmaydash007.webviewinspector.internal.actions

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.webkit.WebView
import android.widget.Toast
import io.github.chinmaydash007.webviewinspector.R

/**
 * Copies a freshly read URL from the exact selected instance on the main thread.
 * The session rescans and resolves selection first. This action validates visibility again and
 * never uses the sheet's display text, decoded query parameters, or a cached URL.
 */
internal class UrlClipboardCopier(
    private val context: Context,
    private val isVisible: (WebView) -> Boolean,
) {
    fun copy(webView: WebView, number: Int) {
        if (!isVisible(webView)) return
        val rawUrl = runCatching { webView.url }.getOrNull() ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val label = context.getString(R.string.wvi_view_number, number)
        clipboard.setPrimaryClip(ClipData.newPlainText(label, rawUrl))

        // Android 13+ supplies its own clipboard confirmation.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, context.getString(R.string.wvi_copied, number), Toast.LENGTH_SHORT).show()
        }
    }
}
