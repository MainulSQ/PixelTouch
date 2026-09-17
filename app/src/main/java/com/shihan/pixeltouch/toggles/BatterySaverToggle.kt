package com.shihan.pixeltouch.toggles

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings

/**
 * PowerManager only lets an app *read* battery saver state; there is no
 * public setter. Two paths are supported here:
 *
 * 1. Default (no extra setup): open the Battery Saver settings screen,
 *    where the switch is right at the top — one extra tap.
 * 2. Power-user opt-in: if the user has granted WRITE_SECURE_SETTINGS via a
 *    one-time adb command (see README), we write the "low_power" global
 *    setting directly, which genuinely flips battery saver instantly with
 *    no screen change. This mirrors what several open-source "battery saver
 *    shortcut" apps do; it only works after that explicit adb grant.
 */
object BatterySaverToggle {

    fun isEnabled(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isPowerSaveMode
    }

    fun hasWriteSecureSettings(context: Context): Boolean =
        context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    /** Returns true if it actually flipped the setting. */
    fun tryDirectToggle(context: Context): Boolean {
        if (!hasWriteSecureSettings(context)) return false
        return try {
            val newValue = if (isEnabled(context)) 0 else 1
            Settings.Global.putInt(context.contentResolver, "low_power", newValue)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
