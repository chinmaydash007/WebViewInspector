package com.webviewinspector.sample.moviecontent

import com.webviewinspector.sample.catalog.Cinema
import com.webviewinspector.sample.catalog.Film

internal object MovieHtml {
    fun nowPlaying(): String {
        val posters =
            Film.entries.joinToString("\n") { film ->
                val arrow = if (film == Film.MIDNIGHT) chevron else ""
                """
                <a class="film ${film.slug}" href="${movieUrl(film.pagePath)}">
                  ${poster(film)}
                  <span class="film-caption"><span><strong>${film.title}</strong>
                  <span class="meta">${film.metadata}</span></span>$arrow</span>
                </a>
                """.trimIndent()
            }
        return """
            <header class="section-head home-heading"><h1>Now playing</h1><span class="city">$pin Mumbai</span></header>
            <div class="posters">$posters</div>
            """.trimIndent()
    }

    fun watchlist(savedFilms: Set<String>): String {
        val films = Film.entries.filter { it.slug in savedFilms }
        if (films.isEmpty()) {
            return """
                <section class="panel watchlist empty">
                  <span class="empty-icon">${bookmark(false)}</span>
                  <div><h2>Your watchlist</h2><p class="meta">Save a film to watch it later.</p></div>
                </section>
                """.trimIndent()
        }
        val rows =
            films.joinToString("\n") { film ->
                """
                <a class="watch-row" href="${movieUrl(film.pagePath)}">
                  ${poster(film)}<span><strong>${film.title}</strong><span class="meta">${film.metadata}</span></span>
                  $chevron
                </a>
                """.trimIndent()
            }
        return """<section class="panel watchlist"><h2>Your watchlist</h2>$rows</section>"""
    }

    fun movie(
        film: Film,
        savedFilms: Set<String>,
    ): String {
        val saved = film.slug in savedFilms
        val saveLabel = if (saved) "Remove from watchlist" else "Save to watchlist"
        val navigation =
            Film.entries.joinToString("\n") { option ->
                val current = if (option == film) " aria-current=\"page\"" else ""
                """<a class="chip" href="${movieUrl(option.pagePath)}"$current>${option.title}</a>"""
            }
        val cinemas = Cinema.entries.joinToString("\n") { cinema -> cinemaCard(film, cinema) }
        val toggleUrl = movieUrl("$WATCHLIST_TOGGLE_PATH?film=${film.slug}")
        return """
            <nav class="film-nav" aria-label="Select a film">$navigation</nav>
            <header class="hero">
              ${poster(film)}
              <div>
                <h1>${film.title}</h1>
                <p class="meta">${film.metadata}<br>${film.runtime}</p>
                <a class="button primary" href="$toggleUrl">${bookmark(saved)}$saveLabel</a>
              </div>
            </header>
            <section class="panel movie-section">
              <h2>About the film</h2><p>${film.synopsis}</p>
              <div class="section-head showtimes"><h2>Showtimes</h2><span class="meta">Sample listings</span></div>
              $cinemas
            </section>
            """.trimIndent()
    }

    private fun cinemaCard(
        film: Film,
        cinema: Cinema,
    ): String {
        val times =
            cinema.showtimes(film).joinToString("\n") { time ->
                val href = showtimeUrl(film, cinema, time).replace("&", "&amp;")
                """<a class="button outlined" href="$href">${displayTime(time)}</a>"""
            }
        return """
            <article class="cinema">
              <h3>${cinema.title}</h3><p class="meta">${cinema.neighborhood} · 2D</p>
              <div class="times">$times</div>
            </article>
            """.trimIndent()
    }

    fun showtime(
        film: Film,
        cinema: Cinema,
        time: String,
    ): String =
        """
        <a class="button tonal" href="${movieUrl(film.pagePath)}">$back All showtimes</a>
        <header class="showtime-hero ${film.slug}">
          ${poster(film)}
          <div><h1>${film.title}</h1><p class="meta">${film.metadata}</p></div>
        </header>
        <article class="panel detail">
          <p class="meta">Sample showtime</p>
          <h2>${displayTime(time)}</h2>
          <h3>${cinema.title}</h3>
          <p class="meta">${cinema.neighborhood} · ${film.language} · 2D</p>
        </article>
        """.trimIndent()

    private fun poster(film: Film) = """<span class="poster ${film.slug}" aria-hidden="true"></span>"""

    private fun displayTime(time: String) = "${time.substringBefore(':').toInt() - 12}:${time.substringAfter(':')} PM"

    fun document(
        title: String,
        style: String,
        content: String,
        inlineStylesheet: String? = null,
    ): String {
        val stylesheet =
            if (inlineStylesheet == null) {
                """<link rel="stylesheet" href="${movieUrl(STYLESHEET_PATH)}">"""
            } else {
                "<style>$inlineStylesheet</style>"
            }
        return """
            <!doctype html>
            <html lang="en">
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>$title · Afterframe</title>
            $stylesheet
            <body class="$style"><main>$content</main></body>
            </html>
            """.trimIndent()
    }

    private val Film.metadata get() = "$language · $genre"

    private val chevron = icon("M10 6l6 6-6 6")
    private val back = icon("M14 6l-6 6 6 6")
    private val pin = icon("M19 10c0 5-7 11-7 11S5 15 5 10a7 7 0 1 1 14 0Z M14 10a2 2 0 1 0-4 0 2 2 0 1 0 4 0")

    private fun bookmark(saved: Boolean) = icon("M6 4h12v16l-6-4-6 4Z", if (saved) "currentColor" else "none")

    private fun icon(
        path: String,
        fill: String = "none",
    ): String =
        """
        <svg aria-hidden="true" viewBox="0 0 24 24" width="20" height="20" fill="$fill" stroke="currentColor"
          stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="$path"/></svg>
        """.trimIndent()
}
