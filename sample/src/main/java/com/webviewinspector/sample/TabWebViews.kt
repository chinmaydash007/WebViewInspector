package com.webviewinspector.sample

import android.graphics.Color
import android.view.ViewGroup
import android.webkit.WebView

internal fun WebView.configureLocalMovieContent() {
    setBackgroundColor(Color.TRANSPARENT)
    settings.allowFileAccess = false
    settings.allowContentAccess = false
}

internal fun WebView.destroyTabWebView() {
    stopLoading()
    (parent as? ViewGroup)?.removeView(this)
    destroy()
}
