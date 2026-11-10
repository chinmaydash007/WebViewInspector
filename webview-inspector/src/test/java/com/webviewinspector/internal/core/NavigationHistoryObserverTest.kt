package com.webviewinspector.internal.core

import com.webviewinspector.internal.state.HistoryCaptureMode
import com.webviewinspector.internal.state.HistoryRecordKind
import com.webviewinspector.internal.state.HistoryTargetKey
import com.webviewinspector.internal.state.NavigationEvent
import com.webviewinspector.internal.state.NavigationHistoryStore
import com.webviewinspector.internal.state.NavigationOutcome
import com.webviewinspector.internal.state.NavigationPhase
import com.webviewinspector.internal.state.TargetRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationHistoryObserverTest {
    private class View(
        var url: String = "https://initial.test",
        var visible: Boolean = true,
    )

    private class Source(
        val supported: Boolean = true,
    ) : NavigationEventSource<View> {
        val deliveries = mutableListOf<(NavigationEvent) -> Unit>()
        var removals = 0

        override fun attach(
            view: View,
            receive: (NavigationEvent) -> Unit,
        ): (() -> Unit)? {
            if (!supported) return null
            deliveries.add(receive)
            return { removals++ }
        }
    }

    private class Fixture(
        supported: Boolean = true,
    ) {
        val source = Source(supported)
        val store = NavigationHistoryStore()
        val registry = TargetRegistry<View>()
        val view = View()
        val observer = NavigationHistoryObserver(1, store, source, { it.url }, { it.visible }, {})
        val key get() = HistoryTargetKey(1, registry.lastSelection!!.id)

        init {
            registry.refresh(listOf(view))
            observer.sync(registry.targets)
        }
    }

    @Test fun repeatedDiscoveryInstallsOnlyOneListenerAndSnapshotsTheInitialUrl() {
        val fixture = Fixture()
        fixture.observer.sync(fixture.registry.targets)
        assertEquals(1, fixture.source.deliveries.size)
        assertEquals(
            HistoryRecordKind.FIRST_OBSERVED,
            fixture.store
                .snapshot(fixture.key)!!
                .records
                .single()
                .kind,
        )
    }

    @Test fun unsupportedCaptureSamplesChangesWithoutNavigationOutcomes() {
        val fixture = Fixture(supported = false)
        fixture.view.url = "https://changed.test/%2f?a+b"
        fixture.observer.sample()
        fixture.observer.sample()
        val snapshot = fixture.store.snapshot(fixture.key)!!
        assertEquals(HistoryCaptureMode.OBSERVED_URLS, snapshot.mode)
        assertEquals(2, snapshot.records.size)
        assertNull(snapshot.records.first().outcome)
    }

    @Test fun eventModeDoesNotDuplicateNavigationsThroughSampling() {
        val fixture = Fixture()
        fixture.view.url = "https://next.test"
        val receive = fixture.source.deliveries.single()
        receive(NavigationEvent(1, NavigationPhase.STARTED, fixture.view.url, 1))
        receive(NavigationEvent(1, NavigationPhase.REDIRECTED, "https://redirect.test", 2))
        receive(
            NavigationEvent(
                1,
                NavigationPhase.COMPLETED,
                "https://redirect.test",
                3,
                outcome = NavigationOutcome.COMMITTED,
            ),
        )
        fixture.observer.sample()
        assertEquals(
            2,
            fixture.store
                .snapshot(fixture.key)!!
                .records.size,
        )
        assertEquals(
            2,
            fixture.store
                .snapshot(fixture.key)!!
                .records
                .first()
                .steps.size,
        )
    }

    @Test fun visibilityLossRemovesListenersAndLateCallbacksCannotWrite() {
        val fixture = Fixture()
        fixture.view.visible = false
        fixture.observer.sample()
        val before = fixture.store.snapshot(fixture.key)
        fixture.source.deliveries.single()(NavigationEvent(9, NavigationPhase.STARTED, "https://late.test", 2))
        assertEquals(before, fixture.store.snapshot(fixture.key))
        assertEquals(1, fixture.source.removals)
    }

    @Test fun reappearingSameInstanceRetainsItsKeyAndAddsAGapWithoutSelectingIt() {
        val fixture = Fixture()
        val key = fixture.key
        fixture.registry.refresh(emptyList())
        fixture.observer.sync(fixture.registry.targets)
        fixture.registry.refresh(listOf(fixture.view))
        fixture.observer.sync(fixture.registry.targets)
        assertNull(fixture.registry.selectedId)
        assertEquals(
            key.targetId,
            fixture.registry.targets
                .single()
                .id,
        )
        assertTrue(
            fixture.store
                .snapshot(key)!!
                .records
                .any { it.kind == HistoryRecordKind.GAP },
        )
    }

    @Test fun disposedSessionRejectsQueuedEventsEvenAfterTheStoreIsCleared() {
        val fixture = Fixture()
        fixture.observer.dispose()
        fixture.store.clearAll()
        fixture.source.deliveries.single()(NavigationEvent(1, NavigationPhase.COMPLETED, "https://late.test", 2))
        fixture.observer.sync(fixture.registry.targets)
        assertNull(fixture.store.snapshot(fixture.key))
        assertEquals(1, fixture.source.removals)
    }

    @Test fun resumingEventCaptureDoesNotReclassifyAnAlreadyRecordedDestinationAsASampledChange() {
        val fixture = Fixture()
        fixture.view.url = "https://destination.test"
        fixture.source.deliveries.single()(NavigationEvent(1, NavigationPhase.STARTED, fixture.view.url, 1))
        fixture.source.deliveries.single()(
            NavigationEvent(1, NavigationPhase.COMPLETED, fixture.view.url, 2, outcome = NavigationOutcome.COMMITTED),
        )
        fixture.observer.pause()
        fixture.observer.sync(fixture.registry.targets)
        val records = fixture.store.snapshot(fixture.key)!!.records
        assertEquals(3, records.size)
        assertFalse(records.any { it.kind == HistoryRecordKind.OBSERVED_CHANGE })
    }
}
