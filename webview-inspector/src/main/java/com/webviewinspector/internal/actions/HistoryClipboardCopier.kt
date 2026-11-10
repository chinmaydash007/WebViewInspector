package com.webviewinspector.internal.actions

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import com.webviewinspector.R
import com.webviewinspector.internal.state.HistoryRecordKind
import com.webviewinspector.internal.state.NavigationHistorySnapshot
import com.webviewinspector.internal.util.HistoryPresentation

/** Copies immutable saved values, explicitly separate from the live-instance URL copier. Main thread. */
internal class HistoryClipboardCopier(
    private val context: Context,
) {
    fun copyUrl(
        rawUrl: String,
        number: Int,
    ) {
        copy(rawUrl, number, R.string.wvi_recorded_copied)
    }

    fun copyTimeline(
        history: NavigationHistorySnapshot,
        number: Int,
    ) {
        if (history.records.isEmpty()) return
        val text =
            buildString {
                appendLine(context.getString(R.string.wvi_timeline_title, number))
                appendLine(HistoryPresentation.mode(context, history.mode))
                if (history.discardedCount >
                    0
                ) {
                    appendLine(context.getString(R.string.wvi_history_discarded, history.discardedCount))
                }
                history.records.asReversed().forEach { record ->
                    appendLine()
                    appendLine(
                        "${HistoryPresentation.time(
                            record.atMillis,
                        )} · ${HistoryPresentation.summary(context, record)}",
                    )
                    if (record.kind == HistoryRecordKind.GAP) {
                        record.gapEndMillis?.let {
                            appendLine(
                                context.getString(R.string.wvi_gap_until, HistoryPresentation.time(it)),
                            )
                        }
                    }
                    if (record.joinedInProgress) appendLine(context.getString(R.string.wvi_joined_navigation))
                    record.steps.forEachIndexed { index, step ->
                        appendLine(
                            "${HistoryPresentation.step(
                                context,
                                record,
                                index,
                            )} · ${HistoryPresentation.time(step.atMillis)}",
                        )
                        appendLine(step.rawUrl)
                    }
                }
            }
        copy(text, number, R.string.wvi_history_copied)
    }

    private fun copy(
        text: String,
        number: Int,
        confirmation: Int,
    ) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        try {
            clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.wvi_view_number, number), text))
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Toast.makeText(context, context.getString(confirmation, number), Toast.LENGTH_SHORT).show()
            }
        } catch (_: RuntimeException) {
            // Binder/clipboard limits can reject a very large timeline. Never truncate or log URLs.
            Toast.makeText(context, R.string.wvi_clipboard_unavailable, Toast.LENGTH_SHORT).show()
        }
    }
}
