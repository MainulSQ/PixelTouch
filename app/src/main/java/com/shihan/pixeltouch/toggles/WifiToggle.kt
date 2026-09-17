package com.shihan.pixeltouch.toggles

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings

/**
 * Since Android 10 (API 29), regular apps can no longer flip Wi-Fi on/off in
 * the background — Google restricted WifiManager.setWifiEnabled() to system
 * and carrier-privileged apps only. tryDirectToggle() still works pre-Q, and
 * on newer Android we fall back to the compact Settings Panel, which shows an
 * inline switch without leaving the current screen (one extra tap, not a
 * trip through the full Settings app).
 */
object WifiToggle {

    @Suppress("DEPRECATION")
    fun tryDirectToggle(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                false
            } else {
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                wm.isWifiEnabled = !wm.isWifiEnabled
                true
            }
        } catch (e: SecurityException) {
            false
        }
    }

    fun isEnabled(context: Context): Boolean {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return wm.isWifiEnabled
    }

    /** Compact inline Wi-Fi / mobile data switch panel (Android 9+). */
    fun quickPanelIntent(): Intent =
        Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_WIFI_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
