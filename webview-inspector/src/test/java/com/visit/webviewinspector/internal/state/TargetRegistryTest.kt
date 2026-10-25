package com.visit.webviewinspector.internal.state

import org.junit.Assert.*
import org.junit.Test

class TargetRegistryTest {
    @Test
    fun selectionFollowsInstanceAcrossReordering() {
        val first = Any()
        val second = Any()
        val registry = TargetRegistry<Any>()
        registry.refresh(listOf(first, second))
        registry.select(registry.targets[1].id)
        registry.refresh(listOf(second, first))
        assertSame(second, registry.selected()?.reference?.get())
        assertEquals(registry.targets.first().id, registry.selectedId)
    }

    @Test
    fun disappearingSelectionRequiresExplicitSelectionEvenAfterEmptyList() {
        val first = Any()
        val second = Any()
        val registry = TargetRegistry<Any>()
        registry.refresh(listOf(first, second))
        registry.refresh(listOf(second))
        assertNull(registry.selected())
        registry.refresh(emptyList())
        registry.refresh(listOf(first))
        assertNull(registry.selected())
        registry.select(registry.targets.first().id)
        assertSame(first, registry.selected()?.reference?.get())
    }

    @Test
    fun equalityDoesNotCollapseDistinctInstances() {
        data class Same(val value: Int)
        val first = Same(1)
        val second = Same(1)
        val registry = TargetRegistry<Same>()
        registry.refresh(listOf(first, second))
        assertNotEquals(registry.targets[0].id, registry.targets[1].id)
        registry.select(registry.targets[1].id)
        assertSame(second, registry.selected()?.reference?.get())
    }

    @Test
    fun firstDelayedTargetIsAutomaticallySelected() {
        val registry = TargetRegistry<Any>()
        registry.refresh(emptyList())
        registry.refresh(listOf(Any()))
        assertNotNull(registry.selected())
    }
}
