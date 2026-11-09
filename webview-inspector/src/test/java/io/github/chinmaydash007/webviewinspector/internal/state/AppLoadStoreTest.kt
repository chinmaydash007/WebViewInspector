package io.github.chinmaydash007.webviewinspector.internal.state

import io.github.chinmaydash007.webviewinspector.internal.util.AppLoadsPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.ref.WeakReference

class AppLoadStoreTest {
    private val first = HistoryTargetKey(1, 1)
    private val second = HistoryTargetKey(1, 2)

    @Test fun equalRecycledIdentifiersCannotMergeDifferentInstances() {
        data class Card(
            val resourceId: Int,
        )
        val a = Card(7)
        val b = Card(7)
        assertEquals(a, b)
        assertNotSame(a, b)
        val store = AppLoadStore<Card>()
        store.capture(a, "same URL", mapOf("Authorization" to "first"), 1)
        store.capture(b, "same URL", mapOf("Authorization" to "second"), 2)
        store.bind(a, first)
        store.bind(b, second)
        assertEquals(
            "first",
            store
                .snapshot(first)!!
                .records
                .single()
                .headers
                .single()
                .value,
        )
        assertEquals(
            "second",
            store
                .snapshot(second)!!
                .records
                .single()
                .headers
                .single()
                .value,
        )
    }

    @Test fun pendingCapturesBindByInstanceAndSnapshotMutableMaps() {
        val store = AppLoadStore<Any>()
        val a = Any()
        val b = Any()
        val headers = linkedMapOf("Authorization" to "Bearer A+%2B", "x-CaSe" to "  raw  ")
        store.capture(a, "https://same.example/?raw=A%2Bb", headers, 10)
        headers["Authorization"] = "changed"
        store.capture(b, "https://same.example/?raw=A%2Bb", emptyMap(), 11)
        assertNull(store.snapshot(first))
        store.bind(b, second)
        store.bind(a, first)
        assertEquals(
            "Bearer A+%2B",
            store
                .snapshot(first)!!
                .records
                .single()
                .headers
                .first()
                .value,
        )
        assertEquals(
            "x-CaSe",
            store
                .snapshot(first)!!
                .records
                .single()
                .headers
                .last()
                .name,
        )
        assertTrue(
            store
                .snapshot(second)!!
                .records
                .single()
                .headers
                .isEmpty(),
        )
    }

    @Test fun repeatedUrlsAndEmptyHeadersAreDistinctCallsWithoutInheritance() {
        val view = Any()
        val store = AppLoadStore<Any>()
        store.capture(view, "https://example/", mapOf("Authorization" to "secret"), 1)
        store.capture(view, "https://example/", emptyMap(), 2)
        store.bind(view, first)
        val records = store.snapshot(first)!!.records
        assertEquals(listOf(2L, 1L), records.map { it.atMillis })
        assertNotEquals(records[0].id, records[1].id)
        assertTrue(records[0].headers.isEmpty())
    }

    @Test fun clearRetainsCapabilityAndDoesNotAffectOtherInstances() {
        val a = Any()
        val b = Any()
        val store = AppLoadStore<Any>()
        store.capture(a, "first", emptyMap(), 1)
        store.capture(b, "second", emptyMap(), 2)
        store.bind(a, first)
        store.bind(b, second)
        store.clear(first)
        assertNotNull(store.snapshot(first))
        assertTrue(store.snapshot(first)!!.records.isEmpty())
        assertEquals(
            "second",
            store
                .snapshot(second)!!
                .records
                .single()
                .rawUrl,
        )
        store.capture(a, "third", emptyMap(), 3)
        assertEquals(
            "third",
            store
                .snapshot(first)!!
                .records
                .single()
                .rawUrl,
        )
    }

    @Test fun globalEvictionCannotForgetThatAClearedLiveInstanceIsAdvanced() {
        val livingViews = List(503) { Any() }
        val store = AppLoadStore<Any>()
        store.capture(livingViews.first(), "cleared", emptyMap(), 0, ownerScope = 1)
        store.bind(livingViews.first(), first)
        store.clear(first)
        livingViews.drop(1).forEachIndexed { index, view ->
            store.capture(view, "pending", emptyMap(), index.toLong(), ownerScope = 1)
        }
        assertEquals(500, store.recordCount())
        assertNotNull(store.snapshot(first))
        assertTrue(store.snapshot(first)!!.records.isEmpty())
        // Retain caller ownership for this assertion; the store itself must only be weak.
        livingViews.forEach { assertNotNull(it) }
        store.removeScope(1)
        assertEquals(0, store.recordCount())
    }

    @Test fun activityDestructionReleasesBoundAndOwnedPendingInputs() {
        val a = Any()
        val b = Any()
        val c = Any()
        val store = AppLoadStore<Any>()
        store.capture(a, "bound", emptyMap(), 1, ownerScope = 1)
        store.bind(a, first)
        store.capture(b, "pending", emptyMap(), 2, ownerScope = 1)
        store.capture(c, "other", emptyMap(), 3, ownerScope = 2)
        val otherActivity = HistoryTargetKey(2, 1)
        store.bind(c, otherActivity)
        store.removeScope(1)
        assertNull(store.snapshot(first))
        assertEquals(1, store.recordCount())
        assertEquals(
            "other",
            store
                .snapshot(otherActivity)!!
                .records
                .single()
                .rawUrl,
        )
        store.clearAll()
        assertEquals(0, store.recordCount())
    }

