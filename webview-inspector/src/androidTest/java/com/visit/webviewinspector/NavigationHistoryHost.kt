package com.visit.webviewinspector

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/** Real local HTTP fixture: no production clients, credentials, requests, or screen changes. */
class NavigationHistoryHost : ComponentActivity() {
    val views = mutableListOf<WebView>()
    internal val clients = mutableListOf<NavigationFixtureClient>()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        WebViewInspector.install(application)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val base = requireNotNull(intent.getStringExtra("historyBaseUrl"))
        setContent {
            Column(Modifier.windowInsetsPadding(WindowInsets.systemBars)) {
                listOf("/initial", "/secondary").forEachIndexed { index, path ->
                    AndroidView(
                        factory = { context ->
                            WebView(context).apply {
                                val client = NavigationFixtureClient()
                                webViewClient = client
                                settings.javaScriptEnabled = true
                                clients.add(client)
                                views.add(this)
                                loadUrl(base + path)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(if (index == 0) 310.dp else 140.dp),
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        views.forEach { it.destroy() }
        super.onDestroy()
    }
}

internal class NavigationFixtureClient : WebViewClient() {
    var finishedPages = 0
    override fun onPageFinished(view: WebView?, url: String?) {
        finishedPages++
    }
}

/** Loopback-only server generates actual HTTP 302 responses, which WebResourceResponse cannot. */
internal class LocalNavigationServer : AutoCloseable {
    private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
    private val workers = Executors.newCachedThreadPool { task -> Thread(task, "wvi-local-http").apply { isDaemon = true } }
    @Volatile private var closed = false
    val base = "http://127.0.0.1:${socket.localPort}"
    val middlePath = "/redirect-two?encoded=%2f%2F&raw=one+two"
    val finalPath = "/final?token=A%2Bb&dup=1&dup=2#raw"

    init {
        workers.execute {
            while (!closed) {
                val client = try { socket.accept() } catch (_: java.io.IOException) { break }
                workers.execute { client.use(::respond) }
            }
        }
    }

    private fun respond(client: Socket) {
        client.soTimeout = 5000
        try {
            val reader = client.getInputStream().bufferedReader()
            val request = reader.readLine()?.split(' ')?.getOrNull(1) ?: return
            while (!reader.readLine().isNullOrEmpty()) { /* Consume protocol headers without storing them. */ }
            val path = request.substringBefore('?')
            if (path == "/broken") return
            val destination = when (path) {
                "/redirect-one" -> middlePath
                "/redirect-two" -> finalPath
                else -> null
            }
            val html = "<html><body><h2>Local navigation fixture</h2><a id='go' href='/redirect-one'>Continue</a></body></html>"
            val body = html.toByteArray(Charsets.UTF_8)
            val output = client.getOutputStream()
            val response = buildString {
                append(if (destination == null) "HTTP/1.1 200 OK\r\n" else "HTTP/1.1 302 Found\r\nLocation: $destination\r\n")
                append("Content-Type: text/html; charset=utf-8\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n")
            }
            output.write(response.toByteArray(Charsets.US_ASCII))
            output.write(body)
            output.flush()
        } catch (_: java.io.IOException) {
            // WebView may cancel a speculative/subresource request while the fixture is closing.
        }
    }

    override fun close() {
        closed = true
        socket.close()
        workers.shutdownNow()
    }
}
