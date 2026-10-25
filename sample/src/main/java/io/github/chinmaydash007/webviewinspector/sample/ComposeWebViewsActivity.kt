package io.github.chinmaydash007.webviewinspector.sample

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

class ComposeWebViewsActivity : ComponentActivity() {
    private val browsers = mutableListOf<WebView>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val baseUrl = (application as DemoApplication).server.baseUrl
        setContent {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                listOf("/embedded-one", "/embedded-two").forEach { path ->
                    AndroidView(
                        factory = { context -> WebView(context).apply { webViewClient = WebViewClient(); loadUrl(baseUrl + path); browsers.add(this) } },
                        modifier = Modifier.fillMaxWidth().height(260.dp),
                    )
                }
            }
        }
    }
    override fun onDestroy() { browsers.forEach { it.destroy() }; super.onDestroy() }
}
