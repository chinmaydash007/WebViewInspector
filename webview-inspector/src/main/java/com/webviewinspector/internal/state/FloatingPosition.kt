package com.webviewinspector.internal.state

/**
 * Normalized control position shared across Activity sessions on the main thread.
 * Owned by the process-level controller; it is never persisted and resets on a fresh install.
 */
internal class FloatingPosition(
    var x: Float = 1f,
    var y: Float = 0.28f,
)
