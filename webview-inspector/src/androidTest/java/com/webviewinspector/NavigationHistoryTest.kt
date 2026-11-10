package com.webviewinspector

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.webviewinspector.internal.core.AndroidXNavigationEventSource
import com.webviewinspector.internal.core.InspectorSession
import com.webviewinspector.internal.core.NavigationEventSource
import com.webviewinspector.internal.state.FloatingPosition
import com.webviewinspector.internal.state.InspectorActivityState
import com.webviewinspector.internal.state.NavigationHistoryStore
import org.hamcrest.Matchers.containsString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class NavigationHistoryTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val application get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var server: LocalNavigationServer
    private lateinit var scenario: ActivityScenario<NavigationHistoryHost>
    private lateinit var activity: NavigationHistoryHost
    private lateinit var sheetRoot: View

    @Before fun start() {
        server = LocalNavigationServer()
        ui { WebViewInspector.install(application) }
        scenario =
            ActivityScenario.launch(
                Intent(application, NavigationHistoryHost::class.java)
                    .putExtra("historyBaseUrl", server.base),
            )
        scenario.onActivity { activity = it }
        await {
            activity.views.size == 2 && activity.views[0].url == server.base + "/initial" &&
                activity.findViewById<View>(R.id.wvi_open) != null
        }
        Thread.sleep(700)
        clearClipboard()
    }

    @After fun stop() {
        if (::scenario.isInitialized) scenario.close()
        ui { WebViewInspector.uninstall() }
        server.close()
        clearClipboard()
    }

    @Test fun realRedirectsAreGroupedAndRecordedUrlsCopyExactly() {
        requireEvents()
        openHistory()
        ui { activity.views[0].evaluateJavascript("document.getElementById('go').click()", null) }
        await { historyText().contains("2 redirects · Committed") }
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            assertSame(activity.clients[0], ui { activity.views[0].webViewClient })
        }
        onView(withText(containsString("2 redirects · Committed"))).perform(scrollTo(), click())
        onView(
            withContentDescription("Copy recorded URL: " + server.base + server.middlePath),
        ).perform(scrollTo(), click())
        assertEquals(server.base + server.middlePath, clipboard())
        clearClipboard()
        onView(withId(R.id.wvi_copy_history)).inRoot(isDialog()).perform(click())
        val timeline = clipboard()
        assertTrue(timeline.contains("Navigation events"))
        assertTrue(timeline.contains(server.base + "/redirect-one"))
        assertTrue(timeline.contains(server.base + server.middlePath))
        assertTrue(timeline.contains(server.base + server.finalPath))
        clearClipboard()
        screenshot("history-redirects")
    }

    @Test fun sameDocumentReloadBackForwardAndUnsuccessfulNavigationAreRecorded() {
        requireEvents()
        openHistory()
        val beforeOther = ui { activity.clients[0].finishedPages }
        ui { activity.views[0].loadUrl(server.base + "/other") }
        await {
            historyText().contains("Committed") && activity.views[0].url == server.base + "/other" &&
                activity.clients[0].finishedPages > beforeOther
        }
        val beforeReload = ui { activity.clients[0].finishedPages }
        ui { activity.views[0].reload() }
        await { historyText().contains("Reload · Committed") && activity.clients[0].finishedPages > beforeReload }
        ui { activity.views[0].evaluateJavascript("history.pushState({}, '', '/other#details')", null) }
        await { historyText().contains("Same document · Committed") }
        ui { activity.views[0].goBack() }
        // Chromium may skip entries created without a user gesture; do not assume a fixed URL.
        await { historyText().contains("Back navigation · Committed") && activity.views[0].canGoForward() }
        ui { activity.views[0].goForward() }
        await { historyText().contains("Forward navigation · Committed") }
        ui { activity.views[0].loadUrl(server.base + "/broken") }
        await { historyText().contains("Error page") || historyText().contains("Not committed") }
        screenshot("history-failure")
    }

    @Test fun historyIsolationClearUnavailableSelectionAndPauseResume() {
        openHistory()
        val domain = "127.0.0.1"
        selectTarget("WebView 2, $domain, /secondary")
        val initial = ui { activity.views[1].copyBackForwardList().size }
        onView(withId(R.id.wvi_more)).inRoot(isDialog()).perform(click())
        onView(withId(R.id.wvi_clear_history)).inRoot(isPlatformPopup()).perform(click())
        assertEquals(initial, ui { activity.views[1].copyBackForwardList().size })
        await { historyText().isEmpty() }
        ui { activity.views[0].loadUrl(server.base + "/other") }
        Thread.sleep(700)
        assertTrue(ui { historyText().isEmpty() })
        val next = server.base + "/secondary?raw=a+b&encoded=%252F#same"
        ui { activity.views[1].loadUrl(next) }
        await { historyText().contains("/secondary") }
        ui { activity.views[1].visibility = View.GONE }
        Thread.sleep(1200)
        onView(withId(R.id.wvi_copy_history)).inRoot(isDialog()).perform(click())
        assertTrue(clipboard().contains(next))
        clearClipboard()
        onView(withId(R.id.wvi_current_tab)).inRoot(isDialog()).perform(click())
        assertFalse(ui { dialogView(R.id.wvi_copy_url).isEnabled })
        onView(withId(R.id.wvi_history_tab)).inRoot(isDialog()).perform(click())
        pressBack()
        onView(withId(R.id.wvi_open)).perform(click())
        captureSheetRoot()
        assertTrue(ui { dialogView(R.id.wvi_history_panel).visibility == View.VISIBLE })
        pressBack()
        scenario.moveToState(Lifecycle.State.CREATED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        ui { activity.views[1].visibility = View.VISIBLE }
        Thread.sleep(1200)
        onView(withId(R.id.wvi_open)).perform(click())
        captureSheetRoot()
        selectTarget("WebView 2, $domain, /secondary")
        await { historyText().contains("Recording gap") }
        screenshot("history-resume")
    }

    @Test fun unsupportedListenerFallbackUsesTheSameNativeHistoryAndCopyActions() {
        val store = NavigationHistoryStore()
        val session =
            ui {
                WebViewInspector.uninstall()
                InspectorSession(
                    activity,
                    FloatingPosition(),
                    InspectorActivityState(1),
                    store,
                    appLoads =
                        com.webviewinspector.internal.state
                            .AppLoadStore(),
                    settingsOwnFocus = { false },
                    navigationSource = NavigationEventSource { _, _ -> null },
                ).also { it.start() }
            }
        try {
            await { activity.findViewById<View>(R.id.wvi_open) != null }
            openHistory()
            val next = server.base + "/other?raw=one+two&encoded=%2f%2F#exact"
            ui { activity.views[0].loadUrl(next) }
            await { historyText().contains("Observed URL change") && historyText().contains(next) }
            assertFalse(ui { historyText().contains("Committed") })
            onView(withId(R.id.wvi_copy_history)).inRoot(isDialog()).perform(click())
            val copied = clipboard()
            assertTrue("Expected $next in copied synthetic timeline: $copied", copied.contains(next))
            screenshot("history-fallback")
        } finally {
            ui {
                session.dispose()
                store.clearAll()
            }
        }
    }

    private fun requireEvents() {
        assumeTrue(
            "Installed WebView provider does not support navigation events with this WebKit version",
            ui {
                AndroidXNavigationEventSource.supportsNavigationEvents()
            },
        )
    }

    @Test fun expandedHistoryRetainsFocusAndScrollWhileNewNavigationsArrive() {
        requireEvents()
        openHistory()
        ui { activity.views[0].evaluateJavascript("document.getElementById('go').click()", null) }
        await { historyText().contains("2 redirects · Committed") }
        onView(withText(containsString("2 redirects · Committed"))).perform(scrollTo(), click())
        var focused: View? = null
        onView(withContentDescription("Copy recorded URL: " + server.base + server.middlePath))
            .perform(scrollTo())
            .check { view, error ->
                if (error != null) throw error
                focused = view
                view.isFocusableInTouchMode = true
                assertTrue("Fixture must establish input focus before testing retention", view.requestFocus())
            }
        val anchor = requireNotNull(focused)
        val before = ui { screenTop(anchor) }
        ui { activity.views[0].loadUrl(server.base + "/new-page") }
        await { activity.views[0].url == server.base + "/new-page" && historyText().contains("/new-page") }
        Thread.sleep(500)
        ui {
            assertTrue("Expanded copy action must survive an update", anchor.isAttachedToWindow && anchor.isShown)
            assertTrue("History updates must preserve focus", anchor.isFocused)
            assertTrue(
                "History updates must preserve the scroll anchor: before=$before, after=${screenTop(anchor)}",
                kotlin.math.abs(before - screenTop(anchor)) <= 3,
            )
        }
        screenshot("history-focus-scroll")
    }

    private fun screenTop(view: View): Int {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return location[1]
    }

    private fun selectTarget(description: String) {
        onView(withId(R.id.wvi_target_picker)).inRoot(isDialog()).perform(click())
        onView(withContentDescription(description)).inRoot(isPlatformPopup()).perform(click())
    }

    private fun openHistory() {
        onView(withId(R.id.wvi_open)).perform(click())
        onView(withId(R.id.wvi_history_tab)).inRoot(isDialog()).perform(click())
        captureSheetRoot()
    }

    private fun historyText(): String = texts(dialogView(R.id.wvi_history_entries))

    private fun captureSheetRoot() {
        onView(withId(R.id.wvi_sheet)).check { view, error ->
            if (error != null) throw error
            sheetRoot = requireNotNull(view)
        }
    }

    private fun dialogView(id: Int): View = requireNotNull(sheetRoot.findViewById(id))

    private fun texts(view: View): String =
        when (view) {
            is TextView -> view.text.toString()
            is ViewGroup -> (0 until view.childCount).joinToString("\n") { texts(view.getChildAt(it)) }
            else -> ""
        }

    private fun clipboard(): String =
        ui {
            (
                application.getSystemService(
                    Context.CLIPBOARD_SERVICE,
                ) as ClipboardManager
            ).primaryClip?.getItemAt(0)?.text.toString()
        }

    private fun clearClipboard() {
        ui {
            if (android.os.Build.VERSION.SDK_INT >=
                28
            ) {
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
        val deadline = System.currentTimeMillis() + 15000
        while (System.currentTimeMillis() < deadline) {
            if (ui(predicate)) return
            Thread.sleep(100)
        }
        val diagnostics =
            ui {
                "url=${activity.views[0].url}, back=${activity.views[0].canGoBack()}, forward=${activity.views[0].canGoForward()}, history=${if (::sheetRoot.isInitialized) historyText() else "no sheet"}"
            }
        throw AssertionError("Timed out waiting for synthetic navigation history: $diagnostics")
    }

    private fun <T> ui(block: () -> T): T {
        val result = AtomicReference<Result<T>>()
        instrumentation.runOnMainSync { result.set(runCatching(block)) }
        return result.get().getOrThrow()
    }

    private fun screenshot(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        File(application.getExternalFilesDir(null), "wvi-$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
