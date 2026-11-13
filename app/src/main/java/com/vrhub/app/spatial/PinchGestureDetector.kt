package com.vrhub.app.spatial

/**
 * Hysteresis-based pinch state, ported from CardboardXR's PinchDetector.
 * Distances are meters between thumb tip (landmark 4) and index tip (landmark 8).
 */
class PinchGestureDetector(
    private val closeDistanceMeters: Float = 0.025f,
    private val releaseDistanceMeters: Float = 0.045f
) {
    init {
        require(closeDistanceMeters > 0f)
        require(releaseDistanceMeters > closeDistanceMeters)
    }

    var isPinched: Boolean = false
        private set

    fun update(thumbIndexDistanceMeters: Float): Boolean {
        if (!thumbIndexDistanceMeters.isFinite() || thumbIndexDistanceMeters < 0f) {
            return isPinched
        }
        if (isPinched) {
            if (thumbIndexDistanceMeters > releaseDistanceMeters) isPinched = false
        } else if (thumbIndexDistanceMeters < closeDistanceMeters) {
            isPinched = true
        }
        return isPinched
    }

    fun reset() {
        isPinched = false
    }
}
