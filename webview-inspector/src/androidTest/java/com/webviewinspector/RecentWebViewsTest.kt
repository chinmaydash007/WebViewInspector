package com.webviewinspector

import android.app.Activity
import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

internal const val PAYMENT_RECENT_URL = "https://payment.example/checkout?amount=0&dup=A+%2B&dup=%252F#raw"
internal const val HELPER_RECENT_URL = "https://helper.example/unseen?raw=%2f+%2F"

class RecentPaymentHost : ComponentActivity() {
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        WebViewInspector.install(application)
        super.onCreate(savedInstanceState)
        webView = WebView(this).apply { fixture(PAYMENT_RECENT_URL) }
        setContentView(webView)
        WebViewInspector.loadUrl(webView, PAYMENT_RECENT_URL, mapOf("x-recent" to "  original+%2B  "))
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}

class RecentNativeHost : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        WebViewInspector.install(application)
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "Order confirmed · Amount paid 0" })
    }

    fun captureUnseen() {
        val detached = WebView(this).apply { fixture(HELPER_RECENT_URL) }
        WebViewInspector.loadUrl(detached, HELPER_RECENT_URL, mapOf("x-unseen" to "  captured+%2B  "))
        detached.destroy()
    }
}

/** Real Activity lifecycle coverage uses synthetic local WebView responses, never payment services. */
@RunWith(AndroidJUnit4::class)
class RecentWebViewsTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val application get() = ApplicationProvider.getApplicationContext<Application>()

    @Before fun install() =
        ui {
            WebViewInspector.uninstall()
            WebViewInspector.install(application)
        }

    @After fun cleanup() =
        ui {
            WebViewInspector.uninstall()
            clipboardManager().setPrimaryClip(android.content.ClipData.newPlainText("", ""))
        }

    @Test fun destroyedPaymentActivityRemainsInspectableOnNativeConfirmation() =
        withNativeAfterPayment(capture = true) { native ->
            onView(withId(R.id.wvi_open)).perform(click())
            screenshot("recent-native-chooser")
            assertTrue(dialog(R.id.wvi_recent_tab).isSelected)
            onView(withId(R.id.wvi_live_tab)).inRoot(isDialog()).perform(click())
            assertTrue(text(dialog(R.id.wvi_webviews_panel)).contains("No visible WebViews"))
            onView(withId(R.id.wvi_view_recent)).inRoot(isDialog()).perform(click())
            choose(1, "payment.example", "/checkout")
            screenshot("recent-saved-payment")
            assertEquals(PAYMENT_RECENT_URL, text(dialog(R.id.wvi_current_url)))
            assertFalse(dialog(R.id.wvi_locate).isEnabled)
            onView(withId(R.id.wvi_copy_url)).inRoot(isDialog()).perform(click())
            assertEquals(PAYMENT_RECENT_URL, clipboard())
            onView(withId(R.id.wvi_history_tab)).inRoot(isDialog()).perform(click())
            clickDialog(R.id.wvi_copy_history)
            assertTrue(clipboard().contains(PAYMENT_RECENT_URL))
            onView(withId(R.id.wvi_app_loads_tab)).inRoot(isDialog()).perform(click())
            clickDialog(R.id.wvi_copy_app_loads)
            assertTrue(clipboard().contains("  original+%2B  "))
            pressBack()
            assertTrue(ui { native.findViewById<View>(R.id.wvi_open).isShown })
            assertFalse(ui { native.findViewById<View>(R.id.wvi_quick_copy).isEnabled })
        }

    @Test fun helperOnlyCaptureSurvivesDestructionWithoutActivatingTheFloatingControl() {
        ActivityScenario.launch(RecentNativeHost::class.java).use { native ->
            native.onActivity { it.captureUnseen() }
            native.onActivity { assertFalse(it.findViewById<View>(R.id.wvi_overlay)?.isShown == true) }
        }
        withNativeAfterPayment {
            onView(withId(R.id.wvi_open)).perform(click())
            assertTrue(text(dialog(R.id.wvi_webviews_panel)).contains("Not observed on screen"))
            choose(1, "helper.example", "/unseen")
            assertTrue(dialog(R.id.wvi_app_loads_panel).isShown)
            clickDialog(R.id.wvi_copy_app_loads)
            assertTrue(clipboard().contains("  captured+%2B  "))
            onView(withId(R.id.wvi_current_tab)).inRoot(isDialog()).perform(click())
            assertTrue(text(dialog(R.id.wvi_target_picker)).contains("RecentNativeHost"))
            clickDialog(R.id.wvi_copy_url)
            assertEquals(HELPER_RECENT_URL, clipboard())
        }
    }

    @Test fun clearKeepsControlVisibleAndDisableResetsTheDetectionLatch() =
        withNativeAfterPayment { native ->
            onView(withId(R.id.wvi_open)).perform(click())
            onView(withId(R.id.wvi_clear_recent)).inRoot(isDialog()).perform(click())
            assertTrue(text(dialog(R.id.wvi_webviews_panel)).contains("No recent WebViews yet"))
            screenshot("recent-empty")
            pressBack()
            assertTrue(ui { native.findViewById<View>(R.id.wvi_open).isShown })
            ui { WebViewInspector.setEnabled(false) }
            assertTrue(ui { native.findViewById<View>(R.id.wvi_overlay) == null })
            ui { WebViewInspector.setEnabled(true) }
            await { native.findViewById<View>(R.id.wvi_overlay) != null }
            assertFalse(ui { native.findViewById<View>(R.id.wvi_overlay).isShown })
            ui { WebViewInspector.uninstall() }
            assertTrue(ui { native.findViewById<View>(R.id.wvi_overlay) == null })
        }

    @Test fun separateActivityInstancesKeepSeparateRecentsAcrossBackgroundAndResume() {
        ActivityScenario.launch(RecentPaymentHost::class.java).use { first ->
            var activity: Activity? = null
            first.onActivity { activity = it }
            await { activity?.findViewById<View>(R.id.wvi_open)?.isShown == true }
        }
        withNativeAfterPayment { native ->
            onView(withId(R.id.wvi_open)).perform(click())
            val rows = dialog(R.id.wvi_targets) as android.view.ViewGroup
            assertEquals(2, rows.childCount)
            choose(1, "payment.example", "/checkout")
            assertEquals(PAYMENT_RECENT_URL, text(dialog(R.id.wvi_current_url)))
            onView(withId(R.id.wvi_target_picker)).inRoot(isDialog()).perform(click())
            choose(2, "payment.example", "/checkout")
            assertTrue(text(dialog(R.id.wvi_target_picker)).contains("WebView 2"))
            assertEquals(PAYMENT_RECENT_URL, text(dialog(R.id.wvi_current_url)))
            assertFalse(ui { native.findViewById<View>(R.id.wvi_quick_copy).isEnabled })
        }
    }

    private fun withNativeAfterPayment(
        capture: Boolean = false,
        test: (RecentNativeHost) -> Unit,
    ) {
        val payment = ActivityScenario.launch(RecentPaymentHost::class.java)
        var original: RecentPaymentHost? = null
        payment.onActivity { original = it }
        await { original?.findViewById<View>(R.id.wvi_open)?.isShown == true }
        if (capture) {
            onView(withId(R.id.wvi_open)).perform(click())
            screenshot("recent-live-chooser")
            pressBack()
        }
        ActivityScenario.launch(RecentNativeHost::class.java).use { destination ->
            payment.close()
            assertTrue(ui { original!!.isDestroyed })
            lateinit var native: RecentNativeHost
            destination.onActivity { native = it }
            await { native.findViewById<View>(R.id.wvi_open)?.isShown == true }
            destination.moveToState(Lifecycle.State.CREATED)
            assertTrue(ui { native.findViewById<View>(R.id.wvi_overlay) == null })
            destination.moveToState(Lifecycle.State.RESUMED)
            await { native.findViewById<View>(R.id.wvi_open)?.isShown == true }
            test(native)
        }
    }

    private fun choose(
        number: Int,
        domain: String,
        path: String,
    ) {
        onView(
            withContentDescription("WebView $number, $domain, $path"),
        ).inRoot(isDialog()).perform(scrollTo(), click())
    }

    private fun dialog(id: Int): View {
        lateinit var result: View
        onView(withId(id)).inRoot(isDialog()).check { view, error ->
            if (error != null) throw error
            result = requireNotNull(view)
        }
        return result
    }

    private fun text(view: View): String =
        ui {
            when (view) {
                is TextView -> view.text.toString()
                is android.view.ViewGroup -> (0 until view.childCount).joinToString("\n") { text(view.getChildAt(it)) }
                else -> ""
            }
        }

    private fun clipboardManager() = application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private fun clipboard(): String =
        ui {
            clipboardManager()
                .primaryClip
                ?.getItemAt(0)
                ?.text
                ?.toString()
                .orEmpty()
        }

    private fun <T> ui(action: () -> T): T {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) return action()
        val result =
            java.util.concurrent.atomic
                .AtomicReference<T>()
        instrumentation.runOnMainSync { result.set(action()) }
        return result.get()
    }

    private fun clickDialog(id: Int) {
        val view = dialog(id)
        ui { assertTrue(view.performClick()) }
    }

    private fun screenshot(name: String) {
        instrumentation.waitForIdleSync()
        val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val file = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png")
        file.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            if (ui(condition)) return
            Thread.sleep(50)
        }
        throw AssertionError("Inspector did not reach the expected lifecycle state")
    }
}
