package com.webviewinspector.internal.ui.common

import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.webviewinspector.R

/**
 * Dialog-owned main-thread scaffold: pinned header/footer and one bounded nested scroller.
 * Current URL freezes its measured height during polling; explicit UI transitions remeasure it.
 * The root gates the whole gesture stream so pinned controls cannot bypass scroll-first dismissal.
 */
internal class InspectorSheetLayout(
    context: Context,
    behavior: BottomSheetBehavior<*>,
) : ViewGroup(context) {
    val scroll =
        SheetScrollView(context, behavior, guardOwnGestures = false).apply {
            id = R.id.wvi_sheet_scroll
            isFillViewport = false
        }
    private val dragGuard = SheetDragGuard(behavior) { scroll.canScrollVertically(-1) }
    private var header: View? = null
    private var footer: View? = null
    private var currentHeight: Int? = null
    private var previousWidth = 0
    private var previousCap = 0
    var history = false
        set(value) {
            if (field != value) {
                field = value
                contentChanged()
            }
        }

    init {
        id = R.id.wvi_sheet
    }

    fun setSections(
        header: View,
        content: View,
        footer: View,
    ) {
        this.header = header
        this.footer = footer
        scroll.addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(footer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    /** Call for user expansion, selection/availability changes, or switching tabs, never every poll. */
    fun contentChanged() {
        currentHeight = null
        requestLayout()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean =
        dragGuard.dispatch(event) { super.dispatchTouchEvent(event) || dragGuard.isBlocking }

    override fun onDetachedFromWindow() {
        dragGuard.release()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val visible = Rect().also(::getWindowVisibleDisplayFrame)
        val windowHeight = visible.height().takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val incomingCap = MeasureSpec.getSize(heightMeasureSpec).takeIf { it > 0 } ?: windowHeight
        val cap = minOf(incomingCap, (windowHeight * MAX_HEIGHT_FRACTION).toInt())
        if (previousWidth != width || previousCap != cap) currentHeight = null
        previousWidth = width
        previousCap = cap
        val exactWidth = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
        val boundedHeight = MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST)
        header?.measure(exactWidth, boundedHeight)
        footer?.measure(exactWidth, boundedHeight)
        val pinnedHeight = (header?.measuredHeight ?: 0) + (footer?.measuredHeight ?: 0)
        val available = (cap - pinnedHeight).coerceAtLeast(0)
        val frozenBody = currentHeight?.let { (it - pinnedHeight).coerceIn(0, available) }
        val bodyHeight = if (history) available else frozenBody ?: available
        val bodyMode = if (history || frozenBody != null) MeasureSpec.EXACTLY else MeasureSpec.AT_MOST
        scroll.measure(exactWidth, MeasureSpec.makeMeasureSpec(bodyHeight, bodyMode))
        val height = (pinnedHeight + scroll.measuredHeight).coerceAtMost(cap)
        if (!history && currentHeight == null) currentHeight = height
        setMeasuredDimension(width, height)
    }

    override fun onLayout(
        changed: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        val headerHeight = header?.measuredHeight ?: 0
        val footerHeight = footer?.measuredHeight ?: 0
        header?.layout(0, 0, width, headerHeight)
        scroll.layout(0, headerHeight, width, height - footerHeight)
        footer?.layout(0, height - footerHeight, width, height)
    }

    private companion object {
        const val MAX_HEIGHT_FRACTION = 0.9f
    }
}
