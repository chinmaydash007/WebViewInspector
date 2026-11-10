package io.github.chinmaydash007.webviewinspector.internal.core

import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import androidx.webkit.Navigation
import androidx.webkit.NavigationListener
import androidx.webkit.WebNavigationClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.github.chinmaydash007.webviewinspector.internal.state.NavigationEvent
import io.github.chinmaydash007.webviewinspector.internal.state.NavigationFlags
import io.github.chinmaydash007.webviewinspector.internal.state.NavigationOutcome
import io.github.chinmaydash007.webviewinspector.internal.state.NavigationPhase
import java.lang.ref.WeakReference
import java.util.IdentityHashMap
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Isolates WebKit navigation events across the experimental and stable APIs.
 * Registration runs on the main thread. Provider callbacks snapshot values immediately, then
 * publish them to main-thread state. No WebViewClient or settings are changed.
 */
@androidx.annotation.OptIn(markerClass = [WebNavigationClient.ExperimentalNavigationCallback::class])
internal class AndroidXNavigationEventSource : NavigationEventSource<WebView> {
    // The same callback signature takes an interface in 1.15 and a class in 1.16.
    // Looking up getters on the runtime type avoids incompatible JVM call instructions.
    private class NavigationMethods(
        type: Class<Navigation>,
    ) {
        val url = type.getMethod("getUrl")
        val sameDocument = type.getMethod("isSameDocument")
        val reload = type.getMethod("isReload")
        val back = type.getMethod("isBack")
        val forward = type.getMethod("isForward")
        val committed = type.getMethod("didCommit")
        val errorPage = type.getMethod("didCommitErrorPage")
    }

    companion object {
        // The stable feature name is absent from the 1.15 compile dependency.
        private const val STABLE_NAVIGATION_FEATURE = "NAVIGATION_LISTENER"
        private val navigationType = Navigation::class.java
        private val navigationMethods by lazy {
            try {
                NavigationMethods(navigationType)
            } catch (_: NoSuchMethodException) {
                null
            }
        }

        fun supportsNavigationEvents(): Boolean = supportedMethods() != null

        private fun supportedMethods(): NavigationMethods? {
            val feature =
                if (navigationType.isInterface) WebViewFeature.NAVIGATION_LISTENER_V1 else STABLE_NAVIGATION_FEATURE
            val supported =
                try {
                    WebViewFeature.isFeatureSupported(feature)
                } catch (error: RuntimeException) {
                    if (error.message != "Unknown feature $feature") throw error
                    false
                }
            return if (supported) navigationMethods else null
        }
    }

    override fun attach(
        view: WebView,
        receive: (NavigationEvent) -> Unit,
    ): (() -> Unit)? {
        val methods = supportedMethods() ?: return null
        val reference = WeakReference(view)
        val identities = IdentityHashMap<Navigation, Long>()
        var nextId = 0L
        val active = AtomicBoolean(true)
        val main = Handler(Looper.getMainLooper())
        val listener =
            object : NavigationListener {
                private fun emit(
                    navigation: Navigation,
                    phase: NavigationPhase,
                ) {
                    if (!active.get()) return
                    val id =
                        synchronized(identities) {
                            identities[navigation] ?: (++nextId).also { identities[navigation] = it }
                        }
                    val outcome =
                        when {
                            phase != NavigationPhase.COMPLETED -> NavigationOutcome.IN_PROGRESS
                            methods.errorPage.invoke(navigation) as Boolean -> NavigationOutcome.ERROR_PAGE
                            methods.committed.invoke(navigation) as Boolean -> NavigationOutcome.COMMITTED
                            else -> NavigationOutcome.NOT_COMMITTED
                        }
                    val event =
                        NavigationEvent(
                            navigationId = id,
                            phase = phase,
                            rawUrl = methods.url.invoke(navigation) as String,
                            atMillis = System.currentTimeMillis(),
                            flags =
                                NavigationFlags(
                                    sameDocument = methods.sameDocument.invoke(navigation) as Boolean,
                                    reload = methods.reload.invoke(navigation) as Boolean,
                                    back = methods.back.invoke(navigation) as Boolean,
                                    forward = methods.forward.invoke(navigation) as Boolean,
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

                override fun onNavigationRedirected(navigation: Navigation) =
                    emit(navigation, NavigationPhase.REDIRECTED)

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
