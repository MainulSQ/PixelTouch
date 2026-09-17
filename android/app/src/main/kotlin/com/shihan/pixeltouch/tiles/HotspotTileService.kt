package com.shihan.pixeltouch.tiles

import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.shihan.pixeltouch.toggles.HotspotToggle

class HotspotTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        when {
            HotspotToggle.isActive -> {
                HotspotToggle.stop()
                updateTile()
            }
            HotspotToggle.hasRequiredPermission(this) -> {
                HotspotToggle.start(
                    this, "PixelTouch-Hotspot", "touch1234",
                    onStarted = { _, _ -> updateTile() },
                    onFailed = { launchSettings() }
                )
            }
            else -> launchSettings()
        }
    }

    private fun launchSettings() {
        val intent = try {
            HotspotToggle.tetherSettingsIntent()
        } catch (e: Exception) {
            HotspotToggle.wirelessSettingsIntent()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(this, 13, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTile() {
        qsTile?.apply {
            state = if (HotspotToggle.isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }
}
