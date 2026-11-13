package com.webviewinspector.sample

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.webviewinspector.WebViewInspector

class XmlWebViewActivity : AppCompatActivity() {
    private lateinit var browser: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.webview_screen)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.screen)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        browser =
            findViewById<WebView>(R.id.browser).apply {
                webViewClient = WebViewClient()
                WebViewInspector.loadUrl(
                    this,
                    (application as DemoApplication).server.baseUrl + "/start?raw=A%2Bb&dup=1&dup=2#fragment",
                    linkedMapOf("Authorization" to "Bearer synthetic-demo", "X-Raw-Example" to "A+%2B"),
                )
            }
    }

    override fun onDestroy() {
        browser.destroy()
        super.onDestroy()
    }
}
