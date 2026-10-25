package com.visit.webviewinspector.internal.core

import android.webkit.WebView
import android.os.Handler
import android.os.Looper
import androidx.webkit.Navigation
import androidx.webkit.NavigationListener
import androidx.webkit.WebNavigationClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.visit.webviewinspector.internal.state.NavigationEvent
import com.visit.webviewinspector.internal.state.NavigationFlags
import com.visit.webviewinspector.internal.state.NavigationOutcome
import com.visit.webviewinspector.internal.state.NavigationPhase
import java.lang.ref.WeakReference
import java.util.IdentityHashMap
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Isolates WebKit 1.15's experimental API so the rest of the library remains platform-independent.
 * Registration runs on the main thread. Provider callbacks snapshot values immediately, then
 * publish them to main-thread state. No WebViewClient or settings are changed.
 */
@androidx.annotation.OptIn(markerClass = [WebNavigationClient.ExperimentalNavigationCallback::class])
internal class AndroidXNavigationEventSource : NavigationEventSource<WebView> {
    override fun attach(view: WebView, receive: (NavigationEvent) -> Unit): (() -> Unit)? {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.NAVIGATION_LISTENER_V1)) return null
        val reference = WeakReference(view)
        val identities = IdentityHashMap<Navigation, Long>()
        var nextId = 0L
        val active = AtomicBoolean(true)
        val main = Handler(Looper.getMainLooper())
        val listener = object : NavigationListener {
            private fun emit(navigation: Navigation, phase: NavigationPhase) {
                if (!active.get()) return
                val id = synchronized(identities) {
                    identities[navigation] ?: (++nextId).also { identities[navigation] = it }
                }
                val outcome = when {
                    phase != NavigationPhase.COMPLETED -> NavigationOutcome.IN_PROGRESS
                    navigation.didCommitErrorPage() -> NavigationOutcome.ERROR_PAGE
                    navigation.didCommit() -> NavigationOutcome.COMMITTED
                    else -> NavigationOutcome.NOT_COMMITTED
                }
                val event = NavigationEvent(
                    navigationId = id,
                    phase = phase,
                    rawUrl = navigation.url,
                    atMillis = System.currentTimeMillis(),
                    flags = NavigationFlags(
                        sameDocument = navigation.isSameDocument,
                        reload = navigation.isReload,
                        back = navigation.isBack,
                        forward = navigation.isForward,
                    ),
                    outcome = outcome,
                )
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    if (active.get()) receive(event)
                } else {
                    main.post { if (active.get()) receive(event) }
                }
                if (phase == NavigationPhase.COMPLETED) synchronized(identities) { identities.remove(navigation) }
            }

            override fun onNavigationStarted(navigation: Navigation) = emit(navigation, NavigationPhase.STARTED)
            override fun onNavigationRedirected(navigation: Navigation) = emit(navigation, NavigationPhase.REDIRECTED)
            override fun onNavigationCompleted(navigation: Navigation) = emit(navigation, NavigationPhase.COMPLETED)
        }
        try {
            // The default executor posts another UI task. Navigation is mutable, so by that task
            // a fast redirect may already have advanced again. Snapshot at provider dispatch time.
            WebViewCompat.addNavigationListener(view, Executor { command -> command.run() }, listener)
        } catch (_: UnsupportedOperationException) {
            return null
        }
        return {
            active.set(false)
            main.removeCallbacksAndMessages(null)
            reference.get()?.let { WebViewCompat.removeNavigationListener(it, listener) }
            synchronized(identities) { identities.clear() }
        }
    }
}
