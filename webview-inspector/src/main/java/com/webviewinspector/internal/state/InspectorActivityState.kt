package com.webviewinspector.internal.state

import android.webkit.WebView

/** Weak Activity-map value: preserves selection/presentation across pause without retaining Views. */
internal class InspectorActivityState(
    val scope: Long,
    resolveTarget: ((WebView) -> TargetRegistry.Target<WebView>)? = null,
) {
    val registry = TargetRegistry(resolveTarget)
    val sheet = InspectorSheetState()
    var inspectedKey: HistoryTargetKey? = null
}
