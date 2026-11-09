package io.github.chinmaydash007.webviewinspector.internal.ui.sheet

import android.content.Context
import android.graphics.Rect
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.OneShotPreDrawListener
import androidx.core.view.ViewCompat
import androidx.core.view.doOnPreDraw
import androidx.core.widget.NestedScrollView
import io.github.chinmaydash007.webviewinspector.R
import io.github.chinmaydash007.webviewinspector.internal.state.HistoryCaptureMode
import io.github.chinmaydash007.webviewinspector.internal.state.HistoryRecordKind
import io.github.chinmaydash007.webviewinspector.internal.state.HistoryScrollAnchor
import io.github.chinmaydash007.webviewinspector.internal.state.InspectorSheetState
import io.github.chinmaydash007.webviewinspector.internal.state.NavigationHistorySnapshot
import io.github.chinmaydash007.webviewinspector.internal.state.NavigationOutcome
import io.github.chinmaydash007.webviewinspector.internal.state.NavigationRecord
import io.github.chinmaydash007.webviewinspector.internal.state.TargetSheetState
import io.github.chinmaydash007.webviewinspector.internal.ui.common.color
import io.github.chinmaydash007.webviewinspector.internal.ui.common.dp
import io.github.chinmaydash007.webviewinspector.internal.ui.common.padding
import io.github.chinmaydash007.webviewinspector.internal.ui.common.shape
import io.github.chinmaydash007.webviewinspector.internal.util.HistoryDates
import io.github.chinmaydash007.webviewinspector.internal.util.HistoryPresentation
import io.github.chinmaydash007.webviewinspector.internal.util.UrlPresentation

/**
 * Main-thread, dialog-owned history renderer. Keyed rows/steps keep focus while capture continues.
 * Header/footer actions live outside this panel. Snapshots never feed decoded clipboard values.
 * Pre-draw corrections and references are released when the panel detaches.
 */
