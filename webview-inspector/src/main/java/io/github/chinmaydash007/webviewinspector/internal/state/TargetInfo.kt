package io.github.chinmaydash007.webviewinspector.internal.state

import io.github.chinmaydash007.webviewinspector.internal.util.UrlPresentation

/** A target's current number and raw URL. Display formatting never replaces [url]. */
internal data class TargetInfo(
    val id: Long,
    val number: Int,
    val url: String?,
) {
    val domain: String
        get() = UrlPresentation.domain(url)

    val path: String
        get() = UrlPresentation.path(url)
}
