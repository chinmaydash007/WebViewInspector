package com.webviewinspector.internal.state

/** Immutable, view-free history values. The controller confines their owner to the main thread. */
internal data class HistoryTargetKey(
    val activityScope: Long,
    val targetId: Long,
)

internal enum class HistoryCaptureMode { NAVIGATION_EVENTS, OBSERVED_URLS }

internal enum class HistoryRecordKind { FIRST_OBSERVED, NAVIGATION, OBSERVED_CHANGE, GAP }

internal enum class NavigationPhase { STARTED, REDIRECTED, COMPLETED }

internal enum class NavigationOutcome { IN_PROGRESS, COMMITTED, NOT_COMMITTED, ERROR_PAGE }

internal data class NavigationFlags(
    val sameDocument: Boolean = false,
    val reload: Boolean = false,
    val back: Boolean = false,
    val forward: Boolean = false,
)

/** Values are copied inside the platform callback; no mutable AndroidX Navigation escapes it. */
internal data class NavigationEvent(
    val navigationId: Long,
    val phase: NavigationPhase,
    val rawUrl: String,
    val atMillis: Long,
    val flags: NavigationFlags = NavigationFlags(),
    val outcome: NavigationOutcome = NavigationOutcome.IN_PROGRESS,
)

internal data class NavigationStep(
    val phase: NavigationPhase?,
    val rawUrl: String,
    val atMillis: Long,
)

internal data class NavigationRecord(
    val id: Long,
    val kind: HistoryRecordKind,
    val atMillis: Long,
    val steps: List<NavigationStep> = emptyList(),
    val flags: NavigationFlags = NavigationFlags(),
    val outcome: NavigationOutcome? = null,
    val completedAtMillis: Long? = null,
    val joinedInProgress: Boolean = false,
    val gapEndMillis: Long? = null,
) {
    val rawUrl: String? get() = steps.lastOrNull()?.rawUrl
}

internal data class NavigationHistorySnapshot(
    val key: HistoryTargetKey,
    val mode: HistoryCaptureMode,
    val records: List<NavigationRecord>,
    val discardedCount: Int,
    val paused: Boolean,
)

/** Activity-owned presentation preferences survive sheet dismissal and pause, but own no Views. */
internal class InspectorSheetState {
    var tab = InspectorTab.CURRENT_URL
    val expandedRecordIds = mutableSetOf<Long>()
    private val targets =
        object : LinkedHashMap<Long, TargetSheetState>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, TargetSheetState>?): Boolean =
                size > MAX_TARGET_PREFERENCES
        }

    fun target(id: Long): TargetSheetState = targets.getOrPut(id) { TargetSheetState() }

    private companion object {
        const val MAX_TARGET_PREFERENCES = 500
    }
}

/** Layout preferences contain only identities and offsets, never Views or another URL cache. */
internal class TargetSheetState {
    val expandedAppLoadIds = mutableSetOf<Long>()
    var queryExpanded = false
    var currentScrollY = 0
    var historyScrollY = 0
    var historyAnchor: HistoryScrollAnchor? = null
    var appLoadsScrollY = 0
    var appLoadsAnchor: HistoryScrollAnchor? = null
}

internal data class HistoryScrollAnchor(
    val recordId: Long,
    val offset: Int,
)
