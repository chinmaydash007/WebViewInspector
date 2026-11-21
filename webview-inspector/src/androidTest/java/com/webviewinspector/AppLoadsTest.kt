package com.webviewinspector

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.action.ViewActions.swipeDown
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.hasFocus
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withTagValue
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.webviewinspector.internal.ui.common.dp
import org.hamcrest.Matchers.equalTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicReference

internal const val APP_URL = "https://one.example/app-start?raw=A%2Bb&dup=1&dup=2#raw"
internal const val APP_AUTH = "Bearer synthetic+%2B value"

/** Test-only native host captures before attachment and keeps a Basic instance beside Advanced. */
class AdvancedInspectorHost : ComponentActivity() {
    internal lateinit var first: RecordingWebView
    internal lateinit var second: WebView
    internal lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        WebViewInspector.install(application)
        super.onCreate(savedInstanceState)
        first = RecordingWebView(this).apply { fixture(FIRST_URL) }
        second = WebView(this).apply { fixture(SECOND_URL) }
        val headers = linkedMapOf("Authorization" to APP_AUTH, "x-CaSe" to "  raw+%2B  ")
        WebViewInspector.loadUrl(first, APP_URL, headers)
        headers["Authorization"] = "changed after load"
        container =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12, 100, 12, 60)
                addView(first, LinearLayout.LayoutParams(-1, dp(260)))
                addView(second, LinearLayout.LayoutParams(-1, dp(130)))
            }
        setContentView(container)
    }

    override fun onDestroy() {
        first.destroy()
        second.destroy()
        super.onDestroy()
    }
}

/** Overrides record forwarding only for contract assertions; normal UI tests delegate to WebView. */
internal class RecordingWebView(
    context: Context,
) : WebView(context) {
    var calls = 0
    var lastUrl: String? = null
    var lastHeaders: Map<String, String>? = null
    var callerThread: Thread? = null
    var forward = true
    var failure: RuntimeException? = null

    override fun loadUrl(url: String) {
        calls++
        lastUrl = url
        lastHeaders = null
        callerThread = Thread.currentThread()
        failure?.let { throw it }
        if (forward) super.loadUrl(url)
    }

    override fun loadUrl(
        url: String,
        additionalHttpHeaders: MutableMap<String, String>,
    ) {
        calls++
        lastUrl = url
        lastHeaders = additionalHttpHeaders
        callerThread = Thread.currentThread()
        failure?.let { throw it }
        if (forward) super.loadUrl(url, additionalHttpHeaders)
    }
}

