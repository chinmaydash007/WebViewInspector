package io.github.chinmaydash007.webviewinspector.internal.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationHistoryStoreTest {
    private val key = HistoryTargetKey(1, 1)
    private val other = HistoryTargetKey(1, 2)
    private val raw = "https://example.test/%2f?x=a+b&x=%252F#raw"

    private fun open(
        store: NavigationHistoryStore,
        target: HistoryTargetKey = key,
    ) {
        store.open(target, HistoryCaptureMode.NAVIGATION_EVENTS, 10)
    }

    private fun event(
        id: Long = 1,
        phase: NavigationPhase = NavigationPhase.STARTED,
        url: String = raw,
    ) = NavigationEvent(
        id,
        phase,
        url,
        20,
        outcome =
            if (phase ==
                NavigationPhase.COMPLETED
            ) {
                NavigationOutcome.COMMITTED
            } else {
                NavigationOutcome.IN_PROGRESS
            },
    )

    @Test fun redirectStepsAreGroupedAndCompletionDoesNotDuplicateTheFinalUrl() {
        val store = NavigationHistoryStore()
        open(store)
        val id = store.startNavigation(key, event())
        store.updateNavigation(key, id, event(phase = NavigationPhase.REDIRECTED, url = "https://redirect.test/"))
        store.updateNavigation(key, id, event(phase = NavigationPhase.COMPLETED, url = "https://redirect.test/"))
        val record = store.snapshot(key)!!.records.single()
        assertEquals(2, record.steps.size)
        assertEquals(raw, record.steps.first().rawUrl)
        assertEquals(NavigationOutcome.COMMITTED, record.outcome)
    }

    @Test fun instancesAndActivityScopesCannotShareHistory() {
        val store = NavigationHistoryStore()
        val anotherActivity = HistoryTargetKey(2, 1)
        listOf(key, other, anotherActivity).forEach { open(store, it) }
        store.startNavigation(key, event())
        assertTrue(store.snapshot(other)!!.records.isEmpty())
        assertTrue(store.snapshot(anotherActivity)!!.records.isEmpty())
        store.removeScope(1)
        assertNull(store.snapshot(key))
        assertNotNull(store.snapshot(anotherActivity))
    }

    @Test fun repeatedUrlsAndSameUrlReloadsRemainSeparateNavigations() {
        val store = NavigationHistoryStore()
        open(store)
        store.startNavigation(key, event())
        store.startNavigation(key, event(id = 2).copy(flags = NavigationFlags(reload = true)))
        assertEquals(2, store.snapshot(key)!!.records.size)
        assertTrue(
            store
                .snapshot(key)!!
                .records
                .first()
                .flags.reload,
        )
    }

    @Test fun observedUrlsDeduplicateSamplesButKeepRevisitsAndOriginalStrings() {
        val store = NavigationHistoryStore()
        open(store)
        assertTrue(store.observe(key, raw, 1, recordChanges = true))
        assertFalse(store.observe(key, raw, 2, recordChanges = true))
        store.observe(key, "https://other.test", 3, recordChanges = true)
        store.observe(key, raw, 4, recordChanges = true)
        val records = store.snapshot(key)!!.records
        assertEquals(3, records.size)
        assertEquals(HistoryRecordKind.FIRST_OBSERVED, records.last().kind)
        assertEquals(raw, records.first().rawUrl)
        assertNull(records.first().outcome)
    }

    @Test fun clearKeepsTheSamplingBaselineAndDoesNotResurrectAnOldNavigation() {
        val store = NavigationHistoryStore()
        open(store)
        store.observe(key, raw, 1, recordChanges = true)
        val id = store.startNavigation(key, event())
        store.clear(key)
        store.updateNavigation(key, id, event(phase = NavigationPhase.COMPLETED))
        assertFalse(store.observe(key, raw, 40, recordChanges = true))
        assertTrue(store.snapshot(key)!!.records.isEmpty())
        store.startNavigation(key, event(id = 2))
        assertEquals(1, store.snapshot(key)!!.records.size)
    }

    @Test fun clearAffectsOnlyTheChosenView() {
        val store = NavigationHistoryStore()
        listOf(key, other).forEach {
            open(store, it)
            store.startNavigation(it, event())
        }
        store.clear(key)
        assertTrue(store.snapshot(key)!!.records.isEmpty())
        assertEquals(1, store.snapshot(other)!!.records.size)
    }

    @Test fun pauseResumeAddsOneGapAndDoesNotInventEventsDuringIt() {
        val store = NavigationHistoryStore()
        open(store)
        store.pause(key, 20)
        store.pause(key, 30)
        store.open(key, HistoryCaptureMode.NAVIGATION_EVENTS, 40)
        val gap = store.snapshot(key)!!.records.single()
        assertEquals(HistoryRecordKind.GAP, gap.kind)
        assertEquals(20, gap.atMillis)
        assertEquals(40L, gap.gapEndMillis)
        assertTrue(gap.steps.isEmpty())
    }

    @Test fun limitsEvictOldestWholeRecordsWithoutTruncatingUrls() {
        val store = NavigationHistoryStore(perTargetLimit = 2, processLimit = 3)
        listOf(key, other).forEach { open(store, it) }
        repeat(3) { store.startNavigation(key, event(id = it.toLong())) }
        assertEquals(2, store.snapshot(key)!!.records.size)
        assertEquals(1, store.snapshot(key)!!.discardedCount)
        repeat(2) { store.startNavigation(other, event(id = it.toLong())) }
        assertEquals(1, store.snapshot(key)!!.records.size)
        assertEquals(
            raw,
            store
                .snapshot(other)!!
                .records
                .first()
                .rawUrl,
        )
    }

    @Test fun byteBudgetCountsUtf8AndRejectsOversizeUrlsWithoutTruncation() {
        val store = NavigationHistoryStore(urlByteLimit = 8)
        open(store)
        store.startNavigation(key, event(url = "🙂🙂"))
        assertEquals(8, store.storedUrlBytes())
        store.startNavigation(key, event(id = 2, url = "🙂🙂🙂"))
        assertTrue(store.snapshot(key)!!.records.isEmpty())
        assertEquals(0, store.storedUrlBytes())
        assertEquals(2, store.snapshot(key)!!.discardedCount)
    }

    @Test fun redirectGrowthCannotExceedTheBudgetOrRecreateAnEvictedRecord() {
        val store = NavigationHistoryStore(urlByteLimit = 4)
        open(store)
        val id = store.startNavigation(key, event(url = "one"))
        store.updateNavigation(key, id, event(phase = NavigationPhase.REDIRECTED, url = "two"))
        store.updateNavigation(key, id, event(phase = NavigationPhase.COMPLETED, url = "two"))
        assertTrue(store.snapshot(key)!!.records.isEmpty())
    }

    @Test fun joinedNavigationAndFailedCompletionAreReportedWithoutInventingAStart() {
        val store = NavigationHistoryStore()
        open(store)
        store.startNavigation(
            key,
            event(phase = NavigationPhase.COMPLETED).copy(outcome = NavigationOutcome.NOT_COMMITTED),
        )
        val record = store.snapshot(key)!!.records.single()
        assertTrue(record.joinedInProgress)
        assertEquals(NavigationOutcome.NOT_COMMITTED, record.outcome)
        store.clearAll()
        assertNull(store.snapshot(key))
    }
}
