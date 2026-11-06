package com.vrhub.app.gestures

/** Hysteresis prevents a pinch from flickering around a single threshold. */
class PinchDetector(
    private val closeDistance: Float = 0.045f,
    private val openDistance: Float = 0.065f
) {
    var isPinched: Boolean = false
        private set

    init { require(openDistance > closeDistance) }

    @Synchronized
    fun update(distance: Float): Boolean {
        if (!distance.isFinite() || distance < 0f) return isPinched
        isPinched = if (isPinched) distance < openDistance else distance <= closeDistance
        return isPinched
    }

    fun reset() { isPinched = false }
}