/** Exercises the real facade, native sheet and clipboard, without changing host WebView clients. */
@RunWith(AndroidJUnit4::class)
class AppLoadsTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: AdvancedInspectorHost
    private lateinit var root: View

    @Test fun helperDelegatesOnceOnCallerThreadAndPreservesErrorsWhenInactive() =
        withHost {
            ui {
                val web = activity.first
                web.forward = false
                val headers = linkedMapOf("Case" to "value")

                fun verifyLoad() {
                    web.calls = 0
                    WebViewInspector.loadUrl(web, APP_URL, headers)
                    assertEquals(1, web.calls)
                    assertEquals(APP_URL, web.lastUrl)
                    assertSame(headers, web.lastHeaders)
                    assertSame(Thread.currentThread(), web.callerThread)
                    web.calls = 0
                    WebViewInspector.loadUrl(web, APP_URL)
                    assertEquals(1, web.calls)
                    assertNull(web.lastHeaders)
                }
                verifyLoad()
                val background = Thread { WebViewInspector.loadUrl(web, APP_URL, headers) }
                web.calls = 0
                background.start()
                background.join()
                assertEquals(1, web.calls)
                assertSame(background, web.callerThread)
                WebViewInspector.setEnabled(false)
                verifyLoad()
                WebViewInspector.uninstall()
                verifyLoad()
                val expected = IllegalStateException("synthetic WebView failure")
                web.failure = expected
                try {
                    WebViewInspector.loadUrl(web, APP_URL, headers)
                    fail("The original WebView exception must propagate")
                } catch (actual: IllegalStateException) {
                    assertSame(expected, actual)
                }
                web.failure = null
                WebViewInspector.install(activity.application)
                val invalidMap =
                    object : AbstractMap<String, String>() {
                        override val entries: Set<Map.Entry<String, String>> get() = throw Exception(
                            "synthetic snapshot failure",
                        )
                    }
                web.calls = 0
                WebViewInspector.loadUrl(web, APP_URL, invalidMap)
                assertEquals(1, web.calls)
                assertSame(invalidMap, web.lastHeaders)
            }
        }

    @Test fun preAttachmentFullValuesAndBasicAdvancedSelectionRemainIndependent() =
        withHost {
            openLoads()
            expandFirst()
            assertTrue(panelText().contains(APP_AUTH))
            assertFalse(panelText().contains("changed after load"))
            assertTrue(ui { text(root.findViewById(R.id.wvi_capture_mode)).contains("Advanced") })
            selectSecond()
            assertTrue(panelText().contains("No app loads captured"))
            assertTrue(ui { text(root.findViewById(R.id.wvi_capture_mode)).contains("Basic") })
            assertFalse(ui { root.findViewById<View>(R.id.wvi_copy_app_loads).isEnabled })
            screenshot("app-loads-basic")
            selectFirst()
            assertTrue(panelText().contains(APP_AUTH))
            screenshot("app-loads-advanced")
        }

    @Test fun exactClipboardAndNoHeaderInheritanceAfterOrdinaryNavigation() =
        withHost {
            openLoads()
            expandFirst()
            onView(withText("Copy app-supplied URL")).inRoot(isDialog()).perform(scrollTo(), click())
            assertEquals(APP_URL, clipboard())
            onView(
                withContentDescription("Copy value for Authorization"),
            ).inRoot(isDialog()).perform(scrollTo(), click())
            assertEquals(APP_AUTH, clipboard())
            onView(withText("Copy headers")).inRoot(isDialog()).perform(scrollTo(), click())
            assertEquals("Authorization: $APP_AUTH\nx-CaSe:   raw+%2B  ", clipboard())
            ui { activity.first.loadUrl(UPDATED_URL) }
            await { activity.first.url == UPDATED_URL }
            // System clipboard previews can cover the pinned footer after the preceding copy.
            // Invoke this actual button directly while still verifying real clipboard contents.
            ui { assertTrue(root.findViewById<View>(R.id.wvi_copy_app_loads).performClick()) }
            assertTrue("Export clipboard was: ${clipboard()}", clipboard().contains(APP_URL))
            assertTrue(clipboard().contains("Authorization: $APP_AUTH"))
            assertFalse(
                "Raw WebView navigation must not invent a helper call or headers",
                clipboard().contains(UPDATED_URL),
            )
            ui { WebViewInspector.loadUrl(activity.first, UPDATED_URL) }
            instrumentation.waitForIdleSync()
            ui { root.findViewById<View>(R.id.wvi_sheet_scroll).scrollTo(0, 0) }
            onView(withTagValue(equalTo("app-load-2"))).inRoot(isDialog()).perform(scrollTo(), click())
            assertTrue(panelText().contains("No headers supplied"))
        }

    @Test fun savedCopyClearAndReturnedSelectionPreserveNavigationHistory() =
        withHost {
            openLoads()
            ui { activity.first.visibility = View.GONE }
            await { text(root.findViewById(R.id.wvi_app_loads_panel)).contains("Saved app loads") }
            onView(withId(R.id.wvi_copy_app_loads)).inRoot(isDialog()).perform(click())
            assertTrue(clipboard().contains(APP_AUTH))
            ui { activity.first.visibility = View.VISIBLE }
            Thread.sleep(1200)
            onView(withId(R.id.wvi_current_tab)).inRoot(isDialog()).perform(click())
            assertTrue(ui { root.findViewById<View>(R.id.wvi_copy_url).isEnabled })
            assertFalse(ui { root.findViewById<View>(R.id.wvi_locate).isEnabled })
            assertFalse(ui { activity.findViewById<View>(R.id.wvi_quick_copy).isEnabled })
            assertTrue(ui { text(root.findViewById(R.id.wvi_copy_url)).contains("Copy recorded URL") })
            selectFirst()
            assertTrue(ui { root.findViewById<View>(R.id.wvi_copy_url).isEnabled })
            onView(withId(R.id.wvi_app_loads_tab)).inRoot(isDialog()).perform(click())
            onView(withId(R.id.wvi_more)).inRoot(isDialog()).perform(click())
            onView(withId(R.id.wvi_clear_app_loads)).inRoot(isPlatformPopup()).perform(click())
            assertFalse(ui { root.findViewById<View>(R.id.wvi_copy_app_loads).isEnabled })
            assertTrue(panelText().contains("Future helper calls"))
            onView(withId(R.id.wvi_history_tab)).inRoot(isDialog()).perform(click())
            assertTrue(ui { root.findViewById<View>(R.id.wvi_copy_history).isEnabled })
            pressBack()
            ui {
                WebViewInspector.setEnabled(false)
                WebViewInspector.setEnabled(true)
            }
            await { activity.findViewById<View>(R.id.wvi_open)?.isShown == true }
            openLoads()
            assertTrue(panelText().contains("No app loads captured"))
        }

    @Test fun expansionFocusScrollPinnedFooterAndMenuSurviveUpdates() =
        withHost {
            ui {
                repeat(12) { index ->
                    WebViewInspector.loadUrl(
                        activity.first,
                        "$APP_URL&call=$index",
                        mapOf("X-Long" to "header-value ".repeat(30)),
                    )
                }
            }
            openLoads()
            onView(withTagValue(equalTo("app-load-13"))).inRoot(isDialog()).perform(scrollTo(), click())
            val footerTop = ui { root.findViewById<View>(R.id.wvi_sheet_footer).top }
            ui { root.findViewById<View>(R.id.wvi_sheet_scroll).scrollTo(0, 280) }
            instrumentation.waitForIdleSync()
            val before = ui { root.findViewById<View>(R.id.wvi_sheet_scroll).scrollY }
            onView(withId(R.id.wvi_history_tab)).inRoot(isDialog()).perform(click())
            onView(withId(R.id.wvi_app_loads_tab)).inRoot(isDialog()).perform(click())
            instrumentation.waitForIdleSync()
            assertEquals(before, ui { root.findViewById<View>(R.id.wvi_sheet_scroll).scrollY })
            onView(withId(R.id.wvi_sheet_header)).inRoot(isDialog()).perform(swipeDown())
            assertTrue(ui { root.isShown })
            assertEquals(footerTop, ui { root.findViewById<View>(R.id.wvi_sheet_footer).top })
            ui {
                root.findViewWithTag<View>("app-load-13").apply {
                    isFocusableInTouchMode = true
                    assertTrue("Establish focus before testing capture updates", requestFocus())
                }
                WebViewInspector.loadUrl(activity.first, "https://one.example/new", emptyMap())
            }
            assertTrue(ui { root.findViewWithTag<View>("app-load-13").hasFocus() })
            assertTrue(panelText().contains("header-value"))
            onView(withId(R.id.wvi_more)).inRoot(isDialog()).perform(click())
            onView(withId(R.id.wvi_about)).inRoot(isPlatformPopup()).perform(click())
            pressBack()
            await { root.findViewById<View>(R.id.wvi_app_loads_tab).hasFocus() }
            screenshot("app-loads-focus-scroll")
        }

    @Test fun xmlHostSupportsEmptyHeadersAndCleansRecordsOnRecreation() {
        ActivityScenario.launch(XmlInspectorHost::class.java).use { scenario ->
            try {
                scenario.onActivity { WebViewInspector.loadUrl(it.views[0], APP_URL) }
                awaitView(R.id.wvi_open)
                openInspectorDetails()
                onView(withId(R.id.wvi_app_loads_panel)).inRoot(isDialog()).check { view, _ -> root = view.rootView }
                onView(withId(R.id.wvi_app_loads_tab)).inRoot(isDialog()).perform(click())
                expandFirst()
                assertTrue(panelText().contains("No headers supplied"))
                pressBack()
                scenario.recreate()
                awaitView(R.id.wvi_open)
                openInspectorDetails()
                onView(withId(R.id.wvi_app_loads_panel)).inRoot(isDialog()).check { view, _ -> root = view.rootView }
                onView(withId(R.id.wvi_app_loads_tab)).inRoot(isDialog()).perform(click())
                assertTrue(panelText().contains("No app loads captured"))
            } finally {
                ui { WebViewInspector.uninstall() }
            }
        }
    }

    private fun withHost(test: () -> Unit) {
        ui { WebViewInspector.uninstall() }
        ActivityScenario.launch(AdvancedInspectorHost::class.java).use { scenario ->
            scenario.onActivity { activity = it }
            try {
                await { activity.findViewById<View>(R.id.wvi_open)?.isShown == true }
                test()
            } finally {
                ui { WebViewInspector.uninstall() }
            }
        }
    }

    private fun openLoads() {
        openInspectorDetails()
        onView(withId(R.id.wvi_app_loads_panel)).inRoot(isDialog()).check { view, _ -> root = view.rootView }
        onView(withId(R.id.wvi_app_loads_tab)).inRoot(isDialog()).perform(click())
    }

    // Short viewports can initially expose only part of a row beneath the capture notice.
    // Scroll the real nested content before tapping; retain Espresso's normal visibility constraints.
    private fun expandFirst() =
        onView(withTagValue(equalTo("app-load-1"))).inRoot(isDialog()).perform(scrollTo(), click())

    private fun selectSecond() {
        onView(withId(R.id.wvi_target_picker)).inRoot(isDialog()).perform(click())
        onView(withContentDescription("WebView 2, two.example, /embedded")).inRoot(isDialog()).perform(click())
    }

    private fun selectFirst() {
        onView(withId(R.id.wvi_target_picker)).inRoot(isDialog()).perform(click())
        onView(withContentDescription("WebView 1, one.example, /app-start")).inRoot(isDialog()).perform(click())
    }

    private fun panelText(): String = ui { text(root.findViewById(R.id.wvi_app_loads_panel)) }

    private fun clipboard(): String =
        ui {
            (
                instrumentation.targetContext.getSystemService(
                    Context.CLIPBOARD_SERVICE,
                ) as ClipboardManager
            ).primaryClip!!.getItemAt(0).text.toString()
        }

    private fun awaitView(id: Int) {
        repeat(50) {
            try {
                onView(withId(id)).check { view, error ->
                    if (error != null) throw error
                    assertTrue(view.isShown)
                }
                return
            } catch (_: AssertionError) {
                Thread.sleep(100)
            }
        }
        fail("View did not appear: $id")
    }

    private fun await(condition: () -> Boolean) {
        repeat(80) {
            if (ui(condition)) return
            Thread.sleep(100)
        }
        assertTrue("Timed out awaiting inspector state", ui(condition))
    }

    private fun text(view: View): String =
        when (view) {
            is TextView -> view.text.toString()
            is ViewGroup -> (0 until view.childCount).joinToString("\n") { text(view.getChildAt(it)) }
            else -> ""
        }

    private fun screenshot(name: String) {
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private fun <T> ui(block: () -> T): T {
        val result = AtomicReference<Result<T>>()
        instrumentation.runOnMainSync { result.set(runCatching(block)) }
        return result.get().getOrThrow()
    }
}
