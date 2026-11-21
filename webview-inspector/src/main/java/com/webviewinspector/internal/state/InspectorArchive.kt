package com.webviewinspector.internal.state

/**
 * Process-owned weak instance catalogue and discovery latch, confined to the main thread.
 * Saved text lives only in the two budgeted stores. Activity destruction releases its session,
 * not this catalogue. Inactive entries are evicted as whole targets; clear never resets the latch.
 */
internal class InspectorArchive<T : Any>(
    private val history: NavigationHistoryStore,
    private val appLoads: AppLoadStore<T>,
    private val inactiveLimit: Int = 500,
) {
    private class Entry<T : Any>(
        val target: TargetRegistry.Target<T>,
        val key: HistoryTargetKey,
        var activityName: String?,
        var lastSeenAt: Long,
    ) {
        var observed = false
    }

    private val entries = linkedMapOf<Long, Entry<T>>()
    private var nextId = 0L
    var hasDetectedWebView = false
        private set

    fun target(
        view: T,
        scope: Long,
        activityName: String?,
        atMillis: Long,
    ): TargetRegistry.Target<T> {
        val existing = entries.values.firstOrNull { it.target.reference.get() === view }
        if (existing != null) {
            if (activityName != null) existing.activityName = activityName
            return existing.target
        }
        val target = TargetRegistry.Target(++nextId, view, scope)
        entries[target.id] = Entry(target, target.key(scope), activityName, atMillis)
        return target
    }

    fun detected(
        targets: List<TargetRegistry.Target<T>>,
        scope: Long,
        activityName: String,
        atMillis: Long,
    ) {
        if (targets.isNotEmpty()) hasDetectedWebView = true
        targets.forEach { target ->
            nextId = maxOf(nextId, target.id)
            val entry = entries.getOrPut(target.id) { Entry(target, target.key(scope), activityName, atMillis) }
            entry.observed = true
            entry.activityName = activityName
            entry.lastSeenAt = atMillis
        }
    }

    fun summary(id: Long): SavedTargetInfo? = entries[id]?.let { summary(it, appLoads.snapshot(it.key)) }

    private fun summary(
        entry: Entry<T>,
        loads: AppLoadsSnapshot?,
    ): SavedTargetInfo {
        val navigation = history.snapshot(entry.key)
        val records = navigation?.records.orEmpty().filter { it.kind != HistoryRecordKind.GAP }
        val lastNavigation = records.firstOrNull { it.rawUrl != null }
        val lastLoad = loads?.records?.firstOrNull()
        return SavedTargetInfo(
            key = entry.key,
            number = entry.target.number,
            activityName = entry.activityName,
            atMillis =
                maxOf(
                    lastNavigation?.steps?.lastOrNull()?.atMillis ?: 0,
                    lastLoad?.atMillis ?: 0,
                ).takeIf { it != 0L } ?: entry.lastSeenAt,
            url = lastNavigation?.rawUrl ?: lastLoad?.rawUrl,
            navigationObserved = entry.observed,
            historyCount = records.size,
            appLoadCount = loads?.records?.size ?: 0,
            urlFromAppLoad = lastNavigation == null && lastLoad != null,
        )
    }

    fun recent(liveIds: Set<Long>): List<SavedTargetInfo> {
        val loads = appLoads.snapshots()
        return entries.values
            .filter { it.target.id !in liveIds }
            .map { summary(it, loads[it.key]) }
            .sortedWith(compareByDescending<SavedTargetInfo> { it.atMillis }.thenByDescending { it.key.targetId })
    }

    fun prune(liveIds: Set<Long>) {
        if (entries.keys.count { it !in liveIds } <= inactiveLimit) return
        recent(liveIds).drop(inactiveLimit).forEach { remove(it.key.targetId) }
    }

    fun clearRecent(liveIds: Set<Long>) {
        entries.keys
            .filter { it !in liveIds }
            .toList()
            .forEach(::remove)
    }

    private fun remove(id: Long) {
        val entry = entries.remove(id) ?: return
        history.remove(entry.key)
        appLoads.remove(entry.key)
    }

    fun clearAll() {
        entries.clear()
        nextId = 0
        hasDetectedWebView = false
        history.clearAll()
        appLoads.clearAll()
    }
}
