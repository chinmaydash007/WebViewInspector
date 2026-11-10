package com.webviewinspector

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.GeneralSwipeAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Swipe
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.webviewinspector.internal.ui.common.BottomSheetFactory
import com.webviewinspector.internal.ui.common.InspectorSheetLayout
import com.webviewinspector.internal.ui.common.inspectorTheme
import com.webviewinspector.internal.ui.common.label
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

/** Exercises real touch dispatch through Material's coordinator, rather than calling scrollTo alone. */
@RunWith(AndroidJUnit4::class)
class BottomSheetGestureTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var dialog: BottomSheetDialog
    private lateinit var scroll: ViewGroup

    @After fun cleanup() {
        ui {
            WebViewInspector.uninstall()
        }
    }

    @Test fun componentActivityScrollsBeforeDismissingForSlowAndFastGestures() {
        verifyScrollBeforeDismiss(ComposeInspectorHost::class.java)
    }

    @Test fun appCompatActivityScrollsBeforeDismissingForSlowAndFastGestures() {
        verifyScrollBeforeDismiss(XmlInspectorHost::class.java)
    }

    private fun <T : Activity> verifyScrollBeforeDismiss(host: Class<T>) {
        ActivityScenario.launch(host).use { scenario ->
            scenario.onActivity { activity ->
                WebViewInspector.uninstall()
                val context = activity.inspectorTheme()
                dialog = BottomSheetFactory.create(context)
                val body = BottomSheetFactory.createBody(context, "Scroll gesture fixture", dialog::dismiss)
                body.id = R.id.wvi_sheet_header
                val layout = InspectorSheetLayout(context, dialog.behavior)
                layout.history = true
                scroll = layout.scroll
                val content = context.label((1..60).joinToString("\n") { "Recorded navigation $it" })
                val footer =
                    context.label("Pinned footer").apply {
                        id = R.id.wvi_sheet_footer
                        minimumHeight =
                            context.resources.displayMetrics.density
                                .times(48)
                                .toInt()
                    }
                layout.setSections(body, content, footer)
                dialog.setContentView(layout)
                dialog.show()
                BottomSheetFactory.expand(dialog)
            }
            try {
                verifyContentGestures()
            } finally {
                ui { dialog.dismiss() }
            }
        }
    }

    private fun verifyContentGestures() {
        instrumentation.waitForIdleSync()
        val focusDeadline = android.os.SystemClock.uptimeMillis() + 5_000
        while (!ui { dialog.window?.decorView?.hasWindowFocus() == true }) {
            assertTrue(
                "Fixture dialog must receive window focus",
                android.os.SystemClock.uptimeMillis() < focusDeadline,
            )
            Thread.sleep(50)
        }
        for (speed in listOf(Swipe.SLOW, Swipe.FAST)) {
            ui { scroll.scrollTo(0, scroll.getChildAt(0).height) }
            instrumentation.waitForIdleSync()
            val bottom = ui { scroll.scrollY }
            assertTrue("Fixture must have scrollable content", bottom > 0)
            for (region in listOf(R.id.wvi_sheet_header, R.id.wvi_sheet_footer)) {
                val position = ui { visibleScreenRect(requireNotNull(dialog.findViewById(region))) }
                val now = android.os.SystemClock.uptimeMillis()
                val down =
                    android.view.MotionEvent.obtain(
                        now,
                        now,
                        android.view.MotionEvent.ACTION_DOWN,
                        position.exactCenterX(),
                        position.top + position.height() * 0.25f,
                        0,
                    )
                instrumentation.sendPointerSync(down)
                down.recycle()
                assertFalse(
                    "Native down at $position, scrollY=${ui { scroll.scrollY }}, must reach the root gate",
                    ui {
                        dialog.behavior.isDraggable
                    },
                )
                val cancel =
                    android.view.MotionEvent.obtain(
                        now,
                        now + 10,
                        android.view.MotionEvent.ACTION_CANCEL,
                        position.exactCenterX(),
                        position.top + position.height() * 0.25f,
                        0,
                    )
                instrumentation.sendPointerSync(cancel)
                cancel.recycle()
                swipeDown(speed, region)
                ui {
                    assertTrue(
                        "Pinned-region $region ($speed) at $position must not dismiss scrolled content",
                        dialog.isShowing,
                    )
                    assertEquals(BottomSheetBehavior.STATE_EXPANDED, dialog.behavior.state)
                    assertTrue("Pinned-region gestures must restore dragging", dialog.behavior.isDraggable)
                }
            }
            swipeDown(speed)
            Thread.sleep(600)
            ui {
                assertTrue("A $speed gesture below the top must not dismiss", dialog.isShowing)
                assertEquals(BottomSheetBehavior.STATE_EXPANDED, dialog.behavior.state)
                assertTrue("The gesture must move content toward the top", scroll.scrollY < bottom)
            }
            // Begin below the top and cross it within the same gesture: no handoff to dismissal.
            ui { scroll.scrollTo(0, 40) }
            swipeDown(speed)
            Thread.sleep(600)
            ui {
                assertTrue("Reaching the top must not dismiss in the same $speed gesture", dialog.isShowing)
                assertEquals(0, scroll.scrollY)
                assertEquals(BottomSheetBehavior.STATE_EXPANDED, dialog.behavior.state)
                assertTrue("Dragging must be restored after the content gesture", dialog.behavior.isDraggable)
            }
        }
        ui {
            scroll.scrollTo(0, 80)
            val root = requireNotNull(dialog.findViewById<View>(R.id.wvi_sheet))
            val now = android.os.SystemClock.uptimeMillis()
            val down = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, 10f, 10f, 0)
            root.dispatchTouchEvent(down)
            down.recycle()
            assertFalse("A gesture below the top must lock sheet dragging", dialog.behavior.isDraggable)
            val cancel =
                android.view.MotionEvent.obtain(
                    now,
                    now + 10,
                    android.view.MotionEvent.ACTION_CANCEL,
                    10f,
                    10f,
                    0,
                )
            root.dispatchTouchEvent(cancel)
            cancel.recycle()
            assertTrue("Cancellation must release the gesture gate", dialog.behavior.isDraggable)
            scroll.scrollTo(0, 0)
        }
        swipeDown(Swipe.FAST)
        Thread.sleep(600)
        assertFalse("A separate downward gesture starting at the top must still dismiss", ui { dialog.isShowing })
    }

    private fun swipeDown(
        speed: Swipe,
        region: Int? = null,
    ) {
        onView(withId(R.id.wvi_sheet)).inRoot(isDialog()).perform(
            object : ViewAction {
                override fun getDescription() = "$speed downward gesture through the sheet content"

                override fun getConstraints(): Matcher<View> = isAssignableFrom(View::class.java)

                override fun perform(
                    uiController: UiController,
                    view: View,
                ) {
                    val bounds = visibleScreenRect(scroll)
                    val start =
                        if (region ==
                            null
                        ) {
                            bounds
                        } else {
                            visibleScreenRect(requireNotNull(dialog.findViewById(region)))
                        }
                    val startY =
                        if (region ==
                            null
                        ) {
                            bounds.top + bounds.height() * 0.2f
                        } else {
                            start.top + start.height() * 0.25f
                        }
                    val endY =
                        if (region ==
                            R.id.wvi_sheet_footer
                        ) {
                            start.bottom - 2f
                        } else {
                            bounds.top + bounds.height() * 0.85f
                        }
                    GeneralSwipeAction(
                        speed,
                        { floatArrayOf(bounds.exactCenterX(), startY) },
                        { floatArrayOf(bounds.exactCenterX(), endY) },
                        Press.FINGER,
                    ).perform(uiController, scroll)
                }
            },
        )
    }

    private fun visibleScreenRect(view: View): Rect {
        val rectangle = Rect()
        assertTrue(view.getGlobalVisibleRect(rectangle))
        // Global-visible coordinates are relative to the window root, not the physical screen.
        val rootOrigin = IntArray(2)
        view.rootView.getLocationOnScreen(rootOrigin)
        rectangle.offset(rootOrigin[0], rootOrigin[1])
        return rectangle
    }

    private fun <T> ui(block: () -> T): T {
        val result = AtomicReference<T>()
        instrumentation.runOnMainSync { result.set(block()) }
        return result.get()
    }
}
