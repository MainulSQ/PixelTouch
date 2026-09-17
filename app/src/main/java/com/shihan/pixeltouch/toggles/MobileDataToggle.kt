package com.shihan.pixeltouch.toggles

import android.content.Intent
import android.provider.Settings

/**
 * There has never been a public API for a third-party app to flip mobile
 * data on/off (TelephonyManager.setDataEnabled() is a hidden/system-only
 * call). The best compliant UX is the same compact inline panel used for
 * Wi-Fi, which shows a mobile-data switch right at the top.
 */
object MobileDataToggle {

    fun quickPanelIntent(): Intent =
        Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
