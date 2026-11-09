package io.github.chinmaydash007.webviewinspector.internal.actions

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import java.lang.ref.WeakReference

/**
 * Owns the selected target's five-second Locate timer and overlay-coordinate conversion.
 * Access is main-thread-only. The WebView is weakly held; the session calls [update] after layout
 * or scrolling and [dispose] when its overlay is removed.
 */
internal class LocateHighlighter(
    private val root: ViewGroup,
    private val overlay: View,
    private val isVisible: (WebView) -> Boolean,
    private val drawHighlight: (Rect?, Int) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var target: WeakReference<WebView>? = null
    private var targetNumber = 0
    private val clearHighlight =
        Runnable {
            target = null
            drawHighlight(null, 0)
        }

    /** The timer survives the brief focus handoff while the inspector dialog is dismissed. */
    fun show(
        webView: WebView,
        number: Int,
    ) {
        target = WeakReference(webView)
        targetNumber = number
        main.removeCallbacks(clearHighlight)
        main.postDelayed(clearHighlight, HIGHLIGHT_DURATION_MS)
        update()
    }

    fun update() {
        val webView = target?.get()?.takeIf(isVisible)
        if (webView == null) {
            drawHighlight(null, 0)
            return
        }

        val bounds = Rect()
        webView.getGlobalVisibleRect(bounds)
        val rootLocation = IntArray(2)
        root.getLocationOnScreen(rootLocation)
        bounds.offset(rootLocation[0], rootLocation[1])

        val overlayLocation = IntArray(2)
        overlay.getLocationOnScreen(overlayLocation)
        bounds.offset(-overlayLocation[0], -overlayLocation[1])
        drawHighlight(bounds, targetNumber)
    }

    fun dispose() {
        main.removeCallbacksAndMessages(null)
        target = null
        drawHighlight(null, 0)
    }

    private companion object {
        const val HIGHLIGHT_DURATION_MS = 5_000L
    }
}
