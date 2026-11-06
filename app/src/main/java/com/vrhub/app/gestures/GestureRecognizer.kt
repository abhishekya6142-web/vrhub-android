package com.vrhub.app.gestures

import com.vrhub.app.handtracking.HandLandmark
import com.vrhub.app.handtracking.TrackedHand

enum class HandGesture { PINCH, OPEN_PALM, FIST, POINT, NONE }

/** Lightweight geometric gesture layer. Thresholds are relative to palm size. */
class GestureRecognizer {
    private val pinch = PinchDetector()

    fun recognize(hand: TrackedHand): HandGesture {
        if (pinch.update(hand.pinchDistance)) return HandGesture.PINCH

        val wrist = hand.landmarks[HandLandmark.WRIST]
        val middleMcp = hand.landmarks[9]
        val palmScale = wrist.distanceTo(middleMcp).coerceAtLeast(0.001f)
        val extended = listOf(8, 12, 16, 20).map { tip ->
            hand.landmarks[tip].distanceTo(wrist) > palmScale * 1.35f
        }
        val thumbExtended = hand.landmarks[4].distanceTo(wrist) > palmScale * 1.15f

        return when {
            extended.all { it } && thumbExtended -> HandGesture.OPEN_PALM
            extended.none { it } && !thumbExtended -> HandGesture.FIST
            extended[0] && !extended[1] && !extended[2] && !extended[3] -> HandGesture.POINT
            else -> HandGesture.NONE
        }
    }

    fun reset() = pinch.reset()
}
