package com.webviewinspector.internal.ui.sheet

import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.material.button.MaterialButton
import com.webviewinspector.R
import com.webviewinspector.internal.state.InspectorState
import com.webviewinspector.internal.state.InspectorTab
import com.webviewinspector.internal.ui.common.clearCopyFeedback
import com.webviewinspector.internal.ui.common.color
import com.webviewinspector.internal.ui.common.dp
import com.webviewinspector.internal.ui.common.padding
import com.webviewinspector.internal.ui.common.setCopyAction
import com.webviewinspector.internal.ui.common.shape

/** Pinned native actions. The sheet owns this component and explicitly dismisses its focusable menu. */
internal class InspectorFooter(
    private val context: Context,
    copyUrl: () -> Boolean,
    copyHistory: () -> Boolean,
    clearHistory: () -> Unit,
    copyAppLoads: () -> Boolean,
    clearAppLoads: () -> Unit,
    about: () -> Unit,
) {
    val view =
        LinearLayout(context).apply {
            id = R.id.wvi_sheet_footer
            orientation = LinearLayout.VERTICAL
            addView(context.focusedDivider())
        }
    private val copyUrlButton = primary(R.id.wvi_copy_url, copyUrl)
    private val copyHistoryButton = primary(R.id.wvi_copy_history, copyHistory)
    private val copyAppLoadsButton = primary(R.id.wvi_copy_app_loads, copyAppLoads)
    private val more =
        context.focusedIcon(R.drawable.wvi_ic_more, context.getString(R.string.wvi_more_actions), ::showMenu).apply {
            id = R.id.wvi_more
            background = context.shape(context.color(R.color.wvi_surface), border = context.color(R.color.wvi_border))
        }
    private val menuContent =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            padding(4)
            addView(
                context
                    .focusedButton(context.getString(R.string.wvi_about_capture)) {
                        dismiss()
                        about()
                    }.apply { id = R.id.wvi_about },
            )
        }
    private val clear =
        context
            .focusedButton(context.getString(R.string.wvi_clear_history)) {
                dismiss()
                clearHistory()
            }.apply { id = R.id.wvi_clear_history }
    private val clearLoads =
        context
            .focusedButton(context.getString(R.string.wvi_clear_app_loads)) {
                dismiss()
                clearAppLoads()
            }.apply { id = R.id.wvi_clear_app_loads }
    private var popup: PopupWindow? = null
    private var lastState: InspectorState? = null

    init {
        val actions =
            LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                padding(horizontal = 20, vertical = 12)
                addView(copyUrlButton, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(copyHistoryButton, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(copyAppLoadsButton, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(
                    more,
                    LinearLayout.LayoutParams(context.dp(48), context.dp(48)).apply { marginStart = context.dp(10) },
                )
            }
        view.addView(actions)
        menuContent.addView(clear)
        menuContent.addView(clearLoads)
    }

    fun render(
        state: InspectorState,
        tab: InspectorTab,
    ) {
        val previous = lastState
        if (state.selected != previous?.selected || tab != InspectorTab.CURRENT_URL) copyUrlButton.clearCopyFeedback()
        if (state.history != previous?.history || tab != InspectorTab.HISTORY) copyHistoryButton.clearCopyFeedback()
        if (state.appLoads != previous?.appLoads ||
            tab != InspectorTab.APP_LOADS
        ) {
            copyAppLoadsButton.clearCopyFeedback()
        }
        lastState = state
        val number = state.selected?.number ?: state.historyNumber
        copyUrlButton.text =
            if (state.selected == null && state.inspectedTarget != null) {
                context.getString(R.string.wvi_copy_recorded)
            } else {
                number?.let { context.getString(R.string.wvi_copy_url, it) } ?: context.getString(R.string.wvi_copy)
            }
        copyHistoryButton.text =
            number?.let { context.getString(R.string.wvi_copy_history, it) }
                ?: context.getString(R.string.wvi_copy_history_generic)
        copyAppLoadsButton.text =
            number?.let { context.getString(R.string.wvi_copy_app_loads, it) }
                ?: context.getString(R.string.wvi_copy_app_loads_generic)
        copyUrlButton.isEnabled =
            state.selected?.url != null || (state.selected == null && state.inspectedTarget?.url != null)
        copyHistoryButton.isEnabled = state.history?.records?.isNotEmpty() == true
        clear.isEnabled = copyHistoryButton.isEnabled || (state.history?.discardedCount ?: 0) > 0
        copyAppLoadsButton.isEnabled = state.appLoads?.records?.isNotEmpty() == true
        clearLoads.isEnabled = copyAppLoadsButton.isEnabled || (state.appLoads?.discardedCount ?: 0) > 0
        copyUrlButton.visibility = if (tab == InspectorTab.CURRENT_URL) View.VISIBLE else View.GONE
        copyHistoryButton.visibility = if (tab == InspectorTab.HISTORY) View.VISIBLE else View.GONE
        copyAppLoadsButton.visibility = if (tab == InspectorTab.APP_LOADS) View.VISIBLE else View.GONE
        clear.visibility = if (tab == InspectorTab.HISTORY) View.VISIBLE else View.GONE
        clearLoads.visibility = if (tab == InspectorTab.APP_LOADS) View.VISIBLE else View.GONE
        more.visibility = if (tab == InspectorTab.CURRENT_URL) View.GONE else View.VISIBLE
        if (tab == InspectorTab.CURRENT_URL) dismiss()
    }

    private fun primary(
        id: Int,
        action: () -> Boolean,
    ): MaterialButton =
        context.focusedButton("").apply {
            this.id = id
            maxLines = 2
            cornerRadius = context.dp(12)
            icon = AppCompatResources.getDrawable(context, R.drawable.wvi_ic_copy)
            iconSize = context.dp(18)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            val states = arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf())
            backgroundTintList =
                ColorStateList(
                    states,
                    intArrayOf(context.color(R.color.wvi_background), context.color(R.color.wvi_primary)),
                )
            val foreground =
                ColorStateList(
                    states,
                    intArrayOf(context.color(R.color.wvi_muted), context.color(R.color.wvi_on_primary)),
                )
            setTextColor(foreground)
            iconTint = foreground
            setCopyAction(action)
        }

    private fun showMenu() {
        if (popup?.isShowing == true) return
        (menuContent.parent as? android.view.ViewGroup)?.removeView(menuContent)
        val width = minOf(context.dp(224), view.width - context.dp(40))
        menuContent.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        popup =
            PopupWindow(menuContent, width, WRAP_CONTENT, true).apply {
                enterTransition = null
                exitTransition = null
                setBackgroundDrawable(
                    context.shape(context.color(R.color.wvi_surface), border = context.color(R.color.wvi_border)),
                )
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
