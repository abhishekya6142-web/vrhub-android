package com.vrhub.app.interaction

import com.vrhub.app.gestures.HandGesture

/** Converts gesture transitions into stable interaction callbacks for UI/window systems. */
class PinchInteractionController(
    private val onPinchStarted: () -> Unit,
    private val onPinchEnded: () -> Unit,
    private val onOpenPalm: () -> Unit
) {
    private var pinching = false

    @Synchronized
    fun update(gesture: HandGesture) {
        val nowPinching = gesture == HandGesture.PINCH
        if (nowPinching != pinching) {
            pinching = nowPinching
            if (pinching) onPinchStarted() else onPinchEnded()
        }
        if (gesture == HandGesture.OPEN_PALM) onOpenPalm()
    }

    @Synchronized
    fun reset() {
        if (pinching) onPinchEnded()
        pinching = false
    }
}
