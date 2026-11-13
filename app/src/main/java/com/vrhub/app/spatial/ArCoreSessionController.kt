package com.vrhub.app.spatial

import android.app.Activity
import android.content.pm.PackageManager
import com.google.ar.core.Anchor
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Session

/**
 * ARCore lifecycle owner for Phone AR and 6DoF Cardboard.
 *
 * Stop/unbind CameraX before resuming this session: Android camera access is exclusive.
 * Call updateFrame() from the GL/render thread, not from the UI thread.
 */
class ArCoreSessionController(private val activity: Activity) {
    private var session: Session? = null
    private var installRequested = false

    val isRunning: Boolean get() = session != null

    fun start(onReady: () -> Unit, onError: (String) -> Unit) {
        if (activity.checkSelfPermission(android.Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            onError("Camera permission is required.")
            return
        }

        try {
            when (ArCoreApk.getInstance().requestInstall(activity, !installRequested)) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    installRequested = true
                    return
                }
                ArCoreApk.InstallStatus.INSTALLED -> Unit
            }

            val newSession = Session(activity)
            val config = Config(newSession).apply {
                planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            }
            newSession.configure(config)
            newSession.resume()
            session = newSession
            onReady()
        } catch (error: Exception) {
            onError(error.localizedMessage ?: "ARCore could not start.")
        }
    }

    fun updateFrame(): Frame? = try {
        session?.update()
    } catch (_: Exception) {
        null
    }

    fun createAnchor(hit: HitResult): Anchor = hit.createAnchor()

    fun pause() {
        try { session?.pause() } catch (_: Exception) { }
    }

    fun resume(onError: (String) -> Unit) {
        try {
            session?.resume()
        } catch (error: Exception) {
            onError(error.localizedMessage ?: "ARCore could not resume.")
        }
    }

    fun close() {
        try { session?.close() } catch (_: Exception) { }
        session = null
    }
}
