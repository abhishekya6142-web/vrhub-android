package com.vrhub.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

class HomeScreenView(
    context: Context,
    private val onStart: () -> Unit
) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f
    private var buttonBounds = RectF()

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        isFocusable = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        phase = (phase + 0.018f) % (Math.PI.toFloat() * 2f)
        p.shader = LinearGradient(0f, 0f, w, h,
            intArrayOf(Color.rgb(5, 10, 28), Color.rgb(12, 20, 48), Color.rgb(4, 8, 22)),
            null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, p)
        p.shader = null

        // Soft ambient glow
        p.shader = RadialGradient(w * 0.5f, h * 0.43f, h * 0.72f,
            intArrayOf(0x443B82F6, 0x143B82F6, Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP)
        canvas.drawCircle(w * 0.5f, h * 0.43f, h * 0.72f, p)
        p.shader = null

        // Animated orbital rings
        val cx = w * 0.5f
        val cy = h * 0.43f
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.5f
        p.color = 0x5538BDF8
        canvas.save()
        canvas.rotate(phase * 18f, cx, cy)
        canvas.drawOval(RectF(cx - h * 0.40f, cy - h * 0.20f, cx + h * 0.40f, cy + h * 0.20f), p)
        canvas.restore()
        p.color = 0x443B82F6
        p.strokeWidth = 1f
        canvas.drawCircle(cx, cy, h * (0.19f + 0.008f * sin(phase.toDouble()).toFloat()), p)
        p.style = Paint.Style.FILL

        // Orbiting light nodes
        for (i in 0 until 5) {
            val a = phase + i * (Math.PI.toFloat() * 2f / 5f)
            val x = cx + cos(a.toDouble()).toFloat() * h * 0.34f
            val y = cy + sin(a.toDouble()).toFloat() * h * 0.14f
            p.color = 0xFF67E8F9.toInt()
            canvas.drawCircle(x, y, 3.5f, p)
            p.color = 0x3367E8F9
            canvas.drawCircle(x, y, 10f, p)
        }

        // Brand
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        p.textSize = h * 0.105f
        p.color = Color.WHITE
        canvas.drawText("VR HUB", cx, h * 0.39f, p)
        p.textSize = h * 0.024f
        p.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        p.color = 0xFF8BE9FF.toInt()
        canvas.drawText("YOUR WORLD. YOUR SPACE.", cx, h * 0.46f, p)

        // Start button with animated glow
        val bw = w * 0.27f
        val bh = h * 0.115f
        buttonBounds.set(cx - bw / 2f, h * 0.69f, cx + bw / 2f, h * 0.69f + bh)
        val pulse = 0.5f + 0.5f * sin(phase.toDouble()).toFloat()
        p.color = 0x2238BDF8
        canvas.drawRoundRect(RectF(buttonBounds).apply { inset(-10f - pulse * 5f, -7f - pulse * 4f) }, bh, bh, p)
        p.shader = LinearGradient(buttonBounds.left, buttonBounds.top, buttonBounds.right, buttonBounds.bottom,
            intArrayOf(0xFF2563EB.toInt(), 0xFF0891B2.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(buttonBounds, bh / 2f, bh / 2f, p)
        p.shader = null
        p.textSize = h * 0.036f
        p.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        p.color = Color.WHITE
        canvas.drawText("START  ›", cx, buttonBounds.centerY() + p.textSize * 0.34f, p)

        p.textSize = h * 0.021f
        p.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        p.color = 0xFF9CAEC8.toInt()
        canvas.drawText("Immersive hand-tracking experience", cx, h * 0.88f, p)

        // Small top status mark
        p.textAlign = Paint.Align.LEFT
        p.textSize = h * 0.023f
        p.color = 0xFF67E8F9.toInt()
        canvas.drawCircle(w * 0.055f, h * 0.09f, 4f, p)
        p.color = 0xFFB8C7DD.toInt()
        canvas.drawText("SYSTEM READY", w * 0.07f, h * 0.098f, p)

        postInvalidateDelayed(16)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> return buttonBounds.contains(event.x, event.y)
            MotionEvent.ACTION_UP -> {
                if (buttonBounds.contains(event.x, event.y)) {
                    performClick()
                    onStart()
                    return true
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
