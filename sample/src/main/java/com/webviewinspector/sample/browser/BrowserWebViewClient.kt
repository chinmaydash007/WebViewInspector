package com.webviewinspector.sample.browser

import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.webviewinspector.sample.R

internal class BrowserWebViewClient(
    private val onLocationChanged: (String) -> Unit,
    private val onStatus: (CharSequence?) -> Unit,
    private val onLoading: (Boolean) -> Unit,
    private val onPageVisible: () -> Unit,
) : WebViewClient() {
    private var mainFrameUrl: String? = null

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean {
        if (request.url.scheme.equals("https", ignoreCase = true)) return false
        if (request.isForMainFrame) onStatus(view.context.getText(R.string.browser_blocked_link))
        return true
    }

    override fun onPageStarted(
        view: WebView,
        url: String,
        favicon: Bitmap?,
    ) {
        mainFrameUrl = url
        onStatus(null)
        onLoading(true)
    }

    override fun doUpdateVisitedHistory(
        view: WebView,
        url: String,
        isReload: Boolean,
    ) {
        onLocationChanged(url)
    }

    override fun onPageFinished(
        view: WebView,
        url: String,
    ) {
        if (view.url != url) return
        onLoading(false)
        onLocationChanged(url)
    }

    override fun onPageCommitVisible(
        view: WebView,
        url: String,
    ) {
        if (url != mainFrameUrl) return
        onLoading(false)
        onPageVisible()
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        if (request.isForMainFrame && request.url.toString() == mainFrameUrl) {
            onLoading(false)
            onStatus(view.context.getText(R.string.browser_load_error))
        }
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse,
    ) {
        if (request.isForMainFrame && request.url.toString() == mainFrameUrl) {
            onLoading(false)
            onStatus(view.context.getString(R.string.browser_http_error, errorResponse.statusCode))
        }
    }

    override fun onReceivedSslError(
        view: WebView,
        handler: SslErrorHandler,
        error: SslError,
    ) {
        handler.cancel()
        if (error.url == mainFrameUrl) {
            onLoading(false)
            onStatus(view.context.getText(R.string.browser_tls_error))
        }
    }
}
