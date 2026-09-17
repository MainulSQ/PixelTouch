package com.shihan.pixeltouch

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/** Starts the Device Admin approval screen from an Activity, as Android requires. */
class ScreenLockSetupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val admin = ComponentName(this, ScreenLockAdminReceiver::class.java)
        val policyManager = getSystemService(DevicePolicyManager::class.java)
        if (policyManager.isAdminActive(admin)) {
            finish()
            return
        }
        startActivityForResult(
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                .putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "PixelTouch needs this permission only to lock the screen from its floating menu."
                ),
            REQUEST_ENABLE_SCREEN_LOCK
        )
    }

    @Deprecated("Replaced by Activity Result APIs; retained for this one-screen system flow.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_SCREEN_LOCK) finish()
    }

    companion object {
        private const val REQUEST_ENABLE_SCREEN_LOCK = 310
    }
}
