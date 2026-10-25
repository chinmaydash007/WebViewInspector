package com.visit.webviewinspector

import android.app.Activity
import android.app.Application
import android.os.Handler
import android.os.Looper
import com.visit.webviewinspector.internal.core.InspectorController

/**
 * Install once in Application.onCreate. All mutation is serialized onto the main thread.
 * URL access is local to this process; the library never modifies a WebView or its clients.
 */
object WebViewInspector {
    private val main = Handler(Looper.getMainLooper())
    private var controller: InspectorController? = null
    @Volatile
    private var enabled = false

    /** Installs once, initially enabled. Repeated calls preserve the active installation. */
    @JvmStatic
    fun install(application: Application) = onMain {
        if (controller == null) {
            controller = InspectorController(application) { enabled = it }.also { it.install() }
        }
    }

    /** Shows library-owned settings for either Activity family, installing first if necessary. */
    @JvmStatic
    fun showSettings(activity: Activity) = onMain {
        if (!activity.isFinishing && !activity.isDestroyed) {
            install(activity.application)
            controller?.showSettings(activity)
        }
    }

    /** Enables/disables the current installation; calls before installation have no effect. */
    @JvmStatic
    fun setEnabled(enabled: Boolean) = onMain {
        controller?.setEnabled(enabled)
    }

    /** Returns the last applied main-thread state, which may lag a queued background-thread call. */
    @JvmStatic
    fun isEnabled(): Boolean = enabled

    /** Releases callbacks, timers, dialogs, and overlays. Repeated calls are safe. */
    @JvmStatic
    fun uninstall() = onMain {
        controller?.dispose()
        controller = null
        enabled = false
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            main.post(action)
        }
    }
}
