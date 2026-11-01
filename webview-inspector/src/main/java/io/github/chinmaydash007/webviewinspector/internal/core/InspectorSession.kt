package io.github.chinmaydash007.webviewinspector.internal.core

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.WebView
import android.widget.FrameLayout
import io.github.chinmaydash007.webviewinspector.R
import io.github.chinmaydash007.webviewinspector.internal.actions.LocateHighlighter
import io.github.chinmaydash007.webviewinspector.internal.actions.UrlClipboardCopier
import io.github.chinmaydash007.webviewinspector.internal.actions.HistoryClipboardCopier
import io.github.chinmaydash007.webviewinspector.internal.actions.AppLoadClipboardCopier
import io.github.chinmaydash007.webviewinspector.internal.state.AppLoadStore
import io.github.chinmaydash007.webviewinspector.internal.state.AppLoadCopyPart
import io.github.chinmaydash007.webviewinspector.internal.util.AppLoadsPresentation
import io.github.chinmaydash007.webviewinspector.internal.detection.WebViewDetector
import io.github.chinmaydash007.webviewinspector.internal.state.FloatingPosition
import io.github.chinmaydash007.webviewinspector.internal.state.InspectorState
import io.github.chinmaydash007.webviewinspector.internal.state.TargetInfo
import io.github.chinmaydash007.webviewinspector.internal.state.InspectorActivityState
import io.github.chinmaydash007.webviewinspector.internal.state.NavigationHistoryStore
import io.github.chinmaydash007.webviewinspector.internal.state.HistoryTargetKey
import io.github.chinmaydash007.webviewinspector.internal.ui.common.BottomSheetFactory
import io.github.chinmaydash007.webviewinspector.internal.ui.common.inspectorTheme
import io.github.chinmaydash007.webviewinspector.internal.ui.floating.FloatingOverlay
import io.github.chinmaydash007.webviewinspector.internal.ui.sheet.InspectorSheet
import java.lang.ref.WeakReference

/**
 * Coordinates inspection and UI for one resumed Activity; all methods run on the main thread.
 *
 * The controller owns this session. Detection, scheduling, copying, and highlighting are delegated
 * to dedicated collaborators. [dispose] releases all collaborators and Activity-bound UI.
 */
