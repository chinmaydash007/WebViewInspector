package com.webviewinspector.sample

import android.app.Application
import com.webviewinspector.WebViewInspector

class AfterframeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WebViewInspector.install(this)
    }
}
