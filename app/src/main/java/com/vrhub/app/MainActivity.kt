package com.vrhub.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.util.Size
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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
import com.vrhub.app.spatial.CardboardHeadTracker
import com.vrhub.app.spatial.CardboardStereoView
import com.vrhub.app.updates.UpdateInfo
import com.vrhub.app.updates.UpdateManager
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
        canvas.drawCircle(nx * iw * s + (width - iw * s) / 2f,
            ny * ih * s + (height - ih * s) / 2f, 24f, paint)
    }
}

class MainActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private lateinit var previewView: PreviewView
    private lateinit var dot: DotView
    private lateinit var info: TextView
    private lateinit var updateManager: UpdateManager
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
    private var experienceStarted = false
    private var pendingCardboard = false
    private var cardboardView: CardboardStereoView? = null
    private var headTracker: CardboardHeadTracker? = null

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            if (ok) { if (pendingCardboard) startCardboardExperience() else startExperience() } else showHome()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this)
        setContentView(root)
        updateManager = UpdateManager(this) { message ->
            runOnUiThread {
                if (!isFinishing) Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
        showHome()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (experienceStarted) {
                    stopExperience()
                    showHome()
                } else finish()
            }
        })
    }

    private fun showHome() {
        experienceStarted = false
        root.removeAllViews()
        root.addView(HomeScreenView(this) { requestStart() },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT))

        // Update control is deliberately kept separate from the animated START button.
        val updateButton = Button(this).apply {
            text = "CHECK UPDATES"
            textSize = 11f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(0xDD123B60.toInt())
                cornerRadius = 24f
                setStroke(1, 0xFF67DDF2.toInt())
            }
            setPadding(dp(14), dp(5), dp(14), dp(5))
            setOnClickListener { checkForUpdates() }
        }
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, dp(44), Gravity.TOP or Gravity.END
        ).apply {
            topMargin = dp(12)
            marginEnd = dp(16)
        }
        root.addView(updateButton, params)
    }

    private fun checkForUpdates() {
        updateManager.checkForUpdates { update ->
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (update != null) showUpdateDialog(update)
            }
        }
    }

    private fun showUpdateDialog(update: UpdateInfo) {
        AlertDialog.Builder(this)
            .setTitle("VR Hub update ${update.versionName}")
            .setMessage(update.releaseNotes.ifBlank { "A new version is available." })
            .setNegativeButton("Later", null)
            .setPositiveButton("Download & Install") { _, _ ->
                updateManager.downloadAndInstall(update)
            }
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun requestStart() {
        AlertDialog.Builder(this)
            .setTitle("Choose your experience")
            .setItems(arrayOf("Phone AR", "Cardboard VR (3D)")) { _, which ->
                pendingCardboard = which == 1
                if (pendingCardboard) {
                    startCardboardExperience()
                } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                    startExperience()
                } else {
                    permLauncher.launch(Manifest.permission.CAMERA)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startCardboardExperience() {
        if (experienceStarted) return
        experienceStarted = true
        root.removeAllViews()
        val stereo = CardboardStereoView(this)
        cardboardView = stereo
        root.addView(stereo, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val recenter = Button(this).apply {
            text = "RECENTER"
            setOnClickListener { stereo.recenter(); headTracker?.recenter() }
        }
        root.addView(recenter, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, dp(44), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(12); marginEnd = dp(12)
        })
        val label = TextView(this).apply {
            text = "CARDBOARD STEREO 3D  •  HEAD TRACKING"
            setTextColor(Color.WHITE)
            setBackgroundColor(0x66000000)
            textSize = 12f
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        root.addView(label, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(12) })
        val tracker = CardboardHeadTracker(this)
        headTracker = tracker
        tracker.onPoseChanged = { yaw, pitch, roll -> stereo.setHeadPose(yaw, pitch, roll) }
        if (tracker.isAvailable) tracker.start()
        else Toast.makeText(this, "Rotation sensor not available; showing fixed stereo view", Toast.LENGTH_LONG).show()
        stereo.onResume()
    }

    private fun startExperience() {
        if (experienceStarted) return
        experienceStarted = true
        root.removeAllViews()
        previewView = PreviewView(this)
        dot = DotView(this)
        info = TextView(this).apply {
            setTextColor(Color.YELLOW)
            setBackgroundColor(0x88000000.toInt())
            textSize = 16f
            setPadding(16, 8, 16, 8)
            text = "Starting hand tracking..."
        }
        val full = ViewGroup.LayoutParams.MATCH_PARENT
        root.addView(previewView, FrameLayout.LayoutParams(full, full))
        root.addView(dot, FrameLayout.LayoutParams(full, full))
        root.addView(info, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        if (landmarker == null) executor.execute {
            if (landmarker == null) landmarker = buildLandmarker()
        }
        startCamera()
    }

    private fun stopExperience() {
        experienceStarted = false
        headTracker?.stop()
        headTracker = null
        cardboardView?.onPause()
        cardboardView = null
        try { ProcessCameraProvider.getInstance(this).get().unbindAll() }
        catch (_: Exception) { }
    }

    override fun onResume() {
        super.onResume()
        if (experienceStarted) {
            cardboardView?.onResume()
            headTracker?.start()
        }
    }

    override fun onPause() {
        headTracker?.stop()
        cardboardView?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        try { landmarker?.close() } catch (_: Exception) { }
        if (::updateManager.isInitialized) updateManager.close()
        executor.shutdown()
        super.onDestroy()
    }

    private fun buildLandmarker(): HandLandmarker? {
        for (d in listOf(Delegate.GPU, Delegate.CPU)) {
            try {
                val base = BaseOptions.builder().setModelAssetPath("hand_landmarker.task")
                    .setDelegate(d).build()
                val opts = HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(base).setRunningMode(RunningMode.LIVE_STREAM)
                    .setNumHands(1).setResultListener { res, _ -> onResult(res) }
                    .setErrorListener { }.build()
                val lm = HandLandmarker.createFromOptions(this, opts)
                delegateName = d.name
                return lm
            } catch (_: Exception) { }
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
            if (sx < 0f) { sx = tx; sy = ty }
            else { sx += (tx - sx) * 0.6f; sy += (ty - sy) * 0.6f }
            x = sx
            y = sy
        } else { sx = -1f; sy = -1f }

        handFrames++
        val now = System.nanoTime()
        val elapsed = now - lastTime
        var status: String? = null
        if (elapsed >= 1_000_000_000L) {
            val camFps = camFrames * 1_000_000_000.0 / elapsed
            val handFps = handFrames * 1_000_000_000.0 / elapsed
            camFrames = 0
            handFrames = 0
            lastTime = now
            status = String.format("cam %.0f | hand %.0f FPS | lat %d ms | %s",
                camFps, handFps, lat, delegateName)
        }
        val w = iw
        val h = ih
        runOnUiThread {
            if (!experienceStarted) return@runOnUiThread
            dot.nx = x; dot.ny = y; dot.iw = w; dot.ih = h
            dot.invalidate()
            if (status != null) info.text = status
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (!experienceStarted) return@addListener
            val provider = future.get()
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)
            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build()
            analysis.setAnalyzer(executor) { proxy ->
                val rot = proxy.imageInfo.rotationDegrees
                var bmp = proxy.toBitmap()
                if (rot != 0) {
                    val matrix = Matrix()
                    matrix.postRotate(rot.toFloat())
                    bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                }
                proxy.close()
                camFrames++
                iw = bmp.width
                ih = bmp.height
                val lm = landmarker
                if (lm != null) {
                    submitNs = System.nanoTime()
                    try { lm.detectAsync(BitmapImageBuilder(bmp).build(), SystemClock.uptimeMillis()) }
                    catch (_: Exception) { }
                }
            }
            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(this))
    }
}