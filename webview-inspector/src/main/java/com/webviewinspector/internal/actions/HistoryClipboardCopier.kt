package com.webviewinspector.internal.actions

import android.content.ClipData
import android.content.Context
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
    ): Boolean = copyText(rawUrl, number)

    fun copyTimeline(
        history: NavigationHistorySnapshot,
        number: Int,
    ): Boolean {
        if (history.records.isEmpty()) return false
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
        return copyText(text, number)
    }

    private fun copyText(
        text: String,
        number: Int,
    ): Boolean {
        val label = context.getString(R.string.wvi_view_number, number)
        return context.writeInspectorClip(ClipData.newPlainText(label, text))
    }
}
