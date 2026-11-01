package io.github.chinmaydash007.webviewinspector.sample

import android.app.Application
import io.github.chinmaydash007.webviewinspector.WebViewInspector

class DemoApplication : Application() {
    internal val server by lazy { DemoServer() }
    override fun onCreate() {
        super.onCreate()
        WebViewInspector.install(this)
    }
}
