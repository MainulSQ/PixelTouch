package com.shihan.pixeltouch.tiles

import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.shihan.pixeltouch.toggles.SoundToggle

class SoundTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val result = SoundToggle.cycleRingerMode(this)
        if (result == "no_access") {
            val intent = SoundToggle.requestDndAccessIntent()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pi = PendingIntent.getActivity(this, 12, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
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
            label = "Sound: ${SoundToggle.currentModeLabel(this@SoundTileService)}"
            state = Tile.STATE_ACTIVE
            updateTile()
        }
    }
}
