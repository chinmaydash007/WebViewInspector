package com.webviewinspector

import android.animation.ValueAnimator
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.webviewinspector.internal.ui.common.dp
import com.webviewinspector.internal.ui.common.inspectorTheme
import com.webviewinspector.internal.ui.sheet.WebViewsTabs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real-frame motion coverage; animator scale is changed only on a selected emulator and restored. */
@RunWith(AndroidJUnit4::class)
class WebViewsTabsMotionTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var originalScale: String? = null

    @Before fun enableMotion() {
        assertEquals("1", shell("getprop ro.kernel.qemu"))
        originalScale = shell("settings get global animator_duration_scale")
        setScale("1")
    }

    @After fun restoreMotion() {
        originalScale?.let(::setScale)
    }

    @Test fun rapidSelectionAndCountRefreshesContinueFromTheCurrentPosition() =
        withTabs { fixture ->
            val start = ui { fixture.indicator.translationX }
            val destination = ui { fixture.position(fixture.recent) }
            ui {
                fixture.recent.performClick()
                fixture.render(5, 4, true)
            }
            awaitBetween(fixture.indicator, start, destination)
            ui {
                val current = fixture.indicator.translationX
                fixture.live.performClick()
                assertEquals(current, fixture.indicator.translationX, 0.5f)
            }
            await { fixture.indicator.translationX == fixture.position(fixture.live) }
            ui {
                fixture.recent.performClick()
                fixture.live.performClick()
                fixture.recent.performClick()
                fixture.render(8, 7, true)
            }
            awaitBetween(fixture.indicator, start, destination)
            await { fixture.indicator.translationX == fixture.position(fixture.recent) }
            ui {
                fixture.render(10, 9, true)
                assertEquals(destination, fixture.indicator.translationX, 0f)
                assertTrue(fixture.recent.isSelected)
                assertTrue(!fixture.live.isSelected)
            }
        }

    @Test fun resizeRtlDetachAndDisabledMotionKeepTheIndicatorOnItsSelectedTab() =
        withTabs { fixture ->
            val start = ui { fixture.indicator.translationX }
            val destination = ui { fixture.position(fixture.recent) }
            ui { fixture.recent.performClick() }
            awaitBetween(fixture.indicator, start, destination)
            ui { fixture.root.layoutDirection = View.LAYOUT_DIRECTION_RTL }
            await {
                fixture.recent.left < fixture.live.left &&
                    fixture.indicator.translationX == fixture.position(fixture.recent)
            }
            val width =
                ui {
                    fixture.root.context
                        .dp(280)
                        .coerceAtMost(fixture.root.width)
                }
            ui {
                fixture.tabs.view.layoutParams = FrameLayout.LayoutParams(width, FrameLayout.LayoutParams.WRAP_CONTENT)
                fixture.render(500, 500, true)
            }
            await {
                fixture.tabs.view.width == width &&
                    fixture.indicator.translationX == fixture.position(fixture.recent) &&
                    fixture.indicator.width == fixture.recent.width - 2 * fixture.root.context.dp(4)
            }
            val from = ui { fixture.indicator.translationX }
            val to = ui { fixture.position(fixture.live) }
            ui { fixture.live.performClick() }
            awaitBetween(fixture.indicator, from, to)
            ui {
                fixture.root.removeView(fixture.tabs.view)
                assertEquals(to, fixture.indicator.translationX, 0f)
                fixture.root.addView(fixture.tabs.view)
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(350)
            ui { assertEquals(fixture.position(fixture.live), fixture.indicator.translationX, 0f) }
            setScale("0")
            if (Build.VERSION.SDK_INT >= 26) await { !ValueAnimator.areAnimatorsEnabled() }
            ui {
                fixture.recent.performClick()
                assertEquals(fixture.position(fixture.recent), fixture.indicator.translationX, 0f)
            }
        }

    private fun withTabs(test: (Fixture) -> Unit) {
        ActivityScenario.launch(RecentNativeHost::class.java).use { scenario ->
            lateinit var fixture: Fixture
            scenario.onActivity { activity ->
                WebViewInspector.uninstall()
                val context = activity.inspectorTheme()
                lateinit var tabs: WebViewsTabs
                tabs = WebViewsTabs(context) { recent -> fixture.select(recent) }
                val root =
                    FrameLayout(context).apply {
                        setPadding(0, context.dp(64), 0, 0)
                        addView(
                            tabs.view,
                            FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.WRAP_CONTENT,
                            ),
                        )
                    }
                activity.setContentView(root)
                fixture = Fixture(root, tabs)
                fixture.render(2, 1, false)
            }
            await { fixture.indicator.width > 0 && fixture.tabs.view.isShown }
            if (Build.VERSION.SDK_INT >= 26) await { ValueAnimator.areAnimatorsEnabled() }
            test(fixture)
        }
    }

    private class Fixture(
        val root: FrameLayout,
        val tabs: WebViewsTabs,
    ) {
        private var liveCount = 2
        private var recentCount = 1
        val indicator: View get() = tabs.view.findViewById(R.id.wvi_tab_indicator)
        val live: View get() = tabs.view.findViewById(R.id.wvi_live_tab)
        val recent: View get() = tabs.view.findViewById(R.id.wvi_recent_tab)

        fun position(tab: View): Float = ((tab.parent as View).left + tab.left).toFloat()

        fun render(
            live: Int,
            recent: Int,
            showingRecent: Boolean,
        ) {
            liveCount = live
            recentCount = recent
            tabs.render(live, recent, showingRecent)
        }

        fun select(recent: Boolean) = tabs.render(liveCount, recentCount, recent)
    }

    private fun awaitBetween(
        indicator: View,
        start: Float,
        destination: Float,
    ) {
        await {
            indicator.translationX > minOf(start, destination) + 0.5f &&
                indicator.translationX < maxOf(start, destination) - 0.5f
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (ui(condition)) return
            SystemClock.sleep(10)
        }
        throw AssertionError("Segmented control did not reach the expected animation position")
    }

    private fun setScale(value: String) {
        if (value == "null") {
            shell("settings delete global animator_duration_scale")
        } else {
            require(value.toFloatOrNull() != null)
            shell("settings put global animator_duration_scale $value")
        }
        // The framework cache can remain idle with no attached window; wait on it only in the fixture.
        SystemClock.sleep(100)
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor
            .AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand(command),
            ).bufferedReader()
            .use {
                it.readText().trim()
            }

    private fun <T> ui(action: () -> T): T {
        val result =
            java.util.concurrent.atomic
                .AtomicReference<T>()
        instrumentation.runOnMainSync { result.set(action()) }
        return result.get()
    }
}
