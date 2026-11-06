package com.vrhub.app.interaction

import com.vrhub.app.handtracking.Landmark3D
import com.vrhub.app.handtracking.TrackedHand
import com.vrhub.app.handtracking.OneEuroFilter3D

/** Screen-space pointer derived from the midpoint of thumb and index fingertips. */
class HandPointer(
    private val smoothing: OneEuroFilter3D = OneEuroFilter3D()
) {
    data class Position(val x: Float, val y: Float, val depth: Float)

    fun update(hand: TrackedHand): Position {
        val point = smoothing.filter(hand.pinchPoint, hand.timestampMs / 1000.0)
        return Position(
            x = (1f - point.x).coerceIn(0f, 1f),
            y = point.y.coerceIn(0f, 1f),
            depth = point.z
        )
    }

    fun reset() = smoothing.reset()
}
