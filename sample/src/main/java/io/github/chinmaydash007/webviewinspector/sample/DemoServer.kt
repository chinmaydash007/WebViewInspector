package io.github.chinmaydash007.webviewinspector.sample

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/** Sample-only loopback pages and real HTTP redirects. No external requests or URL logging. */
internal class DemoServer {
    private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
    private val workers = Executors.newCachedThreadPool { work -> Thread(work, "wvi-sample").apply { isDaemon = true } }
    val baseUrl = "http://127.0.0.1:${server.localPort}"

    init {
        workers.execute {
            while (!server.isClosed) {
                val client =
                    try {
                        server.accept()
                    } catch (_: java.io.IOException) {
                        break
                    }
                workers.execute { client.use(::respond) }
            }
        }
    }

    private fun respond(client: Socket) {
        client.soTimeout = 5000
        try {
            val reader = client.getInputStream().bufferedReader()
            val path =
                reader
                    .readLine()
                    ?.split(' ')
                    ?.getOrNull(1)
                    ?.substringBefore('?') ?: return
            while (!reader.readLine().isNullOrEmpty()) { /* Discard request headers. */ }
            val destination =
                when (path) {
                    "/redirect-one" -> "/redirect-two?encoded=%2f%2F"
                    "/redirect-two" -> "/destination?raw=one+two"
                    else -> null
                }
            val html =
                """<!doctype html><meta name="viewport" content="width=device-width"><style>body{font:16px sans-serif;padding:16px;background:#f6f1ff}a{display:block;padding:16px}</style><h2>Local sample page</h2><p>Follow a link, then inspect this WebView.</p><a href="/redirect-one">Navigate through two redirects</a><a href="/next">Open another page</a><a href="#details">Same-document fragment</a><p id="details">Fragment destination</p>"""
                    .toByteArray()
            val head =
                buildString {
                    append(
                        if (destination ==
                            null
                        ) {
                            "HTTP/1.1 200 OK\r\n"
                        } else {
                            "HTTP/1.1 302 Found\r\nLocation: $destination\r\n"
                        },
                    )
                    append(
                        "Content-Type: text/html; charset=utf-8\r\nContent-Length: ${html.size}\r\nConnection: close\r\n\r\n",
                    )
                }
            client.getOutputStream().apply {
                write(head.toByteArray(Charsets.US_ASCII))
                write(html)
                flush()
            }
        } catch (_: java.io.IOException) {
            // A browser can cancel an in-flight sample request.
        }
    }
}
