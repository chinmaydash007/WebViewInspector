package com.webviewinspector.sample.catalog

internal enum class Film(
    val slug: String,
    val title: String,
    val genre: String,
    val language: String,
    val runtime: String,
    val synopsis: String,
) {
    MIDNIGHT(
        "midnight-local",
        "Midnight Local",
        "Thriller",
        "Hindi",
        "1h 48m",
        "A commuter boards the last train home and finds a message that was meant for someone else.",
    ),
    SIGNAL(
        "the-last-signal",
        "The Last Signal",
        "Sci-fi",
        "English",
        "1h 54m",
        "A radio astronomer follows a mysterious transmission to a silent observatory on the salt flats.",
    ),
    BLUE_HOUR(
        "blue-hour",
        "Blue Hour",
        "Drama",
        "English",
        "1h 39m",
        "Two siblings return to their coastal hometown to spend one last evening at the family lighthouse.",
    ),
    ;

    companion object {
        fun fromSlug(slug: String?) = entries.firstOrNull { it.slug == slug }
    }
}
