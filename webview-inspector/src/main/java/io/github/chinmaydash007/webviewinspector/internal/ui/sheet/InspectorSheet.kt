package io.github.chinmaydash007.webviewinspector.internal.ui.sheet

import android.app.Activity
import android.content.res.ColorStateList
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.OneShotPreDrawListener
import androidx.core.view.ViewCompat
import androidx.core.view.doOnPreDraw
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.chinmaydash007.webviewinspector.R
import io.github.chinmaydash007.webviewinspector.internal.state.HistoryCaptureMode
import io.github.chinmaydash007.webviewinspector.internal.state.HistoryRecordKind
import io.github.chinmaydash007.webviewinspector.internal.state.InspectorSheetState
import io.github.chinmaydash007.webviewinspector.internal.state.InspectorState
import io.github.chinmaydash007.webviewinspector.internal.state.TargetSheetState
import io.github.chinmaydash007.webviewinspector.internal.ui.common.BottomSheetFactory
import io.github.chinmaydash007.webviewinspector.internal.ui.common.InspectorSheetLayout
import io.github.chinmaydash007.webviewinspector.internal.ui.common.color
import io.github.chinmaydash007.webviewinspector.internal.ui.common.dp
import io.github.chinmaydash007.webviewinspector.internal.ui.common.inspectorTheme
import io.github.chinmaydash007.webviewinspector.internal.ui.common.padding
import io.github.chinmaydash007.webviewinspector.internal.ui.common.shape

/**
 * Main-thread coordinator for the Focused inspector. It renders immutable snapshots into native
 * collaborators without discovering WebViews or performing clipboard operations itself.
 * Session dismissal calls [release] to save view-free preferences and release child windows.
 */
