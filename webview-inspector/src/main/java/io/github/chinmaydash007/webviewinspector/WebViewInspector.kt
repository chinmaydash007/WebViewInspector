package io.github.chinmaydash007.webviewinspector

import android.app.Activity
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import io.github.chinmaydash007.webviewinspector.internal.core.InspectorController

/**
 * Install once in Application.onCreate. Lifecycle/settings mutations serialize onto main;
 * explicit load helpers preserve the original synchronous WebView call on the caller thread.
 * Basic inspection never modifies a WebView or its clients. Explicit load helpers preserve
 * the caller's loading operation and optionally snapshot its inputs in process memory.
 */
object WebViewInspector {
    private val main = Handler(Looper.getMainLooper())
    private var controller: InspectorController? = null
    @Volatile
    private var enabled = false

    /** Installs once, initially enabled. Repeated calls preserve the active installation. */
    @JvmStatic
    fun install(application: Application) = onMain {
        if (controller == null) {
            controller = InspectorController(application) { enabled = it }.also { it.install() }
        }
    }

    /** Shows library-owned settings for either Activity family, installing first if necessary. */
    @JvmStatic
    fun showSettings(activity: Activity) = onMain {
        if (!activity.isFinishing && !activity.isDestroyed) {
            install(activity.application)
            controller?.showSettings(activity)
        }
    }

    /** Enables/disables the current installation; calls before installation have no effect. */
    @JvmStatic
    fun setEnabled(enabled: Boolean) = onMain {
        controller?.setEnabled(enabled)
    }

    /** Returns the last applied main-thread state, which may lag a queued background-thread call. */
    @JvmStatic
    fun isEnabled(): Boolean = enabled

    /**
     * Loads synchronously on the WebView's UI thread, exactly like WebView.loadUrl(url).
     * An installed, enabled inspector snapshots this call; no headers are invented.
     */
    @JvmStatic
    fun loadUrl(webView: WebView, url: String) {
        captureLoad(webView, url, emptyMap())
        webView.loadUrl(url)
    }

    /**
     * Snapshots the app-supplied inputs before forwarding the original map and URL unchanged.
     * These are not verified network headers. Capture errors cannot prevent loading, while
     * exceptions from WebView propagate normally. Never posts or changes the caller's thread.
     */
    @JvmStatic
    fun loadUrl(webView: WebView, url: String, headers: Map<String, String>) {
        captureLoad(webView, url, headers)
        webView.loadUrl(url, headers)
    }

    private fun captureLoad(webView: WebView, url: String, headers: Map<String, String>) {
        // Preserve WebView's own thread/error behavior rather than adding asynchronous loading.
        if (Looper.myLooper() != Looper.getMainLooper()) return
        try {
            controller?.captureLoad(webView, url, headers)
        } catch (_: Exception) {
            // Inspection is best effort. Never log inputs or catch the actual loading operation.
        }
    }

    /** Releases callbacks, timers, dialogs, and overlays. Repeated calls are safe. */
    @JvmStatic
    fun uninstall() = onMain {
        controller?.dispose()
        controller = null
        enabled = false
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            main.post(action)
        }
    }
}
