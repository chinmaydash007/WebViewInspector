package com.webviewinspector.internal.ui.sheet

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.webviewinspector.R
import com.webviewinspector.internal.ui.common.color
import com.webviewinspector.internal.ui.common.dp
import com.webviewinspector.internal.ui.common.shape

private const val INDICATOR_INSET_DP = 4
private const val INDICATOR_SLIDE_MS = 200L

/** Owns one decorative sliding surface behind fixed tabs; releases motion on hide/detach. */
internal class SlidingTabTrack(
    context: Context,
    private val tabs: LinearLayout,
) : FrameLayout(context) {
    private val inset = context.dp(INDICATOR_INSET_DP)
    private val indicator =
        View(context).apply {
            id = R.id.wvi_tab_indicator
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            background =
                context.shape(
                    context.color(R.color.wvi_surface),
                    radius = 8,
                    border = context.color(R.color.wvi_border),
                )
            elevation = context.dp(1).toFloat()
        }
    private var selected: View? = null
    private var movement: ObjectAnimator? = null
    private var geometry: Geometry? = null

    init {
        background = context.shape(context.color(R.color.wvi_background), radius = 12)
        // Keep text and press/focus feedback above the indicator's raised surface.
        tabs.elevation = context.dp(2).toFloat()
        addView(indicator, LayoutParams(0, 0))
        addView(tabs, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    fun select(tab: View) {
        if (selected === tab) return
        selected = tab
        if (geometry == null) {
            requestLayout()
            return
        }
        stopMovement()
        val destination = (tabs.left + tab.left).toFloat()
        if (isShown && motionEnabled() && indicator.translationX != destination) {
            movement =
                ObjectAnimator.ofFloat(indicator, View.TRANSLATION_X, indicator.translationX, destination).apply {
                    duration = INDICATOR_SLIDE_MS
                    interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
                    start()
                }
        } else {
            indicator.translationX = destination
        }
        // Equal-weight tabs can differ by a pixel; retain that exact measured width.
        requestLayout()
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val tab = selected ?: tabs.getChildAt(0)
        indicator.measure(
            MeasureSpec.makeMeasureSpec((tab.measuredWidth - 2 * inset).coerceAtLeast(0), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec((tabs.measuredHeight - 2 * inset).coerceAtLeast(0), MeasureSpec.EXACTLY),
        )
    }

    override fun onLayout(
        changed: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        super.onLayout(changed, left, top, right, bottom)
        indicator.layout(
            inset,
            tabs.top + inset,
            inset + indicator.measuredWidth,
            tabs.top + inset + indicator.measuredHeight,
        )
        val next = Geometry(tabs.width, tabs.height, layoutDirection)
        if (geometry != next) {
            geometry = next
            finishMovement()
        } else if (movement?.isRunning != true) {
            snapToSelection()
        }
    }

    override fun onVisibilityChanged(
        changedView: View,
        visibility: Int,
    ) {
        super.onVisibilityChanged(changedView, visibility)
        if (!isShown) finishMovement()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != VISIBLE) finishMovement()
    }

    override fun onDetachedFromWindow() {
        finishMovement()
        geometry = null
        super.onDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Reattaching at unchanged bounds must still rebuild the initial geometry.
        requestLayout()
    }

    private fun finishMovement() {
        stopMovement()
        snapToSelection()
    }

    private fun stopMovement() {
        movement?.cancel()
        movement = null
    }

    private fun snapToSelection() {
        selected?.let { indicator.translationX = (tabs.left + it.left).toFloat() }
    }

    private fun motionEnabled(): Boolean =
        if (Build.VERSION.SDK_INT >= 26) {
            ValueAnimator.areAnimatorsEnabled()
        } else {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
        }

    private data class Geometry(
        val width: Int,
        val height: Int,
        val direction: Int,
    )
}
