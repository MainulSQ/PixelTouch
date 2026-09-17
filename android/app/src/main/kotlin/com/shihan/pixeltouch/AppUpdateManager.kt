package com.shihan.pixeltouch

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import android.app.Activity
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Fetches signed development builds published by this project's GitHub Action. */
object AppUpdateManager {

    private const val RELEASES_LATEST =
        "https://github.com/MainulSQ/PixelTouch/releases/latest"
    private const val RELEASE_DOWNLOAD_BASE =
        "https://github.com/MainulSQ/PixelTouch/releases/download"
    private const val APK_NAME = "PixelTouch.apk"

    private val executor = Executors.newSingleThreadExecutor()
    private var pendingUpdate: Update? = null
    private var shownVersionCode: Int? = null
    private var lastCheckAt = 0L

    private data class Update(
        val versionCode: Int,
        val versionName: String,
        val downloadUrl: String
    )

    fun checkForUpdate(activity: Activity, callback: ((Map<String, Any>) -> Unit)? = null) {
        val now = System.currentTimeMillis()
        if (callback == null && now - lastCheckAt < CHECK_COOLDOWN_MS) return
        lastCheckAt = now
        executor.execute {
            val update = fetchLatestUpdate()
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                if (update == null) {
                    callback?.invoke(mapOf("status" to "unavailable"))
                } else if (update.versionCode <= BuildConfig.VERSION_CODE) {
                    callback?.invoke(mapOf("status" to "up_to_date", "version" to update.versionName))
                } else {
                    callback?.invoke(mapOf("status" to "available", "version" to update.versionName))
                    if (shownVersionCode != update.versionCode) {
                        shownVersionCode = update.versionCode
                        showUpdateDialog(activity, update)
                    }
                }
            }
        }
    }

    fun resumePendingInstall(activity: Activity) {
        val update = pendingUpdate ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || activity.packageManager.canRequestPackageInstalls()) {
            pendingUpdate = null
            downloadAndInstall(activity, update)
        }
    }

    private fun showUpdateDialog(activity: Activity, update: Update) {
        AlertDialog.Builder(activity)
            .setTitle("Update available")
            .setMessage("PixelTouch ${update.versionName} is ready to install.")
            .setNegativeButton("Later", null)
            .setPositiveButton("Update") { _, _ -> requestInstallAndDownload(activity, update) }
            .show()
    }

    private fun requestInstallAndDownload(activity: Activity, update: Update) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            pendingUpdate = update
            Toast.makeText(
                activity,
                "Allow PixelTouch to install updates, then return here",
                Toast.LENGTH_LONG
            ).show()
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${activity.packageName}")
                )
            )
            return
        }
        downloadAndInstall(activity, update)
    }

    private fun downloadAndInstall(activity: Activity, update: Update) {
        Toast.makeText(activity, "Downloading PixelTouch ${update.versionName}", Toast.LENGTH_SHORT).show()
        executor.execute {
            val apk = downloadApk(activity, update) ?: run {
                activity.runOnUiThread {
                    Toast.makeText(activity, "Update download failed", Toast.LENGTH_LONG).show()
                }
                return@execute
            }
            activity.runOnUiThread { launchPackageInstaller(activity, apk) }
        }
    }

    private fun fetchLatestUpdate(): Update? = runCatching {
        // GitHub's REST API is rate-limited for an unauthenticated sideloaded app.
        // This stable release URL returns a redirect to /releases/tag/v{code}, which
        // gives us the same version information without consuming API quota.
        val connection = (URL("$RELEASES_LATEST?check=${System.currentTimeMillis()}").openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "PixelTouch")
            setRequestProperty("Cache-Control", "no-cache")
            useCaches = false
            instanceFollowRedirects = false
        }
        try {
            if (connection.responseCode !in 300..399) return null
            val tag = connection.getHeaderField("Location")
                ?.substringAfterLast('/')
                ?.takeIf { it.startsWith("v") }
                ?: return null
            val versionCode = tag.removePrefix("v").toIntOrNull() ?: return null
            Update(
                versionCode = versionCode,
                versionName = tag,
                downloadUrl = "$RELEASE_DOWNLOAD_BASE/$tag/$APK_NAME"
            )
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private fun downloadApk(activity: Activity, update: Update): File? = runCatching {
        val directory = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: activity.cacheDir
        directory.mkdirs()
        val apk = File(directory, "PixelTouch-${update.versionCode}.apk")
        val connection = (URL(update.downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "PixelTouch")
        }
        try {
            if (connection.responseCode !in 200..299) error("Download failed")
            connection.inputStream.use { input ->
                apk.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
        apk
    }.getOrNull()

    private fun launchPackageInstaller(activity: Activity, apk: File) {
        val uri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            apk
        )
        activity.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        )
    }

    private const val CHECK_COOLDOWN_MS = 30_000L
}
