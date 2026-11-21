package com.webviewinspector.internal.ui.sheet

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.webviewinspector.R
import com.webviewinspector.internal.ui.common.color
import com.webviewinspector.internal.ui.common.dp
import com.webviewinspector.internal.ui.common.padding
import com.webviewinspector.internal.ui.common.shape

private const val PRESS_FADE_IN_MS = 60
private const val PRESS_FADE_OUT_MS = 100
private const val PRESS_TINT_ALPHA = 12

/** Sheet-owned chooser segments; rendering counts never replaces a focused tab. */
internal class WebViewsTabs(
    context: Context,
    select: (Boolean) -> Unit,
) {
    private val live = Segment(context, R.id.wvi_live_tab, R.string.wvi_live_label, 0) { select(false) }
    private val recent = Segment(context, R.id.wvi_recent_tab, R.string.wvi_recent_label, 1) { select(true) }
    private val track =
        SlidingTabTrack(
            context,
            LinearLayout(context).apply {
                isBaselineAligned = false
                addView(live, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f))
                addView(recent, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f))
                ViewCompat.setAccessibilityDelegate(
                    this,
                    object : AccessibilityDelegateCompat() {
                        override fun onInitializeAccessibilityNodeInfo(
                            host: View,
                            info: AccessibilityNodeInfoCompat,
                        ) {
                            super.onInitializeAccessibilityNodeInfo(host, info)
                            info.setCollectionInfo(
                                AccessibilityNodeInfoCompat.CollectionInfoCompat.obtain(
                                    1,
                                    2,
                                    false,
                                    AccessibilityNodeInfoCompat.CollectionInfoCompat.SELECTION_MODE_SINGLE,
                                ),
                            )
                        }
                    },
                )
            },
        )
    val view =
        LinearLayout(context).apply {
            padding(horizontal = 20, vertical = 4)
            addView(
                track,
                LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT),
            )
        }

    fun render(
        liveCount: Int,
        recentCount: Int,
        showingRecent: Boolean,
    ) {
        live.render(liveCount, !showingRecent, R.string.wvi_live_count)
        recent.render(recentCount, showingRecent, R.string.wvi_recent_count)
        track.select(if (showingRecent) recent else live)
    }

    private class Segment(
        context: Context,
        id: Int,
        title: Int,
        index: Int,
        action: () -> Unit,
    ) : FrameLayout(context) {
        private val label = context.focusedLabel(context.getString(title), medium = true)
        private val count =
            context.focusedLabel(size = 12f, medium = true).apply {
                gravity = Gravity.CENTER
                padding(horizontal = 6, vertical = 2)
                minimumWidth = context.dp(20)
                maxLines = 1
            }
        private val content =
            LinearLayout(context).apply {
                gravity = Gravity.CENTER
                padding(horizontal = 12, vertical = 10)
                minimumHeight = context.dp(48)
                label.maxLines = 1
                addView(label)
                addView(count, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
            }

        init {
            this.id = id
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
            background =
                InsetDrawable(
                    StateListDrawable().apply {
                        addState(
                            intArrayOf(android.R.attr.state_focused),
                            context.shape(Color.TRANSPARENT, radius = 8, border = context.color(R.color.wvi_primary)),
                        )
                        addState(intArrayOf(), context.shape(Color.TRANSPARENT, radius = 8))
                    },
                    context.dp(4),
                )
            // The inset is visual; it must not shift FrameLayout's measured content.
            setPadding(0, 0, 0, 0)
            foreground =
                InsetDrawable(
                    StateListDrawable().apply {
                        setEnterFadeDuration(PRESS_FADE_IN_MS)
                        setExitFadeDuration(PRESS_FADE_OUT_MS)
                        addState(
                            intArrayOf(android.R.attr.state_pressed),
                            context.shape(
                                ColorUtils.setAlphaComponent(context.color(R.color.wvi_text), PRESS_TINT_ALPHA),
                                radius = 8,
                            ),
                        )
                        addState(intArrayOf(), context.shape(Color.TRANSPARENT, radius = 8))
                    },
                    context.dp(4),
                )
            addView(content, LayoutParams(MATCH_PARENT, MATCH_PARENT))
            label.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            count.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            ViewCompat.setScreenReaderFocusable(this, true)
            ViewCompat.setAccessibilityDelegate(
                this,
                object : AccessibilityDelegateCompat() {
                    override fun onInitializeAccessibilityNodeInfo(
                        host: View,
                        info: AccessibilityNodeInfoCompat,
                    ) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.roleDescription = context.getString(R.string.wvi_tab_role)
                        info.setCollectionItemInfo(
                            AccessibilityNodeInfoCompat.CollectionItemInfoCompat.obtain(
                                0,
                                1,
                                index,
                                1,
                                false,
                                isSelected,
                            ),
                        )
                    }
                },
            )
        }

        fun render(
            value: Int,
            selected: Boolean,
            description: Int,
        ) {
            val number = context.getString(R.string.wvi_tab_count, value)
            if (count.text != number) count.text = number
            if (isSelected != selected || count.background == null) {
                isSelected = selected
                val textColor = context.color(if (selected) R.color.wvi_primary else R.color.wvi_muted)
                label.setTextColor(textColor)
                count.setTextColor(textColor)
                count.background =
                    context.shape(context.color(if (selected) R.color.wvi_tint else R.color.wvi_surface), radius = 5)
            }
            contentDescription = context.getString(description, value)
            ViewCompat.setStateDescription(
                this,
                context.getString(if (selected) R.string.wvi_selected else R.string.wvi_unselected),
            )
        }

        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
        ) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val available = (width - content.paddingLeft - content.paddingRight).coerceAtLeast(1)
            count.measure(
                MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            )
            val stack = label.paint.measureText(label.text.toString()) + count.measuredWidth + context.dp(6) > available
            content.orientation = if (stack) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            val labelWidth =
                if (stack) available else (available - count.measuredWidth - context.dp(6)).coerceAtLeast(1)
            if (label.maxWidth != labelWidth) label.maxWidth = labelWidth
            (count.layoutParams as LinearLayout.LayoutParams).apply {
                marginStart = if (stack) 0 else context.dp(6)
                topMargin = if (stack) context.dp(2) else 0
                resolveLayoutDirection(this@Segment.layoutDirection)
            }
            content.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            )
            val height = resolveSizeAndState(content.measuredHeight, heightMeasureSpec, 0)
            content.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
            )
            setMeasuredDimension(width, height)
        }
    }
}
