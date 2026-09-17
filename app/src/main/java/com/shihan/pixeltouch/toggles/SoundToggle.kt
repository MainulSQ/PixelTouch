package com.shihan.pixeltouch.toggles

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings

/**
 * Sound mode is the one control in this app that Android lets a normal app
 * flip instantly, end to end, with no extra tap through Settings — as long
 * as the user has granted "Do Not Disturb access" once (required since
 * Android 6 for any app that changes ringer mode).
 */
object SoundToggle {

    fun hasDndAccess(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.isNotificationPolicyAccessGranted
    }

    fun requestDndAccessIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Cycles Normal -> Vibrate -> Silent -> Normal. Returns "no_access" if DND access is missing. */
    fun cycleRingerMode(context: Context): String {
        if (!hasDndAccess(context)) return "no_access"
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val next = when (am.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
            AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
            else -> AudioManager.RINGER_MODE_NORMAL
        }
        am.ringerMode = next
        return labelFor(next)
    }

    fun currentModeLabel(context: Context): String {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return labelFor(am.ringerMode)
    }

    fun adjustMediaVolume(context: Context, direction: Int) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            direction,
            AudioManager.FLAG_SHOW_UI or AudioManager.FLAG_PLAY_SOUND
        )
    }

    private fun labelFor(mode: Int): String = when (mode) {
        AudioManager.RINGER_MODE_NORMAL -> "normal"
        AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
        else -> "silent"
    }
}
