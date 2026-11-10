package com.webviewinspector.internal.core

import com.webviewinspector.internal.state.HistoryCaptureMode
import com.webviewinspector.internal.state.HistoryTargetKey
import com.webviewinspector.internal.state.NavigationEvent
import com.webviewinspector.internal.state.NavigationHistoryStore
import com.webviewinspector.internal.state.NavigationPhase
import com.webviewinspector.internal.state.TargetRegistry
import java.lang.ref.WeakReference

/** The only platform-dependent boundary of the independently testable capture coordinator. */
internal fun interface NavigationEventSource<T : Any> {
    /** Returns cleanup, or null when unsupported. Cleanup must stop future delivery. */
    fun attach(
        view: T,
        receive: (NavigationEvent) -> Unit,
    ): (() -> Unit)?
}

/**
 * Session-owned listener subscriptions and URL sampling, confined to the main thread.
 * Weak targets and invalidated bindings prevent late callbacks from reviving disposed histories.
 */
internal class NavigationHistoryObserver<T : Any>(
    private val activityScope: Long,
    private val store: NavigationHistoryStore,
    private val source: NavigationEventSource<T>,
    private val readUrl: (T) -> String?,
    private val isVisible: (T) -> Boolean,
    private val changed: () -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Binding<T : Any>(
        val key: HistoryTargetKey,
        view: T,
    ) {
        val view = WeakReference(view)
        val navigationRecords = mutableMapOf<Long, Long>()
        var cleanup: (() -> Unit)? = null
        var active = true
        var mode = HistoryCaptureMode.NAVIGATION_EVENTS
    }

    private val bindings = mutableMapOf<Long, Binding<T>>()
    private var disposed = false

    fun sync(targets: List<TargetRegistry.Target<T>>) {
        if (disposed) return
        val visibleIds = targets.map { it.id }.toSet()
        bindings.keys.filter { it !in visibleIds }.forEach(::detach)
        targets.forEach { target ->
            if (bindings.containsKey(target.id)) return@forEach
            val view = target.reference.get()?.takeIf(isVisible) ?: return@forEach
            val binding = Binding(HistoryTargetKey(activityScope, target.id), view)
            bindings[target.id] = binding
            store.open(binding.key, binding.mode, clock())
            binding.cleanup =
                source.attach(view) { event ->
                    if (!binding.active || disposed) return@attach
                    val current = binding.view.get()
                    if (current == null || !isVisible(current)) {
                        detach(target.id)
                        changed()
                        return@attach
                    }
                    val recordId = binding.navigationRecords[event.navigationId]
                    if (recordId == null) {
                        binding.navigationRecords[event.navigationId] = store.startNavigation(binding.key, event)
                    } else {
                        store.updateNavigation(binding.key, recordId, event)
                    }
                    store.rememberUrl(binding.key, event.rawUrl)
                    if (event.phase == NavigationPhase.COMPLETED) binding.navigationRecords.remove(event.navigationId)
                    changed()
                }
            binding.mode =
                if (binding.cleanup == null) HistoryCaptureMode.OBSERVED_URLS else HistoryCaptureMode.NAVIGATION_EVENTS
            store.setMode(binding.key, binding.mode)
            // Reattachment can reveal a change that happened during a documented observation gap.
            store.observe(binding.key, readUrl(view), clock(), recordChanges = true)
        }
    }

    fun sample() {
        if (disposed) return
        bindings.values.toList().forEach { binding ->
            val view = binding.view.get()
            if (view == null || !isVisible(view)) {
                detach(binding.key.targetId)
            } else if (binding.mode == HistoryCaptureMode.OBSERVED_URLS) {
                store.observe(binding.key, readUrl(view), clock(), recordChanges = true)
            }
        }
    }

    /** Called on focus loss/backgrounding; the same Activity can resume with its saved history. */
    fun pause() {
        bindings.keys.toList().forEach(::detach)
    }

    fun dispose() {
        disposed = true
        pause()
    }

    private fun detach(id: Long) {
        val binding = bindings.remove(id) ?: return
        binding.active = false
        binding.cleanup?.invoke()
        binding.cleanup = null
        binding.navigationRecords.clear()
        store.pause(binding.key, clock())
    }
}
