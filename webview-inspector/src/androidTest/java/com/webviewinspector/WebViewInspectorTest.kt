package com.webviewinspector

import android.app.Activity
import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.GeneralSwipeAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Swipe
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.isFocusable
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.webviewinspector.internal.ui.common.color
import com.webviewinspector.internal.ui.common.dp
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import com.webviewinspector.test.R as TestR

@RunWith(AndroidJUnit4::class)
class WebViewInspectorTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val application get() = ApplicationProvider.getApplicationContext<Application>()

    @Before fun install() {
        clearClipboard()
        ui {
            WebViewInspector.install(application)
            WebViewInspector.install(application)
        }
    }

    @After fun uninstall() {
        ui {
            WebViewInspector.uninstall()
            WebViewInspector.uninstall()
        }
        clearClipboard()
    }

    @Test fun composeWithPlatformThemeSupportsSelectionExactCopyAndBack() {
        ActivityScenario.launch(ComposeInspectorHost::class.java).use { scenario ->
            lateinit var activity: ComposeInspectorHost
            scenario.onActivity { activity = it }
            await { activity.views.size == 2 && header(activity).contains("of 2") }
            val originalClient =
                if (android.os.Build.VERSION.SDK_INT >=
                    26
                ) {
                    ui { activity.views[1].webViewClient }
                } else {
                    null
                }
            onView(withId(R.id.wvi_open)).perform(click())
            selectTarget("WebView 2, two.example, /embedded")
            onView(withId(R.id.wvi_copy_url)).check(matches(withText("Copy URL · WebView 2")))
            screenshot("compose-sheet")
            onView(withId(R.id.wvi_copy_url)).inRoot(isDialog()).perform(click())
            assertEquals(ui { activity.views[1].url }, clipboard())
            assertEquals(SECOND_URL, clipboard())
            clearClipboard()
            pressBack()
            await { activity.hasWindowFocus() && header(activity).contains("WebView 2 of 2") }
            onView(withId(R.id.wvi_quick_copy)).perform(click())
            assertEquals(SECOND_URL, clipboard())
            if (android.os.Build.VERSION.SDK_INT >=
                26
            ) {
                assertSame(originalClient, ui { activity.views[1].webViewClient })
            }
            val bridge = FixtureBridge()
            ui {
                activity.views[1].addJavascriptInterface(bridge, "VerificationBridge")
                activity.views[1].reload()
            }
            Thread.sleep(600)
            await {
                activity.views[1].evaluateJavascript("VerificationBridge.ping()", null)
                bridge.called
            }
        }
    }

    @Test fun xmlDuplicateIdsSelectionUnavailableAndCurrentUrl() {
        ActivityScenario.launch(XmlInspectorHost::class.java).use { scenario ->
            lateinit var activity: XmlInspectorHost
            scenario.onActivity { activity = it }
            await { header(activity).contains("of 2") }
            onView(withId(R.id.wvi_open)).perform(click())
            selectTarget("WebView 2, two.example, /embedded")
            ui { activity.views[1].loadUrl(UPDATED_URL) }
            await { activity.views[1].url == UPDATED_URL }
            onView(withId(R.id.wvi_copy_url)).inRoot(isDialog()).perform(click())
            assertEquals(UPDATED_URL, clipboard())
            clearClipboard()
            // Reorder two instances with identical resource IDs; selection follows the instance.
            ui {
                val selected = activity.views[1]
                activity.container.removeView(selected)
                activity.container.addView(selected, 2)
            }
            Thread.sleep(1200)
            onView(withId(R.id.wvi_copy_url)).check(matches(withText("Copy URL · WebView 1")))
            onView(withId(R.id.wvi_copy_url)).inRoot(isDialog()).perform(click())
            assertEquals(UPDATED_URL, clipboard())
            clearClipboard()
            ui { activity.container.removeView(activity.views[1]) }
            Thread.sleep(1200)
            onView(withId(R.id.wvi_copy_url)).check(matches(not(isEnabled())))
            pressBack()
            await { activity.hasWindowFocus() && header(activity).contains("Select WebView") }
            onView(withId(R.id.wvi_quick_copy)).check(matches(not(isEnabled())))
            onView(withId(R.id.wvi_open)).perform(click())
            screenshot("unavailable-reopen")
            selectTarget("WebView 1, one.example, /full")
            onView(withId(R.id.wvi_copy_url)).inRoot(isDialog()).perform(click())
            assertEquals(FIRST_URL, clipboard())
        }
    }

    @Test fun fullPageHiddenDelayedTargetsAndLocate() {
        ActivityScenario.launch(XmlInspectorHost::class.java).use { scenario ->
            lateinit var activity: XmlInspectorHost
            scenario.onActivity { activity = it }
            await { header(activity).contains("of 2") }
            ui { activity.views[1].visibility = View.GONE }
            await { header(activity).contains("of 1") }
            ui { activity.addDelayed() }
            await { header(activity).contains("of 2") }
            onView(withId(R.id.wvi_open)).perform(click())
            selectTarget("WebView 2, late.example, /card")
            onView(withId(R.id.wvi_locate)).perform(scrollTo(), click())
            await { activity.hasWindowFocus() && header(activity).contains("WebView 2 of 2") }
            assertTrue("Locate must draw an outline after its dialog closes", hasOutline(activity.views.last()))
            screenshot("locate")
            onView(withId(R.id.wvi_quick_copy)).perform(click())
            assertEquals("https://late.example/card?id=3", clipboard())
            clearClipboard()
            Thread.sleep(5200)
            assertFalse("Locate must expire", hasOutline(activity.views.last()))
            screenshot("locate-expired")
            ui {
                activity.container.removeView(activity.views.last())
                activity.container.removeView(activity.views[0])
                activity.setContentView(activity.views[0], ViewGroup.LayoutParams(-1, -1))
            }
            await { header(activity).contains("Select WebView · 1") }
            onView(withId(R.id.wvi_open)).perform(click())
            selectTarget("WebView 1, one.example, /full")
            onView(withId(R.id.wvi_copy_url)).inRoot(isDialog()).perform(click())
            assertEquals(FIRST_URL, clipboard())
        }
    }

    @Test fun settingsToggleTouchPassthroughRotationAndCleanup() {
        ActivityScenario.launch(XmlInspectorHost::class.java).use { scenario ->
            lateinit var activity: XmlInspectorHost
            scenario.onActivity { activity = it }
            await { header(activity).contains("of 2") }
            onView(withId(TestR.id.wvi_test_touch)).perform(click())
            assertEquals(1, ui { activity.touches })
            ui { WebViewInspector.showSettings(activity) }
            instrumentation.waitForIdleSync()
            onView(withId(R.id.wvi_enabled)).inRoot(isDialog()).perform(click())
            assertFalse(WebViewInspector.isEnabled())
            assertNull(ui { activity.window.decorView.findViewById<View>(R.id.wvi_overlay) })
            onView(withId(R.id.wvi_enabled)).inRoot(isDialog()).perform(click())
            assertTrue(WebViewInspector.isEnabled())
            pressBack()
            await { header(activity).contains("of 2") }
            scenario.recreate()
            scenario.onActivity { activity = it }
            await { header(activity).contains("of 2") }
            onView(withId(R.id.wvi_open)).perform(click())
            selectTarget("WebView 2, two.example, /embedded")
            pressBack()
            onView(withId(R.id.wvi_open)).perform(click())
            scenario.moveToState(Lifecycle.State.CREATED)
            assertNull(ui { activity.window.decorView.findViewById<View>(R.id.wvi_overlay) })
            scenario.moveToState(Lifecycle.State.RESUMED)
            await { header(activity).contains("WebView 2 of 2") }
            ui { WebViewInspector.uninstall() }
            assertNull(ui { activity.window.decorView.findViewById<View>(R.id.wvi_overlay) })
        }
    }

    @Test fun recycledCardsCopyTheirCurrentInstanceUrl() {
        ActivityScenario.launch(RecycledInspectorHost::class.java).use { scenario ->
            lateinit var activity: RecycledInspectorHost
            scenario.onActivity { activity = it }
            await { header(activity).contains("of ") }
            onView(withId(R.id.wvi_quick_copy)).perform(click())
            assertTrue(clipboard().startsWith("https://recycled.example/card/0"))
            clearClipboard()
            ui { activity.recycler.scrollToPosition(30) }
            Thread.sleep(1400)
            // A recycled instance can remain selected; it must now copy its rebound URL.
            val quick = ui { activity.window.decorView.findViewById<View>(R.id.wvi_quick_copy) }
            if (ui { quick.isEnabled }) {
                onView(withId(R.id.wvi_quick_copy)).perform(click())
                val visibleUrls =
                    ui {
                        activity.bound
                            .filter {
                                it.isShown &&
                                    it.getGlobalVisibleRect(android.graphics.Rect())
                            }.map { it.url }
                    }
                assertTrue(visibleUrls.contains(clipboard()))
                assertFalse(clipboard().contains("/card/0?"))
            } else {
                assertTrue(ui { header(activity).contains("Select WebView") })
            }
        }
    }

    @Test fun dragClampsAndPositionSurvivesActivityRecreation() {
        ActivityScenario.launch(XmlInspectorHost::class.java).use { scenario ->
            lateinit var activity: XmlInspectorHost
            scenario.onActivity { activity = it }
            await { header(activity).contains("of 2") }
            onView(withContentDescription(application.getString(R.string.wvi_drag))).perform(
                GeneralSwipeAction(
                    Swipe.SLOW,
                    { view ->
                        val point = IntArray(2)
                        view.getLocationOnScreen(point)
                        floatArrayOf(point[0] + view.width / 2f, point[1] + view.height / 2f)
                    },
                    { view ->
                        val point = IntArray(2)
                        view.getLocationOnScreen(point)
                        floatArrayOf(
                            view.context.dp(20).toFloat(),
                            point[1] + view.height / 2f,
                        )
                    },
                    Press.FINGER,
                ),
            )
            val before =
                ui {
                    activity.window.decorView
                        .findViewById<View>(R.id.wvi_control)
                        .translationX
                }
            assertTrue(before >= 0 && before < ui { activity.dp(30) })
            scenario.recreate()
            scenario.onActivity { activity = it }
            await { header(activity).contains("of 2") }
            val after =
                ui {
                    activity.window.decorView
                        .findViewById<View>(R.id.wvi_control)
                        .translationX
                }
            assertEquals(before, after, 2f)
            screenshot("floating")
        }
    }

    @Test fun keyboardInsetsAccessibilityAndActualRotation() {
        ActivityScenario.launch(XmlInspectorHost::class.java).use { scenario ->
            lateinit var activity: XmlInspectorHost
            scenario.onActivity { activity = it }
            await { header(activity).contains("of 2") }
            ui {
                val grip =
                    activity.window.decorView
                        .findViewById<ViewGroup>(R.id.wvi_control)
                        .getChildAt(0)
                assertTrue(grip.isFocusable)
                assertFalse(grip.contentDescription.isNullOrBlank())
                grip.requestFocus()
                repeat(60) {
                    grip.dispatchKeyEvent(
                        android.view.KeyEvent(
                            android.view.KeyEvent.ACTION_DOWN,
                            android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                        ),
                    )
                }
            }
            onView(withId(TestR.id.wvi_test_edit)).perform(click())
            await { activity.hasWindowFocus() }
            ui {
                // A tap can focus the editor without requesting the IME on a headless system image.
                // Establish the keyboard precondition explicitly; the real inset checks stay below.
                val editor = activity.findViewById<EditText>(TestR.id.wvi_test_edit)
                assertTrue("The keyboard fixture must accept editor focus", editor.requestFocus())
                WindowCompat.getInsetsController(activity.window, editor).show(WindowInsetsCompat.Type.ime())
            }
            await {
                ViewCompat.getRootWindowInsets(activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) ==
                    true
            }
            ui {
                val control = activity.window.decorView.findViewById<View>(R.id.wvi_control)
                val point = IntArray(2)
                control.getLocationOnScreen(point)
                val visible = android.graphics.Rect()
                control.getWindowVisibleDisplayFrame(visible)
                assertTrue("Control must remain above keyboard", point[1] + control.height <= visible.bottom)
            }
            screenshot("keyboard")
            pressBack()
            ui {
                val editor = activity.findViewById<EditText>(TestR.id.wvi_test_edit)
                WindowCompat.getInsetsController(activity.window, editor).hide(WindowInsetsCompat.Type.ime())
            }
            await {
                ViewCompat.getRootWindowInsets(activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) ==
                    false
            }
            ui { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            Thread.sleep(1500)
            scenario.onActivity { activity = it }
            await {
                activity.resources.configuration.orientation ==
                    android.content.res.Configuration.ORIENTATION_LANDSCAPE &&
                    header(activity).contains("of 2")
            }
            ui {
                // Recreation can restore editor/IME state; the next check targets sheet copying.
                val editor = activity.findViewById<EditText>(TestR.id.wvi_test_edit)
                WindowCompat.getInsetsController(activity.window, editor).hide(WindowInsetsCompat.Type.ime())
            }
            await {
                ViewCompat.getRootWindowInsets(activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) ==
                    false
            }
            screenshot("landscape-before-sheet")
            onView(withId(R.id.wvi_open)).perform(click())
            onView(withId(R.id.wvi_copy_url)).inRoot(isDialog()).perform(click())
            assertEquals(FIRST_URL, clipboard())
            screenshot("landscape")
            pressBack()
            ui { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        }
    }

    @Test fun nativeDialogOutsideTapAndSwipeDismissal() {
        ActivityScenario.launch(ComposeInspectorHost::class.java).use { scenario ->
            lateinit var activity: ComposeInspectorHost
            scenario.onActivity { activity = it }
            await { header(activity).contains("of 2") }
            onView(withId(R.id.wvi_open)).perform(click())
            val point =
                ui {
                    val top =
                        ViewCompat
                            .getRootWindowInsets(activity.window.decorView)
                            ?.getInsets(WindowInsetsCompat.Type.systemBars())
                            ?.top ?: 0
                    floatArrayOf(activity.dp(16).toFloat(), top + activity.dp(10).toFloat())
                }
            val now = android.os.SystemClock.uptimeMillis()
            listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP).forEach { action ->
                val event =
                    android.view.MotionEvent.obtain(
                        now,
                        android.os.SystemClock.uptimeMillis(),
                        action,
                        point[0],
                        point[1],
                        0,
                    )
                instrumentation.sendPointerSync(event)
                event.recycle()
            }
            await { activity.hasWindowFocus() }
            onView(withId(R.id.wvi_open)).perform(click())
            onView(withId(com.google.android.material.R.id.design_bottom_sheet)).perform(
                GeneralSwipeAction(
                    Swipe.SLOW,
                    { view ->
                        val location = IntArray(2)
                        view.getLocationOnScreen(location)
                        floatArrayOf(location[0] + view.width / 2f, location[1] + view.context.dp(12).toFloat())
                    },
                    { view ->
                        val location = IntArray(2)
                        view.getLocationOnScreen(location)
                        floatArrayOf(
                            location[0] + view.width / 2f,
                            location[1] + view.height - view.context.dp(12).toFloat(),
                        )
                    },
                    Press.FINGER,
                ),
            )
            await { activity.hasWindowFocus() }
            assertFalse(ui { activity.isFinishing })
        }
    }

    private fun selectTarget(description: String) {
        onView(withId(R.id.wvi_target_picker)).inRoot(isDialog()).perform(click())
        onView(withContentDescription(description)).inRoot(isPlatformPopup()).perform(click())
    }

    private fun header(activity: Activity): String {
        val control = activity.window.decorView.findViewById<ViewGroup>(R.id.wvi_control) ?: return ""

        fun texts(view: View): String =
            if (view is TextView) {
                view.text.toString()
            } else if (view is ViewGroup) {
                (0 until view.childCount).joinToString { texts(view.getChildAt(it)) }
            } else {
                ""
            }
        return texts(control)
    }

    private fun clipboard(): String =
        ui {
            (
                application.getSystemService(
                    Context.CLIPBOARD_SERVICE,
                ) as ClipboardManager
            ).primaryClip?.getItemAt(0)?.text.toString()
        }

    private fun hasOutline(view: WebView): Boolean {
        val bounds = ui { android.graphics.Rect().also { view.getGlobalVisibleRect(it) } }
        val color = ui { view.context.color(R.color.wvi_primary) and 0xffffff }
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return false
        val result =
            (-3..3).any { delta ->
                val y = (bounds.top + delta).coerceIn(0, bitmap.height - 1)
                (bitmap.getPixel(bounds.centerX().coerceIn(0, bitmap.width - 1), y) and 0xffffff) == color
            }
        bitmap.recycle()
        return result
    }

    private fun clearClipboard() {
        // Keep the system clipboard preview out of physical gesture tests. Each copy must also produce new content.
        ui {
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                (application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).clearPrimaryClip()
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation
                .executeShellCommand(
                    "am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS",
                ).use {
                    android.os.ParcelFileDescriptor
                        .AutoCloseInputStream(it)
                        .use { stream -> stream.readBytes() }
                }
            Thread.sleep(350)
        }
    }

    private fun await(predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 12_000
        while (System.currentTimeMillis() < deadline) {
            if (ui(predicate)) return
            Thread.sleep(100)
        }
        fail("Timed out waiting for inspector state")
    }

    private fun <T> ui(block: () -> T): T {
        val result = AtomicReference<T>()
        instrumentation.runOnMainSync { result.set(block()) }
        return result.get()
    }

    private fun screenshot(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val file = File(application.getExternalFilesDir(null), "wvi-$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
