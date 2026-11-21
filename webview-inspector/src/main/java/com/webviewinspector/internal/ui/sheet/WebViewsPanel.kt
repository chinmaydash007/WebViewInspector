package com.webviewinspector.internal.ui.sheet

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import com.webviewinspector.R
import com.webviewinspector.internal.state.InspectorSheetState
import com.webviewinspector.internal.state.InspectorState
import com.webviewinspector.internal.state.SavedTargetInfo
import com.webviewinspector.internal.ui.common.color
import com.webviewinspector.internal.ui.common.dp
import com.webviewinspector.internal.ui.common.padding
import com.webviewinspector.internal.ui.common.shape
import com.webviewinspector.internal.util.UrlPresentation
import java.text.DateFormat
import java.util.Date

/** Keyed native chooser rows. The parent owns scrolling, focus, measurement, and gestures. */
internal class WebViewsPanel(
    private val context: Context,
    private val presentation: InspectorSheetState,
    private val select: (Long) -> Unit,
    private val changed: () -> Unit,
    clearRecent: () -> Unit,
) {
    val tabs = LinearLayout(context).apply { padding(horizontal = 20, vertical = 4) }
    val view =
        LinearLayout(context).apply {
            id = R.id.wvi_webviews_panel
            orientation = LinearLayout.VERTICAL
            padding(horizontal = 20, vertical = 12)
        }
    val clear =
        context.focusedButton(context.getString(R.string.wvi_clear_recent), clearRecent).apply {
            id =
                R.id.wvi_clear_recent
        }
    private val liveTab = tab(R.id.wvi_live_tab) { presentation.recentTargets = false }
    private val recentTab = tab(R.id.wvi_recent_tab) { presentation.recentTargets = true }
    private val rows =
        LinearLayout(context).apply {
            id = R.id.wvi_targets
            orientation = LinearLayout.VERTICAL
        }
    private val empty =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            padding(12)
        }
    private val emptyTitle = context.focusedLabel(medium = true).asHeading()
    private val emptyBody = context.focusedLabel(muted = true)
    private val viewRecent =
        context
            .focusedButton(context.getString(R.string.wvi_view_recent)) {
                presentation.recentTargets = true
                renderLast()
            }.apply { id = R.id.wvi_view_recent }
    private val rowViews = mutableMapOf<Long, LinearLayout>()
    private var state: InspectorState? = null

    init {
        tabs.addView(liveTab, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        tabs.addView(recentTab, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        empty.addView(emptyTitle)
        empty.addView(emptyBody)
        empty.addView(viewRecent)
        view.addView(empty)
        view.addView(rows)
    }

    private fun tab(
        id: Int,
        action: () -> Unit,
    ) = context
        .focusedButton("") {
            action()
            renderLast()
        }.apply { this.id = id }

    private fun renderLast() {
        state?.let(::render)
        changed()
    }

    fun render(state: InspectorState) {
        this.state = state
        liveTab.text = context.getString(R.string.wvi_live_count, state.targets.size)
        recentTab.text = context.getString(R.string.wvi_recent_count, state.recentTargets.size)
        selectTab(liveTab, !presentation.recentTargets)
        selectTab(recentTab, presentation.recentTargets)
        val items =
            if (presentation.recentTargets) {
                state.recentTargets.map(::savedRow)
            } else {
                state.targets.map { Row(it.id, it.number, it.url, context.getString(R.string.wvi_visible_now)) }
            }
        val ids = items.map { it.id }.toSet()
        rowViews.keys
            .filter { it !in ids }
            .toList()
            .forEach { rows.removeView(rowViews.remove(it)) }
        items.forEachIndexed { index, item ->
            val row = rowViews.getOrPut(item.id) { row(item.id) }
            if (row.parent == null) {
                rows.addView(row, index)
            } else if (rows.indexOfChild(row) != index) {
                rows.removeView(row)
                rows.addView(row, index)
            }
            val number = context.getString(R.string.wvi_view_number, item.number)
            (row.getChildAt(0) as android.widget.TextView).text = number + " · " + UrlPresentation.domain(item.url)
            (row.getChildAt(1) as android.widget.TextView).text =
                if (item.url == null) {
                    context.getString(R.string.wvi_no_recorded_url)
                } else {
                    UrlPresentation.path(item.url)
                }
            (row.getChildAt(2) as android.widget.TextView).text = item.metadata
            val domain = UrlPresentation.domain(item.url)
            val path = UrlPresentation.path(item.url)
            row.contentDescription = "WebView ${item.number}, $domain, $path"
        }
        rows.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        emptyTitle.setText(if (presentation.recentTargets) R.string.wvi_no_recent else R.string.wvi_no_live)
        emptyBody.setText(if (presentation.recentTargets) R.string.wvi_no_recent_body else R.string.wvi_no_live_body)
        viewRecent.visibility =
            if (!presentation.recentTargets && state.recentTargets.isNotEmpty()) View.VISIBLE else View.GONE
        clear.visibility = if (presentation.recentTargets) View.VISIBLE else View.GONE
        clear.isEnabled = state.recentTargets.isNotEmpty()
    }

    private fun savedRow(saved: SavedTargetInfo): Row {
        val captureLabel = if (saved.navigationObserved) R.string.wvi_not_visible else R.string.wvi_app_supplied_only
        val metadata =
            listOfNotNull(
                saved.activityName,
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(saved.atMillis)),
                context.getString(captureLabel),
                context.getString(R.string.wvi_record_counts, saved.historyCount, saved.appLoadCount),
            ).joinToString(" · ")
        return Row(saved.key.targetId, saved.number, saved.url, metadata)
    }

    private fun row(id: Long) =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            padding(horizontal = 12, vertical = 16)
            minimumHeight = context.dp(88)
            background = context.shape(context.color(R.color.wvi_background), radius = 10)
            isClickable = true
            isFocusable = true
            addView(context.focusedLabel(medium = true))
            addView(context.focusedLabel(size = 13f))
            addView(context.focusedLabel(size = 12f, muted = true))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = context.dp(10) }
            setOnClickListener { select(id) }
            ViewCompat.setScreenReaderFocusable(this, true)
        }

    private fun selectTab(
        tab: com.google.android.material.button.MaterialButton,
        selected: Boolean,
    ) {
        tab.isSelected = selected
        val backgroundColor = if (selected) R.color.wvi_tint else R.color.wvi_background
        tab.backgroundTintList = ColorStateList.valueOf(context.color(backgroundColor))
        val selectedLabel = if (selected) R.string.wvi_selected else R.string.wvi_unselected
        ViewCompat.setStateDescription(tab, context.getString(selectedLabel))
    }

    private data class Row(
        val id: Long,
        val number: Int,
        val url: String?,
        val metadata: String,
    )
}
