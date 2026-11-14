package com.webviewinspector.sample.headers

import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.webviewinspector.WebViewInspector
import com.webviewinspector.sample.R
import com.webviewinspector.sample.databinding.HeadersPageBinding
import com.webviewinspector.sample.destroyTabWebView
import com.webviewinspector.sample.fadeInSampleContent
import com.webviewinspector.sample.resetSampleFade
import java.util.UUID

/** Loads the endpoint itself in the WebView. Its response body is the request receipt. */
internal class HeaderReceipt(
    private val views: HeadersPageBinding,
    showInspector: Boolean,
) {
    private val browser = views.receiptWeb
    private val main = Handler(Looper.getMainLooper())
    private var pendingUrl: String? = null
    private var lastWithHeaders = true
    private val deadline =
        Runnable {
            finish(R.string.receipt_error)
            browser.stopLoading()
        }

    init {
        if (showInspector) views.headersExplainer.setText(R.string.headers_summary)
        browser.settings.allowFileAccess = false
        browser.settings.allowContentAccess = false
        browser.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        browser.setBackgroundColor(views.root.context.getColor(R.color.sample_surface))
        browser.setDownloadListener { url, _, _, _, _ ->
            if (url == pendingUrl) finish(R.string.receipt_error)
        }
        browser.webViewClient =
            object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ) = true

                override fun onPageCommitVisible(
                    view: WebView,
                    url: String,
                ) {
                    if (url == pendingUrl) finish(R.string.receipt_loaded)
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    if (request.isForMainFrame && request.url.toString() == pendingUrl) finish(R.string.receipt_error)
                }

                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: WebResourceResponse,
                ) {
                    if (request.isForMainFrame && request.url.toString() == pendingUrl) finish(R.string.receipt_error)
                }
            }
        views.sendDemo.setOnClickListener { send(withHeaders = true) }
        views.sendEmpty.setOnClickListener { send(withHeaders = false) }
        views.retry.setOnClickListener { send(lastWithHeaders) }
    }

    private fun send(withHeaders: Boolean) {
        main.removeCallbacks(deadline)
        lastWithHeaders = withHeaders
        val run = "demo-" + UUID.randomUUID().toString().take(8)
        val url = "https://httpbin.org/dump/request?run=$run"
        pendingUrl = url
        showStatus(R.string.receipt_loading)
        browser.isVisible = true
        showResponse(false)
        main.postDelayed(deadline, 20_000)
        if (withHeaders) {
            val headers =
                linkedMapOf(
                    "X-Demo-Run" to run,
                    "X-Demo-City" to "Mumbai",
                    "X-Demo-Language" to "hi-IN",
                )
            WebViewInspector.loadUrl(browser, url, headers)
        } else {
            WebViewInspector.loadUrl(browser, url)
        }
    }

    private fun finish(message: Int) {
        pendingUrl = null
        main.removeCallbacks(deadline)
        showStatus(message)
        views.receiptNotice.fadeInSampleContent()
        showResponse(message == R.string.receipt_loaded)
    }

    private fun showStatus(message: Int) {
        val (background, foreground, icon) =
            when (message) {
                R.string.receipt_loaded -> {
                    Triple(
                        R.color.sample_success_container,
                        R.color.sample_on_success_container,
                        R.drawable.ic_receipt_success,
                    )
                }

                R.string.receipt_error -> {
                    Triple(
                        R.color.sample_error_container,
                        R.color.sample_on_error_container,
                        R.drawable.ic_receipt_error,
                    )
                }

                else -> {
                    Triple(R.color.sample_container, R.color.sample_muted, 0)
                }
            }
        val context = views.root.context
        views.receiptNotice.resetSampleFade()
        views.receiptNotice.setCardBackgroundColor(context.getColor(background))
        views.receiptStatus.setTextColor(context.getColor(foreground))
        views.receiptStatus.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
        views.receiptStatus.setText(message)
        views.receiptProgress.isInvisible = message != R.string.receipt_loading
        views.retry.isVisible = message == R.string.receipt_error
    }

    private fun showResponse(visible: Boolean) {
        views.receiptCover.isVisible = !visible
        if (visible) browser.fadeInSampleContent() else browser.resetSampleFade()
        browser.importantForAccessibility =
            if (visible) View.IMPORTANT_FOR_ACCESSIBILITY_AUTO else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    fun dispose() {
        pendingUrl = null
        main.removeCallbacks(deadline)
        browser.destroyTabWebView()
    }
}
