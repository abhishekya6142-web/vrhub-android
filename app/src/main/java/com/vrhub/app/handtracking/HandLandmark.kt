package com.vrhub.app.handtracking

/** MediaPipe's 21-point hand layout. Coordinates are normalized to [0, 1] for x/y. */
data class Landmark3D(
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float = 1f
) {
    fun distanceTo(other: Landmark3D): Float {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
    }
}

enum class Handedness { LEFT, RIGHT, UNKNOWN }

data class TrackedHand(
    val landmarks: List<Landmark3D>,
    val handedness: Handedness = Handedness.UNKNOWN,
    val timestampMs: Long,
    val confidence: Float = 1f
) {
    init { require(landmarks.size == HandLandmark.COUNT) { "A hand must contain exactly 21 landmarks" } }
    val wrist: Landmark3D get() = landmarks[HandLandmark.WRIST]
    val thumbTip: Landmark3D get() = landmarks[HandLandmark.THUMB_TIP]
    val indexTip: Landmark3D get() = landmarks[HandLandmark.INDEX_TIP]
    val pinchPoint: Landmark3D get() = Landmark3D(
        (thumbTip.x + indexTip.x) / 2f,
        (thumbTip.y + indexTip.y) / 2f,
        (thumbTip.z + indexTip.z) / 2f
    )
    val pinchDistance: Float get() = thumbTip.distanceTo(indexTip)
}

object HandLandmark {
    const val COUNT = 21
    const val WRIST = 0
    const val THUMB_TIP = 4
    const val INDEX_TIP = 8
    const val MIDDLE_TIP = 12
    const val RING_TIP = 16
    const val LITTLE_TIP = 20
    val PALM = intArrayOf(0, 5, 9, 13, 17)
    val FINGER_TIPS = intArrayOf(4, 8, 12, 16, 20)
}
