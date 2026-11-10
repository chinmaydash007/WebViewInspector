package com.webviewinspector.internal.ui.common

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.webviewinspector.R

private const val DEFAULT_CORNER_RADIUS_DP = 12
private const val ICON_CORNER_RADIUS_DP = 10
private const val ICON_TOUCH_SIZE_DP = 48
private const val BORDER_WIDTH_DP = 1
private const val DEFAULT_TEXT_SIZE_SP = 14f

/** Uses a complete library theme so platform/ComponentActivity hosts need no Material attributes. */
internal fun Context.inspectorTheme(): Context = ContextThemeWrapper(this, R.style.WviTheme)

internal fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

internal fun Context.color(resourceId: Int): Int = ContextCompat.getColor(this, resourceId)

internal fun View.padding(
    horizontal: Int,
    vertical: Int = horizontal,
) {
    setPadding(context.dp(horizontal), context.dp(vertical), context.dp(horizontal), context.dp(vertical))
}

internal fun Context.shape(
    color: Int,
    radius: Int = DEFAULT_CORNER_RADIUS_DP,
    border: Int? = null,
): GradientDrawable =
    GradientDrawable().also { drawable ->
        drawable.setColor(color)
        drawable.cornerRadius = dp(radius).toFloat()
        border?.let { borderColor -> drawable.setStroke(dp(BORDER_WIDTH_DP), borderColor) }
    }

internal fun Context.label(
    text: CharSequence = "",
    size: Float = DEFAULT_TEXT_SIZE_SP,
    muted: Boolean = false,
): TextView =
    TextView(this).also { label ->
        label.text = text
        label.textSize = size
        label.setTextColor(color(if (muted) R.color.wvi_muted else R.color.wvi_text))
    }

internal fun Context.icon(
    drawable: Int,
    description: String,
    onClick: () -> Unit,
): ImageButton =
    ImageButton(this).also { button ->
        button.setImageResource(drawable)
        button.imageTintList = ColorStateList.valueOf(color(R.color.wvi_primary))
        button.background = shape(color(R.color.wvi_surface), radius = ICON_CORNER_RADIUS_DP)
        button.contentDescription = description
        button.isFocusable = true
        button.setOnClickListener { onClick() }
        button.layoutParams = LinearLayout.LayoutParams(dp(ICON_TOUCH_SIZE_DP), dp(ICON_TOUCH_SIZE_DP))
    }

internal fun TextView.bold(): TextView =
    apply {
        setTypeface(typeface, Typeface.BOLD)
    }
