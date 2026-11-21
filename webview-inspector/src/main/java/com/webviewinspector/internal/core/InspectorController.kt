package com.webviewinspector.internal.core

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import android.webkit.WebView
import com.webviewinspector.internal.state.AppLoadStore
import com.webviewinspector.internal.state.FloatingPosition
import com.webviewinspector.internal.state.InspectorActivityState
import com.webviewinspector.internal.state.InspectorArchive
import com.webviewinspector.internal.state.NavigationHistoryStore
import com.webviewinspector.internal.ui.common.BottomSheetFactory
import com.webviewinspector.internal.ui.sheet.SettingsSheet
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.IdentityHashMap
import java.util.WeakHashMap

/**
 * Process-level owner of lifecycle registration, the current Activity session, and settings.
 *
 * Called only through the main-thread facade. Registries and focus watches use weak references;
 * normalized position and enabled state last for this installation only. [dispose] unregisters all
 * callbacks and releases every Activity-bound resource.
 */
internal class InspectorController(
    private val application: Application,
    private val enabledChanged: (Boolean) -> Unit,
) : Application.ActivityLifecycleCallbacks {
    private val main = Handler(Looper.getMainLooper())
    private val resumedActivities = WeakHashMap<Activity, FocusWatch>()
    private val activityStates = WeakHashMap<Activity, InspectorActivityState>()
    private val historyStore = NavigationHistoryStore()
    private val appLoads = AppLoadStore<WebView>()
    private val archive = InspectorArchive(historyStore, appLoads)
    private var nextActivityScope = 0L
    private val position = FloatingPosition()
    private var session: InspectorSession? = null
    private var settings: SettingsSheet? = null
    private var settingsOwner: WeakReference<Activity>? = null
    private var enabled = true
    private var disposed = false

    fun install() {
        application.registerActivityLifecycleCallbacks(this)
        enabledChanged(true)
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        enabledChanged(value)
        if (value) {
            val activity =
                resumedActivities.keys.lastOrNull { it.hasWindowFocus() }
                    ?: settingsOwner?.get()?.takeIf { resumedActivities.containsKey(it) }
                    ?: resumedActivities.keys.lastOrNull()
            activity?.let(::activate)
        } else {
            session?.dispose()
            session = null
            activityStates.clear()
            archive.clearAll()
        }
    }

    /** Captures explicit helper inputs even before attachment; normal discovery owns selection. */
    fun captureLoad(
        view: WebView,
        url: String,
        headers: Map<String, String>,
    ) {
        if (!enabled || disposed) return
        val owner = activityOwner(view.context)
        if (owner?.isDestroyed == true) return
        val scope =
            owner?.let { activity ->
                activityState(activity).scope
            }
        val now = System.currentTimeMillis()
        val target = archive.target(view, scope ?: 0, owner?.javaClass?.simpleName, now)
        appLoads.capture(
            view,
            rawUrl = url,
            headers = headers,
            atMillis = now,
            ownerScope = scope,
        )
        appLoads.bind(view, target.key(scope ?: 0))
        archive.prune(session?.liveTargetIds().orEmpty())
        session?.capturedLoadsChanged()
    }

    private fun activityState(activity: Activity): InspectorActivityState =
        activityStates.getOrPut(activity) {
            val scope = ++nextActivityScope
            val name = activity.javaClass.simpleName
            InspectorActivityState(scope) { view -> archive.target(view, scope, name, System.currentTimeMillis()) }
        }

    private fun activityOwner(context: Context): Activity? {
        var current = context
        val visited = Collections.newSetFromMap(IdentityHashMap<Context, Boolean>())
        while (visited.add(current)) {
            if (current is Activity) return current
            current = (current as? ContextWrapper)?.baseContext ?: return null
        }
        return null
    }

    fun showSettings(activity: Activity) {
        settings?.dialog?.dismiss()
        settingsOwner = WeakReference(activity)
        val settingsSheet = SettingsSheet(activity, enabled, ::setEnabled)
        settings = settingsSheet
        settingsSheet.dialog.setOnDismissListener {
            if (settings === settingsSheet) {
                settings = null
                settingsOwner = null
            }
            session?.refreshVisibility()
        }
        settingsSheet.dialog.show()
        BottomSheetFactory.expand(settingsSheet.dialog)
    }

    /** Reuses an active session, or disposes the old Activity's UI before switching owners. */
    private fun activate(activity: Activity) {
        if (!enabled || disposed || activity.isFinishing || activity.isDestroyed) return
        if (session?.belongsTo(activity) == true) {
            session?.refreshVisibility()
            return
        }

        session?.dispose()
        session =
            InspectorSession(
                activity = activity,
                position = position,
                activityState = activityState(activity),
                historyStore = historyStore,
                appLoads = appLoads,
                archive = archive,
                settingsOwnFocus = {
                    settings?.dialog?.isShowing == true && settingsOwner?.get() === activity
                },
            ).also { it.start() }
    }

    /** Releases live UI on pause/stop while preserving weak selection for the same Activity. */
    private fun detach(activity: Activity) {
        resumedActivities.remove(activity)?.detach()
        if (session?.belongsTo(activity) == true) {
            session?.dispose()
            session = null
        }
        if (settingsOwner?.get() === activity) {
            settings?.dialog?.dismiss()
            settings = null
            settingsOwner = null
        }
        resumedActivities.keys.lastOrNull { it.hasWindowFocus() }?.let(::activate)
    }

    override fun onActivityResumed(activity: Activity) {
        resumedActivities.remove(activity)?.detach()
        val activityReference = WeakReference(activity)
        val root = activity.window.decorView
        val listener =
            ViewTreeObserver.OnWindowFocusChangeListener { focused ->
                main.post {
                    val resumedActivity =
                        activityReference
                            .get()
                            ?.takeIf { resumedActivities.containsKey(it) }
                            ?: return@post
                    if (focused) {
                        activate(resumedActivity)
                    } else {
                        session?.takeIf { it.belongsTo(resumedActivity) }?.refreshVisibility()
                    }
                }
            }
        root.viewTreeObserver.addOnWindowFocusChangeListener(listener)
        resumedActivities[activity] = FocusWatch(WeakReference(root), listener)
        activate(activity)
    }

    override fun onActivityPaused(activity: Activity) = detach(activity)

    override fun onActivityStopped(activity: Activity) = detach(activity)

    override fun onActivityDestroyed(activity: Activity) {
        detach(activity)
        activityStates.remove(activity)?.let { state ->
            state.registry.clear()
        }
    }

    override fun onActivityCreated(
        activity: Activity,
        state: Bundle?,
    ) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(
        activity: Activity,
        state: Bundle,
    ) = Unit

    fun dispose() {
        disposed = true
        application.unregisterActivityLifecycleCallbacks(this)
        resumedActivities.values.toList().forEach { it.detach() }
        resumedActivities.clear()
        activityStates.clear()
        main.removeCallbacksAndMessages(null)
        session?.dispose()
        session = null
        archive.clearAll()
        settings?.dialog?.dismiss()
        settings = null
        settingsOwner = null
        enabledChanged(false)
    }

    /** Weak root ownership avoids extending an Activity's lifetime through its focus observer. */
    private class FocusWatch(
        private val root: WeakReference<View>,
        private val listener: ViewTreeObserver.OnWindowFocusChangeListener,
    ) {
        fun detach() {
            root
                .get()
                ?.viewTreeObserver
                ?.takeIf { it.isAlive }
                ?.removeOnWindowFocusChangeListener(listener)
        }
    }
}
