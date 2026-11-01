package io.github.chinmaydash007.webviewinspector

import io.github.chinmaydash007.webviewinspector.internal.ui.common.dp

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.chinmaydash007.webviewinspector.test.R as TestR
import java.io.ByteArrayInputStream

internal const val FIRST_URL = "https://one.example/full?token=A%2Bb&dup=1&dup=2#raw"
internal const val SECOND_URL = "https://two.example/embedded?encoded=%252F&empty=&plus=a+b#fragment"
internal const val UPDATED_URL = "https://updated.example/new?q=%2f%2F&raw=one+two#exact"

@SuppressLint("SetJavaScriptEnabled")
internal fun WebView.fixture(url: String) {
    id = TestR.id.wvi_test_webview
    settings.javaScriptEnabled = true
    webViewClient = object : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse =
            WebResourceResponse("text/html", "UTF-8", ByteArrayInputStream(
                "<html><body style='background:#f0eafa'><h2>WebView fixture</h2><p>Native WebView content</p></body></html>".toByteArray()))
    }
    addJavascriptInterface(FixtureBridge(), "FixtureBridge")
    loadUrl(url)
}

internal class FixtureBridge {
    @Volatile var called = false
    @JavascriptInterface fun ping() { called = true }
}

internal class FixtureWebView(context: Context) : WebView(context)

class ComposeInspectorHost : ComponentActivity() {
    val views = mutableListOf<WebView>()
    override fun onCreate(savedInstanceState: Bundle?) {
        WebViewInspector.install(application)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Column(Modifier.windowInsetsPadding(WindowInsets.systemBars)) {
                AndroidView(factory = { WebView(it).apply { fixture(FIRST_URL); views.add(this) } }, modifier = Modifier.fillMaxWidth().height(320.dp))
                AndroidView(factory = { FixtureWebView(it).apply { fixture(SECOND_URL); views.add(this) } }, modifier = Modifier.fillMaxWidth().height(150.dp))
            }
        }
    }
    override fun onDestroy() { views.forEach { it.destroy() }; super.onDestroy() }
}

class XmlInspectorHost : AppCompatActivity() {
    lateinit var container: LinearLayout
    val views = mutableListOf<WebView>()
    var touches = 0
    // SDK window callbacks can route touch directly to the Activity's content container.
    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean =
        findViewById<ViewGroup>(android.R.id.content)?.dispatchTouchEvent(event) ?: super.dispatchTouchEvent(event)
    override fun onCreate(savedInstanceState: Bundle?) {
        WebViewInspector.install(application)
        super.onCreate(savedInstanceState)
        setContentView(TestR.layout.wvi_test_xml_host)
        enableEdgeToEdge()
        container = findViewById(TestR.id.wvi_test_container)
        ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(dp(12) + bars.left, dp(12) + bars.top, dp(12) + bars.right, dp(12) + bars.bottom)
            insets
        }
        for (i in 0 until container.childCount) (container.getChildAt(i) as? WebView)?.let { views.add(it) }
        views[0].fixture(FIRST_URL); views[1].fixture(SECOND_URL)
        findViewById<Button>(TestR.id.wvi_test_touch).setOnClickListener { touches++ }
    }
    fun addDelayed(): WebView = WebView(this).apply {
        fixture("https://late.example/card?id=3")
        container.addView(this, LinearLayout.LayoutParams(-1, dp(90))); views.add(this)
    }
    override fun onDestroy() { views.forEach { it.destroy() }; super.onDestroy() }
}

class RecycledInspectorHost : ComponentActivity() {
    lateinit var recycler: RecyclerView
    val bound = mutableListOf<WebView>()
    override fun onCreate(savedInstanceState: Bundle?) {
        WebViewInspector.install(application)
        super.onCreate(savedInstanceState)
        recycler = RecyclerView(this).apply {
            ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
            layoutManager = LinearLayoutManager(this@RecycledInspectorHost)
            adapter = object : RecyclerView.Adapter<Holder>() {
                override fun getItemCount() = 40
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
                    val web = WebView(parent.context)
                    bound.add(web)
                    return Holder(FrameLayout(parent.context).apply {
                        layoutParams = RecyclerView.LayoutParams(-1, dp(230))
                        addView(web, FrameLayout.LayoutParams(-1, -1))
                    }, web)
                }
                override fun onBindViewHolder(holder: Holder, position: Int) {
                    holder.web.fixture("https://recycled.example/card/$position?position=$position")
                }
            }
        }
        setContentView(recycler)
    }
    class Holder(view: View, val web: WebView) : RecyclerView.ViewHolder(view)
    override fun onDestroy() { bound.forEach { it.destroy() }; super.onDestroy() }
}