internal class InspectorSheet(
    activity: Activity,
    select: (Long) -> Unit,
    copy: () -> Unit,
    locate: () -> Unit,
    private val presentation: InspectorSheetState,
    copyRecordedUrl: (Long, Int?) -> Unit,
    copyHistory: () -> Unit,
    clearHistory: () -> Unit,
) {
    val dialog = BottomSheetFactory.create(activity.inspectorTheme())
    private val context = dialog.context
    private val layout = InspectorSheetLayout(context, dialog.behavior)
    private val picker = TargetPicker(context, select)
    private val capture = context.focusedLabel(size = 12f, muted = true).apply { id = R.id.wvi_capture_mode }
    private val current = CurrentUrlPanel(context, locate, { switchTab(history = true) }, layout::contentChanged)
    private val history = HistoryPanel(context, presentation, layout.scroll, copyRecordedUrl, ::showAbout, layout::contentChanged)
    private val footer = InspectorFooter(context, copy, copyHistory, clearHistory, ::showAbout)
    private val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val currentLabel = context.focusedLabel(context.getString(R.string.wvi_current_tab), medium = true)
    private val historyLabel = context.focusedLabel(context.getString(R.string.wvi_history_tab), medium = true)
    private val historyCount = context.focusedLabel(size = 12f, muted = true).apply { padding(horizontal = 5, vertical = 1) }
    private val currentTab = createTab(R.id.wvi_current_tab, currentLabel) { switchTab(history = false) }
    private val historyTab = createTab(R.id.wvi_history_tab, historyLabel, historyCount) { switchTab(history = true) }
    private var lastState: InspectorState? = null
    private var about: AlertDialog? = null
    private var restoreListener: OneShotPreDrawListener? = null
    private var released = false

    init {
        val header = LinearLayout(context).apply {
            id = R.id.wvi_sheet_header
            orientation = LinearLayout.VERTICAL
        }
        header.addView(View(context).apply {
            background = context.shape(context.color(R.color.wvi_border), radius = 4)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(context.dp(34), context.dp(4)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = context.dp(10)
            bottomMargin = context.dp(4)
        })
        val heading = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            padding(horizontal = 20, vertical = 0)
            addView(context.focusedLabel(context.getString(R.string.wvi_focused_title), size = 20f, medium = true).asHeading(), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(context.focusedIcon(R.drawable.wvi_ic_close, context.getString(R.string.wvi_close), dialog::dismiss))
        }
        header.addView(heading)
        header.addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            padding(horizontal = 20, vertical = 0)
            addView(context.focusedLabel(activity.javaClass.simpleName, size = 12f, muted = true).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = context.dp(8) })
            addView(capture)
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = context.dp(12) })
        header.addView(picker.view, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            marginStart = context.dp(20)
            marginEnd = context.dp(20)
            bottomMargin = context.dp(8)
        })
        header.addView(LinearLayout(context).apply {
            padding(horizontal = 20, vertical = 0)
            addView(currentTab)
            addView(historyTab, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = context.dp(24) })
        })
        header.addView(context.focusedDivider())
        content.addView(current.view)
        content.addView(history.view)
        layout.setSections(header, content, footer.view)
        dialog.setContentView(layout)
        dialog.setTitle(context.getString(R.string.wvi_focused_title))
        renderTabs()
    }

    fun render(state: InspectorState) {
        if (released || lastState == state) return
        val previous = lastState
        val changedTarget = inspectedId(previous) != inspectedId(state)
        if (changedTarget) saveScroll()
        lastState = state
        picker.render(state)
        val saved = state.selected == null && state.history != null
        capture.text = context.getString(when {
            saved -> R.string.wvi_saved_badge
            state.history?.mode == HistoryCaptureMode.OBSERVED_URLS -> R.string.wvi_observed_badge
            else -> R.string.wvi_events_badge
        })
        capture.setTextColor(context.color(if (saved || state.history?.mode == HistoryCaptureMode.OBSERVED_URLS) R.color.wvi_muted else R.color.wvi_success))
        capture.contentDescription = if (saved) context.getString(R.string.wvi_saved_history) else state.history?.let {
            context.getString(if (it.mode == HistoryCaptureMode.OBSERVED_URLS) R.string.wvi_observed_urls else R.string.wvi_navigation_events)
        } ?: capture.text
        val preferences = preferences(state)
        current.render(state, preferences)
        history.render(state.history, state.historyNumber, live = state.selected != null)
        historyCount.text = (state.history?.records?.count { it.kind != HistoryRecordKind.GAP } ?: 0).toString()
        footer.render(state, presentation.historyTab)
        if (previous == null || changedTarget || (previous.selected == null) != (state.selected == null) ||
            (previous.selected?.url == null) != (state.selected?.url == null)) {
            layout.contentChanged()
        }
        if (previous == null || changedTarget) restoreScroll()
    }

    private fun switchTab(history: Boolean) {
        if (presentation.historyTab == history) return
        saveScroll()
        presentation.historyTab = history
        picker.dismiss()
        renderTabs()
        restoreScroll()
    }

    private fun renderTabs() {
        val showingHistory = presentation.historyTab
        current.view.visibility = if (showingHistory) View.GONE else View.VISIBLE
        history.view.visibility = if (showingHistory) View.VISIBLE else View.GONE
        currentTab.isSelected = !showingHistory
        historyTab.isSelected = showingHistory
        listOf(currentTab to currentLabel, historyTab to historyLabel).forEach { (tab, label) ->
            label.setTextColor(context.color(if (tab.isSelected) R.color.wvi_primary else R.color.wvi_muted))
            tab.getChildAt(1).visibility = if (tab.isSelected) View.VISIBLE else View.INVISIBLE
        }
        historyCount.background = context.shape(context.color(if (showingHistory) R.color.wvi_tint else R.color.wvi_background), radius = 5)
        historyCount.setTextColor(context.color(if (showingHistory) R.color.wvi_primary else R.color.wvi_muted))
        layout.history = showingHistory
        lastState?.let { footer.render(it, showingHistory) }
    }

    private fun createTab(id: Int, label: TextView, badge: TextView? = null, action: () -> Unit): FrameLayout = object : FrameLayout(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val row = getChildAt(0)
            row.measure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            setMeasuredDimension(row.measuredWidth, row.measuredHeight)
            getChildAt(1).measure(MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(context.dp(2), MeasureSpec.EXACTLY))
        }
    }.apply {
        this.id = id
        layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
        addView(LinearLayout(context).apply {
            minimumHeight = context.dp(48)
            gravity = Gravity.CENTER_VERTICAL
            addView(label)
            badge?.let { addView(it, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = context.dp(6) }) }
        }, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        addView(View(context).apply {
            setBackgroundColor(context.color(R.color.wvi_primary))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, FrameLayout.LayoutParams(MATCH_PARENT, context.dp(2), Gravity.BOTTOM))
        ViewCompat.setScreenReaderFocusable(this, true)
    }

    private fun showAbout() {
        if (about?.isShowing == true || released) return
        val mode = if (lastState?.history?.mode == HistoryCaptureMode.OBSERVED_URLS) R.string.wvi_sampled_details else R.string.wvi_capture_details
        about = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.wvi_about_capture)
            .setMessage(context.getString(mode) + "\n\n" + context.getString(R.string.wvi_history_boundary) + "\n\n" + context.getString(R.string.wvi_memory_details))
            .setPositiveButton(android.R.string.ok, null)
            .create().also { child ->
                child.setOnDismissListener {
                    about = null
                    if (!released && layout.isAttachedToWindow) historyTab.requestFocus()
                }
                child.show()
            }
    }

    private fun inspectedId(state: InspectorState?): Long? = state?.selectedId ?: state?.history?.key?.targetId
    private fun preferences(state: InspectorState?): TargetSheetState? = inspectedId(state)?.let(presentation::target)

    private fun saveScroll() {
        val preferences = preferences(lastState) ?: return
        if (presentation.historyTab) history.saveScroll(preferences) else preferences.currentScrollY = layout.scroll.scrollY
    }

    private fun restoreScroll() {
        restoreListener?.removeListener()
        val preferences = preferences(lastState)
        if (presentation.historyTab) history.restoreScroll(preferences)
        else restoreListener = layout.doOnPreDraw { layout.scroll.scrollTo(0, preferences?.currentScrollY ?: 0) }
    }

    /** Called through the session's existing dismissal listener and before host cleanup. Idempotent. */
    fun release() {
        if (released) return
        saveScroll()
        released = true
        restoreListener?.removeListener()
        restoreListener = null
        picker.dismiss()
        footer.dismiss()
        about?.dismiss()
        about = null
    }
}
