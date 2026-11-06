package com.vrhub.app.handtracking

import kotlin.math.PI
import kotlin.math.sqrt

/** Adaptive low-pass filter: stable at rest, responsive while the hand moves. */
class OneEuroFilter3D(
    private val minCutoff: Float = 1.2f,
    private val beta: Float = 0.02f,
    private val derivativeCutoff: Float = 1f
) {
    private var initialized = false
    private var lastTimeSeconds = 0.0
    private var value = Landmark3D(0f, 0f, 0f)
    private var derivative = Landmark3D(0f, 0f, 0f)

    fun reset() { initialized = false }

    @Synchronized
    fun filter(input: Landmark3D, timeSeconds: Double): Landmark3D {
        if (!initialized) {
            initialized = true
            lastTimeSeconds = timeSeconds
            value = input
            derivative = Landmark3D(0f, 0f, 0f)
            return input
        }
        val dt = (timeSeconds - lastTimeSeconds).toFloat()
        if (dt <= 0f) return value
        lastTimeSeconds = timeSeconds

        val rawDerivative = Landmark3D(
            (input.x - value.x) / dt,
            (input.y - value.y) / dt,
            (input.z - value.z) / dt
        )
        val derivativeAlpha = alpha(derivativeCutoff, dt)
        derivative = lerp(derivative, rawDerivative, derivativeAlpha)
        val speed = sqrt(derivative.x * derivative.x + derivative.y * derivative.y + derivative.z * derivative.z)
        val adaptiveCutoff = minCutoff + beta * speed
        value = lerp(value, input, alpha(adaptiveCutoff, dt))
        return value
    }

    private fun alpha(cutoff: Float, dt: Float): Float {
        val safeCutoff = cutoff.coerceAtLeast(0.0001f)
        val tau = 1f / (2f * PI.toFloat() * safeCutoff)
        return 1f / (1f + tau / dt)
    }

    private fun lerp(a: Landmark3D, b: Landmark3D, t: Float) = Landmark3D(
        a.x + (b.x - a.x) * t,
        a.y + (b.y - a.y) * t,
        a.z + (b.z - a.z) * t,
        b.visibility
    )
}
