package io.github.chinmaydash007.webviewinspector.internal.util

import android.content.Context
import io.github.chinmaydash007.webviewinspector.R
import io.github.chinmaydash007.webviewinspector.internal.state.HistoryCaptureMode
import io.github.chinmaydash007.webviewinspector.internal.state.HistoryRecordKind
import io.github.chinmaydash007.webviewinspector.internal.state.NavigationOutcome
import io.github.chinmaydash007.webviewinspector.internal.state.NavigationPhase
import io.github.chinmaydash007.webviewinspector.internal.state.NavigationRecord
import java.text.DateFormat
import java.util.Date

/** Display-only names/timestamps. Raw URL values are never decoded or reconstructed here. */
internal object HistoryPresentation {
    fun time(atMillis: Long): String = DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(atMillis))

    fun mode(
        context: Context,
        mode: HistoryCaptureMode,
    ): String =
        context.getString(
            if (mode ==
                HistoryCaptureMode.NAVIGATION_EVENTS
            ) {
                R.string.wvi_navigation_events
            } else {
                R.string.wvi_observed_urls
            },
        )

    fun kind(
        context: Context,
        record: NavigationRecord,
    ): String {
        val resource =
            when (record.kind) {
                HistoryRecordKind.FIRST_OBSERVED -> {
                    R.string.wvi_first_observed
                }

                HistoryRecordKind.OBSERVED_CHANGE -> {
                    R.string.wvi_observed_change
                }

                HistoryRecordKind.GAP -> {
                    R.string.wvi_recording_gap
                }

                HistoryRecordKind.NAVIGATION -> {
                    when {
                        record.flags.back -> R.string.wvi_back_navigation
                        record.flags.forward -> R.string.wvi_forward_navigation
                        record.flags.reload -> R.string.wvi_reload
                        record.flags.sameDocument -> R.string.wvi_same_document
                        else -> R.string.wvi_page_navigation
                    }
                }
            }
        return context.getString(resource)
    }

    fun summary(
        context: Context,
        record: NavigationRecord,
    ): String {
        if (record.kind != HistoryRecordKind.NAVIGATION) return kind(context, record)
        val redirects = record.steps.count { it.phase == NavigationPhase.REDIRECTED }
        val type =
            if (redirects >
                0
            ) {
                context.getString(R.string.wvi_redirect_count, redirects)
            } else {
                kind(context, record)
            }
        val outcome =
            context.getString(
                when (record.outcome) {
                    NavigationOutcome.COMMITTED -> R.string.wvi_committed
                    NavigationOutcome.NOT_COMMITTED -> R.string.wvi_not_committed
                    NavigationOutcome.ERROR_PAGE -> R.string.wvi_error_page
                    else -> R.string.wvi_in_progress
                },
            )
        return context.getString(R.string.wvi_history_summary, type, outcome)
    }

    fun step(
        context: Context,
        record: NavigationRecord,
        index: Int,
    ): String =
        when (record.steps[index].phase) {
            NavigationPhase.STARTED -> {
                context.getString(R.string.wvi_started)
            }

            NavigationPhase.REDIRECTED -> {
                context.getString(
                    R.string.wvi_redirect_step,
                    record.steps.take(index + 1).count { it.phase == NavigationPhase.REDIRECTED },
                )
            }

            NavigationPhase.COMPLETED -> {
                context.getString(R.string.wvi_completed)
            }

            null -> {
                context.getString(
                    if (record.kind ==
                        HistoryRecordKind.FIRST_OBSERVED
                    ) {
                        R.string.wvi_first_observed
                    } else {
                        R.string.wvi_observed_change
                    },
                )
            }
        }
}
