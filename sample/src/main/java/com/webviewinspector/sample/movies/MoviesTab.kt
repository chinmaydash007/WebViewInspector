package com.webviewinspector.sample.movies

import android.os.Bundle
import com.webviewinspector.sample.catalog.Film
import com.webviewinspector.sample.catalog.WatchlistStore
import com.webviewinspector.sample.configureLocalMovieContent
import com.webviewinspector.sample.databinding.MoviesPageBinding
import com.webviewinspector.sample.destroyTabWebView
import com.webviewinspector.sample.moviecontent.MovieWebViewClient
import com.webviewinspector.sample.moviecontent.movieUrl
import com.webviewinspector.sample.moviecontent.pagePath

internal class MoviesTab(
    views: MoviesPageBinding,
    watchlist: WatchlistStore,
    savedState: Bundle?,
    onWatchlistChanged: () -> Unit,
) {
    private val webView = views.root
    private var started = false

    init {
        webView.configureLocalMovieContent()
        webView.webViewClient =
            MovieWebViewClient(webView.context, watchlist, onWatchlistChanged = onWatchlistChanged)
        if (savedState != null) started = webView.restoreState(savedState) != null
    }

    fun show(film: Film?) {
        when {
            film != null -> openFilm(film)
            !started -> openFilm(Film.MIDNIGHT)
        }
    }

    private fun openFilm(film: Film) {
        val url = movieUrl(film.pagePath)
        if (webView.url == url) return
        started = true
        webView.loadUrl(url)
    }

    fun goBack(): Boolean {
        if (!webView.canGoBack()) return false
        webView.goBack()
        return true
    }

    fun saveState(): Bundle? = if (webView.url == null) null else Bundle().also(webView::saveState)

    fun dispose() = webView.destroyTabWebView()
}
