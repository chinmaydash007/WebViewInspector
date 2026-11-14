package com.webviewinspector.sample.home

import com.webviewinspector.sample.catalog.Film
import com.webviewinspector.sample.catalog.WatchlistStore
import com.webviewinspector.sample.configureLocalMovieContent
import com.webviewinspector.sample.databinding.HomePageBinding
import com.webviewinspector.sample.destroyTabWebView
import com.webviewinspector.sample.moviecontent.MovieWebViewClient
import com.webviewinspector.sample.moviecontent.NOW_PLAYING_PATH
import com.webviewinspector.sample.moviecontent.WATCHLIST_PATH
import com.webviewinspector.sample.moviecontent.movieUrl

internal class HomeTab(
    private val views: HomePageBinding,
    watchlist: WatchlistStore,
    onFilmLink: (Film) -> Unit,
) {
    private var watchlistDirty = false

    init {
        val context = views.root.context
        views.nowPlayingWeb.configureLocalMovieContent()
        views.watchlistWeb.configureLocalMovieContent()
        views.nowPlayingWeb.webViewClient = MovieWebViewClient(context, watchlist, onFilmLink = onFilmLink)
        views.watchlistWeb.webViewClient = MovieWebViewClient(context, watchlist, onFilmLink = onFilmLink)
        views.nowPlayingWeb.loadUrl(movieUrl("$NOW_PLAYING_PATH?city=mumbai"))
        views.watchlistWeb.loadUrl(movieUrl(WATCHLIST_PATH))
    }

    fun invalidateWatchlist() {
        watchlistDirty = true
    }

    fun refreshWatchlistIfNeeded() {
        if (!watchlistDirty) return
        views.watchlistWeb.reload()
        watchlistDirty = false
    }

    fun dispose() {
        views.nowPlayingWeb.destroyTabWebView()
        views.watchlistWeb.destroyTabWebView()
    }
}
