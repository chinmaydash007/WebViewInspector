package io.github.chinmaydash007.webviewinspector.internal.state

import java.security.MessageDigest

/**
 * Process-owned bounded history, containing only immutable values and target keys, never Views.
 * The controller calls it on the main thread. Scope removal/destruction and uninstall release data.
 * Tests can use smaller limits without depending on Android or WebKit.
 */
internal class NavigationHistoryStore(
    private val perTargetLimit: Int = 100,
    private val processLimit: Int = 500,
    private val urlByteLimit: Int = 2 * 1024 * 1024,
) {
    private class History {
        var mode = HistoryCaptureMode.OBSERVED_URLS
        val records = mutableListOf<NavigationRecord>()
        var discarded = 0
        var pausedAt: Long? = null
        var observed = false
        var lastFingerprint: List<Byte>? = null
    }

    private val histories = linkedMapOf<HistoryTargetKey, History>()
    private var nextRecordId = 0L

    fun open(key: HistoryTargetKey, mode: HistoryCaptureMode, atMillis: Long) {
        val history = histories.getOrPut(key) { History() }
        history.mode = mode
        val pausedAt = history.pausedAt
        history.pausedAt = null
        if (pausedAt != null) {
            append(history, NavigationRecord(
                id = ++nextRecordId,
                kind = HistoryRecordKind.GAP,
                atMillis = pausedAt,
                gapEndMillis = atMillis,
            ))
        }
    }

    fun setMode(key: HistoryTargetKey, mode: HistoryCaptureMode) {
        histories[key]?.mode = mode
    }

    /** Marks a real observation gap once, without retaining any source Activity or WebView. */
    fun pause(key: HistoryTargetKey, atMillis: Long) {
        histories[key]?.let { if (it.pausedAt == null) it.pausedAt = atMillis }
    }

    /**
     * Seeds an honestly labelled first observation. Subsequent samples record only changes and
     * never claim a redirect or success. Fingerprints avoid retaining unbudgeted baseline URLs.
     */
    fun observe(key: HistoryTargetKey, rawUrl: String?, atMillis: Long, recordChanges: Boolean): Boolean {
        if (rawUrl == null) return false
        val history = histories[key] ?: return false
        val fingerprint = fingerprint(rawUrl)
        if (history.observed && history.lastFingerprint == fingerprint) return false
        val initial = !history.observed
        history.observed = true
        history.lastFingerprint = fingerprint
        if (!initial && !recordChanges) return false
        append(history, NavigationRecord(
            id = ++nextRecordId,
            kind = if (initial) HistoryRecordKind.FIRST_OBSERVED else HistoryRecordKind.OBSERVED_CHANGE,
            atMillis = atMillis,
            steps = listOf(NavigationStep(phase = null, rawUrl = rawUrl, atMillis = atMillis)),
        ))
        return true
    }

    /** Listener events update the baseline without creating sampled duplicates on the next resume. */
    fun rememberUrl(key: HistoryTargetKey, rawUrl: String) {
        histories[key]?.let { history ->
            history.observed = true
            history.lastFingerprint = fingerprint(rawUrl)
        }
    }

    fun startNavigation(key: HistoryTargetKey, event: NavigationEvent): Long {
        val id = ++nextRecordId
        val history = histories[key] ?: return id
        append(history, NavigationRecord(
            id = id,
            kind = HistoryRecordKind.NAVIGATION,
            atMillis = event.atMillis,
            steps = listOf(NavigationStep(event.phase, event.rawUrl, event.atMillis)),
            flags = event.flags,
            outcome = event.outcome,
            completedAtMillis = event.atMillis.takeIf { event.phase == NavigationPhase.COMPLETED },
            joinedInProgress = event.phase != NavigationPhase.STARTED,
        ))
        return id
    }

    /** An evicted/cleared record cannot be resurrected by a late redirect or completion. */
    fun updateNavigation(key: HistoryTargetKey, recordId: Long, event: NavigationEvent) {
        val history = histories[key] ?: return
        val index = history.records.indexOfFirst { it.id == recordId }
        if (index < 0) return
        val previous = history.records[index]
        val appendStep = event.phase != NavigationPhase.COMPLETED || previous.rawUrl != event.rawUrl
        history.records[index] = previous.copy(
            steps = if (appendStep) previous.steps + NavigationStep(event.phase, event.rawUrl, event.atMillis) else previous.steps,
            flags = event.flags,
            outcome = event.outcome,
            completedAtMillis = event.atMillis.takeIf { event.phase == NavigationPhase.COMPLETED },
        )
        enforceLimits()
    }

    fun snapshot(key: HistoryTargetKey): NavigationHistorySnapshot? = histories[key]?.let { history ->
        NavigationHistorySnapshot(
            key = key,
            mode = history.mode,
            records = history.records.asReversed().toList(),
            discardedCount = history.discarded,
            paused = history.pausedAt != null,
        )
    }

    /** Keeps the sampling baseline so clearing does not immediately re-add the same current URL. */
    fun clear(key: HistoryTargetKey) {
        histories[key]?.let { history ->
            history.records.clear()
            history.discarded = 0
        }
    }

    fun removeScope(scope: Long) {
        histories.keys.removeAll { it.activityScope == scope }
    }

    fun clearAll() = histories.clear()

    internal fun storedUrlBytes(): Int = histories.values.sumOf { history ->
        history.records.sumOf { record -> record.steps.sumOf { it.rawUrl.toByteArray(Charsets.UTF_8).size } }
    }

    private fun append(history: History, record: NavigationRecord) {
        history.records.add(record)
        enforceLimits()
    }

    private fun enforceLimits() {
        histories.values.forEach { history ->
            while (history.records.size > perTargetLimit) discardOldest(history)
        }
        while (histories.values.sumOf { it.records.size } > processLimit || storedUrlBytes() > urlByteLimit) {
            val oldest = histories.values.filter { it.records.isNotEmpty() }
                .minByOrNull { it.records.first().id } ?: break
            discardOldest(oldest)
        }
        // Empty retired targets need no unbounded metadata after their records have been evicted.
        if (histories.size > processLimit) {
            val retired = histories.entries.filter { it.value.records.isEmpty() && it.value.pausedAt != null }
            retired.take(histories.size - processLimit).forEach { histories.remove(it.key) }
        }
    }

    private fun discardOldest(history: History) {
        history.records.removeAt(0)
        history.discarded++
    }

    private fun fingerprint(rawUrl: String): List<Byte> =
        MessageDigest.getInstance("SHA-256").digest(rawUrl.toByteArray(Charsets.UTF_8)).toList()
}
