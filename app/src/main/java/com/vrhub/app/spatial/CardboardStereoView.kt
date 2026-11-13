package com.vrhub.app.spatial

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.tan

/**
 * Side-by-side Cardboard renderer.
 * Each half gets its own perspective projection and eye offset; the viewport is fully
 * covered by the GL background, so there are no letterbox gaps or stretched eye images.
 */
class CardboardStereoView(context: Context) : GLSurfaceView(context) {
    private val rendererImpl = StereoRenderer()
    init {
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause = true
        setRenderer(rendererImpl)
        renderMode = RENDERMODE_CONTINUOUSLY
        setBackgroundColor(android.graphics.Color.BLACK)
    }

    fun setHeadPose(yaw: Float, pitch: Float, roll: Float) {
        rendererImpl.setHeadPose(yaw, pitch, roll)
    }

    fun recenter() = rendererImpl.recenter()

    private class StereoRenderer : Renderer {
        @Volatile private var yaw = 0f
        @Volatile private var pitch = 0f
        @Volatile private var roll = 0f
        @Volatile private var yawZero = 0f
        private var program = 0
        private var positionHandle = 0
        private var mvpHandle = 0
        private lateinit var vertices: FloatBuffer
        private val projection = FloatArray(16)
        private val view = FloatArray(16)
        private val model = FloatArray(16)
        private val mv = FloatArray(16)
        private val mvp = FloatArray(16)
        private var frame = 0f

        fun setHeadPose(y: Float, p: Float, r: Float) { yaw = y; pitch = p; roll = r }
        fun recenter() { yawZero = yaw }

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            GLES20.glClearColor(0.015f, 0.025f, 0.075f, 1f)
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
            val vertexShader = """
                attribute vec3 aPosition;
                uniform mat4 uMvp;
                void main() { gl_Position = uMvp * vec4(aPosition, 1.0); }
            """.trimIndent()
            val fragmentShader = """
                precision mediump float;
                void main() {
                    float glow = 0.5 + 0.5 * sin(gl_FragCoord.y * 0.012);
                    gl_FragColor = vec4(0.025 + glow * 0.018, 0.055 + glow * 0.035, 0.14 + glow * 0.08, 1.0);
                }
            """.trimIndent()
            program = createProgram(vertexShader, fragmentShader)
            positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
            mvpHandle = GLES20.glGetUniformLocation(program, "uMvp")
            val data = cubeVertices()
            vertices = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder())
                .asFloatBuffer().apply { put(data); position(0) }
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            GLES20.glViewport(0, 0, width, height)
        }

        override fun onDrawFrame(gl: GL10?) {
            val viewport = IntArray(4)
            GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, viewport, 0)
            val width = viewport[2].coerceAtLeast(2)
            val height = viewport[3].coerceAtLeast(1)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            GLES20.glUseProgram(program)
            frame += 0.006f
            val half = width / 2
            for (eye in 0..1) {
                val eyeWidth = if (eye == 0) half else width - half
                val x = if (eye == 0) 0 else half
                GLES20.glViewport(x, 0, eyeWidth, height)
                val aspect = eyeWidth.toFloat() / height
                Matrix.perspectiveM(projection, 0, 70f, aspect, 0.05f, 100f)
                Matrix.setLookAtM(view, 0,
                    if (eye == 0) -0.032f else 0.032f, 0f, 0f,
                    if (eye == 0) -0.032f else 0.032f, 0f, -1f,
                    0f, 1f, 0f)
                Matrix.rotateM(view, 0, Math.toDegrees(-(yaw - yawZero).toDouble()).toFloat(), 0f, 1f, 0f)
                Matrix.rotateM(view, 0, Math.toDegrees(-pitch.toDouble()).toFloat(), 1f, 0f, 0f)
                Matrix.rotateM(view, 0, Math.toDegrees(-roll.toDouble()).toFloat(), 0f, 0f, 1f)
                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, 0f, 0f, -3.2f)
                Matrix.rotateM(model, 0, frame * 18f, 0.3f, 1f, 0.15f)
                Matrix.multiplyMM(mv, 0, view, 0, model, 0)
                Matrix.multiplyMM(mvp, 0, projection, 0, mv, 0)
                GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)
                vertices.position(0)
                GLES20.glEnableVertexAttribArray(positionHandle)
                GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertices)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 36)
                GLES20.glDisableVertexAttribArray(positionHandle)
            }
        }

        private fun createProgram(vs: String, fs: String): Int {
            fun compile(type: Int, source: String): Int {
                val shader = GLES20.glCreateShader(type)
                GLES20.glShaderSource(shader, source)
                GLES20.glCompileShader(shader)
                val status = IntArray(1)
                GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
                check(status[0] == GLES20.GL_TRUE) { GLES20.glGetShaderInfoLog(shader) }
                return shader
            }
            val p = GLES20.glCreateProgram()
            GLES20.glAttachShader(p, compile(GLES20.GL_VERTEX_SHADER, vs))
            GLES20.glAttachShader(p, compile(GLES20.GL_FRAGMENT_SHADER, fs))
            GLES20.glLinkProgram(p)
            val status = IntArray(1)
            GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, status, 0)
            check(status[0] == GLES20.GL_TRUE) { GLES20.glGetProgramInfoLog(p) }
            return p
        }

        private fun cubeVertices(): FloatArray {
            val v = floatArrayOf(
                -1f,-1f,1f, 1f,-1f,1f, 1f,1f,1f, -1f,-1f,1f, 1f,1f,1f, -1f,1f,1f,
                -1f,-1f,-1f, -1f,1f,-1f, 1f,1f,-1f, -1f,-1f,-1f, 1f,1f,-1f, 1f,-1f,-1f,
                -1f,1f,-1f, -1f,1f,1f, 1f,1f,1f, -1f,1f,-1f, 1f,1f,1f, 1f,1f,-1f,
                -1f,-1f,-1f, 1f,-1f,-1f, 1f,-1f,1f, -1f,-1f,-1f, 1f,-1f,1f, -1f,-1f,1f,
                1f,-1f,-1f, 1f,1f,-1f, 1f,1f,1f, 1f,-1f,-1f, 1f,1f,1f, 1f,-1f,1f,
                -1f,-1f,-1f, -1f,-1f,1f, -1f,1f,1f, -1f,-1f,-1f, -1f,1f,1f, -1f,1f,-1f
            )
            return v
        }
    }
}
