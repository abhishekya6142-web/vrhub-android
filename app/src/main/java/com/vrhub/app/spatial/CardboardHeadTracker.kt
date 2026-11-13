package com.vrhub.app.spatial

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import kotlin.math.PI

/** 3DoF phone orientation source for Cardboard mode. Position tracking requires ARCore. */
class CardboardHeadTracker(context: Context) : SensorEventListener {
    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    @Suppress("DEPRECATION")
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val rotationMatrix = FloatArray(9)
    private val displayMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private var yawOffset = 0f

    var yawRadians = 0f
        private set
    var pitchRadians = 0f
        private set
    var rollRadians = 0f
        private set

    val isAvailable: Boolean get() = rotationSensor != null
    var onPoseChanged: ((Float, Float, Float) -> Unit)? = null

    fun start() {
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    fun recenter() {
        yawOffset += yawRadians
        yawRadians = 0f
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_GAME_ROTATION_VECTOR &&
            event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        @Suppress("DEPRECATION")
        val displayRotation = windowManager.defaultDisplay.rotation
        val axes = when (displayRotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        SensorManager.remapCoordinateSystem(rotationMatrix, axes.first, axes.second, displayMatrix)
        SensorManager.getOrientation(displayMatrix, orientation)

        yawRadians = wrapPi(orientation[0] - yawOffset)
        pitchRadians = orientation[1]
        rollRadians = orientation[2]
        onPoseChanged?.invoke(yawRadians, pitchRadians, rollRadians)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun wrapPi(value: Float): Float {
        var result = value
        val pi = PI.toFloat()
        while (result > pi) result -= 2f * pi
        while (result < -pi) result += 2f * pi
        return result
    }
}
