@file:Suppress("UNUSED_PARAMETER")

package io.github.chinmaydash007.webviewinspector

import android.app.Activity
import android.app.Application
import android.webkit.WebView

/**
 * Production counterpart of the diagnostic facade, with the same Java/Kotlin entry points.
 * It owns no observers, UI, URL access, or dependencies on the inspector implementation.
 */
object WebViewInspector {
    /** Deliberately installs nothing in production. */
    @JvmStatic
    fun install(application: Application) = Unit

    /** Has no UI in production. */
    @JvmStatic
    fun showSettings(activity: Activity) = Unit

    /** Production inspection always remains disabled. */
    @JvmStatic
    fun setEnabled(enabled: Boolean) = Unit

    @JvmStatic
    fun isEnabled(): Boolean = false

    /** Production preserves the original load without observing or retaining its inputs. */
    @JvmStatic
    fun loadUrl(
        webView: WebView,
        url: String,
    ) = webView.loadUrl(url)

    /** Forwards the original arguments exactly once, synchronously, without inspection. */
    @JvmStatic
    fun loadUrl(
        webView: WebView,
        url: String,
        headers: Map<String, String>,
    ) = webView.loadUrl(url, headers)

    /** Safe to call repeatedly, matching the diagnostic facade. */
    @JvmStatic
    fun uninstall() = Unit
}
