package com.visit.webviewinspector.internal.ui.sheet

import android.app.Activity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import com.google.android.material.switchmaterial.SwitchMaterial
import com.visit.webviewinspector.R
import com.visit.webviewinspector.internal.ui.common.BottomSheetFactory
import com.visit.webviewinspector.internal.ui.common.bold
import com.visit.webviewinspector.internal.ui.common.dp
import com.visit.webviewinspector.internal.ui.common.inspectorTheme
import com.visit.webviewinspector.internal.ui.common.label
import com.visit.webviewinspector.internal.ui.common.padding

/**
 * Native inspector-toggle content, independent of FragmentManager and the host's theme.
 * Created on the main thread; the process controller owns its dialog and dismisses it on host exit.
 */
internal class SettingsSheet(activity: Activity, enabled: Boolean, change: (Boolean) -> Unit) {
    private val context = activity.inspectorTheme()
    val dialog = BottomSheetFactory.create(context)

    init {
        val body = BottomSheetFactory.createBody(
            context,
            context.getString(R.string.wvi_developer_tools),
            dialog::dismiss,
        )
        body.addView(context.label(context.getString(R.string.wvi_title), size = SECTION_TITLE_SIZE_SP).bold())
        val toggle = SwitchMaterial(context).apply {
            id = R.id.wvi_enabled
            text = context.getString(R.string.wvi_enable)
            isChecked = enabled
            minHeight = context.dp(TOGGLE_HEIGHT_DP)
            setOnCheckedChangeListener { _, checked -> change(checked) }
        }
        body.addView(toggle, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        body.addView(context.label(context.getString(R.string.wvi_enabled_description), muted = true))
        body.addView(context.label(context.getString(R.string.wvi_settings_description), muted = true).apply {
            padding(horizontal = 0, vertical = DESCRIPTION_PADDING_DP)
        })
        dialog.setContentView(BottomSheetFactory.scroll(dialog, body))
    }

    private companion object {
        const val SECTION_TITLE_SIZE_SP = 18f
        const val TOGGLE_HEIGHT_DP = 56
        const val DESCRIPTION_PADDING_DP = 16
    }
}
