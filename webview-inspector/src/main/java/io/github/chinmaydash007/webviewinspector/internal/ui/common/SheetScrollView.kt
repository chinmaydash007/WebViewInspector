package io.github.chinmaydash007.webviewinspector.internal.ui.common

import android.content.Context
import android.view.MotionEvent
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomsheet.BottomSheetBehavior

/**
 * Coordinates sheet content with Material's nested scrolling on the main thread.
 * A gesture starting below the top belongs to the content until release, even if it reaches
 * the top mid-gesture. A subsequent downward gesture at the top can dismiss normally.
 * Owned by the dialog's View tree; cancellation and detachment restore the drag setting.
 */
internal class SheetScrollView(
    context: Context,
    behavior: BottomSheetBehavior<*>,
    guardOwnGestures: Boolean = true,
) : NestedScrollView(context) {
    private val dragGuard = if (guardOwnGestures) SheetDragGuard(behavior) { canScrollVertically(-1) } else null

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        return dragGuard?.dispatch(event) { super.dispatchTouchEvent(event) } ?: super.dispatchTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        dragGuard?.release()
        super.onDetachedFromWindow()
    }
}
