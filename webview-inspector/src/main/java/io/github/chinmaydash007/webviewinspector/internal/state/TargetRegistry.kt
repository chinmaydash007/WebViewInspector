package io.github.chinmaydash007.webviewinspector.internal.state

import java.lang.ref.WeakReference

/**
 * Owns visible target identities and selection without retaining the underlying views.
 *
 * The Activity session accesses this registry on the main thread. Resource IDs, adapter positions,
 * and value equality never identify a target. A missing selection requires an explicit new choice.
 */
internal class TargetRegistry<T : Any> {
    internal class Target<T : Any>(
        val id: Long,
        view: T,
    ) {
        val reference = WeakReference(view)
        var number = 0
    }

    private val knownTargets = mutableListOf<Target<T>>()

    /** Saved inspection context owns only a weak reference, even after live selection is lost. */
    var lastSelection: Target<T>? = null
        private set

    var targets: List<Target<T>> = emptyList()
        private set

    var selectedId: Long? = null
        private set

    private var nextId = 0L
    private var hasSelectedBefore = false

    /** Keeps identities for surviving instances; only the first discovery selects automatically. */
    fun refresh(visibleViews: List<T>) {
        knownTargets.removeAll { it.reference.get() == null }
        targets =
            visibleViews.mapIndexed { index, view ->
                val target =
                    knownTargets.firstOrNull { it.reference.get() === view }
                        ?: Target(id = ++nextId, view = view).also(knownTargets::add)
                target.number = index + 1
                target
            }

        if (selectedId != null && targets.none { it.id == selectedId }) {
            selectedId = null
        }

        if (!hasSelectedBefore && targets.isNotEmpty()) {
            select(targets.first().id)
        }
    }

    fun select(id: Long) {
        if (targets.any { it.id == id }) {
            selectedId = id
            hasSelectedBefore = true
            lastSelection = targets.first { it.id == id }
        }
    }

    fun selected(): Target<T>? = targets.firstOrNull { it.id == selectedId }

    /** Releases references and selection. A discarded registry is not reused by the controller. */
    fun clear() {
        targets = emptyList()
        knownTargets.clear()
        selectedId = null
        lastSelection = null
    }
}
