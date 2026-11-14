package com.webviewinspector.internal.actions

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import com.webviewinspector.R

/** Main-thread copying of explicit saved app-load inputs, with no live WebView access. */
internal class AppLoadClipboardCopier(
    private val context: Context,
) {
    fun copy(text: String): Boolean {
        val clip = ClipData.newPlainText(context.getString(R.string.wvi_app_loads_tab), text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Hide token-containing system previews without changing the copied raw text.
            clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        }
        return context.writeInspectorClip(clip)
    }
}
