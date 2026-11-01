package io.github.chinmaydash007.webviewinspector.internal.ui.sheet

import android.content.Context
import android.content.res.ColorStateList
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import io.github.chinmaydash007.webviewinspector.R
import io.github.chinmaydash007.webviewinspector.internal.state.InspectorState
import io.github.chinmaydash007.webviewinspector.internal.state.TargetInfo
import io.github.chinmaydash007.webviewinspector.internal.ui.common.color
import io.github.chinmaydash007.webviewinspector.internal.ui.common.dp
import io.github.chinmaydash007.webviewinspector.internal.ui.common.padding
import io.github.chinmaydash007.webviewinspector.internal.ui.common.shape
import io.github.chinmaydash007.webviewinspector.internal.util.UrlPresentation

/**
 * Dialog-owned native picker; all rendering/callbacks run on main. Rows use instance IDs.
 * Saved summaries come from the bounded history snapshot, not a second raw-URL cache.
 * [dismiss] releases its focusable popup when the inspector or host exits.
 */
internal class TargetPicker(private val context: Context, private val select: (Long) -> Unit) {
    val view = LinearLayout(context).apply {
        id = R.id.wvi_target_picker
        gravity = Gravity.CENTER_VERTICAL
        padding(horizontal = 12, vertical = 8)
        minimumHeight = context.dp(64)
        background = context.shape(context.color(R.color.wvi_surface), border = context.color(R.color.wvi_border))
        isFocusable = true
        setOnClickListener { show() }
        ViewCompat.setScreenReaderFocusable(this, true)
    }
    private val number = context.focusedLabel(size = 14f, medium = true).apply {
        gravity = Gravity.CENTER
        setTextColor(context.color(R.color.wvi_primary))
        background = context.shape(context.color(R.color.wvi_tint), radius = 8)
    }
    private val caption = context.focusedLabel(size = 12f, muted = true)
    private val domain = context.focusedLabel(size = 14f, medium = true).apply {
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    private val arrow = ImageView(context).apply {
        setImageResource(R.drawable.wvi_ic_chevron)
        imageTintList = ColorStateList.valueOf(context.color(R.color.wvi_muted))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val rows = LinearLayout(context).apply {
        id = R.id.wvi_targets
        orientation = LinearLayout.VERTICAL
    }
    private val rowViews = mutableMapOf<Long, LinearLayout>()
    private var state: InspectorState? = null
    private var popup: PopupWindow? = null

    init {
        view.addView(number, LinearLayout.LayoutParams(context.dp(28), context.dp(28)).apply { marginEnd = context.dp(10) })
        view.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(caption)
            addView(domain)
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        view.addView(arrow, LinearLayout.LayoutParams(context.dp(18), context.dp(18)).apply { marginStart = context.dp(8) })
    }

    fun render(state: InspectorState) {
        this.state = state
        val target = state.selected
        val inspectedId = target?.id ?: state.history?.key?.targetId
        val inspectedNumber = target?.number ?: state.historyNumber
        val rawUrl = target?.url ?: state.history?.records?.firstNotNullOfOrNull { it.rawUrl }
        val returned = state.targets.any { it.id == inspectedId }
        number.text = inspectedNumber?.toString() ?: "—"
        caption.text = when {
            target != null -> context.getString(R.string.wvi_selection, target.number, state.targets.size)
            inspectedNumber != null -> context.getString(R.string.wvi_view_number, inspectedNumber) + " · " +
                context.getString(if (returned) R.string.wvi_reselect else R.string.wvi_not_visible)
            else -> context.getString(R.string.wvi_choose)
        }
        domain.text = rawUrl?.let(UrlPresentation::domain) ?: context.getString(R.string.wvi_last_inspected)
        val canChoose = state.targets.size > 1 || (state.selected == null && state.targets.isNotEmpty())
        view.isClickable = canChoose
        arrow.visibility = if (canChoose) View.VISIBLE else View.GONE
        view.contentDescription = if (canChoose) {
            context.getString(R.string.wvi_choose) + ", " + caption.text + ", " + domain.text
        } else caption.text.toString() + ", " + domain.text
        if (state.targets.isEmpty()) dismiss()
        updateRows(state)
    }

    private fun updateRows(state: InspectorState) {
        val ids = state.targets.map { it.id }.toSet()
        rowViews.keys.filter { it !in ids }.forEach { id -> rows.removeView(rowViews.remove(id)) }
        state.targets.forEachIndexed { index, target ->
            val row = rowViews.getOrPut(target.id) { createRow(target.id) }
            if (row.parent == null) rows.addView(row, index)
            else if (rows.indexOfChild(row) != index) {
                rows.removeView(row)
                rows.addView(row, index)
            }
            (row.getChildAt(0) as android.widget.TextView).text =
                context.getString(R.string.wvi_view_number, target.number) + " · " + target.domain
            (row.getChildAt(1) as android.widget.TextView).text = target.path
            row.isSelected = target.id == state.selectedId
            row.background = context.shape(context.color(if (row.isSelected) R.color.wvi_tint else R.color.wvi_surface), radius = 8)
            row.contentDescription = "WebView ${target.number}, ${target.domain}, ${target.path}"
        }
    }

    private fun createRow(id: Long): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        minimumHeight = context.dp(64)
        padding(horizontal = 12, vertical = 10)
        isFocusable = true
        isClickable = true
        addView(context.focusedLabel(medium = true))
        addView(context.focusedLabel(size = 12f, muted = true).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        })
        setOnClickListener {
            dismiss()
            select(id)
        }
        ViewCompat.setScreenReaderFocusable(this, true)
    }

    private fun show() {
        if (popup?.isShowing == true || state?.targets.isNullOrEmpty()) return
        (rows.parent as? android.view.ViewGroup)?.removeView(rows)
        val scroller = ScrollView(context).apply { addView(rows) }
        val width = view.width
        rows.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        popup = PopupWindow(scroller, width, minOf(rows.measuredHeight, context.dp(360)), true).apply {
            enterTransition = null
            exitTransition = null
            setBackgroundDrawable(context.shape(context.color(R.color.wvi_surface), border = context.color(R.color.wvi_border)))
            elevation = context.dp(8).toFloat()
            isOutsideTouchable = true
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            setOnDismissListener {
                popup = null
                if (view.isAttachedToWindow) view.requestFocus()
            }
            showAsDropDown(view, 0, context.dp(4), Gravity.START)
        }
    }

    fun dismiss() {
        popup?.dismiss()
        popup = null
    }
}
