package com.webviewinspector

import android.content.ClipboardManager
import android.os.Build
import android.os.SystemClock
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.button.MaterialButton
import com.webviewinspector.internal.actions.AppLoadClipboardCopier
import com.webviewinspector.internal.ui.common.clearCopyFeedback
import com.webviewinspector.internal.ui.common.inspectorTheme
import com.webviewinspector.internal.ui.common.setCopyAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class ClipboardFeedbackTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val hasInlineConfirmation get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU

    @Suppress("DEPRECATION") // The inspector announces with TYPE_ANNOUNCEMENT on API 24–32.
    @Test
    fun quickCopyWritesExactUrlWithoutAnInspectorSuccessToast() {
        ActivityScenario.launch(XmlInspectorHost::class.java).use { scenario ->
            lateinit var activity: XmlInspectorHost
            scenario.onActivity { activity = it }
            await {
                activity.hasWindowFocus() &&
                    activity.findViewById<ImageButton>(R.id.wvi_quick_copy)?.isEnabled == true
            }

            val feedback = ConcurrentLinkedQueue<String>()
            val automation = instrumentation.uiAutomation
            automation.setOnAccessibilityEventListener { event ->
                if (event.packageName.toString() == activity.packageName) {
                    val toast =
                        event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED &&
                            event.className.toString() == "android.widget.Toast"
                    if (toast || event.eventType == AccessibilityEvent.TYPE_ANNOUNCEMENT) {
                        feedback.addAll(event.text.map(CharSequence::toString))
                    }
                }
            }
            try {
                // On API 24 the enablement callback can arrive after UiAutomation connects.
                await { activity.getSystemService(AccessibilityManager::class.java)?.isEnabled == true }
                ui {
                    val button = activity.findViewById<ImageButton>(R.id.wvi_quick_copy)
                    val copyIcon = button.drawable
                    assertTrue(button.performClick())
                    if (hasInlineConfirmation) {
                        assertNotSame(
                            copyIcon,
                            button.drawable,
                        )
                    } else {
                        assertSame(copyIcon, button.drawable)
                    }
                    assertEquals(FIRST_URL, activity.getSystemService(ClipboardManager::class.java)!!.text())
                }
                if (hasInlineConfirmation) {
                    await { feedback.contains("Copied to clipboard") }
                }
                SystemClock.sleep(1200)
                assertEquals(
                    if (hasInlineConfirmation) listOf("Copied to clipboard") else emptyList(),
                    feedback.toList(),
                )
            } finally {
                automation.setOnAccessibilityEventListener(null)
                ui { WebViewInspector.uninstall() }
            }
        }
    }

    @Test fun confirmationKeepsButtonStableAndResetsAfterTimeoutRebindOrDetach() =
        withCopyButton { button, parent, _ ->
            val copyIcon = ui { button.icon }
            val originalWidth = ui { button.width }
            val originalHeight = ui { button.height }
            ui {
                assertTrue(button.requestFocusFromTouch())
                assertTrue(button.performClick())
                if (hasInlineConfirmation) assertNotSame(copyIcon, button.icon) else assertSame(copyIcon, button.icon)
            }
            instrumentation.waitForIdleSync()
            ui {
                assertEquals("Copy input", button.text.toString())
                assertEquals(originalWidth, button.width)
                assertEquals(originalHeight, button.height)
                assertTrue(button.hasFocus())
                val firstIcon = button.icon
                assertTrue(button.performClick())
                assertSame(firstIcon, button.icon)
            }
            await { button.icon === copyIcon }
            ui {
                assertTrue(button.performClick())
                button.clearCopyFeedback()
                assertSame(copyIcon, button.icon)
                assertTrue(button.performClick())
                parent.removeView(button)
                assertSame(copyIcon, button.icon)
            }
        }

    @Test fun rejectedClipboardWritePreservesPreviousValueAndSkipsConfirmation() =
        withCopyButton(copyText = "x".repeat(2 * 1024 * 1024)) { button, _, copier ->
            ui {
                val clipboard = button.context.getSystemService(ClipboardManager::class.java)!!
                assertTrue(copier.copy(FIRST_URL))
                val copyIcon = button.icon
                assertTrue(button.performClick())
                assertSame(copyIcon, button.icon)
                assertEquals(FIRST_URL, clipboard.text())
            }
        }

    private fun withCopyButton(
        copyText: String = FIRST_URL,
        test: (MaterialButton, LinearLayout, AppLoadClipboardCopier) -> Unit,
    ) {
        ActivityScenario.launch(XmlInspectorHost::class.java).use { scenario ->
            lateinit var parent: LinearLayout
            lateinit var button: MaterialButton
            lateinit var copier: AppLoadClipboardCopier
            scenario.onActivity { activity ->
                WebViewInspector.uninstall()
                copier = AppLoadClipboardCopier(activity)
                parent = activity.container
                button =
                    MaterialButton(activity.inspectorTheme()).apply {
                        text = "Copy input"
                        iconSize = 18
                        setIconResource(R.drawable.wvi_ic_copy)
                        setCopyAction { copier.copy(copyText) }
                    }
                parent.addView(button, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
            }
            await { button.hasWindowFocus() && button.isShown && button.width > 0 }
            test(button, parent, copier)
        }
    }

    private fun ClipboardManager.text(): String? = primaryClip?.getItemAt(0)?.text?.toString()

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5000
        while (!ui(condition)) {
            check(SystemClock.uptimeMillis() < deadline) { "Timed out waiting for clipboard feedback" }
            SystemClock.sleep(25)
        }
    }

    private fun <T> ui(block: () -> T): T {
        val result = AtomicReference<Result<T>>()
        instrumentation.runOnMainSync { result.set(runCatching(block)) }
        return result.get().getOrThrow()
    }
}
