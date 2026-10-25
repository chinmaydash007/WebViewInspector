package com.visit.webviewinspector.internal.state

/** Immutable render snapshot shared by the floating control and inspector sheet; owns no views. */
internal data class InspectorState(
    val targets: List<TargetInfo>,
    val selectedId: Long?,
    val history: NavigationHistorySnapshot? = null,
    val historyNumber: Int? = null,
) {
    val selected: TargetInfo?
        get() = targets.firstOrNull { it.id == selectedId }
}
