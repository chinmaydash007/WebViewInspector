package com.webviewinspector.internal.state

/** Immutable render snapshot shared by the floating control and inspector sheet; owns no views. */
internal data class InspectorState(
    val targets: List<TargetInfo>,
    val selectedId: Long?,
    val history: NavigationHistorySnapshot? = null,
    val historyNumber: Int? = null,
    val appLoads: AppLoadsSnapshot? = null,
    val recentTargets: List<SavedTargetInfo> = emptyList(),
    val inspectedTarget: SavedTargetInfo? = null,
    val hasDetectedWebView: Boolean = targets.isNotEmpty(),
) {
    val selected: TargetInfo?
        get() = targets.firstOrNull { it.id == selectedId }
}

/** An ephemeral, view-free summary; strings reference the stores' existing budgeted records. */
internal data class SavedTargetInfo(
    val key: HistoryTargetKey,
    val number: Int,
    val activityName: String?,
    val atMillis: Long,
    val url: String?,
    val navigationObserved: Boolean,
    val historyCount: Int,
    val appLoadCount: Int,
    val urlFromAppLoad: Boolean = false,
)
