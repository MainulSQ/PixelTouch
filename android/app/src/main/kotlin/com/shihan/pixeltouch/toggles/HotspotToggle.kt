package com.shihan.pixeltouch.toggles

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

/**
 * There is no public API to flip the user's normal, named tethering hotspot
 * on/off. What IS public is WifiManager.startLocalOnlyHotspot(), which spins
 * up a real hotspot other devices can join. From Android 11 (API 30) you can
 * give it a fixed SSID/password via SoftApConfiguration, so it behaves like a
 * genuine instant on/off toggle. Android chooses the network credentials for
 * this public API, and it needs location permission (an Android platform
 * requirement for any Wi-Fi scanning/AP API, not something this app asks for
 * on a whim).
 *
 * If permission is missing or the call fails, we fall back to opening the
 * Tethering settings screen, where the hotspot switch sits at the very top.
 */
object HotspotToggle {

    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null

    val isActive: Boolean
        get() = reservation != null

    fun hasRequiredPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun start(
        context: Context,
        ssid: String,
        passphrase: String,
        onStarted: (String, String) -> Unit,
        onFailed: (String) -> Unit
    ) {
        if (!hasRequiredPermission(context)) {
            onFailed("permission")
            return
        }
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val callback = object : WifiManager.LocalOnlyHotspotCallback() {
            override fun onStarted(res: WifiManager.LocalOnlyHotspotReservation) {
                reservation = res
                val config = res.wifiConfiguration
                onStarted(config?.SSID ?: ssid, config?.preSharedKey ?: passphrase)
            }

            override fun onStopped() {
                reservation = null
            }

            override fun onFailed(reason: Int) {
                reservation = null
                onFailed("code_$reason")
            }
        }
        try {
            @Suppress("DEPRECATION")
            wm.startLocalOnlyHotspot(callback, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            onFailed(e.message ?: "error")
        }
    }

    fun stop() {
        reservation?.close()
        reservation = null
    }

    fun tetherSettingsIntent(): Intent {
        // "android.settings.TETHER_SETTINGS" is not part of the public
        // Settings constants but is present on stock Android/Pixel; fall
        // back to the wireless settings screen if it isn't resolvable.
        return Intent("android.settings.TETHER_SETTINGS")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun wirelessSettingsIntent(): Intent =
        Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
