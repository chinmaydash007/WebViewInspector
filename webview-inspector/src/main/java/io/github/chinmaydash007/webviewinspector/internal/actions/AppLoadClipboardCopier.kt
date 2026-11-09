package io.github.chinmaydash007.webviewinspector.internal.actions

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import io.github.chinmaydash007.webviewinspector.R

/** Main-thread copying of explicit saved app-load inputs, with no live WebView access. */
internal class AppLoadClipboardCopier(
    private val context: Context,
) {
    fun copy(text: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(context.getString(R.string.wvi_app_loads_tab), text)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Hide token-containing system previews without changing the copied raw text.
                clip.description.extras =
                    PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
            }
            clipboard.setPrimaryClip(clip)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Toast.makeText(context, R.string.wvi_app_load_copied, Toast.LENGTH_SHORT).show()
            }
        } catch (_: RuntimeException) {
            Toast.makeText(context, R.string.wvi_clipboard_unavailable, Toast.LENGTH_SHORT).show()
        }
    }
}
