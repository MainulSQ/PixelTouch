package com.shihan.pixeltouch

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.core.content.ContextCompat

/** Small, stateful wrapper around the camera flash so the overlay can toggle it instantly. */
object TorchToggle {
    enum class Result { ON, OFF, PERMISSION_REQUIRED, UNAVAILABLE }

    private var isEnabled = false

    fun toggle(context: Context): Result {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) return Result.PERMISSION_REQUIRED

        return try {
            val manager = context.getSystemService(CameraManager::class.java)
            val cameraId = manager.cameraIdList.firstOrNull {
                manager.getCameraCharacteristics(it)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return Result.UNAVAILABLE
            isEnabled = !isEnabled
            manager.setTorchMode(cameraId, isEnabled)
            if (isEnabled) Result.ON else Result.OFF
        } catch (_: Exception) {
            Result.UNAVAILABLE
        }
    }
}
