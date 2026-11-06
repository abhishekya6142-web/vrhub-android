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
import android.os.SystemClock
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
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
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
    @Volatile private var landmarker: HandLandmarker? = null
    @Volatile private var delegateName = "none"
    @Volatile private var iw = 1
    @Volatile private var ih = 1
    @Volatile private var submitNs = 0L
    private var camFrames = 0
    private var handFrames = 0
    private var lastTime = System.nanoTime()
    private var sx = -1f
    private var sy = -1f

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
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setNumHands(1)
                    .setResultListener { res, _ -> onResult(res) }
                    .setErrorListener { }
                    .build()
                val lm = HandLandmarker.createFromOptions(this, opts)
                delegateName = d.name
                return lm
            } catch (e: Exception) {
                // next delegate
            }
        }
        return null
    }

    private fun onResult(res: HandLandmarkerResult) {
        val lat = (System.nanoTime() - submitNs) / 1_000_000
        val hand = res.landmarks().firstOrNull()
        var x = -1f
        var y = -1f
        if (hand != null) {
            val tx = hand[8].x()
            val ty = hand[8].y()
            if (sx < 0f) {
                sx = tx
                sy = ty
            } else {
                sx += (tx - sx) * 0.6f
                sy += (ty - sy) * 0.6f
            }
            x = sx
            y = sy
        } else {
            sx = -1f
            sy = -1f
        }

        handFrames++
        val now = System.nanoTime()
        val elapsed = now - lastTime
        var text: String? = null
        if (elapsed >= 1_000_000_000L) {
            val camFps = camFrames * 1_000_000_000.0 / elapsed
            val handFps = handFrames * 1_000_000_000.0 / elapsed
            camFrames = 0
            handFrames = 0
            lastTime = now
            text = String.format("cam %.0f | hand %.0f FPS | lat %d ms | %s", camFps, handFps, lat, delegateName)
        }
        val w = iw
        val h = ih
        runOnUiThread {
            dot.nx = x
            dot.ny = y
            dot.iw = w
            dot.ih = h
            dot.invalidate()
            if (text != null) info.text = text
        }
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

                camFrames++
                iw = bmp.width
                ih = bmp.height
                val lm = landmarker
                if (lm != null) {
                    submitNs = System.nanoTime()
                    try {
                        lm.detectAsync(BitmapImageBuilder(bmp).build(), SystemClock.uptimeMillis())
                    } catch (e: Exception) {
                        // skip frame
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