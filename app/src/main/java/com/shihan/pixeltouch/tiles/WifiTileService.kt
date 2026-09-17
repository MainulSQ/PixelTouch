package com.shihan.pixeltouch.tiles

import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.shihan.pixeltouch.toggles.WifiToggle

/** Optional entry point: add this tile from the Quick Settings "Edit" drawer, no overlay bubble needed. */
class WifiTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (!WifiToggle.tryDirectToggle(this)) {
            launchPanel()
        }
        updateTile()
    }

    private fun launchPanel() {
        val intent = WifiToggle.quickPanelIntent()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(this, 10, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTile() {
        qsTile?.apply {
            state = if (WifiToggle.isEnabled(this@WifiTileService)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }
}
