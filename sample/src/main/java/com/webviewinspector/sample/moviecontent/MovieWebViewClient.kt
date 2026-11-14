package com.webviewinspector.sample.moviecontent

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.webviewinspector.sample.catalog.Cinema
import com.webviewinspector.sample.catalog.Film
import com.webviewinspector.sample.catalog.WatchlistStore
import com.webviewinspector.sample.sampleMotionEnabled

internal class MovieWebViewClient(
    private val context: Context,
    private val watchlist: WatchlistStore,
    private val onFilmLink: ((Film) -> Unit)? = null,
    private val onWatchlistChanged: () -> Unit = {},
) : WebViewClient() {
    private val showtimeStylesheet by lazy {
        context.assets
            .open("afterframe/$STYLESHEET_PATH")
            .bufferedReader()
            .use { it.readText() }
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse {
        val uri = request.url
        return when (val path = localMoviePath(uri)) {
            STYLESHEET_PATH -> {
                WebResourceResponse("text/css", "UTF-8", context.assets.open("afterframe/$path"))
            }

            POSTERS_PATH -> {
                WebResourceResponse("image/jpeg", null, context.assets.open("afterframe/$path"))
            }

            NOW_PLAYING_PATH -> {
                html("Now playing", "embedded", MovieHtml.nowPlaying())
            }

            WATCHLIST_PATH -> {
                html("Your watchlist", "embedded watchlist-page", MovieHtml.watchlist(watchlist.snapshot()))
            }

            SHOWTIME_PATH -> {
                showtime(uri) ?: missingPage()
            }

            else -> {
                filmForPath(path)?.let { film ->
                    html(film.title, "full", MovieHtml.movie(film, watchlist.snapshot()))
                } ?: missingPage()
            }
        }
    }

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean {
        val path = localMoviePath(request.url) ?: return true
        if (!request.isForMainFrame) return true
        if (path == WATCHLIST_TOGGLE_PATH) {
            Film.fromSlug(request.url.getQueryParameter("film"))?.let { film ->
                watchlist.toggle(film)
                onWatchlistChanged()
                view.reload()
            }
            return true
        }
        val film = filmForPath(path)
        if (film != null && onFilmLink != null) {
            onFilmLink(film)
            return true
        }
        return false
    }

    private fun showtime(uri: Uri): WebResourceResponse? {
        val film = Film.fromSlug(uri.getQueryParameter("film")) ?: return null
        val cinema = Cinema.entries.firstOrNull { it.slug == uri.getQueryParameter("cinema") } ?: return null
        val time = uri.getQueryParameter("time")?.takeIf { it in cinema.showtimes(film) } ?: return null
        return html("Showtime", "full showtime", MovieHtml.showtime(film, cinema, time), showtimeStylesheet)
    }

    private fun html(
        title: String,
        style: String,
        content: String,
        inlineStylesheet: String? = null,
    ): WebResourceResponse {
        val night =
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        val classes =
            buildString {
                append(style)
                if (night) append(" dark")
                if (context.sampleMotionEnabled()) append(" motion")
            }
        return WebResourceResponse(
            "text/html",
            "UTF-8",
            200,
            "OK",
            mapOf("Cache-Control" to "no-store"),
            MovieHtml.document(title, classes, content, inlineStylesheet).byteInputStream(),
        )
    }

    private fun missingPage() =
        WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), "".byteInputStream())
}
