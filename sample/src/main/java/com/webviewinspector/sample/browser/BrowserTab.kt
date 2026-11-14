package com.webviewinspector.sample.browser

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.view.KeyEvent
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.webviewinspector.sample.R
import com.webviewinspector.sample.databinding.BrowserPageBinding
import com.webviewinspector.sample.destroyTabWebView
import com.webviewinspector.sample.fadeInSampleContent
import com.webviewinspector.sample.resetSampleFade

internal class BrowserTab(
    private val views: BrowserPageBinding,
    window: Window,
    savedState: Bundle?,
) {
    private val webView = views.browserWeb
    private val insets = WindowCompat.getInsetsController(window, views.root)
    private var displayedAddress = ""

    init {
        if (Build.VERSION.SDK_INT >= 26) views.root.defaultFocusHighlightEnabled = false
        configureWebView()
        restore(savedState)
        views.browserAddress.doAfterTextChanged { views.browserInput.error = null }
        views.browserGo.setOnClickListener { openAddress() }
        views.browserAddress.setOnEditorActionListener { _, action, event ->
            when {
                event?.keyCode == KeyEvent.KEYCODE_ENTER -> {
                    if (event.action == KeyEvent.ACTION_UP) openAddress()
                    true
                }

                action == EditorInfo.IME_ACTION_GO -> {
                    openAddress()
                    true
                }

                else -> {
                    false
                }
            }
        }
        views.browserBack.setOnClickListener { goBack() }
        views.browserForward.setOnClickListener {
            if (webView.canGoForward()) webView.goForward()
        }
    }

    // This sample WebView deliberately runs site scripts. It has no JavaScript bridge or file access.
    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        // Sites without a background still need the standard WebView page contrast.
        webView.setBackgroundColor(Color.WHITE)
        webView.settings.apply {
            allowFileAccess = false
            allowContentAccess = false
            javaScriptEnabled = true
            domStorageEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(true)
        }
        webView.webViewClient =
            BrowserWebViewClient(
                onLocationChanged = ::updateLocation,
                onStatus = ::showStatus,
                onLoading = { loading -> views.browserProgress.isInvisible = !loading },
                onPageVisible = { webView.fadeInSampleContent() },
            )
        webView.webChromeClient =
            object : WebChromeClient() {
                override fun onCreateWindow(
                    view: WebView,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message,
                ): Boolean {
                    showStatus(R.string.browser_popup)
                    return false
                }
            }
        webView.setDownloadListener { _, _, _, _, _ -> showStatus(R.string.browser_download) }
    }

    private fun openAddress() {
        val url = browserAddress(views.browserAddress.text.toString())
        if (url == null) {
            views.browserInput.error = views.root.context.getText(R.string.browser_invalid_address)
            return
        }

        setAddress(url)
        finishEditing()
        views.browserInput.error = null
        showStatus(null)
        showWebView()
        webView.loadUrl(url)
    }

    private fun updateLocation(url: String) {
        // History callbacks also fire while the user is drafting a different URL.
        if (!url.startsWith("https://", ignoreCase = true)) return
        val address = views.browserAddress
        if (address.text.toString() == displayedAddress) setAddress(url)
        updateNavigationButtons()
    }

    private fun setAddress(url: String) {
        displayedAddress = url
        val address = views.browserAddress
        if (address.text.toString() != url) address.setText(url)
    }

    private fun updateNavigationButtons() {
        views.browserBack.isEnabled = webView.canGoBack()
        views.browserForward.isEnabled = webView.canGoForward()
    }

    private fun showStatus(message: Int) = showStatus(views.root.context.getText(message))

    private fun showStatus(message: CharSequence?) {
        views.browserStatus.resetSampleFade()
        views.browserStatus.text = message
        views.browserStatus.isVisible = message != null
        if (message != null) views.browserStatus.fadeInSampleContent()
    }

    private fun showWebView() {
        views.browserEmpty.isVisible = false
        webView.isInvisible = false
    }

    fun finishEditing() {
        insets.hide(WindowInsetsCompat.Type.ime())
        views.root.requestFocus()
    }

    fun goBack(): Boolean {
        if (!webView.canGoBack()) return false
        webView.goBack()
        return true
    }

    private fun restore(savedState: Bundle?) {
        val history = savedState?.getBundle(STATE_HISTORY)?.let(webView::restoreState)
        val currentUrl = history?.currentItem?.url
        if (currentUrl != null) showWebView()
        setAddress(currentUrl ?: savedState?.getString(STATE_ADDRESS).orEmpty())
        savedState?.getString(STATE_DRAFT)?.let(views.browserAddress::setText)
        updateNavigationButtons()
    }

    fun saveState(): Bundle =
        Bundle().apply {
            if (webView.url != null) putBundle(STATE_HISTORY, Bundle().also(webView::saveState))
            putString(STATE_ADDRESS, displayedAddress)
            val address = views.browserAddress.text.toString()
            if (address != displayedAddress) putString(STATE_DRAFT, address)
        }

    fun dispose() = webView.destroyTabWebView()

    private companion object {
        const val STATE_HISTORY = "browserHistory"
        const val STATE_ADDRESS = "displayedAddress"
        const val STATE_DRAFT = "addressDraft"
    }
}
