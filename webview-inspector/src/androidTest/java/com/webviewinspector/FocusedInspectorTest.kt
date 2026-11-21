package com.webviewinspector

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
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
import com.webviewinspector.internal.state.HistoryCaptureMode
import com.webviewinspector.internal.state.HistoryRecordKind
import com.webviewinspector.internal.state.HistoryTargetKey
import com.webviewinspector.internal.state.InspectorSheetState
import com.webviewinspector.internal.state.InspectorState
import com.webviewinspector.internal.state.NavigationHistorySnapshot
import com.webviewinspector.internal.state.NavigationOutcome
import com.webviewinspector.internal.state.NavigationPhase
import com.webviewinspector.internal.state.NavigationRecord
import com.webviewinspector.internal.state.NavigationStep
import com.webviewinspector.internal.state.TargetInfo
import com.webviewinspector.internal.ui.common.BottomSheetFactory
import com.webviewinspector.internal.ui.sheet.InspectorSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Calendar
import java.util.concurrent.atomic.AtomicReference

/** Controlled snapshots cover presentation edges without changing WebViews or relying on a provider. */
@RunWith(AndroidJUnit4::class)
class FocusedInspectorTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var sheet: InspectorSheet
    private lateinit var state: InspectorState
    private var preferences = InspectorSheetState().apply { choosingTarget = false }
    private lateinit var histories: Map<Long, NavigationHistorySnapshot>
    private var copiedId: Long? = null

    @Test fun pinnedActionsTargetPickerAndContentSizedCurrentWorkInBothHosts() {
        for (host in listOf(ComposeInspectorHost::class.java, XmlInspectorHost::class.java)) {
            withSheet(host) {
                ui { assertNavigationBarClearance() }
                val currentHeight = ui { sheet.dialog.findViewById<View>(R.id.wvi_sheet)!!.height }
                onView(withId(R.id.wvi_query_toggle)).inRoot(isDialog()).perform(scrollTo(), click())
                assertTrue(ui { sheet.dialog.findViewById<View>(R.id.wvi_query_values)!!.isShown })
                onView(withId(R.id.wvi_history_tab)).inRoot(isDialog()).perform(click())
                ui { assertNavigationBarClearance() }
                val historyHeight = ui { sheet.dialog.findViewById<View>(R.id.wvi_sheet)!!.height }
                assertTrue(
                    "Current URL should fit its content; History uses the larger cap",
                    historyHeight >= currentHeight,
                )
                val before = ui { bounds(R.id.wvi_sheet_footer) }
                ui { sheet.dialog.findViewById<View>(R.id.wvi_sheet_scroll)!!.scrollTo(0, 1200) }
                instrumentation.waitForIdleSync()
                assertEquals(before, ui { bounds(R.id.wvi_sheet_footer) })
                onView(withId(R.id.wvi_copy_history)).inRoot(isDialog()).perform(click())
                assertEquals(1L, copiedId)
                chooseSecond()
                onView(withId(R.id.wvi_copy_history)).inRoot(isDialog()).perform(click())
                assertEquals(2L, copiedId)
                screenshot("focused-${host.simpleName}")
            }
        }
    }

    @Test fun savedAndReturnedTargetsRequireExplicitReselection() =
        withSheet(ComposeInspectorHost::class.java) {
            ui {
                state = state.copy(targets = state.targets.drop(1), selectedId = null)
                sheet.render(state)
            }
            assertFalse(ui { sheet.dialog.findViewById<View>(R.id.wvi_copy_url)!!.isEnabled })
            onView(withId(R.id.wvi_view_saved)).inRoot(isDialog()).perform(scrollTo(), click())
            onView(withId(R.id.wvi_copy_history)).inRoot(isDialog()).perform(click())
            assertEquals(1L, copiedId)
            ui {
                state = state.copy(targets = listOf(TargetInfo(1, 1, FIRST_URL), TargetInfo(2, 2, SECOND_URL)))
                sheet.render(state)
            }
            onView(withId(R.id.wvi_current_tab)).inRoot(isDialog()).perform(click())
            assertFalse(ui { sheet.dialog.findViewById<View>(R.id.wvi_copy_url)!!.isEnabled })
            assertTrue(ui { text(sheet.dialog.findViewById(R.id.wvi_target_picker)!!).contains("Select again") })
            onView(withId(R.id.wvi_target_picker)).inRoot(isDialog()).perform(click())
            onView(withContentDescription("WebView 1, one.example, /full")).inRoot(isDialog()).perform(click())
            assertTrue(ui { sheet.dialog.findViewById<View>(R.id.wvi_copy_url)!!.isEnabled })
            screenshot("focused-returned")
        }

    @Test fun helpMenuClearAndChildBackKeepParentAliveAndReleaseWindows() =
        withSheet(XmlInspectorHost::class.java) {
            onView(withId(R.id.wvi_history_tab)).inRoot(isDialog()).perform(click())
            onView(withId(R.id.wvi_more)).inRoot(isDialog()).perform(click())
            onView(withId(R.id.wvi_about)).inRoot(isPlatformPopup()).perform(click())
            onView(withText("About capture")).check { view, error ->
                if (error != null) throw error
                assertTrue(view.isShown)
            }
            pressBack()
            assertTrue(ui { sheet.dialog.isShowing })
            onView(withId(R.id.wvi_more)).inRoot(isDialog()).perform(click())
            onView(withId(R.id.wvi_clear_history)).inRoot(isPlatformPopup()).perform(click())
            assertFalse(ui { sheet.dialog.findViewById<View>(R.id.wvi_copy_history)!!.isEnabled })
            assertTrue(
                ui { text(sheet.dialog.findViewById(R.id.wvi_history_panel)!!).contains("No recorded navigation yet") },
            )
            onView(withId(R.id.wvi_more)).inRoot(isDialog()).perform(click())
            ui {
                sheet.release()
                sheet.dialog.dismiss()
            }
            onView(withId(R.id.wvi_sheet)).check(
                androidx.test.espresso.assertion.ViewAssertions
                    .doesNotExist(),
            )
        }

    @Test fun perTargetScrollQueryAndExpansionSurviveNavigationAndReopening() =
        withSheet(ComposeInspectorHost::class.java) {
            onView(withId(R.id.wvi_query_toggle)).inRoot(isDialog()).perform(scrollTo(), click())
            onView(withId(R.id.wvi_history_tab)).inRoot(isDialog()).perform(click())
            ui { sheet.dialog.findViewById<View>(R.id.wvi_sheet_scroll)!!.scrollTo(0, 500) }
            instrumentation.waitForIdleSync()
            val firstScroll = ui { sheet.dialog.findViewById<View>(R.id.wvi_sheet_scroll)!!.scrollY }
            chooseSecond()
            ui { sheet.dialog.findViewById<View>(R.id.wvi_sheet_scroll)!!.scrollTo(0, 140) }
            onView(withId(R.id.wvi_target_picker)).inRoot(isDialog()).perform(click())
            onView(withContentDescription("WebView 1, one.example, /full")).inRoot(isDialog()).perform(click())
            instrumentation.waitForIdleSync()
            assertEquals(firstScroll, ui { sheet.dialog.findViewById<View>(R.id.wvi_sheet_scroll)!!.scrollY })
            onView(withId(R.id.wvi_current_tab)).inRoot(isDialog()).perform(click())
            assertTrue(ui { sheet.dialog.findViewById<View>(R.id.wvi_query_values)!!.isShown })
            onView(withId(R.id.wvi_history_tab)).inRoot(isDialog()).perform(click())
            ui {
                sheet.release()
                sheet.dialog.dismiss()
            }
            ui { createSheet(requireNotNull(activity)) }
            instrumentation.waitForIdleSync()
            assertEquals(com.webviewinspector.internal.state.InspectorTab.HISTORY, preferences.tab)
            assertEquals(firstScroll, ui { sheet.dialog.findViewById<View>(R.id.wvi_sheet_scroll)!!.scrollY })
        }

    @Test fun dateGroupsGapCountDiscardedAndObservedLabelsRemainHonest() =
        withSheet(XmlInspectorHost::class.java) {
            onView(withId(R.id.wvi_history_tab)).inRoot(isDialog()).perform(click())
            ui {
                val now = System.currentTimeMillis()
                val yesterday =
                    Calendar
                        .getInstance()
                        .apply {
                            timeInMillis = now
                            add(Calendar.DATE, -1)
                        }.timeInMillis
                val records =
                    listOf(
                        record(1001, now, "/today"),
                        NavigationRecord(1002, HistoryRecordKind.GAP, now - 1000, gapEndMillis = now),
                        record(1003, yesterday, "/yesterday"),
                    )
                state =
                    state.copy(
                        history =
                            histories.getValue(1).copy(
                                mode = HistoryCaptureMode.OBSERVED_URLS,
                                records =
                                    records.map {
                                        if (it.kind ==
                                            HistoryRecordKind.GAP
                                        ) {
                                            it
                                        } else {
                                            it.copy(kind = HistoryRecordKind.OBSERVED_CHANGE, outcome = null)
                                        }
                                    },
                                discardedCount = 4,
                            ),
                    )
                sheet.render(state)
            }
            val historyText = ui { text(sheet.dialog.findViewById(R.id.wvi_history_panel)!!) }
            assertTrue(historyText.contains("Today") && historyText.contains("Yesterday"))
            assertTrue(historyText.contains("4 older records discarded"))
            assertTrue(historyText.contains("Recording gap"))
            assertFalse(historyText.contains("Committed"))
            assertEquals(
                "History2",
                ui {
                    text(sheet.dialog.findViewById(R.id.wvi_history_tab)!!).replace(" ", "").replace("\n", "")
                },
            )
            screenshot("focused-observed-date-groups")
        }

    @Test fun routineUrlUpdatesKeepHeightAndVisibleNoUrlStateKeepsLocateAvailable() =
        withSheet(ComposeInspectorHost::class.java) {
            val height = ui { sheet.dialog.findViewById<View>(R.id.wvi_sheet)!!.height }
            ui {
                val longUrl = "https://one.example/full?encoded=" + "a%2Bb".repeat(240)
                state = state.copy(targets = state.targets.map { if (it.id == 1L) it.copy(url = longUrl) else it })
                sheet.render(state)
            }
            instrumentation.waitForIdleSync()
            assertEquals(
                "Polling must not resize Current URL",
                height,
                ui {
                    sheet.dialog.findViewById<View>(R.id.wvi_sheet)!!.height
                },
            )
            assertTrue(ui { sheet.dialog.findViewById<View>(R.id.wvi_sheet_scroll)!!.canScrollVertically(1) })
            ui {
                state = state.copy(targets = state.targets.map { if (it.id == 1L) it.copy(url = null) else it })
                sheet.render(state)
            }
            instrumentation.waitForIdleSync()
            assertFalse(ui { sheet.dialog.findViewById<View>(R.id.wvi_copy_url)!!.isEnabled })
            assertTrue(
                ui {
                    sheet.dialog.findViewById<View>(R.id.wvi_locate)!!.isEnabled &&
                        sheet.dialog.findViewById<View>(R.id.wvi_locate)!!.isShown
                },
            )
            assertFalse(ui { sheet.dialog.findViewById<View>(R.id.wvi_current_url)!!.isShown })
            onView(
                withText("No URL yet"),
            ).inRoot(
                isDialog(),
            ).check(
                androidx.test.espresso.assertion.ViewAssertions.matches(
                    androidx.test.espresso.matcher.ViewMatchers
                        .isDisplayed(),
                ),
            )
            screenshot("focused-no-url")
        }

    private var activity: Activity? = null

    private fun <T : Activity> withSheet(
        host: Class<T>,
        test: () -> Unit,
    ) {
        ActivityScenario.launch(host).use { scenario ->
            scenario.onActivity {
                activity = it
                preferences = InspectorSheetState().apply { choosingTarget = false }
                WebViewInspector.uninstall()
                val now = System.currentTimeMillis()
                histories =
                    (1L..2L).associateWith { id ->
                        NavigationHistorySnapshot(
                            HistoryTargetKey(1, id),
                            HistoryCaptureMode.NAVIGATION_EVENTS,
                            (1L..24L).map { record(id * 100 + it, now - it * 1000, "/record-$it") },
                            0,
                            paused = false,
                        )
                    }
                state =
                    InspectorState(
                        listOf(TargetInfo(1, 1, FIRST_URL), TargetInfo(2, 2, SECOND_URL)),
                        1,
                        histories.getValue(1),
                        1,
                    )
                createSheet(it)
            }
            try {
                instrumentation.waitForIdleSync()
                test()
            } finally {
                ui {
                    sheet.release()
                    sheet.dialog.dismiss()
                    WebViewInspector.uninstall()
                    activity = null
                }
            }
        }
    }

    private fun createSheet(activity: Activity) {
        sheet =
            InspectorSheet(
                activity,
                select = { id ->
                    state = state.copy(selectedId = id, history = histories.getValue(id), historyNumber = id.toInt())
                    sheet.render(state)
                },
                copy = {
                    copiedId = state.selectedId
                    copiedId != null
                },
                locate = {},
                presentation = preferences,
                copyRecordedUrl = { _, _ -> false },
                copyHistory = {
                    copiedId = state.history?.key?.targetId
                    copiedId != null
                },
                clearHistory = {
                    state = state.copy(history = state.history!!.copy(records = emptyList(), discardedCount = 0))
                    sheet.render(state)
                },
                copyAppLoad = { _, _ -> false },
                copyAppLoads = { false },
                clearAppLoads = {},
            )
        sheet.render(state)
        val created = sheet
        created.dialog.setOnDismissListener { created.release() }
        sheet.dialog.show()
        BottomSheetFactory.expand(sheet.dialog)
    }

    private fun chooseSecond() {
        onView(withId(R.id.wvi_target_picker)).inRoot(isDialog()).perform(click())
        onView(withContentDescription("WebView 2, two.example, /embedded")).inRoot(isDialog()).perform(click())
    }

    private fun record(
        id: Long,
        time: Long,
        path: String,
    ) = NavigationRecord(
        id,
        HistoryRecordKind.NAVIGATION,
        time,
        listOf(NavigationStep(NavigationPhase.STARTED, "https://one.example$path?raw=a%2Bb", time)),
        outcome = NavigationOutcome.COMMITTED,
    )

    private fun bounds(id: Int): Rect = Rect().also { sheet.dialog.findViewById<View>(id)!!.getGlobalVisibleRect(it) }

    private fun assertNavigationBarClearance() {
        val host = requireNotNull(activity)
        val windowBottom =
            if (Build.VERSION.SDK_INT >= 30) {
                host.windowManager.currentWindowMetrics.bounds.bottom
            } else {
                @Suppress("DEPRECATION")
                Point().also { host.windowManager.defaultDisplay.getRealSize(it) }.y
            }
        val surface =
            requireNotNull(sheet.dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet))
        val footer = requireNotNull(sheet.dialog.findViewById<View>(R.id.wvi_sheet_footer))
        val insets = requireNotNull(ViewCompat.getRootWindowInsets(surface))
        val safeBottom =
            insets.getInsets(WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout()).bottom
        assertEquals("The sheet surface should reach the window bottom", windowBottom, bottomOnScreen(surface))
        assertEquals(
            "The footer should end above the navigation area",
            windowBottom - safeBottom,
            bottomOnScreen(footer),
        )
    }

    private fun bottomOnScreen(view: View): Int = IntArray(2).also(view::getLocationOnScreen)[1] + view.height

    private fun text(view: View): String =
        when (view) {
            is TextView -> view.text.toString()
            is ViewGroup -> (0 until view.childCount).joinToString("") { text(view.getChildAt(it)) }
            else -> ""
        }

    private fun screenshot(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
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
