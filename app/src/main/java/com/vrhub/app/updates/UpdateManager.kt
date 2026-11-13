package com.vrhub.app.updates

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class UpdateManager(
    private val context: Context,
    private val onStatus: (String) -> Unit
) {
    companion object {
        private const val UPDATE_JSON_URL =
            "https://raw.githubusercontent.com/abhishekya6142-web/vrhub-android/main/update.json"
        private const val APK_FILE_NAME = "vrhub-update.apk"
    }

    private val executor = Executors.newSingleThreadExecutor()
    private var pendingDownloadId = -1L

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id != pendingDownloadId) return

            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                if (!cursor.moveToFirst()) {
                    onStatus("Download status nahi mil saka.")
                    return
                }
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                if (statusIndex < 0) {
                    onStatus("Download status nahi mil saka.")
                    return
                }
                if (cursor.getInt(statusIndex) == DownloadManager.STATUS_SUCCESSFUL) {
                    onStatus("Download complete. Installer open ho raha hai...")
                    installDownloadedApk()
                } else {
                    val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                    val reason = if (reasonIndex >= 0) cursor.getInt(reasonIndex) else -1
                    onStatus("APK download fail hua. Error: $reason")
                }
            }
        }
    }

    init {
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= 33) {
            // DownloadManager sends this broadcast from outside our app.
            context.registerReceiver(downloadReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(downloadReceiver, filter)
        }
    }

    fun checkForUpdates(onResult: (UpdateInfo?) -> Unit) {
        onStatus("Updates check ho rahe hain...")
        executor.execute {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(UPDATE_JSON_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 10000
                    requestMethod = "GET"
                    setRequestProperty("Cache-Control", "no-cache")
                }
                if (connection.responseCode !in 200..299) {
                    throw IllegalStateException("Server response ${connection.responseCode}")
                }
                val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val latest = UpdateInfo(
                    versionCode = json.getLong("versionCode"),
                    versionName = json.getString("versionName"),
                    apkUrl = json.getString("apkUrl"),
                    releaseNotes = json.optString("releaseNotes", "Bug fixes and improvements")
                )
                val packageInfo = if (Build.VERSION.SDK_INT >= 33) {
                    context.packageManager.getPackageInfo(
                        context.packageName,
                        android.content.pm.PackageManager.PackageInfoFlags.of(0)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(context.packageName, 0)
                }
                val installedCode = if (Build.VERSION.SDK_INT >= 28) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                }
                if (latest.versionCode > installedCode) {
                    onStatus("Update available: ${latest.versionName}")
                    onResult(latest)
                } else {
                    onStatus("VR Hub is up to date.")
                    onResult(null)
                }
            } catch (e: Exception) {
                onStatus("Update check nahi ho saka: ${e.message ?: "Network error"}")
                onResult(null)
            } finally {
                connection?.disconnect()
            }
        }
    }

    fun downloadAndInstall(update: UpdateInfo) {
        if (!update.apkUrl.startsWith("https://")) {
            onStatus("Secure HTTPS APK link required.")
            return
        }
        try {
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_FILE_NAME)
            if (file.exists() && !file.delete()) {
                onStatus("Purani update file remove nahi hui.")
                return
            }
            val request = DownloadManager.Request(Uri.parse(update.apkUrl))
                .setTitle("VR Hub update ${update.versionName}")
                .setDescription("Downloading update...")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, APK_FILE_NAME)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            pendingDownloadId = manager.enqueue(request)
            onStatus("Update download start ho gaya. Download complete hone par installer khulega.")
        } catch (e: Exception) {
            onStatus("Download start nahi hua: ${e.message}")
        }
    }

    private fun installDownloadedApk() {
        try {
            val apk = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_FILE_NAME)
            if (!apk.isFile || apk.length() == 0L) {
                onStatus("Downloaded APK nahi mili ya file khaali hai.")
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                onStatus("Install permission allow karo, phir downloaded APK ko dobara install karo.")
                val settingsIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(settingsIntent)
                return
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apk
            )
            val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = uri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
            onStatus("Android installer khul raha hai. Wahan Install dabao.")
        } catch (e: Exception) {
            onStatus("Installer open nahi hua: ${e.message ?: "Unknown error"}")
        }
    }

    fun close() {
        try {
            context.unregisterReceiver(downloadReceiver)
        } catch (_: Exception) {
        }
        executor.shutdown()
    }
}
