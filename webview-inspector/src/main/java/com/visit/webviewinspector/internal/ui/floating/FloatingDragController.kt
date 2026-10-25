package com.visit.webviewinspector.internal.ui.floating

import android.annotation.SuppressLint
import android.graphics.Rect
import android.graphics.RectF
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.visit.webviewinspector.R
import com.visit.webviewinspector.internal.state.FloatingPosition
import com.visit.webviewinspector.internal.ui.common.dp
import kotlin.math.abs

/**
 * Moves one floating control within its overlay, including touch, keyboard, and accessibility input.
 *
 * Owned by [FloatingOverlay] and used on the main thread. All listeners belong to these views;
 * no Activity/window observer survives their removal. Position is normalized into process state.
 */
internal class FloatingDragController(
    private val overlay: View,
    private val control: View,
    private val grip: View,
    private val position: FloatingPosition,
    private val updateLabelMaxWidth: (Int) -> Unit,
) {
    private val context = overlay.context
    private val safeBounds = RectF()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var startX = 0f
    private var startY = 0f
    private var dragging = false

    @SuppressLint("ClickableViewAccessibility")
    fun attach() {
        grip.setOnTouchListener { view, event -> handleTouch(view, event) }
        installAccessibilityMovement()
        grip.setOnKeyListener { _, keyCode, event -> handleKey(keyCode, event) }
        ViewCompat.setOnApplyWindowInsetsListener(overlay) { _, insets ->
            overlay.post { place() }
            insets
        }
        overlay.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> place() }
        control.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) place()
        }
    }

    private fun handleTouch(view: View, event: MotionEvent): Boolean = when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            downX = event.rawX
            downY = event.rawY
            startX = control.translationX
            startY = control.translationY
            dragging = false
            view.parent.requestDisallowInterceptTouchEvent(true)
            true
        }
        MotionEvent.ACTION_MOVE -> {
            val deltaX = event.rawX - downX
            val deltaY = event.rawY - downY
            if (abs(deltaX) > touchSlop || abs(deltaY) > touchSlop) dragging = true
            if (dragging) {
                updateSafeBounds()
                move(startX + deltaX, startY + deltaY)
                rememberPosition()
            }
            true
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            if (dragging) {
                snapToEdge()
                rememberPosition()
            } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                view.performClick()
            }
            view.parent.requestDisallowInterceptTouchEvent(false)
            true
        }
        else -> false
    }

    private fun installAccessibilityMovement() {
        val actions = listOf(
            Triple(R.string.wvi_move_left, -MOVE_STEP_DP, 0),
            Triple(R.string.wvi_move_right, MOVE_STEP_DP, 0),
            Triple(R.string.wvi_move_up, 0, -MOVE_STEP_DP),
            Triple(R.string.wvi_move_down, 0, MOVE_STEP_DP),
        )
        actions.forEach { (label, deltaX, deltaY) ->
            ViewCompat.addAccessibilityAction(grip, context.getString(label)) { _, _ ->
                updateSafeBounds()
                move(control.translationX + context.dp(deltaX), control.translationY + context.dp(deltaY))
                rememberPosition()
                true
            }
        }
    }

    private fun handleKey(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        val step = context.dp(MOVE_STEP_DP).toFloat()
        val deltaX = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> -step
            KeyEvent.KEYCODE_DPAD_RIGHT -> step
            else -> 0f
        }
        val deltaY = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> -step
            KeyEvent.KEYCODE_DPAD_DOWN -> step
            else -> 0f
        }
        if (deltaX == 0f && deltaY == 0f) return false

        updateSafeBounds()
        move(control.translationX + deltaX, control.translationY + deltaY)
        rememberPosition()
        return true
    }

    /** Restores normalized position after geometry, insets, or label size changes. */
    fun place() {
        if (overlay.width == 0 || control.width == 0) return
        val availableLabelWidth = (overlay.width - context.dp(CONTROL_RESERVED_WIDTH_DP))
            .coerceAtLeast(context.dp(MIN_LABEL_WIDTH_DP))
        updateLabelMaxWidth(minOf(context.dp(MAX_LABEL_WIDTH_DP), availableLabelWidth))
        updateSafeBounds()
        move(
            x = safeBounds.left + safeBounds.width() * position.x,
            y = safeBounds.top + safeBounds.height() * position.y,
        )
    }

    private fun updateSafeBounds() {
        val margin = context.dp(EDGE_MARGIN_DP)
        val insetTypes = WindowInsetsCompat.Type.systemBars() or
            WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime()
        val insets = ViewCompat.getRootWindowInsets(overlay)?.getInsets(insetTypes)
        safeBounds.set(
            (insets?.left ?: 0) + margin.toFloat(),
            (insets?.top ?: 0) + margin.toFloat(),
            (overlay.width - (insets?.right ?: 0) - margin - control.width).toFloat(),
            (overlay.height - (insets?.bottom ?: 0) - margin - control.height).toFloat(),
        )

        // Visible frame also covers adjustResize and older platform keyboard insets.
        val visibleFrame = Rect()
        overlay.getWindowVisibleDisplayFrame(visibleFrame)
        val overlayLocation = IntArray(2)
        overlay.getLocationOnScreen(overlayLocation)
        if (visibleFrame.height() > 0) {
            safeBounds.top = maxOf(safeBounds.top, visibleFrame.top - overlayLocation[1] + margin.toFloat())
            safeBounds.bottom = minOf(
                safeBounds.bottom,
                visibleFrame.bottom - overlayLocation[1] - margin - control.height.toFloat(),
            )
        }
        safeBounds.right = maxOf(safeBounds.left, safeBounds.right)
        safeBounds.bottom = maxOf(safeBounds.top, safeBounds.bottom)
    }

    private fun move(x: Float, y: Float) {
        control.translationX = x.coerceIn(safeBounds.left, safeBounds.right)
        control.translationY = y.coerceIn(safeBounds.top, safeBounds.bottom)
    }

    private fun snapToEdge() {
        val edgeX = if (control.translationX < safeBounds.centerX()) safeBounds.left else safeBounds.right
        move(edgeX, control.translationY)
    }

    private fun rememberPosition() {
        position.x = if (safeBounds.width() > 0) {
            (control.translationX - safeBounds.left) / safeBounds.width()
        } else {
            0f
        }
        position.y = if (safeBounds.height() > 0) {
            (control.translationY - safeBounds.top) / safeBounds.height()
        } else {
            0f
        }
    }

    private companion object {
        const val EDGE_MARGIN_DP = 12
        const val MOVE_STEP_DP = 16
        const val CONTROL_RESERVED_WIDTH_DP = 112
        const val MIN_LABEL_WIDTH_DP = 48
        const val MAX_LABEL_WIDTH_DP = 160
    }
}
