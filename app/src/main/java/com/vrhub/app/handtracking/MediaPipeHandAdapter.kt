package com.vrhub.app.handtracking

import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

/** Converts MediaPipe Tasks output into the app's stable, renderer-independent hand model. */
object MediaPipeHandAdapter {
    fun convert(result: HandLandmarkerResult, timestampMs: Long): List<TrackedHand> {
        val hands = result.landmarks()
        val handedness = result.handedness()
        return hands.mapIndexed { handIndex, points ->
            val category = handedness.getOrNull(handIndex)?.firstOrNull()
            val label = category?.categoryName()?.uppercase()
            val side = when (label) {
                "LEFT" -> Handedness.LEFT
                "RIGHT" -> Handedness.RIGHT
                else -> Handedness.UNKNOWN
            }
            val confidence = category?.score() ?: 1f
            val landmarks = points.map { p ->
                Landmark3D(p.x(), p.y(), p.z())
            }
            TrackedHand(
                landmarks = landmarks,
                handedness = side,
                timestampMs = timestampMs,
                confidence = confidence
            )
        }
    }
}
