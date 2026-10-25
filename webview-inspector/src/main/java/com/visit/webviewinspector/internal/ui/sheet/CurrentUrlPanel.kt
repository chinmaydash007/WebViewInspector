package com.visit.webviewinspector.internal.ui.sheet

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import com.visit.webviewinspector.R
import com.visit.webviewinspector.internal.state.InspectorState
import com.visit.webviewinspector.internal.state.TargetSheetState
import com.visit.webviewinspector.internal.ui.common.color
import com.visit.webviewinspector.internal.ui.common.dp
import com.visit.webviewinspector.internal.ui.common.padding
import com.visit.webviewinspector.internal.ui.common.shape
import com.visit.webviewinspector.internal.util.UrlPresentation

/** Native Current URL/empty states. Owns no WebViews; user expansion requests a bounded remeasure. */
internal class CurrentUrlPanel(
    private val context: Context,
    locate: () -> Unit,
    viewSaved: () -> Unit,
    private val contentChanged: () -> Unit,
) {
    val view = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        padding(20)
    }
    private val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val empty = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        padding(horizontal = 0, vertical = 8)
    }
    private val emptyTitle = context.focusedLabel(medium = true).asHeading()
    private val emptyBody = context.focusedLabel(muted = true)
    private val savedButton = context.focusedButton(context.getString(R.string.wvi_view_saved), viewSaved).apply {
        id = R.id.wvi_view_saved
        setPadding(0, 0, 0, 0)
        gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
    }
    private val url = context.focusedLabel(size = 13f).apply {
        id = R.id.wvi_current_url
        typeface = Typeface.MONOSPACE
        setTextIsSelectable(true)
        setLineSpacing(context.dp(4).toFloat(), 1f)
        padding(14)
        background = context.shape(context.color(R.color.wvi_background), border = context.color(R.color.wvi_border))
    }
    private val locateButton = context.focusedButton(context.getString(R.string.wvi_locate), locate).apply {
        id = R.id.wvi_locate
        icon = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.wvi_ic_scan)
        iconSize = context.dp(18)
        strokeWidth = context.dp(1)
        strokeColor = android.content.res.ColorStateList.valueOf(context.color(R.color.wvi_border))
        cornerRadius = context.dp(10)
    }
    private val queryToggle = context.focusedButton("", ::toggleQuery).apply {
        id = R.id.wvi_query_toggle
        gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
        setPadding(0, 0, 0, 0)
    }
    private val parameters = LinearLayout(context).apply {
        id = R.id.wvi_query_values
        orientation = LinearLayout.VERTICAL
    }
    private var preferences: TargetSheetState? = null
    private var previousUrl: String? = null
    private var hadUrl = false

    init {
        content.addView(context.focusedLabel(context.getString(R.string.wvi_full_url), size = 12f, medium = true).asHeading(), spaced(bottom = 10))
        content.addView(url, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        content.addView(context.focusedLabel(context.getString(R.string.wvi_exact_url_note), size = 12f, muted = true), spaced(top = 8, bottom = 12))
        content.addView(locateButton, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        content.addView(context.focusedDivider(), spaced(top = 20, height = 1))
        content.addView(queryToggle)
        content.addView(parameters)
        empty.addView(emptyTitle, spaced(bottom = 10))
        empty.addView(emptyBody, spaced(bottom = 14))
        empty.addView(savedButton, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        view.addView(content)
        view.addView(empty)
    }

    fun render(state: InspectorState, preferences: TargetSheetState?) {
        this.preferences = preferences
        val target = state.selected
        val rawUrl = target?.url
        val hasUrl = rawUrl != null
        content.visibility = if (hasUrl) View.VISIBLE else View.GONE
        empty.visibility = if (hasUrl) View.GONE else View.VISIBLE
        locateButton.isEnabled = target != null
        locateButton.visibility = if (target != null) View.VISIBLE else View.GONE
        val locateParent = if (hasUrl) content else empty
        if (locateButton.parent !== locateParent) {
            (locateButton.parent as? android.view.ViewGroup)?.removeView(locateButton)
            locateParent.addView(locateButton, if (hasUrl) 3 else 2, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        }
        if (hasUrl) {
            if (rawUrl != previousUrl || !hadUrl) {
                url.text = rawUrl
                renderQuery(rawUrl)
            }
        } else {
            val returned = state.targets.any { it.id == state.history?.key?.targetId }
            emptyTitle.text = when {
                target != null -> context.getString(R.string.wvi_no_url)
                state.historyNumber != null && !returned -> context.getString(R.string.wvi_unavailable_title, state.historyNumber)
                else -> context.getString(R.string.wvi_unselected_title)
            }
            val saved = state.history?.records?.isNotEmpty() == true
            emptyBody.text = context.getString(if (target != null) R.string.wvi_no_url_body else R.string.wvi_unavailable_body) +
                if (saved) "\n" + context.getString(R.string.wvi_saved_available) else ""
            savedButton.visibility = if (saved) View.VISIBLE else View.GONE
        }
        previousUrl = rawUrl
        hadUrl = hasUrl
        renderExpansion()
    }

    private fun renderQuery(rawUrl: String?) {
        val entries = UrlPresentation.queryParameters(rawUrl)
        queryToggle.text = context.getString(R.string.wvi_parameters, entries.size)
        queryToggle.visibility = if (entries.isEmpty()) View.GONE else View.VISIBLE
        parameters.removeAllViews()
        parameters.addView(context.focusedLabel(context.getString(R.string.wvi_decoded_query), size = 12f, muted = true), spaced(bottom = 8))
        entries.forEach { entry ->
            parameters.addView(context.focusedDivider())
            parameters.addView(context.focusedLabel(entry.name, size = 12f, muted = true), spaced(top = 10, bottom = 4))
            parameters.addView(context.focusedLabel(entry.value, size = 13f).apply {
                typeface = Typeface.MONOSPACE
                setTextIsSelectable(true)
            }, spaced(bottom = 10))
        }
    }

    private fun toggleQuery() {
        preferences?.let { it.queryExpanded = !it.queryExpanded }
        renderExpansion()
        contentChanged()
    }

    private fun renderExpansion() {
        val expanded = preferences?.queryExpanded == true && queryToggle.visibility == View.VISIBLE
        parameters.visibility = if (expanded) View.VISIBLE else View.GONE
        queryToggle.isSelected = expanded
        androidx.core.view.ViewCompat.setStateDescription(queryToggle, context.getString(if (expanded) R.string.wvi_expanded else R.string.wvi_collapsed))
    }

    private fun spaced(top: Int = 0, bottom: Int = 0, height: Int = WRAP_CONTENT) =
        LinearLayout.LayoutParams(MATCH_PARENT, if (height >= 0) context.dp(height) else height).apply {
            topMargin = context.dp(top)
            bottomMargin = context.dp(bottom)
        }
}
