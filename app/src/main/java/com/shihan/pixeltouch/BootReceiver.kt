package com.shihan.pixeltouch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** Restarts the floating bubble after a reboot, only if it was running before and overlay permission is still granted. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val wasRunning = prefs.getBoolean(KEY_RUNNING, false)
        if (wasRunning && Settings.canDrawOverlays(context)) {
            context.startForegroundService(Intent(context, OverlayService::class.java))
        }
    }

    companion object {
        const val PREFS = "pixeltouch_prefs"
        const val KEY_RUNNING = "bubble_running"
    }
}