internal class InspectorSession(
    activity: Activity,
    position: FloatingPosition,
    private val activityState: InspectorActivityState,
    private val historyStore: NavigationHistoryStore,
    private val appLoads: AppLoadStore<WebView>,
    private val settingsOwnFocus: () -> Boolean,
    navigationSource: NavigationEventSource<WebView> = AndroidXNavigationEventSource(),
) {
    private val host = WeakReference(activity)
    private val registry = activityState.registry
    private val root = activity.window.decorView as ViewGroup
    private val overlay = FloatingOverlay(activity.inspectorTheme(), position, ::open, ::copy)
    private val detector = WebViewDetector(root, overlay)
    private val clipboardCopier = UrlClipboardCopier(activity, detector::isVisible)
    private val historyCopier = HistoryClipboardCopier(activity)
    private val appLoadCopier = AppLoadClipboardCopier(activity)
    private val highlighter = LocateHighlighter(root, overlay, detector::isVisible, overlay::highlight)
    private val monitor = InspectorMonitor(root, ::discoverTargets, ::sampleUrls, highlighter::update)
    private val historyObserver = NavigationHistoryObserver(
        activityScope = activityState.scope,
        store = historyStore,
        source = navigationSource,
        readUrl = { view -> runCatching { view.url }.getOrNull() },
        isVisible = detector::isVisible,
        changed = ::render,
    )
    private var sheet: InspectorSheet? = null
    private var disposed = false

    fun belongsTo(activity: Activity): Boolean = host.get() === activity

    fun start() {
        attachOverlay()
        monitor.start {
            refreshVisibility()
            discoverTargets()
        }
    }

    private fun attachOverlay() {
        // Content-based SDK input routing must reach our controls. Discovery still scans decor.
        val content = root.findViewById<ViewGroup>(android.R.id.content) ?: root
        if (overlay.parent !== content) {
            (overlay.parent as? ViewGroup)?.removeView(overlay)
            content.addView(overlay, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }
        if (content.getChildAt(content.childCount - 1) !== overlay) {
            overlay.bringToFront()
        }
    }

    /** Keeps the source Activity monitored while either library dialog owns window focus. */
    fun refreshVisibility() {
        if (disposed) return
        val activity = host.get()
        val active = activity != null && !activity.isFinishing && !activity.isDestroyed &&
            (activity.hasWindowFocus() || sheet?.dialog?.isShowing == true || settingsOwnFocus())
        monitor.setActive(active)

        if (active) {
            historyObserver.sync(registry.targets)
            render()
            highlighter.update()
        } else {
            historyObserver.pause()
            overlay.visibility = View.GONE
        }
    }

    private fun discoverTargets() {
        if (!monitor.isActive || disposed) return
        attachOverlay()
        registry.refresh(detector.discover())
        historyObserver.sync(registry.targets)
        render()
        highlighter.update()
    }

    private fun snapshot(): InspectorState {
        appLoads.prune()
        registry.targets.forEach { target ->
            target.reference.get()?.let { appLoads.bind(it, HistoryTargetKey(activityState.scope, target.id)) }
        }
        val targets = registry.targets.mapIndexed { index, target ->
            val rawUrl = target.reference.get()?.let { webView ->
                runCatching { webView.url }.getOrNull()
            }
            TargetInfo(id = target.id, number = index + 1, url = rawUrl)
        }
        val historyTarget = registry.lastSelection
        val history = historyTarget?.let { historyStore.snapshot(HistoryTargetKey(activityState.scope, it.id)) }
        val loads = historyTarget?.let { appLoads.snapshot(HistoryTargetKey(activityState.scope, it.id)) }
        return InspectorState(
            targets = targets,
            selectedId = registry.selectedId,
            history = history,
            historyNumber = historyTarget?.number,
            appLoads = loads,
        )
    }

    /** Explicit capture can refresh the UI immediately; regular polling remains unchanged. */
    fun capturedLoadsChanged() = render()

    private fun sampleUrls() {
        historyObserver.sample()
        render()
    }

    private fun render() {
        if (disposed || !monitor.isActive) return
        val state = snapshot()
        overlay.render(state)
        sheet?.render(state)
    }

    private fun open() {
        val activity = host.get() ?: return
        if (disposed || activity.isFinishing || activity.isDestroyed || sheet?.dialog?.isShowing == true) return

        // The previous dismissal listener may still be queued when the control is reopened.
        sheet = null
        discoverTargets()
        val inspectorSheet = InspectorSheet(
            activity = activity,
            select = { id ->
                discoverTargets()
                registry.select(id)
                render()
            },
            copy = ::copy,
            locate = ::locate,
            presentation = activityState.sheet,
            copyRecordedUrl = { recordId, stepIndex ->
                val state = snapshot()
                val record = state.history?.records?.firstOrNull { it.id == recordId }
                val rawUrl = if (stepIndex == null) record?.rawUrl else record?.steps?.getOrNull(stepIndex)?.rawUrl
                if (rawUrl != null) historyCopier.copyUrl(rawUrl, state.historyNumber ?: 1)
            },
            copyHistory = {
                val state = snapshot()
                state.history?.let { historyCopier.copyTimeline(it, state.historyNumber ?: 1) }
            },
            clearHistory = {
                snapshot().history?.let { history ->
                    historyStore.clear(history.key)
                    activityState.sheet.expandedRecordIds.removeAll(history.records.map { it.id }.toSet())
                }
                render()
            },
            copyAppLoad = { recordId, part ->
                val record = snapshot().appLoads?.records?.firstOrNull { it.id == recordId }
                val text = record?.let {
                    when (part) {
                        AppLoadCopyPart.Headers -> AppLoadsPresentation.headers(it)
                        AppLoadCopyPart.Url -> it.rawUrl
                        is AppLoadCopyPart.HeaderValue -> it.headers.getOrNull(part.index)?.value
                    }
                }
                text?.let(appLoadCopier::copy)
            },
            copyAppLoads = {
                val state = snapshot()
                state.appLoads?.let { appLoadCopier.copy(AppLoadsPresentation.export(it, state.historyNumber ?: 1)) }
            },
            clearAppLoads = {
                snapshot().appLoads?.let { loads ->
                    appLoads.clear(loads.key)
                    activityState.sheet.target(loads.key.targetId).expandedAppLoadIds.clear()
                }
                render()
            },
        )
        sheet = inspectorSheet
        inspectorSheet.render(snapshot())
        inspectorSheet.dialog.setOnDismissListener {
            inspectorSheet.release()
            if (sheet === inspectorSheet) sheet = null
            if (!disposed) root.post { refreshVisibility() }
        }
        inspectorSheet.dialog.show()
        BottomSheetFactory.expand(inspectorSheet.dialog)
        refreshVisibility()
    }

    /** Rescans before resolving the selected identity; the copier then re-reads that same view. */
    private fun copy() {
        discoverTargets()
        val target = registry.selected() ?: return
        val webView = target.reference.get()?.takeIf(detector::isVisible) ?: return
        val number = registry.targets.indexOf(target) + 1
        clipboardCopier.copy(webView, number)
        render()
    }

    private fun locate() {
        discoverTargets()
        val target = registry.selected() ?: return
        val webView = target.reference.get()?.takeIf(detector::isVisible) ?: return
        val number = registry.targets.indexOf(target) + 1
        sheet?.dialog?.dismiss()
        highlighter.show(webView, number)
        overlay.announceForAccessibility(overlay.context.getString(R.string.wvi_located, number))
    }

    /** Idempotently tears down the session on pause, stop, destruction, disabling, or uninstall. */
    fun dispose() {
        if (disposed) return
        disposed = true
        monitor.dispose()
        historyObserver.dispose()
        highlighter.dispose()
        sheet?.release()
        sheet?.dialog?.dismiss()
        sheet = null
        (overlay.parent as? ViewGroup)?.removeView(overlay)
    }
}
