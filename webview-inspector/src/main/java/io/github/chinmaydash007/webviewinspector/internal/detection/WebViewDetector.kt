package io.github.chinmaydash007.webviewinspector.internal.detection

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView

/**
 * Reads attached, visible native WebViews from one Activity's decor on the main thread.
 * The session owns this detector. It installs no callbacks and never changes WebView configuration.
 */
internal class WebViewDetector(
    private val root: ViewGroup,
    private val inspectorOverlay: View,
) {
    fun discover(): List<WebView> {
        val targets = ArrayList<WebView>()
        collectVisibleWebViews(root, targets)
        return targets
    }

    /** Also used immediately before Copy/Locate, so detached or hidden targets cannot be acted on. */
    fun isVisible(webView: WebView): Boolean {
        if (!webView.isAttachedToWindow || !webView.isShown || webView.width <= 0 || webView.height <= 0) {
            return false
        }

        var ancestor: View? = webView
        while (ancestor != null) {
            if (ancestor.alpha <= 0f) return false
            ancestor = ancestor.parent as? View
        }

        val visibleBounds = Rect()
        return webView.getGlobalVisibleRect(visibleBounds) && !visibleBounds.isEmpty
    }

    private fun collectVisibleWebViews(view: View, targets: MutableList<WebView>) {
        if (view === inspectorOverlay || view.visibility != View.VISIBLE || view.alpha <= 0f) return

        if (view is WebView) {
            if (isVisible(view)) targets.add(view)
            return
        }

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                collectVisibleWebViews(view.getChildAt(index), targets)
            }
        }
    }
}
