package com.webviewinspector.sample.catalog

internal enum class Cinema(
    val slug: String,
    val title: String,
    val neighborhood: String,
) {
    MARQUEE("marquee", "Marquee Cinema", "Bandra West"),
    HARBOUR("harbour", "Harbour Picturehouse", "Lower Parel"),
    ;

    fun showtimes(film: Film): List<String> =
        when (this) {
            MARQUEE -> if (film == Film.MIDNIGHT) listOf("18:45", "21:15") else listOf("17:30", "20:30")
            HARBOUR -> if (film == Film.MIDNIGHT) listOf("20:00") else listOf("19:15")
        }
}
