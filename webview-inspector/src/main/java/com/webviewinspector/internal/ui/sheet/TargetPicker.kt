package com.webviewinspector.internal.ui.sheet

import android.content.Context
import com.webviewinspector.R
import com.webviewinspector.internal.state.InspectorState
import com.webviewinspector.internal.util.UrlPresentation

/** Selected-target summary and return action for the shared Live/Recent chooser. */
internal class TargetPicker(
    private val context: Context,
    choose: () -> Unit,
) {
    val view =
        context.focusedButton(context.getString(R.string.wvi_webviews), choose).apply {
            id = R.id.wvi_target_picker
            maxLines = 4
            gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
        }

    fun render(state: InspectorState) {
        val selected = state.selected
        val saved = state.inspectedTarget
        val number = selected?.number ?: saved?.number ?: state.historyNumber
        val inspectedId = saved?.key?.targetId ?: state.history?.key?.targetId ?: state.appLoads?.key?.targetId
        val availability =
            when {
                selected != null -> context.getString(R.string.wvi_visible_now)
                state.targets.any { it.id == inspectedId } -> context.getString(R.string.wvi_reselect)
                else -> context.getString(R.string.wvi_not_visible)
            }
        view.text =
            listOfNotNull(
                context.getString(R.string.wvi_webviews),
                number?.let { context.getString(R.string.wvi_view_number, it) + " · " + availability },
                (selected?.url ?: saved?.url)?.let(UrlPresentation::domain),
                saved?.activityName,
            ).joinToString("\n")
    }
}