    @Test fun collectedPendingInstancesAreRemovedButBoundSavedInputsRemain() {
        val references = mutableListOf<WeakReference<Any>>()
        val store = AppLoadStore(referenceFactory = { view: Any -> WeakReference(view).also(references::add) })
        val a = Any()
        val b = Any()
        store.capture(a, "pending", emptyMap(), 1)
        store.capture(b, "saved", emptyMap(), 2)
        store.bind(b, first)
        references.forEach { it.clear() }
        store.prune()
        assertEquals(1, store.recordCount())
        assertEquals(
            "saved",
            store
                .snapshot(first)!!
                .records
                .single()
                .rawUrl,
        )
    }

    @Test fun visibilityChangesAndRebindingDoNotDuplicateOrDropRecords() {
        val a = Any()
        val store = AppLoadStore<Any>()
        store.capture(a, "before attachment", emptyMap(), 1)
        store.bind(a, first)
        repeat(5) { store.bind(a, first) }
        assertEquals(1, store.snapshot(first)!!.records.size)
        assertNull(store.snapshot(second))
    }

    @Test fun perInstanceAndProcessLimitsIncludePendingCaptures() {
        val store = AppLoadStore<Any>(perTargetLimit = 2, processLimit = 3)
        val a = Any()
        val b = Any()
        repeat(3) { store.capture(a, "a$it", emptyMap(), it.toLong()) }
        store.capture(b, "b0", emptyMap(), 4)
        store.capture(b, "b1", emptyMap(), 5)
        store.bind(a, first)
        store.bind(b, second)
        assertEquals(3, store.recordCount())
        assertEquals(listOf("a2"), store.snapshot(first)!!.records.map { it.rawUrl })
        assertEquals(2, store.snapshot(first)!!.discardedCount)
        assertEquals(2, store.snapshot(second)!!.records.size)
    }

    @Test fun utf8BudgetIncludesHeaderNamesAndValuesAndNeverTruncates() {
        val view = Any()
        val store = AppLoadStore<Any>(textLimit = 8)
        store.capture(view, "a", mapOf("H" to "éé"), 1) // Six UTF-8 bytes.
        store.capture(view, "b", mapOf("X" to "raw"), 2)
        store.bind(view, first)
        assertEquals(5, store.storedTextBytes())
        assertEquals(
            "raw",
            store
                .snapshot(first)!!
                .records
                .single()
                .headers
                .single()
                .value,
        )
        assertEquals(1, store.snapshot(first)!!.discardedCount)
        store.capture(view, "oversized", mapOf("X" to "must not be truncated"), 3)
        assertTrue(store.snapshot(first)!!.records.isEmpty())
        assertEquals(3, store.snapshot(first)!!.discardedCount)
    }

    @Test fun immutableSnapshotsSurviveLaterCaptureAndClearing() {
        val view = Any()
        val store = AppLoadStore<Any>()
        store.capture(view, "original", mapOf("Case" to "value"), 1)
        store.bind(view, first)
        val saved = store.snapshot(first)!!
        store.capture(view, "new", emptyMap(), 2)
        store.clear(first)
        assertEquals("original", saved.records.single().rawUrl)
        assertEquals(
            "value",
            saved.records
                .single()
                .headers
                .single()
                .value,
        )
    }

    @Test fun exportPreservesOriginalStringsOrderTimestampsAndDiscardNotice() {
        val view = Any()
        val store = AppLoadStore<Any>(perTargetLimit = 2)
        store.capture(view, "discard", emptyMap(), 0)
        store.capture(
            view,
            "https://example/?raw=A%2Bb&dup=1&dup=2#raw",
            linkedMapOf(
                "x-CaSe" to "  +%2B  ",
                "Authorization" to "Bearer secret",
            ),
            1,
        )
        store.capture(view, "https://example/next", emptyMap(), 2)
        store.bind(view, first)
        val snapshot = store.snapshot(first)!!
        val text = AppLoadsPresentation.export(snapshot, 2)
        assertTrue(
            text.startsWith(
                "App loads · WebView 2\nApp-supplied inputs; not verified network headers.\nDiscarded app loads: 1\n",
            ),
        )
        assertTrue(
            text.contains(
                "https://example/?raw=A%2Bb&dup=1&dup=2#raw\nHeaders: 2\nx-CaSe:   +%2B  \nAuthorization: Bearer secret\n",
            ),
        )
        assertTrue(text.indexOf("#raw") < text.indexOf("https://example/next"))
        assertTrue(text.contains("1970-01-01T"))
        assertEquals(
            "x-CaSe:   +%2B  \nAuthorization: Bearer secret",
            AppLoadsPresentation.headers(snapshot.records[1]),
        )
    }
}
