package io.github.chinmaydash007.webviewinspector.internal.state

import java.lang.ref.WeakReference

/**
 * Process-owned, main-thread app-load snapshots, independent of navigation capture.
 * Undiscovered instances remain weak pending entries under the same record/text budgets.
 * Binding adds Activity/target identity without making a target visible or selecting it.
 * The controller clears known scopes on destruction and all entries on disable/uninstall.
 */
internal class AppLoadStore<T : Any>(
    private val perTargetLimit: Int = 100,
    private val processLimit: Int = 500,
    private val textLimit: Long = 2L * 1024 * 1024,
    private val referenceFactory: (T) -> WeakReference<T> = { WeakReference(it) },
) {
    private data class StoredRecord(
        val record: AppLoadRecord,
        val textBytes: Long,
    )

    private class Entry<T : Any>(
        val reference: WeakReference<T>,
        var ownerScope: Long?,
    ) {
        var key: HistoryTargetKey? = null
        val records = mutableListOf<StoredRecord>()
        var discarded = 0
    }

    private val entries = mutableListOf<Entry<T>>()
    private var nextRecordId = 0L

    /** Snapshot before delegation; no original map or view is retained in a record. */
    fun capture(
        view: T,
        rawUrl: String,
        headers: Map<String, String>,
        atMillis: Long,
        ownerScope: Long? = null,
    ) {
        prune()
        val snapshot = headers.entries.map { AppLoadHeader(it.key, it.value) }
        val entry =
            entries.firstOrNull { it.reference.get() === view }
                ?: Entry(referenceFactory(view), ownerScope).also(entries::add)
        if (entry.ownerScope == null) entry.ownerScope = ownerScope
        val record = AppLoadRecord(++nextRecordId, atMillis, rawUrl, snapshot)
        entry.records.add(StoredRecord(record, textBytes(record)))
        while (entry.records.size > perTargetLimit) discardOldest(entry)
        while (recordCount() > processLimit || storedTextBytes() > textLimit) {
            val oldest =
                entries.filter { it.records.isNotEmpty() }.minByOrNull {
                    it.records
                        .first()
                        .record.id
                } ?: break
            discardOldest(oldest)
        }
        prune()
    }

    /** Resolves a pending capture by exact instance; never compares URLs or resource IDs. */
    fun bind(
        view: T,
        key: HistoryTargetKey,
    ) {
        prune()
        entries.firstOrNull { it.reference.get() === view }?.let { entry ->
            entry.key = key
            entry.ownerScope = key.activityScope
        }
    }

    fun snapshot(key: HistoryTargetKey): AppLoadsSnapshot? {
        prune()
        return entries.firstOrNull { it.key == key }?.let {
            AppLoadsSnapshot(key, it.records.asReversed().map(StoredRecord::record), it.discarded)
        }
    }

    /** Clears only inspector app loads, retaining Advanced capability for the live weak instance. */
    fun clear(key: HistoryTargetKey) {
        entries.firstOrNull { it.key == key }?.let {
            it.records.clear()
            it.discarded = 0
        }
    }

    fun removeScope(scope: Long) {
        entries.removeAll { it.ownerScope == scope }
    }

    fun clearAll() = entries.clear()

    fun prune() {
        // Bound records remain usable as Saved app loads, even when their WebView is collected.
        entries.removeAll { it.key == null && it.reference.get() == null }
        // Keep live instances Advanced after Clear, even under global record-budget pressure.
        // Only retired, empty metadata can expire; it owns no URL/header text or live view.
        val retired = entries.filter { it.reference.get() == null && it.records.isEmpty() }
        retired.take((retired.size - MAX_RETIRED_TARGETS).coerceAtLeast(0)).forEach(entries::remove)
    }

    internal fun recordCount(): Int = entries.sumOf { it.records.size }

    internal fun storedTextBytes(): Long = entries.sumOf { entry -> entry.records.sumOf(StoredRecord::textBytes) }

    private fun discardOldest(entry: Entry<T>) {
        entry.records.removeAt(0)
        entry.discarded++
    }

    private fun textBytes(record: AppLoadRecord): Long =
        record.rawUrl
            .toByteArray(Charsets.UTF_8)
            .size
            .toLong() +
            record.headers.sumOf {
                it.name
                    .toByteArray(Charsets.UTF_8)
                    .size
                    .toLong() +
                    it.value.toByteArray(Charsets.UTF_8).size
            }

    private companion object {
        const val MAX_RETIRED_TARGETS = 500
    }
}
