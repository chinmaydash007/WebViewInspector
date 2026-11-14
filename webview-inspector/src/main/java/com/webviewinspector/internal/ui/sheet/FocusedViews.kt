package com.webviewinspector.internal.ui.sheet

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.webviewinspector.R
import com.webviewinspector.internal.ui.common.color
import com.webviewinspector.internal.ui.common.dp
import com.webviewinspector.internal.ui.common.label
import com.webviewinspector.internal.ui.common.shape

/** Inspector-only styling. Existing floating/settings helpers deliberately keep their appearance. */
internal fun Context.focusedLabel(
    text: CharSequence = "",
    size: Float = 14f,
    muted: Boolean = false,
    medium: Boolean = false,
): TextView =
    label(text, size, muted).apply {
        includeFontPadding = false
        setLineSpacing(dp(2).toFloat(), 1f)
        if (medium) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

internal fun Context.focusedIcon(
    drawable: Int,
    description: String,
    action: (() -> Unit)? = null,
): ImageButton =
    ImageButton(this).apply {
        setImageResource(drawable)
        imageTintList = ColorStateList.valueOf(color(R.color.wvi_muted))
        scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        setPadding(dp(14), dp(14), dp(14), dp(14))
        contentDescription = description
        background =
            RippleDrawable(ColorStateList.valueOf(color(R.color.wvi_tint)), null, shape(color(R.color.wvi_surface)))
        if (action != null) setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
    }

internal fun Context.focusedButton(
    text: String,
    action: (() -> Unit)? = null,
): MaterialButton =
    MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
        this.text = text
        isAllCaps = false
        letterSpacing = 0f
        textSize = 14f
        minHeight = dp(48)
        minimumHeight = dp(48)
        insetTop = 0
        insetBottom = 0
        setTextColor(color(R.color.wvi_primary))
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        if (action != null) setOnClickListener { action() }
    }

internal fun Context.focusedDivider(): View =
    View(this).apply {
        setBackgroundColor(color(R.color.wvi_border))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

internal fun TextView.asHeading(): TextView =
    apply {
        androidx.core.view.ViewCompat
            .setAccessibilityHeading(this, true)
    }
