package io.github.chinmaydash007.webviewinspector.internal.state

import android.webkit.WebView

/** Weak Activity-map value: preserves selection/presentation across pause without retaining Views. */
internal class InspectorActivityState(val scope: Long) {
    val registry = TargetRegistry<WebView>()
    val sheet = InspectorSheetState()
}
