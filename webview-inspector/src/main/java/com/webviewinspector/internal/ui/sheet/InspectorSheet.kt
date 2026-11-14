package com.webviewinspector.internal.ui.sheet

import android.app.Activity
import android.content.res.ColorStateList
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.OneShotPreDrawListener
import androidx.core.view.ViewCompat
import androidx.core.view.doOnPreDraw
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.webviewinspector.R
import com.webviewinspector.internal.state.AppLoadCopyPart
import com.webviewinspector.internal.state.HistoryCaptureMode
import com.webviewinspector.internal.state.HistoryRecordKind
import com.webviewinspector.internal.state.InspectorSheetState
import com.webviewinspector.internal.state.InspectorState
import com.webviewinspector.internal.state.InspectorTab
import com.webviewinspector.internal.state.TargetSheetState
import com.webviewinspector.internal.ui.common.BottomSheetFactory
import com.webviewinspector.internal.ui.common.InspectorSheetLayout
import com.webviewinspector.internal.ui.common.color
import com.webviewinspector.internal.ui.common.dp
import com.webviewinspector.internal.ui.common.inspectorTheme
import com.webviewinspector.internal.ui.common.padding
import com.webviewinspector.internal.ui.common.shape

private const val HEADER_HORIZONTAL_INSET_DP = 20
private const val CLOSE_ICON_TRAILING_SPACE_DP = 18

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
    copyAppLoad: (Long, AppLoadCopyPart) -> Unit,
    copyAppLoads: () -> Unit,
    clearAppLoads: () -> Unit,
) {
    val dialog = BottomSheetFactory.create(activity.inspectorTheme())
    private val context = dialog.context
    private val layout = InspectorSheetLayout(context, dialog.behavior)
    private val picker = TargetPicker(context, select)
    private val capture = context.focusedLabel(size = 12f, muted = true).apply { id = R.id.wvi_capture_mode }
    private val current = CurrentUrlPanel(context, locate, { switchTab(InspectorTab.HISTORY) }, layout::contentChanged)
    private val history =
        HistoryPanel(context, presentation, layout.scroll, copyRecordedUrl, ::showAbout, layout::contentChanged)
    private val appLoads =
        AppLoadsPanel(
            context = context,
            presentation = presentation,
            scroll = layout.scroll,
            copy = copyAppLoad,
            about = ::showAbout,
            contentChanged = layout::contentChanged,
        )
    private val footer =
        InspectorFooter(
            context = context,
            copyUrl = copy,
            copyHistory = copyHistory,
            clearHistory = clearHistory,
            copyAppLoads = copyAppLoads,
            clearAppLoads = clearAppLoads,
            about = ::showAbout,
        )
    private val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val currentLabel = context.focusedLabel(context.getString(R.string.wvi_current_tab), medium = true)
    private val historyLabel = context.focusedLabel(context.getString(R.string.wvi_history_tab), medium = true)
    private val historyCount =
        context.focusedLabel(size = 12f, muted = true).apply {
            padding(horizontal = 5, vertical = 1)
        }
    private val appLoadsLabel = context.focusedLabel(context.getString(R.string.wvi_app_loads_tab), medium = true)
    private val appLoadsCount =
        context.focusedLabel(size = 12f, muted = true).apply {
            padding(horizontal = 5, vertical = 1)
        }
    private val currentTab = createTab(R.id.wvi_current_tab, currentLabel) { switchTab(InspectorTab.CURRENT_URL) }
    private val historyTab =
        createTab(R.id.wvi_history_tab, historyLabel, historyCount) { switchTab(InspectorTab.HISTORY) }
    private val appLoadsTab =
        createTab(R.id.wvi_app_loads_tab, appLoadsLabel, appLoadsCount) { switchTab(InspectorTab.APP_LOADS) }
    private var lastState: InspectorState? = null
    private var about: AlertDialog? = null
    private var restoreListener: OneShotPreDrawListener? = null
    private var childFocusObserver: ViewTreeObserver? = null
    private var childFocusListener: ViewTreeObserver.OnWindowFocusChangeListener? = null
    private val restoreChildFocus =
        Runnable {
            if (!released && layout.isAttachedToWindow &&
                layout.hasWindowFocus()
            ) {
                selectedTabView().requestFocusFromTouch()
            }
        }
    private var released = false

    init {
        val header =
            LinearLayout(context).apply {
                id = R.id.wvi_sheet_header
                orientation = LinearLayout.VERTICAL
            }
        header.addView(
            View(context).apply {
                background = context.shape(context.color(R.color.wvi_border), radius = 4)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            LinearLayout.LayoutParams(context.dp(34), context.dp(4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = context.dp(10)
                bottomMargin = context.dp(4)
            },
        )
        val heading =
            LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                // Include the space around the centered glyph in the shared header inset.
                setPaddingRelative(
                    context.dp(HEADER_HORIZONTAL_INSET_DP),
                    0,
                    context.dp(HEADER_HORIZONTAL_INSET_DP - CLOSE_ICON_TRAILING_SPACE_DP),
                    0,
                )
                addView(
                    context
                        .focusedLabel(
                            context.getString(R.string.wvi_focused_title),
                            size = 20f,
                            medium = true,
                        ).asHeading(),
                    LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f),
                )
                addView(
                    context.focusedIcon(
                        R.drawable.wvi_ic_close,
                        context.getString(R.string.wvi_close),
                        dialog::dismiss,
                    ),
                )
            }
        header.addView(heading)
        header.addView(
            LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                padding(horizontal = HEADER_HORIZONTAL_INSET_DP, vertical = 0)
                addView(
                    context.focusedLabel(activity.javaClass.simpleName, size = 12f, muted = true).apply {
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    },
                    LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = context.dp(8) },
                )
                addView(capture)
            },
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = context.dp(12) },
        )
        header.addView(
            picker.view,
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                marginStart = context.dp(20)
                marginEnd = context.dp(20)
                bottomMargin = context.dp(8)
            },
        )
        header.addView(
            LinearLayout(context).apply {
                padding(horizontal = 20, vertical = 0)
                gravity = Gravity.BOTTOM
                addView(currentTab)
                addView(historyTab)
                addView(appLoadsTab)
            },
        )
        header.addView(context.focusedDivider())
        content.addView(current.view)
        content.addView(history.view)
        content.addView(appLoads.view)
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
        val mode =
            context.getString(
                when {
                    saved -> R.string.wvi_saved_badge
                    state.history?.mode == HistoryCaptureMode.OBSERVED_URLS -> R.string.wvi_observed_badge
                    else -> R.string.wvi_events_badge
                },
            )
        capture.text =
            context.getString(if (state.appLoads == null) R.string.wvi_basic_badge else R.string.wvi_advanced_badge) +
            " · " +
            mode
        capture.setTextColor(
            context.color(
                if (saved ||
                    state.history?.mode == HistoryCaptureMode.OBSERVED_URLS
                ) {
                    R.color.wvi_muted
                } else {
                    R.color.wvi_success
                },
            ),
        )
        val captureDescription =
            if (saved) {
                context.getString(R.string.wvi_saved_history)
            } else {
                state.history?.let {
                    context.getString(
                        if (it.mode ==
                            HistoryCaptureMode.OBSERVED_URLS
                        ) {
                            R.string.wvi_observed_urls
                        } else {
                            R.string.wvi_navigation_events
                        },
                    )
                } ?: capture.text
            }
        capture.contentDescription =
            context.getString(if (state.appLoads == null) R.string.wvi_basic_badge else R.string.wvi_advanced_badge) +
            ", " +
            captureDescription
        val preferences = preferences(state)
        current.render(state, preferences)
        history.render(state.history, state.historyNumber, live = state.selected != null)
        appLoads.render(state.appLoads, state.historyNumber, live = state.selected != null)
        historyCount.text = (state.history?.records?.count { it.kind != HistoryRecordKind.GAP } ?: 0).toString()
        appLoadsCount.text = (state.appLoads?.records?.size ?: 0).toString()
        footer.render(state, presentation.tab)
        if (previous == null || changedTarget || (previous.selected == null) != (state.selected == null) ||
            (previous.selected?.url == null) != (state.selected?.url == null)
        ) {
            layout.contentChanged()
        }
        if (previous == null || changedTarget) restoreScroll()
    }

    private fun switchTab(tab: InspectorTab) {
        if (presentation.tab == tab) return
        saveScroll()
        presentation.tab = tab
        picker.dismiss()
        renderTabs()
        restoreScroll()
    }

    private fun renderTabs() {
        val showingHistory = presentation.tab == InspectorTab.HISTORY
        val showingLoads = presentation.tab == InspectorTab.APP_LOADS
        current.view.visibility = if (presentation.tab == InspectorTab.CURRENT_URL) View.VISIBLE else View.GONE
        history.view.visibility = if (showingHistory) View.VISIBLE else View.GONE
        appLoads.view.visibility = if (showingLoads) View.VISIBLE else View.GONE
        currentTab.isSelected = presentation.tab == InspectorTab.CURRENT_URL
        historyTab.isSelected = showingHistory
        appLoadsTab.isSelected = showingLoads
        listOf(
            currentTab to currentLabel,
            historyTab to historyLabel,
            appLoadsTab to appLoadsLabel,
        ).forEach { (tab, label) ->
            label.setTextColor(context.color(if (tab.isSelected) R.color.wvi_primary else R.color.wvi_muted))
            tab.getChildAt(1).visibility = if (tab.isSelected) View.VISIBLE else View.INVISIBLE
        }
        listOf(historyCount to showingHistory, appLoadsCount to showingLoads).forEach { (badge, selected) ->
            badge.background =
                context.shape(context.color(if (selected) R.color.wvi_tint else R.color.wvi_background), radius = 5)
            badge.setTextColor(context.color(if (selected) R.color.wvi_primary else R.color.wvi_muted))
        }
        layout.history = showingHistory || showingLoads
        lastState?.let { footer.render(it, presentation.tab) }
    }

    private fun createTab(
        id: Int,
        label: TextView,
        badge: TextView? = null,
        action: () -> Unit,
    ): FrameLayout =
        object : FrameLayout(context) {
            override fun onMeasure(
                widthMeasureSpec: Int,
                heightMeasureSpec: Int,
            ) {
                val row = getChildAt(0) as LinearLayout
                val width = MeasureSpec.getSize(widthMeasureSpec)
                badge?.measure(
                    MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                )
                val contentWidth = (width - context.dp(8)).coerceAtLeast(1)
                // Large fonts keep whole tab words readable by moving the count below the label.
                val stackBadge =
                    badge != null &&
                        label.paint.measureText(label.text.toString()) + badge.measuredWidth + context.dp(6) >
                        contentWidth
                row.orientation = if (stackBadge) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
                val labelWidth =
                    if (stackBadge ||
                        badge == null
                    ) {
                        contentWidth
                    } else {
                        (contentWidth - badge.measuredWidth - context.dp(6)).coerceAtLeast(1)
                    }
                if (label.maxWidth != labelWidth) label.maxWidth = labelWidth
                badge?.let {
                    (it.layoutParams as LinearLayout.LayoutParams).apply {
                        marginStart = if (stackBadge) 0 else context.dp(6)
                        topMargin = if (stackBadge) context.dp(2) else 0
                    }
                }
                row.measure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                )
                setMeasuredDimension(row.measuredWidth, row.measuredHeight)
                getChildAt(1).measure(
                    MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(context.dp(2), MeasureSpec.EXACTLY),
                )
            }
        }.apply {
            this.id = id
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
            addView(
                LinearLayout(context).apply {
                    minimumHeight = context.dp(48)
                    gravity = Gravity.CENTER
                    label.maxLines = 3
                    addView(label)
                    badge?.let {
                        addView(
                            it,
                            LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                                marginStart =
                                    context.dp(6)
                            },
                        )
                    }
                },
                FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT),
            )
            addView(
                View(context).apply {
                    setBackgroundColor(context.color(R.color.wvi_primary))
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                FrameLayout.LayoutParams(MATCH_PARENT, context.dp(2), Gravity.BOTTOM),
            )
            ViewCompat.setScreenReaderFocusable(this, true)
        }

    private fun showAbout() {
        if (about?.isShowing == true || released) return
        clearChildFocusRestore()
        val mode =
            if (lastState?.history?.mode ==
                HistoryCaptureMode.OBSERVED_URLS
            ) {
                R.string.wvi_sampled_details
            } else {
                R.string.wvi_capture_details
            }
        about =
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.wvi_about_capture)
                .setMessage(
                    context.getString(mode) + "\n\n" + context.getString(R.string.wvi_history_boundary) + "\n\n" +
                        context.getString(R.string.wvi_memory_details) +
                        "\n\n" +
                        context.getString(R.string.wvi_app_loads_about),
                ).setPositiveButton(android.R.string.ok, null)
                .create()
                .also { child ->
                    child.setOnDismissListener {
                        about = null
                        restoreAfterChildDismissal()
                    }
                    child.show()
                }
    }

    private fun selectedTabView(): View =
        when (presentation.tab) {
            InspectorTab.CURRENT_URL -> currentTab
            InspectorTab.HISTORY -> historyTab
            InspectorTab.APP_LOADS -> appLoadsTab
        }

    /** About can dismiss before the platform restores its parent's window focus, notably API 24. */
    private fun restoreAfterChildDismissal() {
        clearChildFocusRestore()
        if (released || !layout.isAttachedToWindow) return
        if (layout.hasWindowFocus()) {
            layout.post(restoreChildFocus)
        } else {
            val listener =
                ViewTreeObserver.OnWindowFocusChangeListener { focused ->
                    if (focused) {
                        clearChildFocusRestore()
                        // Run after the platform finishes its own focus restoration for this window.
                        layout.post(restoreChildFocus)
                    }
                }
            childFocusListener = listener
            childFocusObserver = layout.viewTreeObserver.also { it.addOnWindowFocusChangeListener(listener) }
        }
    }

    private fun clearChildFocusRestore() {
        layout.removeCallbacks(restoreChildFocus)
        val listener = childFocusListener
        if (listener != null) childFocusObserver?.takeIf { it.isAlive }?.removeOnWindowFocusChangeListener(listener)
        childFocusListener = null
        childFocusObserver = null
    }

    private fun inspectedId(state: InspectorState?): Long? =
        state?.selectedId ?: state?.history?.key?.targetId ?: state?.appLoads?.key?.targetId

    private fun preferences(state: InspectorState?): TargetSheetState? = inspectedId(state)?.let(presentation::target)

    private fun saveScroll() {
        val preferences = preferences(lastState) ?: return
        when (presentation.tab) {
            InspectorTab.HISTORY -> history.saveScroll(preferences)
            InspectorTab.APP_LOADS -> appLoads.saveScroll(preferences)
            InspectorTab.CURRENT_URL -> preferences.currentScrollY = layout.scroll.scrollY
        }
    }

    private fun restoreScroll() {
        restoreListener?.removeListener()
        val preferences = preferences(lastState)
        when (presentation.tab) {
            InspectorTab.HISTORY -> {
                history.restoreScroll(preferences)
            }

            InspectorTab.APP_LOADS -> {
                appLoads.restoreScroll(preferences)
            }

            InspectorTab.CURRENT_URL -> {
                restoreListener =
                    layout.doOnPreDraw { layout.scroll.scrollTo(0, preferences?.currentScrollY ?: 0) }
            }
        }
    }

    /** Called through the session's existing dismissal listener and before host cleanup. Idempotent. */
    fun release() {
        if (released) return
        saveScroll()
        released = true
        clearChildFocusRestore()
        restoreListener?.removeListener()
        restoreListener = null
        picker.dismiss()
        footer.dismiss()
        about?.dismiss()
        about = null
    }
}
