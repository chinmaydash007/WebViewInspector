package com.webviewinspector.internal.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.webviewinspector.R

/**
 * Builds shared native sheet chrome on the main thread using a library-themed Activity context.
 * It retains no dialogs. The controller/session owns showing, dismissal, and lifecycle cleanup.
 */
internal object BottomSheetFactory {
    fun create(themedContext: Context): BottomSheetDialog =
        BottomSheetDialog(themedContext, R.style.WviBottomSheetTheme).apply {
            window?.let { sheetWindow ->
                if (Build.VERSION.SDK_INT >= 29) {
                    sheetWindow.isNavigationBarContrastEnforced = false
                }
                val lightSurface = ColorUtils.calculateLuminance(context.color(R.color.wvi_surface)) > 0.5
                WindowCompat
                    .getInsetsController(sheetWindow, sheetWindow.decorView)
                    .isAppearanceLightNavigationBars = lightSurface
            }
        }

    fun setContent(
        dialog: BottomSheetDialog,
        content: View,
    ) {
        val insetContent =
            SheetContent(dialog.context).apply {
                addView(content, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            }
        // Leave Material's own inset listener on the sheet surface. Only its content needs padding.
        ViewCompat.setOnApplyWindowInsetsListener(insetContent) { view, insets ->
            val safe =
                insets.getInsets(WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout())
            view.updatePadding(left = safe.left, right = safe.right, bottom = safe.bottom)
            insets
        }
        dialog.setContentView(insetContent, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    fun expand(dialog: BottomSheetDialog) {
        dialog.behavior.apply {
            skipCollapsed = true
            maxHeight = (dialog.context.resources.displayMetrics.heightPixels * MAX_HEIGHT_FRACTION).toInt()
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    fun createBody(
        context: Context,
        title: String,
        close: () -> Unit,
    ): LinearLayout =
        LinearLayout(context).apply {
            id = R.id.wvi_sheet
            orientation = LinearLayout.VERTICAL
            padding(horizontal = BODY_PADDING_HORIZONTAL_DP, vertical = BODY_PADDING_VERTICAL_DP)
            addView(createHandle(context))
            addView(createHeader(context, title, close), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }

    fun scroll(
        dialog: BottomSheetDialog,
        body: LinearLayout,
    ): NestedScrollView =
        SheetScrollView(dialog.context, dialog.behavior).apply {
            isFillViewport = false
            addView(body, ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }

    private fun createHandle(context: Context): View =
        View(context).apply {
            background = context.shape(context.color(R.color.wvi_border), radius = HANDLE_RADIUS_DP)
            layoutParams =
                LinearLayout.LayoutParams(context.dp(HANDLE_WIDTH_DP), context.dp(HANDLE_HEIGHT_DP)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    bottomMargin = context.dp(HANDLE_MARGIN_BOTTOM_DP)
                }
        }

    private fun createHeader(
        context: Context,
        title: String,
        close: () -> Unit,
    ): LinearLayout =
        LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            val heading =
                context.label(title, size = HEADER_SIZE_SP).bold().apply {
                    ViewCompat.setAccessibilityHeading(this, true)
                }
            addView(heading, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(context.icon(R.drawable.wvi_ic_close, context.getString(R.string.wvi_close), close))
        }

    private const val MAX_HEIGHT_FRACTION = 0.9f
    private const val BODY_PADDING_HORIZONTAL_DP = 20
    private const val BODY_PADDING_VERTICAL_DP = 12
    private const val HANDLE_WIDTH_DP = 36
    private const val HANDLE_HEIGHT_DP = 4
    private const val HANDLE_RADIUS_DP = 4
    private const val HANDLE_MARGIN_BOTTOM_DP = 12
    private const val HEADER_SIZE_SP = 20f
}

private class SheetContent(
    context: Context,
) : FrameLayout(context) {
    // Android 7 only has light navigation icons, and its window may hide the bar color behind
    // edge-to-edge content. Draw the scrim over the sheet surface within the navigation inset.
    private val navigationScrim =
        if (Build.VERSION.SDK_INT < 26) {
            Paint().apply { color = context.color(R.color.wvi_legacy_navigation_scrim) }
        } else {
            null
        }

    init {
        setWillNotDraw(navigationScrim == null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scrim = navigationScrim ?: return
        val bottom = (height - paddingBottom).toFloat()
        canvas.drawRect(0f, bottom, width.toFloat(), height.toFloat(), scrim)
        canvas.drawRect(0f, 0f, paddingLeft.toFloat(), bottom, scrim)
        canvas.drawRect((width - paddingRight).toFloat(), 0f, width.toFloat(), bottom, scrim)
    }
}
