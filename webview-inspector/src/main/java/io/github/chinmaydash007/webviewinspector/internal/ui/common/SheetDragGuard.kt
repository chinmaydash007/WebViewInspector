package io.github.chinmaydash007.webviewinspector.internal.ui.common

import android.view.MotionEvent
import com.google.android.material.bottomsheet.BottomSheetBehavior

/**
 * Main-thread gesture gate owned by one dialog content root (or the legacy settings scroller).
 * Locking lasts through nested-scroll completion; no callbacks or timers outlive its owner.
 */
internal class SheetDragGuard(
    private val behavior: BottomSheetBehavior<*>,
    private val contentCanScrollUp: () -> Boolean,
) {
    private var previousDraggable: Boolean? = null
    val isBlocking: Boolean get() = previousDraggable != null

    fun dispatch(
        event: MotionEvent,
        dispatchToChildren: () -> Boolean,
    ): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            release()
            if (contentCanScrollUp()) {
                previousDraggable = behavior.isDraggable
                behavior.isDraggable = false
            }
        }
        return try {
            dispatchToChildren()
        } finally {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                release()
            }
        }
    }

    fun release() {
        previousDraggable?.let { behavior.isDraggable = it }
        previousDraggable = null
    }
}
