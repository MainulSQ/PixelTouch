package com.shihan.pixeltouch

import android.accessibilityservice.AccessibilityService
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.provider.MediaStore
import android.view.Display
import android.view.accessibility.AccessibilityEvent

/**
 * Locks the screen the same way the power button does, so fingerprint and face
 * unlock keep working. It reads no screen content; it only exists for lock.
 */
class ScreenLockAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    companion object {
        private var instance: ScreenLockAccessibilityService? = null

        /** Returns false when the service is off or this Android version can't lock. */
        fun lockScreen(): Boolean {
            val service = instance ?: return false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
            return service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        }

        /** Captures one full-screen image through the user-enabled Accessibility service. */
        fun takeScreenshot(context: Context, onResult: (Boolean) -> Unit): Boolean {
            val service = instance ?: return false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                service.mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        val saved = runCatching {
                            val hardwareBitmap = Bitmap.wrapHardwareBuffer(
                                screenshot.hardwareBuffer,
                                screenshot.colorSpace
                            ) ?: return@runCatching false
                            val bitmap = hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
                            screenshot.hardwareBuffer.close()
                            saveScreenshot(context, bitmap)
                        }.getOrDefault(false)
                        onResult(saved)
                    }

                    override fun onFailure(errorCode: Int) = onResult(false)
                }
            )
            return true
        }

        private fun saveScreenshot(context: Context, bitmap: Bitmap): Boolean {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "PixelTouch-${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PixelTouch")
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            return runCatching {
                resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } == true
            }.getOrElse {
                resolver.delete(uri, null, null)
                false
            }
        }

        fun settingsIntent(): Intent =
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
