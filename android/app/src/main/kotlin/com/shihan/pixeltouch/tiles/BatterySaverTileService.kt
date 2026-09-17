package com.shihan.pixeltouch.tiles

import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.shihan.pixeltouch.toggles.BatterySaverToggle

class BatterySaverTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (!BatterySaverToggle.tryDirectToggle(this)) {
            val intent = BatterySaverToggle.settingsIntent()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pi = PendingIntent.getActivity(this, 14, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                startActivityAndCollapse(pi)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
        updateTile()
    }

    private fun updateTile() {
        qsTile?.apply {
            state = if (BatterySaverToggle.isEnabled(this@BatterySaverTileService)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }
}
