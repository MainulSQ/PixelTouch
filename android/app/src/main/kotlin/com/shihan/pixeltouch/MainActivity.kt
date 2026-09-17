package com.shihan.pixeltouch

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

/** Android-only PixelTouch integrations exposed to the Flutter UI. */
class MainActivity : FlutterActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppUpdateManager.checkForUpdate(this)
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "status" -> result.success(status())
                "openOverlayPermission" -> { openOverlayPermission(); result.success(null) }
                "openDndSettings" -> { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)); result.success(null) }
                "requestLocation" -> { requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 10); result.success(null) }
                "requestNotifications" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 11)
                    result.success(null)
                }
                "startBubble" -> { startForegroundService(Intent(this, OverlayService::class.java)); setBubbleRunning(true); result.success(null) }
                "stopBubble" -> { stopService(Intent(this, OverlayService::class.java)); setBubbleRunning(false); result.success(null) }
                "openAccessibility" -> { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); result.success(null) }
                else -> result.notImplemented()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AppUpdateManager.resumePendingInstall(this)
    }

    private fun openOverlayPermission() {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    private fun setBubbleRunning(running: Boolean) {
        getSharedPreferences(BootReceiver.PREFS, MODE_PRIVATE).edit().putBoolean(BootReceiver.KEY_RUNNING, running).apply()
    }

    private fun status() = mapOf(
        "overlay" to Settings.canDrawOverlays(this),
        "dnd" to (getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true),
        "location" to (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED),
        "notifications" to (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED),
        "running" to getSharedPreferences(BootReceiver.PREFS, MODE_PRIVATE).getBoolean(BootReceiver.KEY_RUNNING, false)
    )

    companion object {
        const val CHANNEL = "com.shihan.pixeltouch/device"
        const val EXTRA_REQUEST_CAMERA = "com.shihan.pixeltouch.REQUEST_CAMERA"
    }
}
