package com.shihan.pixeltouch

import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.shihan.pixeltouch.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val requestLocation =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshStatus() }
    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        AppUpdateManager.checkForUpdate(this)

        binding.btnOverlay.setOnClickListener {
            openOverlayPermission()
        }
        binding.btnDnd.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
        binding.btnLocation.setOnClickListener {
            requestLocation.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
        binding.btnNotifications.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                Toast.makeText(this, "Not required on this Android version", Toast.LENGTH_SHORT).show()
            }
        }
        binding.btnStart.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Grant the overlay permission first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startForegroundService(Intent(this, OverlayService::class.java))
            setBubbleRunningPref(true)
            Toast.makeText(this, "PixelTouch bubble started — look for the floating dot", Toast.LENGTH_SHORT).show()
        }
        binding.btnStop.setOnClickListener {
            stopService(Intent(this, OverlayService::class.java))
            setBubbleRunningPref(false)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        AppUpdateManager.resumePendingInstall(this)
    }

    private fun setBubbleRunningPref(running: Boolean) {
        getSharedPreferences(BootReceiver.PREFS, MODE_PRIVATE)
            .edit()
            .putBoolean(BootReceiver.KEY_RUNNING, running)
            .apply()
    }

    private fun openOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            refreshStatus()
            return
        }

        // Android 15 may block overlay access for APKs installed outside an app store.
        // The user must approve the app's restricted settings before the switch works.
        if (Build.VERSION.SDK_INT >= 35) {
            AlertDialog.Builder(this)
                .setTitle("Allow floating bubble")
                .setMessage(
                    "Android may block this permission for sideloaded APKs. " +
                        "Open PixelTouch App Info, choose the three-dot menu, tap " +
                        "Allow restricted settings, then return and enable Display over other apps."
                )
                .setPositiveButton("Open App Info") { _, _ -> openAppInfo() }
                .setNegativeButton("Open Permission") { _, _ -> openOverlaySettings() }
                .show()
        } else {
            openOverlaySettings()
        }
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                .setData(Uri.parse("package:$packageName"))
        )
    }

    private fun openAppInfo() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:$packageName"))
        )
    }

    private fun refreshStatus() {
        val overlayGranted = Settings.canDrawOverlays(this)
        binding.statusOverlay.text = if (overlayGranted) {
            "Granted"
        } else if (Build.VERSION.SDK_INT >= 35) {
            "Required — if blocked, allow restricted settings in App Info"
        } else {
            "Required — tap below to grant"
        }

        val nm = getSystemService(NotificationManager::class.java)
        val dndGranted = nm?.isNotificationPolicyAccessGranted == true
        binding.statusDnd.text = if (dndGranted) "Granted" else "Needed for the sound toggle — tap below"

        val locationGranted = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        binding.statusLocation.text = if (locationGranted) "Granted" else "Needed for instant hotspot on/off — tap below"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notifGranted = ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            binding.statusNotifications.text = if (notifGranted) "Granted" else "Needed to keep the bubble alive — tap below"
        } else {
            binding.statusNotifications.text = "Not required on this Android version"
        }

        val secureGranted = checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
        binding.statusAdb.text = if (secureGranted)
            "Granted — Battery Saver now toggles instantly"
        else
            "Not granted — Battery Saver opens Settings instead (see README for the adb command)"
    }
}
