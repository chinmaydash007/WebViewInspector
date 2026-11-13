package com.webviewinspector.sample

import android.app.Application
import com.webviewinspector.WebViewInspector

class DemoApplication : Application() {
    internal val server by lazy { DemoServer() }

    override fun onCreate() {
        super.onCreate()
        WebViewInspector.install(this)
    }
}
