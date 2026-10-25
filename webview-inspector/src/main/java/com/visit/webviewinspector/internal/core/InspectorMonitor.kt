package com.visit.webviewinspector.internal.core

import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.view.ViewTreeObserver

/**
 * Owns one session's main-thread discovery timers and root layout/scroll listeners.
 *
 * Discovery is debounced; URL sampling has its own cadence. The session supplies operations and
 * decides when monitoring is active. [dispose] must run when that session ends.
 */
internal class InspectorMonitor(
    private val root: ViewGroup,
    private val discoverTargets: () -> Unit,
    private val sampleUrls: () -> Unit,
    private val updateGeometry: () -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var disposed = false

    var isActive = false
        private set

    private val debouncedDiscovery = Runnable {
        if (isActive) discoverTargets()
    }

    private val fallbackDiscovery = object : Runnable {
        override fun run() {
            if (!isActive) return
            discoverTargets()
            main.postDelayed(this, FALLBACK_DISCOVERY_INTERVAL_MS)
        }
    }

    private val urlSampler = object : Runnable {
        override fun run() {
            if (!isActive) return
            sampleUrls()
            main.postDelayed(this, URL_SAMPLE_INTERVAL_MS)
        }
    }

    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        scheduleDiscovery()
        updateGeometry()
    }

    private val scrollListener = ViewTreeObserver.OnScrollChangedListener {
        scheduleDiscovery()
        updateGeometry()
    }

    /** Waits for the root's posted work before the session performs its initial activation/scan. */
    fun start(onAttached: () -> Unit) {
        root.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        root.viewTreeObserver.addOnScrollChangedListener(scrollListener)
        root.post {
            if (!disposed) onAttached()
        }
    }

    /** Stops polling on focus loss/backgrounding; library-owned dialog focus remains active. */
    fun setActive(active: Boolean) {
        if (disposed || isActive == active) return

        isActive = active
        main.removeCallbacks(fallbackDiscovery)
        main.removeCallbacks(urlSampler)
        main.removeCallbacks(debouncedDiscovery)

        if (active) {
            main.post(fallbackDiscovery)
            main.post(urlSampler)
        }
    }

    private fun scheduleDiscovery() {
        if (!isActive) return
        main.removeCallbacks(debouncedDiscovery)
        main.postDelayed(debouncedDiscovery, DISCOVERY_DEBOUNCE_MS)
    }

    /** Releases every listener and scheduled callback owned by this monitor. */
    fun dispose() {
        disposed = true
        isActive = false
        main.removeCallbacksAndMessages(null)
        root.viewTreeObserver.takeIf { it.isAlive }?.let { observer ->
            observer.removeOnGlobalLayoutListener(layoutListener)
            observer.removeOnScrollChangedListener(scrollListener)
        }
    }

    private companion object {
        const val DISCOVERY_DEBOUNCE_MS = 100L
        const val FALLBACK_DISCOVERY_INTERVAL_MS = 1_000L
        const val URL_SAMPLE_INTERVAL_MS = 500L
    }
}
