package com.vrhub.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Bundle
import android.util.Size
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import java.util.concurrent.Executors

class DotView(c: Context) : View(c) {
    var nx = -1f
    var ny = -1f
    var iw = 1
    var ih = 1
    private val paint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        if (nx < 0f) return
        val s = maxOf(width.toFloat() / iw, height.toFloat() / ih)
        val x = nx * iw * s + (width - iw * s) / 2f
        val y = ny * ih * s + (height - ih * s) / 2f
        canvas.drawCircle(x, y, 24f, paint)
    }
}

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var dot: DotView
    private lateinit var info: TextView
    private val executor = Executors.newSingleThreadExecutor()
    private var landmarker: HandLandmarker? = null
    private var delegateName = "none"
    private var frames = 0
    private var lastTime = System.nanoTime()
    private var inferMs = 0L

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            if (ok) startCamera() else info.text = "Camera permission chahiye"
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this)
        previewView = PreviewView(this)
        dot = DotView(this)
        info = TextView(this).apply {
            setTextColor(Color.YELLOW)
            setBackgroundColor(0x88000000.toInt())
            textSize = 16f
            setPadding(16, 8, 16, 8)
            text = "Starting..."
        }
        val full = ViewGroup.LayoutParams.MATCH_PARENT
        root.addView(previewView, FrameLayout.LayoutParams(full, full))
        root.addView(dot, FrameLayout.LayoutParams(full, full))
        root.addView(
            info,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        setContentView(root)

        executor.execute { landmarker = buildLandmarker() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            permLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun buildLandmarker(): HandLandmarker? {
        for (d in listOf(Delegate.GPU, Delegate.CPU)) {
            try {
                val base = BaseOptions.builder()
                    .setModelAssetPath("hand_landmarker.task")
                    .setDelegate(d)
                    .build()
                val opts = HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(base)
                    .setRunningMode(RunningMode.IMAGE)
                    .setNumHands(1)
                    .build()
                val lm = HandLandmarker.createFromOptions(this, opts)
                delegateName = d.name
                return lm
            } catch (e: Exception) {
                // try next delegate
            }
        }
        return null
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)

            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()

            analysis.setAnalyzer(executor) { proxy ->
                val rot = proxy.imageInfo.rotationDegrees
                var bmp = proxy.toBitmap()
                if (rot != 0) {
                    val m = Matrix()
                    m.postRotate(rot.toFloat())
                    bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                }
                proxy.close()

                var x = -1f
                var y = -1f
                val lm = landmarker
                if (lm != null) {
                    val t0 = System.nanoTime()
                    val res = lm.detect(BitmapImageBuilder(bmp).build())
                    inferMs = (System.nanoTime() - t0) / 1_000_000
                    val hand = res.landmarks().firstOrNull()
                    if (hand != null) {
                        x = hand[8].x()
                        y = hand[8].y()
                    }
                }

                frames++
                val now = System.nanoTime()
                val elapsed = now - lastTime
                val iw = bmp.width
                val ih = bmp.height
                val fpsText = if (elapsed >= 1_000_000_000L) {
                    val fps = frames * 1_000_000_000.0 / elapsed
                    frames = 0
                    lastTime = now
                    String.format("FPS %.1f | infer %d ms | %s", fps, inferMs, delegateName)
                } else null

                runOnUiThread {
                    dot.nx = x
                    dot.ny = y
                    dot.iw = iw
                    dot.ih = ih
                    dot.invalidate()
                    if (fpsText != null) {
                        info.text = if (landmarker == null) "$fpsText | model load ho raha hai ya fail" else fpsText
                    }
                }
            }

            provider.unbindAll()
            provider.bindToLifecycle(
                this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
            )
        }, ContextCompat.getMainExecutor(this))
    }
}
