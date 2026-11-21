package com.webviewinspector.internal.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class InspectorArchiveTest {
    private val history = NavigationHistoryStore()
    private val loads = AppLoadStore<Any>()
    private val archive = InspectorArchive(history, loads)

    @Test fun helperCaptureKeepsItsIdentityWhenDiscoveredInAnotherScope() {
        val view = Any()
        val target = archive.target(view, 0, null, 10)
        loads.capture(view, RAW, mapOf("x-raw" to "  A+%2B  "), 10)
        loads.bind(view, target.key(0))
        assertFalse(archive.hasDetectedWebView)
        assertFalse(archive.recent(emptySet()).single().navigationObserved)
        val discovered = archive.target(view, 7, "PaymentScreen", 20)
        assertSame(target, discovered)
        assertEquals(target.key(0), discovered.key(7))
        archive.detected(listOf(discovered), 7, "PaymentScreen", 20)
        assertTrue(archive.hasDetectedWebView)
        assertTrue(archive.recent(setOf(target.id)).isEmpty())
        assertEquals(
            RAW,
            loads
                .snapshot(discovered.key(7))!!
                .records
                .single()
                .rawUrl,
        )
    }

    @Test fun destroyedOwnerAndCollectedViewDoNotRemoveSavedRecords() {
        val view = Any()
        val target = archive.target(view, 1, "PaymentScreen", 10)
        archive.detected(listOf(target), 1, "PaymentScreen", 10)
        val key = target.key(1)
        history.open(key, HistoryCaptureMode.OBSERVED_URLS, 10)
        history.observe(key, RAW, 10, true)
        loads.capture(view, RAW, mapOf("x-raw" to "  A+%2B  "), 9)
        loads.bind(view, key)
        history.pause(key, 11)
        target.reference.clear()
        loads.prune()
        val saved = archive.recent(emptySet()).single()
        assertEquals(RAW, saved.url)
        assertEquals("PaymentScreen", saved.activityName)
        assertEquals(1, saved.historyCount)
        assertEquals(1, saved.appLoadCount)
        assertEquals(
            "  A+%2B  ",
            loads
                .snapshot(key)!!
                .records
                .single()
                .headers
                .single()
                .value,
        )
    }

    @Test fun sameUrlAndLocalActivityScopeCannotMergeInstances() {
        val first = archive.target(Any(), 1, "First", 10)
        val second = archive.target(Any(), 2, "Second", 10)
        assertNotEquals(first.id, second.id)
        for (target in listOf(first, second)) {
            history.open(target.key(0), HistoryCaptureMode.OBSERVED_URLS, 10)
            history.observe(target.key(0), RAW, 10, true)
        }
        history.clear(first.key(0))
        assertNull(archive.summary(first.id)!!.url)
        assertEquals(RAW, archive.summary(second.id)!!.url)
    }

    @Test fun inactiveEvictionDropsWholeTargetsFromBothBudgetsAndProtectsLiveData() {
        val bounded = InspectorArchive(history, loads, inactiveLimit = 1)
        val views = List(3) { Any() }
        val targets =
            views.mapIndexed { index, view ->
                val target = bounded.target(view, 1, "Screen", index.toLong())
                history.open(target.key(1), HistoryCaptureMode.OBSERVED_URLS, index.toLong())
                history.observe(target.key(1), RAW, index.toLong(), true)
                loads.capture(view, RAW, emptyMap(), index.toLong())
                loads.bind(view, target.key(1))
                target
            }
        bounded.prune(setOf(targets.first().id))
        assertNull(bounded.summary(targets[1].id))
        assertNull(history.snapshot(targets[1].key(1)))
        assertNull(loads.snapshot(targets[1].key(1)))
        assertEquals(listOf(targets.last().id), bounded.recent(setOf(targets.first().id)).map { it.key.targetId })
        assertEquals(RAW, bounded.summary(targets.first().id)!!.url)
    }

    @Test fun clearingRecentPreservesActivationAndLiveHistoryButDisableResetsIt() {
        val first = archive.target(Any(), 1, "First", 10)
        val second = archive.target(Any(), 2, "Second", 20)
        archive.detected(listOf(first, second), 1, "Screen", 20)
        archive.clearRecent(setOf(second.id))
        assertTrue(archive.hasDetectedWebView)
        assertTrue(archive.recent(setOf(second.id)).isEmpty())
        assertNull(archive.summary(first.id))
        assertEquals(second.id, archive.summary(second.id)!!.key.targetId)
        archive.clearAll()
        assertFalse(archive.hasDetectedWebView)
        assertTrue(archive.recent(emptySet()).isEmpty())
        assertEquals(1L, archive.target(Any(), 3, "Fresh session", 30).id)
    }

    @Test fun detectingANullUrlStillActivatesInspectorWithoutInventingNavigation() {
        val target = archive.target(Any(), 1, "Empty screen", 10)
        archive.detected(listOf(target), 1, "Empty screen", 10)
        val saved = archive.recent(emptySet()).single()
        assertTrue(archive.hasDetectedWebView)
        assertNull(saved.url)
        assertEquals(0, saved.historyCount)
        assertTrue(saved.navigationObserved)
    }

    @Test fun newestCaptureOrdersRecentsAndClearCannotRevealAnUnbudgetedUrlCache() {
        val view = Any()
        val target = archive.target(view, 1, "Screen", 1)
        val other = archive.target(Any(), 2, "Other", 2)
        loads.capture(view, RAW, emptyMap(), 50)
        loads.bind(view, target.key(1))
        assertEquals(listOf(target.id, other.id), archive.recent(emptySet()).map { it.key.targetId })
        loads.clear(target.key(1))
        assertNull(archive.summary(target.id)!!.url)
    }

    private companion object {
        const val RAW = "https://payment.example/checkout?amount=0&dup=A+%2B&dup=%252F#raw"
    }
}
