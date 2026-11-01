package io.github.chinmaydash007.webviewinspector.internal.ui.floating

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.TextUtils
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import io.github.chinmaydash007.webviewinspector.R
import io.github.chinmaydash007.webviewinspector.internal.state.FloatingPosition
import io.github.chinmaydash007.webviewinspector.internal.state.InspectorState
import io.github.chinmaydash007.webviewinspector.internal.ui.common.bold
import io.github.chinmaydash007.webviewinspector.internal.ui.common.color
import io.github.chinmaydash007.webviewinspector.internal.ui.common.dp
import io.github.chinmaydash007.webviewinspector.internal.ui.common.icon
import io.github.chinmaydash007.webviewinspector.internal.ui.common.label
import io.github.chinmaydash007.webviewinspector.internal.ui.common.padding
import io.github.chinmaydash007.webviewinspector.internal.ui.common.shape

/**
 * Renders the floating selection/quick-copy control and a non-interactive Locate outline.
 * The Activity session attaches/removes this View on the main thread. Touches outside the control
 * pass through to host content; movement is delegated to [FloatingDragController].
 */
@SuppressLint("ViewConstructor")
internal class FloatingOverlay(
    context: Context,
    position: FloatingPosition,
    open: () -> Unit,
    copy: () -> Unit,
) : FrameLayout(context) {
    private val control = LinearLayout(context)
    private val title = context.label(size = TITLE_SIZE_SP).bold()
    private val domain = context.label(size = DOMAIN_SIZE_SP, muted = true)
    private val opener = LinearLayout(context)
    private val copyButton = context.icon(R.drawable.wvi_ic_copy, context.getString(R.string.wvi_copy), copy)
    private val grip = context.label("⠿", size = GRIP_SIZE_SP, muted = true).apply {
        gravity = Gravity.CENTER
        contentDescription = context.getString(R.string.wvi_drag)
        isFocusable = true
        isClickable = true
        layoutParams = LinearLayout.LayoutParams(context.dp(GRIP_WIDTH_DP), context.dp(CONTROL_HEIGHT_DP))
        setOnClickListener { open() }
    }
    private val dragController = FloatingDragController(this, control, grip, position) { maximum ->
        // Changing maxWidth only when necessary avoids repeated layout/discovery loops.
        if (title.maxWidth != maximum) title.maxWidth = maximum
        if (domain.maxWidth != maximum) domain.maxWidth = maximum
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var outline: RectF? = null
    private var outlineNumber = 0
    private var lastState: InspectorState? = null

    init {
        id = R.id.wvi_overlay
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setWillNotDraw(false)
        buildControl(open)
        dragController.attach()
    }

    private fun buildControl(open: () -> Unit) {
        control.id = R.id.wvi_control
        control.orientation = LinearLayout.HORIZONTAL
        control.gravity = Gravity.CENTER_VERTICAL
        control.padding(CONTROL_PADDING_DP)
        control.background = context.shape(
            color = context.color(R.color.wvi_surface),
            radius = CONTROL_RADIUS_DP,
            border = context.color(R.color.wvi_primary),
        )
        control.elevation = context.dp(CONTROL_ELEVATION_DP).toFloat()
        control.addView(grip)
        opener.apply {
            id = R.id.wvi_open
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = context.dp(CONTROL_HEIGHT_DP)
            padding(horizontal = LABEL_PADDING_HORIZONTAL_DP, vertical = LABEL_PADDING_VERTICAL_DP)
            isClickable = true
            isFocusable = true
            contentDescription = context.getString(R.string.wvi_open)
            setOnClickListener { open() }
        }
        listOf(title, domain).forEach { label ->
            label.maxLines = 1
            label.ellipsize = TextUtils.TruncateAt.END
            label.maxWidth = context.dp(INITIAL_LABEL_WIDTH_DP)
            opener.addView(label)
        }
        control.addView(opener)
        copyButton.id = R.id.wvi_quick_copy
        control.addView(copyButton)
        addView(control, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    fun render(state: InspectorState) {
        visibility = if (state.targets.isEmpty()) GONE else VISIBLE
        if (lastState == state) return
        lastState = state

        val selected = state.selected
        title.text = selected?.let {
            context.getString(R.string.wvi_selection, it.number, state.targets.size)
        } ?: context.getString(R.string.wvi_select_count, state.targets.size)
        domain.text = selected?.domain ?: context.getString(R.string.wvi_choose)
        opener.contentDescription = "${title.text}, ${domain.text}, ${context.getString(R.string.wvi_open)}"
        copyButton.isEnabled = selected?.url != null
        copyButton.alpha = if (copyButton.isEnabled) 1f else DISABLED_ALPHA
        copyButton.contentDescription = selected?.let {
            context.getString(R.string.wvi_copy_url, it.number)
        } ?: context.getString(R.string.wvi_copy)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        dragController.place()
    }

    fun highlight(bounds: Rect?, number: Int = 0) {
        outline = bounds?.let { RectF(it) }
        outlineNumber = number
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bounds = outline ?: return
        paint.color = context.color(R.color.wvi_primary)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = context.dp(OUTLINE_WIDTH_DP).toFloat()
        val radius = context.dp(OUTLINE_RADIUS_DP).toFloat()
        canvas.drawRoundRect(bounds, radius, radius, paint)
        drawTargetBadge(canvas, bounds)
    }

    private fun drawTargetBadge(canvas: Canvas, bounds: RectF) {
        paint.style = Paint.Style.FILL
        paint.textSize = context.dp(BADGE_TEXT_SIZE_DP).toFloat()
        val text = context.getString(R.string.wvi_view_number, outlineNumber)
        val textX = bounds.left + context.dp(BADGE_OFFSET_X_DP)
        val textY = maxOf(bounds.top + context.dp(BADGE_BASELINE_DP), context.dp(BADGE_BASELINE_DP).toFloat())
        canvas.drawRoundRect(
            textX - context.dp(BADGE_PADDING_X_DP),
            textY - context.dp(BADGE_ASCENT_DP),
            textX + paint.measureText(text) + context.dp(BADGE_PADDING_X_DP),
            textY + context.dp(BADGE_DESCENT_DP),
            BADGE_RADIUS_PX,
            BADGE_RADIUS_PX,
            paint,
        )
        paint.color = context.color(R.color.wvi_on_primary)
        canvas.drawText(text, textX, textY, paint)
    }

    private companion object {
        const val TITLE_SIZE_SP = 13f
        const val DOMAIN_SIZE_SP = 11f
        const val GRIP_SIZE_SP = 22f
        const val GRIP_WIDTH_DP = 40
        const val CONTROL_HEIGHT_DP = 52
        const val CONTROL_PADDING_DP = 2
        const val CONTROL_RADIUS_DP = 14
        const val CONTROL_ELEVATION_DP = 8
        const val LABEL_PADDING_HORIZONTAL_DP = 4
        const val LABEL_PADDING_VERTICAL_DP = 6
        const val INITIAL_LABEL_WIDTH_DP = 160
        const val DISABLED_ALPHA = 0.35f
        const val OUTLINE_WIDTH_DP = 3
        const val OUTLINE_RADIUS_DP = 8
        const val BADGE_TEXT_SIZE_DP = 14
        const val BADGE_OFFSET_X_DP = 8
        const val BADGE_BASELINE_DP = 24
        const val BADGE_PADDING_X_DP = 4
        const val BADGE_ASCENT_DP = 18
        const val BADGE_DESCENT_DP = 5
        const val BADGE_RADIUS_PX = 5f
    }
}
