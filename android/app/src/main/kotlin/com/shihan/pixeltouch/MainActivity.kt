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
                "menuControls" -> result.success(menuControls())
                "setMenuControlVisible" -> {
                    val id = call.argument<Int>("id")
                    val visible = call.argument<Boolean>("visible")
                    if (id == null || visible == null) result.error("invalid_args", "id and visible are required", null)
                    else { setMenuControlVisible(id, visible); result.success(null) }
                }
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

    private val menuControlIds = listOf(
        R.id.action_wifi, R.id.action_data, R.id.action_sound, R.id.action_screenshot,
        R.id.action_torch, R.id.action_hotspot, R.id.action_battery, R.id.action_lock,
        R.id.action_bluetooth, R.id.action_display, R.id.action_settings,
        R.id.action_screenshot,
    )

    private fun menuControls(): List<Map<String, Any>> {
        val visible = visibleMenuControls()
        return menuControlIds.map { id -> mapOf("id" to id, "label" to menuLabel(id), "visible" to (id in visible)) }
    }

    private fun setMenuControlVisible(id: Int, visible: Boolean) {
        if (id !in menuControlIds) return
        val controls = visibleMenuControls().toMutableSet()
        if (visible) controls.add(id) else controls.remove(id)
        if (controls.isEmpty()) return
        getSharedPreferences(BootReceiver.PREFS, MODE_PRIVATE).edit()
            .putString("visible_menu_controls", menuControlIds.filter { it in controls }.joinToString(","))
            .apply()
    }

    private fun visibleMenuControls(): Set<Int> = getSharedPreferences(BootReceiver.PREFS, MODE_PRIVATE)
        .getString("visible_menu_controls", null)?.split(',')?.mapNotNull { it.toIntOrNull() }?.toSet()
        ?: menuControlIds.toSet()

    private fun menuLabel(id: Int): String = getString(when (id) {
        R.id.action_wifi -> R.string.action_wifi
        R.id.action_data -> R.string.action_data
        R.id.action_sound -> R.string.action_sound
        R.id.action_torch -> R.string.action_torch
        R.id.action_hotspot -> R.string.action_hotspot
        R.id.action_battery -> R.string.action_battery
        R.id.action_lock -> R.string.action_lock
        R.id.action_bluetooth -> R.string.action_bluetooth
        R.id.action_display -> R.string.action_display
        R.id.action_settings -> R.string.action_settings
        R.id.action_screenshot -> R.string.action_screenshot
        else -> R.string.app_name
    })

    companion object {
        const val CHANNEL = "com.shihan.pixeltouch/device"
        const val EXTRA_REQUEST_CAMERA = "com.shihan.pixeltouch.REQUEST_CAMERA"
    }
}
