package com.webviewinspector.internal.actions

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.webviewinspector.R

/** Writes once on the main thread. Binder size limits can reject large exports. */
internal fun Context.writeInspectorClip(clip: ClipData): Boolean {
    try {
        val clipboard = getSystemService(ClipboardManager::class.java)
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip)
            return true
        }
    } catch (_: RuntimeException) {
        // Keep the original text intact and out of error logs.
    }
    Toast.makeText(this, R.string.wvi_clipboard_unavailable, Toast.LENGTH_SHORT).show()
    return false
}
