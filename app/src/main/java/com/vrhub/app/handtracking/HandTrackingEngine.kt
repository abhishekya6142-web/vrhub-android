package com.vrhub.app.handtracking

import com.vrhub.app.gestures.GestureRecognizer
import com.vrhub.app.gestures.HandGesture
import com.vrhub.app.interaction.HandPointer

/** Turns raw MediaPipe results into smoothed hand, gesture and pointer events. */
class HandTrackingEngine {
    data class Frame(
        val hands: List<TrackedHand>,
        val gestures: List<HandGesture>,
        val pointers: List<HandPointer.Position>
    )

    private val recognizer = GestureRecognizer()
    private val pointer = HandPointer()

    @Synchronized
    fun process(result: HandLandmarkerResult, timestampMs: Long): Frame {
        val hands = MediaPipeHandAdapter.convert(result, timestampMs)
        val gestures = hands.map { recognizer.recognize(it) }
        val pointers = hands.map { pointer.update(it) }
        if (hands.isEmpty()) reset()
        return Frame(hands, gestures, pointers)
    }

    @Synchronized
    fun reset() {
        recognizer.reset()
        pointer.reset()
    }
}
