package com.webviewinspector.sample.catalog

import android.content.Context
import androidx.core.content.edit

internal class WatchlistStore(
    context: Context,
) {
    private val preferences = context.getSharedPreferences("afterframe", Context.MODE_PRIVATE)

    // Readers only use snapshots. SharedPreferences owns the original set.
    fun snapshot(): Set<String> = preferences.getStringSet(KEY, DEFAULT_WATCHLIST).orEmpty().toSet()

    fun toggle(film: Film) {
        val updated = snapshot().toMutableSet()
        if (!updated.add(film.slug)) updated.remove(film.slug)
        preferences.edit { putStringSet(KEY, updated) }
    }

    private companion object {
        const val KEY = "watchlist"
        val DEFAULT_WATCHLIST = setOf(Film.SIGNAL.slug, Film.BLUE_HOUR.slug)
    }
}
