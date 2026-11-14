package com.webviewinspector.sample.moviecontent

import android.net.Uri
import com.webviewinspector.sample.catalog.Cinema
import com.webviewinspector.sample.catalog.Film

private const val HOST = "appassets.androidplatform.net"
private const val PREFIX = "/afterframe/"

internal const val NOW_PLAYING_PATH = "now-playing.html"
internal const val WATCHLIST_PATH = "watchlist.html"
internal const val WATCHLIST_TOGGLE_PATH = "watchlist/toggle"
internal const val SHOWTIME_PATH = "showtime.html"
internal const val STYLESHEET_PATH = "afterframe.css"
internal const val POSTERS_PATH = "posters.jpg"

internal fun movieUrl(path: String): String = "https://$HOST$PREFIX$path"

internal val Film.pagePath get() = "movies/$slug.html"

internal fun showtimeUrl(
    film: Film,
    cinema: Cinema,
    time: String,
): String {
    val query = "film=${film.slug}&cinema=${cinema.slug}&time=${Uri.encode(time)}"
    return movieUrl("$SHOWTIME_PATH?$query")
}

internal fun filmForPath(path: String?): Film? = Film.entries.firstOrNull { it.pagePath == path }

internal fun localMoviePath(uri: Uri): String? {
    val path = uri.path ?: return null
    return path.removePrefix(PREFIX).takeIf {
        uri.scheme == "https" && uri.encodedAuthority == HOST && path.startsWith(PREFIX)
    }
}
