package io.github.chinmaydash007.webviewinspector.internal.ui.sheet

import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.PopupWindow
import com.google.android.material.button.MaterialButton
import io.github.chinmaydash007.webviewinspector.R
import io.github.chinmaydash007.webviewinspector.internal.state.InspectorState
import io.github.chinmaydash007.webviewinspector.internal.ui.common.color
import io.github.chinmaydash007.webviewinspector.internal.ui.common.dp
import io.github.chinmaydash007.webviewinspector.internal.ui.common.padding
import io.github.chinmaydash007.webviewinspector.internal.ui.common.shape

/** Pinned native actions. The sheet owns this component and explicitly dismisses its focusable menu. */
internal class InspectorFooter(
    private val context: Context,
    copyUrl: () -> Unit,
    copyHistory: () -> Unit,
    clearHistory: () -> Unit,
    about: () -> Unit,
) {
    val view = LinearLayout(context).apply {
        id = R.id.wvi_sheet_footer
        orientation = LinearLayout.VERTICAL
        addView(context.focusedDivider())
    }
    private val copyUrlButton = primary(R.id.wvi_copy_url, copyUrl)
    private val copyHistoryButton = primary(R.id.wvi_copy_history, copyHistory)
    private val more = context.focusedIcon(R.drawable.wvi_ic_more, context.getString(R.string.wvi_more_actions), ::showMenu).apply {
        id = R.id.wvi_more
        background = context.shape(context.color(R.color.wvi_surface), border = context.color(R.color.wvi_border))
    }
    private val menuContent = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        padding(4)
        addView(context.focusedButton(context.getString(R.string.wvi_about_capture)) {
            dismiss()
            about()
        }.apply { id = R.id.wvi_about })
    }
    private val clear = context.focusedButton(context.getString(R.string.wvi_clear_history)) {
        dismiss()
        clearHistory()
    }.apply { id = R.id.wvi_clear_history }
    private var popup: PopupWindow? = null

    init {
        val actions = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            padding(horizontal = 20, vertical = 12)
            addView(copyUrlButton, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(copyHistoryButton, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(more, LinearLayout.LayoutParams(context.dp(48), context.dp(48)).apply { marginStart = context.dp(10) })
        }
        view.addView(actions)
        menuContent.addView(clear)
    }

    fun render(state: InspectorState, historyTab: Boolean) {
        val number = state.selected?.number ?: state.historyNumber
        copyUrlButton.text = number?.let { context.getString(R.string.wvi_copy_url, it) } ?: context.getString(R.string.wvi_copy)
        copyHistoryButton.text = number?.let { context.getString(R.string.wvi_copy_history, it) } ?: context.getString(R.string.wvi_copy_history_generic)
        copyUrlButton.isEnabled = state.selected?.url != null
        copyHistoryButton.isEnabled = state.history?.records?.isNotEmpty() == true
        clear.isEnabled = copyHistoryButton.isEnabled || (state.history?.discardedCount ?: 0) > 0
        copyUrlButton.visibility = if (historyTab) View.GONE else View.VISIBLE
        copyHistoryButton.visibility = if (historyTab) View.VISIBLE else View.GONE
        more.visibility = if (historyTab) View.VISIBLE else View.GONE
        if (!historyTab) dismiss()
    }

    private fun primary(id: Int, action: () -> Unit): MaterialButton = context.focusedButton("", action).apply {
        this.id = id
        maxLines = 2
        cornerRadius = context.dp(12)
        icon = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.wvi_ic_copy)
        iconSize = context.dp(18)
        iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
        val states = arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf())
        backgroundTintList = ColorStateList(states, intArrayOf(context.color(R.color.wvi_background), context.color(R.color.wvi_primary)))
        val foreground = ColorStateList(states, intArrayOf(context.color(R.color.wvi_muted), context.color(R.color.wvi_on_primary)))
        setTextColor(foreground)
        iconTint = foreground
    }

    private fun showMenu() {
        if (popup?.isShowing == true) return
        (menuContent.parent as? android.view.ViewGroup)?.removeView(menuContent)
        val width = minOf(context.dp(224), view.width - context.dp(40))
        menuContent.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        popup = PopupWindow(menuContent, width, WRAP_CONTENT, true).apply {
            enterTransition = null
            exitTransition = null
            setBackgroundDrawable(context.shape(context.color(R.color.wvi_surface), border = context.color(R.color.wvi_border)))
            elevation = context.dp(8).toFloat()
            isOutsideTouchable = true
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            setOnDismissListener {
                popup = null
                if (more.isAttachedToWindow && more.isShown) more.requestFocus()
            }
            showAsDropDown(more, more.width - width, -more.height - menuContent.measuredHeight - context.dp(8))
        }
    }

    fun dismiss() {
        popup?.dismiss()
        popup = null
    }
}
