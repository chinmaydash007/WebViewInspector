package io.github.chinmaydash007.webviewinspector.internal.state

/** Original application inputs, never a representation of verified network traffic. */
internal data class AppLoadHeader(val name: String, val value: String)

/** An immutable snapshot of one helper invocation, including repeated URLs and empty headers. */
internal data class AppLoadRecord(
    val id: Long,
    val atMillis: Long,
    val rawUrl: String,
    val headers: List<AppLoadHeader>,
)

/** View-free presentation data. A snapshot exists only after this instance used the helper. */
internal data class AppLoadsSnapshot(
    val key: HistoryTargetKey,
    val records: List<AppLoadRecord>,
    val discardedCount: Int,
)

/** View-free Activity-lifetime tab preference; a new Activity starts on Current URL. */
internal enum class InspectorTab { CURRENT_URL, HISTORY, APP_LOADS }

/** Explicit saved-copy actions keep URL, whole-header and individual-value paths unambiguous. */
internal sealed interface AppLoadCopyPart {
    data object Url : AppLoadCopyPart
    data object Headers : AppLoadCopyPart
    data class HeaderValue(val index: Int) : AppLoadCopyPart
}
