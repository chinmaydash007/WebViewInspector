package com.webviewinspector

import android.view.View
import android.view.ViewGroup
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withId
import org.hamcrest.Matcher

/** Existing detail suites explicitly choose the first Live row when opening a fresh chooser. */
internal fun openInspectorDetails() {
    onView(withId(R.id.wvi_open)).perform(click())
    onView(withId(R.id.wvi_targets)).inRoot(isDialog()).perform(
        object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(ViewGroup::class.java)

            override fun getDescription(): String = "Choose the first visible WebView when the chooser is open"

            override fun perform(
                uiController: UiController,
                view: View,
            ) {
                if (view.isShown) {
                    val rows = view as ViewGroup
                    check(rows.childCount > 0) { "Expected a Live WebView in this detail fixture" }
                    check(rows.getChildAt(0).performClick())
                    uiController.loopMainThreadUntilIdle()
                }
            }
        },
    )
}
