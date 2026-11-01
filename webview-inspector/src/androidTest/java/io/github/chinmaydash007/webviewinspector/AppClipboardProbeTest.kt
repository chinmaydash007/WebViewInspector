package io.github.chinmaydash007.webviewinspector

import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Optional app smoke probe: copy in the app first, then foreground this host to read the clipboard. */
@RunWith(AndroidJUnit4::class)
class AppClipboardProbeTest {
    @Test fun copiedAppUrlMatchesExactly() {
        val expected = InstrumentationRegistry.getArguments().getString("wviExpectedUrl")
        assumeTrue("Only run after manually copying an app URL", expected != null)
        withForegroundClipboard { actual -> assertEquals(expected, actual) }
    }

    @Test fun copiedAppHistoryContainsItsSourceAndBothExpectedUrls() {
        val arguments = InstrumentationRegistry.getArguments()
        val previous = arguments.getString("wviExpectedPreviousUrl")
        val current = arguments.getString("wviExpectedCurrentUrl")
        assumeTrue("Only run after copying an app timeline", previous != null && current != null)
        withForegroundClipboard { actual ->
            assertTrue(actual.orEmpty().contains(requireNotNull(previous)))
            assertTrue(actual.orEmpty().contains(requireNotNull(current)))
            assertTrue(actual.orEmpty().contains(InstrumentationRegistry.getInstrumentation().targetContext.getString(io.github.chinmaydash007.webviewinspector.R.string.wvi_navigation_events)))
        }
    }

    private fun withForegroundClipboard(assertion: (String?) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(ComposeInspectorHost::class.java).use { scenario ->
            lateinit var activity: ComposeInspectorHost
            scenario.onActivity { activity = it }
            val deadline = System.currentTimeMillis() + 12_000
            var focused = false
            while (!focused && System.currentTimeMillis() < deadline) {
                instrumentation.runOnMainSync { focused = activity.hasWindowFocus() }
                if (!focused) Thread.sleep(100)
            }
            instrumentation.runOnMainSync {
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                try {
                    assertTrue("Clipboard probe must own foreground focus", focused)
                    assertion(clipboard.primaryClip?.getItemAt(0)?.text?.toString())
                } finally {
                    WebViewInspector.uninstall()
                }
            }
        }
    }
}