internal class HistoryPanel(
    private val context: Context,
    private val presentation: InspectorSheetState,
    private val scroll: NestedScrollView,
    private val copyRecordedUrl: (Long, Int?) -> Unit,
    about: () -> Unit,
    private val contentChanged: () -> Unit,
) {
    val view =
        LinearLayout(context).apply {
            id = R.id.wvi_history_panel
            orientation = LinearLayout.VERTICAL
            padding(horizontal = 20, vertical = 0)
        }
    private val saved = notice()
    private val sampled = notice()
    private val discarded = context.focusedLabel(size = 12f, muted = true)
    private val firstDate = context.focusedLabel(size = 12f, medium = true).asHeading()
    private val entries =
        LinearLayout(context).apply {
            id = R.id.wvi_history_entries
            orientation = LinearLayout.VERTICAL
        }
    private val empty =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            padding(horizontal = 0, vertical = 24)
            addView(
                context.focusedLabel(context.getString(R.string.wvi_history_empty_title), medium = true).asHeading(),
            )
            addView(
                context.focusedLabel(context.getString(R.string.wvi_history_empty_body), muted = true).apply {
                    padding(horizontal = 0, vertical = 8)
                },
            )
        }
    private val rows = mutableMapOf<Long, RecordRow>()
    private val dates = mutableMapOf<String, TextView>()
    private var lastHistory: NavigationHistorySnapshot? = null
    private var pendingScroll: OneShotPreDrawListener? = null

    init {
        view.addView(saved)
        view.addView(sampled)
        view.addView(discarded)
        view.addView(
            LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(firstDate, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(context.focusedLabel(context.getString(R.string.wvi_newest_first), size = 12f, muted = true))
                addView(
                    context.focusedIcon(R.drawable.wvi_ic_info, context.getString(R.string.wvi_about_capture), about),
                )
            },
        )
        view.addView(entries)
        view.addView(empty)
        view.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) = Unit

                override fun onViewDetachedFromWindow(view: View) = clearPendingScroll()
            },
        )
    }

    fun render(
        history: NavigationHistorySnapshot?,
        number: Int?,
        live: Boolean,
    ) {
        saved.visibility = if (!live && history != null) View.VISIBLE else View.GONE
        saved.text =
            context.getString(R.string.wvi_saved_title, number ?: 1) + "\n" + context.getString(R.string.wvi_saved_body)
        sampled.visibility = if (history?.mode == HistoryCaptureMode.OBSERVED_URLS) View.VISIBLE else View.GONE
        sampled.text =
            context.getString(R.string.wvi_sampled_title) + "\n" + context.getString(R.string.wvi_sampled_body)
        discarded.visibility = if ((history?.discardedCount ?: 0) > 0) View.VISIBLE else View.GONE
        discarded.text = context.getString(R.string.wvi_history_discarded, history?.discardedCount ?: 0)
        val records = history?.records.orEmpty()
        empty.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
        firstDate.text = records.firstOrNull()?.let { dateLabel(it.atMillis) }.orEmpty()
        if (lastHistory == history) return

        val anchor = entries.findFocus() ?: rows.values.firstOrNull { it.view.getGlobalVisibleRect(Rect()) }?.view
        val previousTop = anchor?.let(::screenTop)
        val preserve =
            lastHistory?.key == history?.key && (
                scroll.scrollY > 0 || entries.findFocus() != null ||
                    presentation.expandedRecordIds.any { rows[it]?.view?.getGlobalVisibleRect(Rect()) == true }
            )
        if (lastHistory?.key != history?.key) {
            entries.removeAllViews()
            rows.clear()
            dates.clear()
        }
        val ids = records.map { it.id }.toSet()
        rows.keys.filter { it !in ids }.forEach { id -> entries.removeView(rows.remove(id)?.view) }
        val wanted = mutableListOf<View>()
        val dateKeys = mutableSetOf<String>()
        var previousDate: String? = null
        records.forEachIndexed { index, record ->
            val date = HistoryDates.key(record.atMillis)
            if (date != previousDate && index > 0) {
                dateKeys.add(date)
                val heading =
                    dates.getOrPut(date) {
                        context
                            .focusedLabel(
                                size = 12f,
                                medium = true,
                            ).asHeading()
                            .apply { padding(horizontal = 0, vertical = 14) }
                    }
                heading.text = dateLabel(record.atMillis)
                wanted.add(heading)
            }
            previousDate = date
            val row = rows.getOrPut(record.id) { RecordRow(record.id) }
            row.render(record)
            wanted.add(row.view)
        }
        dates.keys.filter { it !in dateKeys }.forEach { key -> entries.removeView(dates.remove(key)) }
        wanted.forEachIndexed { index, child ->
            if (child.parent == null) {
                entries.addView(child, index)
            } else if (entries.indexOfChild(child) != index) {
                entries.removeView(child)
                entries.addView(child, index)
            }
        }
        if (preserve && anchor != null && previousTop != null) {
            beforeDraw { if (anchor.isAttachedToWindow) scroll.scrollBy(0, screenTop(anchor) - previousTop) }
        }
        lastHistory = history
    }

    fun saveScroll(preferences: TargetSheetState) {
        preferences.historyScrollY = scroll.scrollY
        val row = rows.entries.firstOrNull { it.value.view.getGlobalVisibleRect(Rect()) }
        preferences.historyAnchor =
            row?.let { HistoryScrollAnchor(it.key, screenTop(it.value.view) - screenTop(scroll)) }
    }

    fun restoreScroll(preferences: TargetSheetState?) {
        beforeDraw {
            scroll.scrollTo(0, preferences?.historyScrollY ?: 0)
            val anchor = preferences?.historyAnchor
            val row = anchor?.let { rows[it.recordId]?.view }
            if (row != null && anchor != null) scroll.scrollBy(0, screenTop(row) - screenTop(scroll) - anchor.offset)
        }
    }

    private fun notice(): TextView =
        context.focusedLabel(size = 12f, muted = true).apply {
            padding(12)
            background =
                context.shape(context.color(R.color.wvi_background), border = context.color(R.color.wvi_border))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = context.dp(12) }
        }

    private fun dateLabel(atMillis: Long): String =
        when (HistoryDates.relative(atMillis, System.currentTimeMillis())) {
            HistoryDates.RelativeDay.TODAY -> context.getString(R.string.wvi_today)
            HistoryDates.RelativeDay.YESTERDAY -> context.getString(R.string.wvi_yesterday)
            HistoryDates.RelativeDay.EARLIER -> HistoryDates.formatted(atMillis)
        }

    private inner class RecordRow(
        private val recordId: Long,
    ) {
        val view = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        private val path =
            context.focusedLabel(medium = true).apply {
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            }
        private val domain = context.focusedLabel(size = 12f, muted = true)
        private val summary = context.focusedLabel(size = 12f, muted = true)
        private val arrow =
            ImageView(context).apply {
                setImageResource(R.drawable.wvi_ic_chevron)
                imageTintList =
                    android.content.res.ColorStateList
                        .valueOf(context.color(R.color.wvi_muted))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
        private val expand =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                minimumHeight = context.dp(76)
                padding(horizontal = 0, vertical = 13)
                isClickable = true
                isFocusable = true
                addView(path)
                addView(
                    domain,
                    LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = context.dp(3) },
                )
                addView(
                    LinearLayout(context).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        addView(summary, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                        addView(arrow, LinearLayout.LayoutParams(context.dp(14), context.dp(14)))
                    },
                    LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = context.dp(6) },
                )
                ViewCompat.setScreenReaderFocusable(this, true)
                setOnClickListener {
                    if (!presentation.expandedRecordIds.add(recordId)) presentation.expandedRecordIds.remove(recordId)
                    updateExpanded()
                    contentChanged()
                }
            }
        private val copy =
            context.focusedIcon(R.drawable.wvi_ic_copy, context.getString(R.string.wvi_copy_recorded)) {
                copyRecordedUrl(recordId, null)
            }
        private val details = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        private val steps = mutableListOf<LinearLayout>()
        private val partial =
            context.focusedLabel(
                context.getString(R.string.wvi_joined_navigation),
                size = 12f,
                muted = true,
            )
        private var record: NavigationRecord? = null

        init {
            view.addView(
                LinearLayout(context).apply {
                    gravity = Gravity.TOP
                    addView(expand, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                    addView(
                        copy,
                        LinearLayout.LayoutParams(context.dp(48), context.dp(48)).apply {
                            topMargin =
                                context.dp(5)
                        },
                    )
                },
            )
            view.addView(details)
            view.addView(context.focusedDivider())
        }

        fun render(value: NavigationRecord) {
            if (record == value) return
            record = value
            val rawUrl = value.rawUrl
            val gap = value.kind == HistoryRecordKind.GAP
            path.text = rawUrl?.let(UrlPresentation::path) ?: context.getString(R.string.wvi_recording_gap)
            domain.text =
                rawUrl?.let(UrlPresentation::domain)
                    ?: value.gapEndMillis
                        ?.let {
                            context.getString(
                                R.string.wvi_gap_until,
                                HistoryPresentation.time(it),
                            )
                        }.orEmpty()
            copy.visibility = if (rawUrl != null) View.VISIBLE else View.GONE
            copy.contentDescription = context.getString(R.string.wvi_copy_recorded_url, rawUrl.orEmpty())
            expand.isEnabled = !gap
            arrow.visibility = if (gap) View.GONE else View.VISIBLE
            summary.text = summaryWithOutcome(value)
            value.steps.forEachIndexed { index, step ->
                val stepView =
                    steps.getOrNull(index) ?: createStep(index).also {
                        steps.add(it)
                        details.addView(
                            it,
                            details.indexOfChild(partial).takeIf { position -> position >= 0 } ?: details.childCount,
                        )
                    }
                val heading = stepView.getChildAt(0) as ViewGroup
                (heading.getChildAt(0) as TextView).text =
                    "${HistoryPresentation.step(context, value, index)} · ${HistoryPresentation.time(step.atMillis)}"
                heading.getChildAt(1).contentDescription =
                    context.getString(R.string.wvi_copy_recorded_url, step.rawUrl)
                (stepView.getChildAt(1) as TextView).text = step.rawUrl
            }
            if (partial.parent == null) details.addView(partial)
            partial.visibility = if (value.joinedInProgress) View.VISIBLE else View.GONE
            updateExpanded()
        }

        private fun createStep(index: Int): LinearLayout =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                padding(horizontal = 10, vertical = 0)
                addView(
                    LinearLayout(context).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        addView(
                            context.focusedLabel(size = 12f, muted = true),
                            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f),
                        )
                        addView(
                            context.focusedIcon(R.drawable.wvi_ic_copy, context.getString(R.string.wvi_copy_recorded)) {
                                copyRecordedUrl(recordId, index)
                            },
                        )
                    },
                )
                addView(
                    context.focusedLabel(size = 13f).apply {
                        typeface = Typeface.MONOSPACE
                        setTextIsSelectable(true)
                        setLineSpacing(context.dp(3).toFloat(), 1f)
                        padding(12)
                        background = context.shape(context.color(R.color.wvi_background), radius = 8)
                    },
                    LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = context.dp(10) },
                )
            }

        private fun updateExpanded() {
            val expanded = recordId in presentation.expandedRecordIds
            details.visibility = if (expanded) View.VISIBLE else View.GONE
            arrow.rotation = if (expanded) 180f else 0f
            expand.isSelected = expanded
            record?.let { value ->
                expand.contentDescription =
                    context.getString(
                        if (expanded) R.string.wvi_record_collapse else R.string.wvi_record_expand,
                        value.rawUrl?.let(UrlPresentation::path).orEmpty(),
                        HistoryPresentation.summary(context, value),
                        HistoryPresentation.time(value.atMillis),
                    )
            }
        }
    }

    private fun summaryWithOutcome(record: NavigationRecord): CharSequence {
        val line =
            SpannableString(
                HistoryPresentation.summary(context, record) + " · " + HistoryPresentation.time(record.atMillis),
            )
        val outcome =
            record.outcome?.let {
                context.getString(
                    when (it) {
                        NavigationOutcome.COMMITTED -> R.string.wvi_committed
                        NavigationOutcome.NOT_COMMITTED -> R.string.wvi_not_committed
                        NavigationOutcome.ERROR_PAGE -> R.string.wvi_error_page
                        NavigationOutcome.IN_PROGRESS -> R.string.wvi_in_progress
                    },
                )
            }
        if (outcome != null) {
            val index = line.indexOf(outcome)
            if (index >=
                0
            ) {
                line.setSpan(
                    ForegroundColorSpan(
                        context.color(
                            if (record.outcome ==
                                NavigationOutcome.COMMITTED
                            ) {
                                R.color.wvi_success
                            } else {
                                R.color.wvi_warning
                            },
                        ),
                    ),
                    index,
                    index + outcome.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
        return line
    }

    private fun beforeDraw(action: () -> Unit) {
        clearPendingScroll()
        // This may be scheduled before dialog.show(). AndroidX follows observer replacement
        // on attachment; retaining the detached observer can leave a repeating scroll callback.
        pendingScroll =
            view.doOnPreDraw {
                pendingScroll = null
                action()
            }
    }

    private fun clearPendingScroll() {
        pendingScroll?.removeListener()
        pendingScroll = null
    }

    private fun screenTop(view: View): Int = IntArray(2).also(view::getLocationOnScreen)[1]
}
