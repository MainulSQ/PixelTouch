package com.shihan.pixeltouch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.shihan.pixeltouch.toggles.BatterySaverToggle
import com.shihan.pixeltouch.toggles.HotspotToggle
import com.shihan.pixeltouch.toggles.MobileDataToggle
import com.shihan.pixeltouch.toggles.SoundToggle
import com.shihan.pixeltouch.toggles.WifiToggle
import kotlin.math.hypot

/**
 * Draws the floating AssistiveTouch-style bubble and its expandable quick
 * toggle menu. Kept deliberately simple: two small overlay windows (bubble +
 * menu) rather than one complex animated view, so it's easy to reskin.
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var menuView: View? = null
    private var menuParams: WindowManager.LayoutParams? = null
    private lateinit var bubbleParams: WindowManager.LayoutParams

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        addBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            getSharedPreferences(BootReceiver.PREFS, MODE_PRIVATE)
                .edit().putBoolean(BootReceiver.KEY_RUNNING, false).apply()
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        removeMenu()
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        HotspotToggle.stop()
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun addBubble() {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_bubble, null)
        bubbleView = view

        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 300
        }

        windowManager.addView(view, bubbleParams)

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = bubbleParams.x
                    initialY = bubbleParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (hypot(dx.toDouble(), dy.toDouble()) > 12) isDragging = true
                    bubbleParams.x = initialX + dx.toInt()
                    bubbleParams.y = initialY + dy.toInt()
                    runCatching { windowManager.updateViewLayout(bubbleView, bubbleParams) }
                    updateMenuPosition()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) snapToEdge() else toggleMenu()
                    true
                }
                else -> false
            }
        }
    }

    private fun snapToEdge() {
        val screenWidth = resources.displayMetrics.widthPixels
        val bubbleWidth = bubbleView?.width?.takeIf { it > 0 } ?: 150
        val middle = screenWidth / 2
        bubbleParams.x = if (bubbleParams.x + bubbleWidth / 2 < middle) 0 else screenWidth - bubbleWidth
        runCatching { windowManager.updateViewLayout(bubbleView, bubbleParams) }
        updateMenuPosition()
    }

    private fun toggleMenu() {
        if (menuView != null) removeMenu() else showMenu()
    }

    private fun showMenu() {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_menu, null)
        menuView = view

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = menuX()
            y = menuY()
        }

        menuParams = params
        wireMenuActions(view)
        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                removeMenu()
                true
            } else {
                false
            }
        }
        windowManager.addView(view, params)
    }

    private fun removeMenu() {
        menuView?.let { runCatching { windowManager.removeView(it) } }
        menuView = null
        menuParams = null
    }

    private fun updateMenuPosition() {
        val view = menuView ?: return
        val params = menuParams ?: return
        params.x = menuX()
        params.y = menuY()
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun wireMenuActions(view: View) {
        updateSoundStatus(view)
        updateHotspotVisual(view)

        view.findViewById<View>(R.id.action_wifi).setOnClickListener {
            if (!WifiToggle.tryDirectToggle(this)) {
                launchFromOverlay(WifiToggle.quickPanelIntent())
            }
        }
        view.findViewById<View>(R.id.action_wifi).setOnLongClickListener {
            launchFromOverlay(WifiToggle.quickPanelIntent())
            true
        }
        view.findViewById<View>(R.id.action_data).setOnClickListener {
            launchFromOverlay(MobileDataToggle.quickPanelIntent())
        }
        view.findViewById<View>(R.id.action_data).setOnLongClickListener {
            launchFromOverlay(MobileDataToggle.quickPanelIntent())
            true
        }
        view.findViewById<View>(R.id.action_volume_down).setOnClickListener {
            SoundToggle.adjustMediaVolume(this, AudioManager.ADJUST_LOWER)
        }
        view.findViewById<View>(R.id.action_volume_up).setOnClickListener {
            SoundToggle.adjustMediaVolume(this, AudioManager.ADJUST_RAISE)
        }
        view.findViewById<View>(R.id.action_sound).setOnClickListener {
            val result = SoundToggle.cycleRingerMode(this)
            if (result == "no_access") {
                Toast.makeText(this, "Grant Do Not Disturb access first (opening settings)", Toast.LENGTH_LONG).show()
                launchFromOverlay(SoundToggle.requestDndAccessIntent())
            } else {
                Toast.makeText(this, "Sound: $result", Toast.LENGTH_SHORT).show()
                updateSoundStatus(view)
            }
        }
        view.findViewById<View>(R.id.action_hotspot).setOnClickListener {
            if (HotspotToggle.isActive) {
                HotspotToggle.stop()
                updateHotspotVisual(view)
                Toast.makeText(this, "Hotspot stopped", Toast.LENGTH_SHORT).show()
            } else {
                HotspotToggle.start(
                    this, "PixelTouch-Hotspot", "touch1234",
                    onStarted = { ssid, _ ->
                        updateHotspotVisual(view)
                        Toast.makeText(this, "Hotspot on: $ssid", Toast.LENGTH_LONG).show()
                    },
                    onFailed = { reason ->
                        updateHotspotVisual(view)
                        if (reason == "permission") {
                            Toast.makeText(this, "Location permission needed for hotspot", Toast.LENGTH_LONG).show()
                        }
                        launchHotspotSettings()
                    }
                )
            }
        }
        view.findViewById<View>(R.id.action_battery).setOnClickListener {
            if (BatterySaverToggle.tryDirectToggle(this)) {
                Toast.makeText(this, "Battery saver toggled", Toast.LENGTH_SHORT).show()
            } else {
                launchFromOverlay(BatterySaverToggle.settingsIntent())
            }
        }
        view.findViewById<View>(R.id.action_stop).setOnClickListener {
            getSharedPreferences(BootReceiver.PREFS, MODE_PRIVATE)
                .edit().putBoolean(BootReceiver.KEY_RUNNING, false).apply()
            stopSelf()
        }
        view.findViewById<View>(R.id.action_close).setOnClickListener {
            removeMenu()
        }
    }

    private fun updateSoundStatus(view: View) {
        view.findViewById<TextView>(R.id.status_sound).text =
            "Sound: ${SoundToggle.currentModeLabel(this)}"
    }

    private fun updateHotspotVisual(view: View) {
        view.findViewById<View>(R.id.action_hotspot).setBackgroundResource(
            if (HotspotToggle.isActive) R.drawable.bg_menu_tile_accent else R.drawable.bg_menu_tile
        )
    }

    private fun menuX(): Int {
        val screenWidth = resources.displayMetrics.widthPixels
        val menuWidth = (198 * resources.displayMetrics.density).toInt()
        val proposed = bubbleParams.x - (menuWidth / 2) + ((bubbleView?.width ?: 62) / 2)
        return proposed.coerceIn(8, (screenWidth - menuWidth - 8).coerceAtLeast(8))
    }

    private fun menuY(): Int {
        val screenHeight = resources.displayMetrics.heightPixels
        val bubbleHeight = bubbleView?.height?.takeIf { it > 0 } ?: 150
        val menuHeight = (260 * resources.displayMetrics.density).toInt()
        val below = bubbleParams.y + bubbleHeight + 14
        return if (below + menuHeight < screenHeight) {
            below
        } else {
            (bubbleParams.y - menuHeight - 14).coerceAtLeast(8)
        }
    }

    private fun launchHotspotSettings() {
        try {
            startActivity(HotspotToggle.tetherSettingsIntent())
        } catch (e: Exception) {
            launchFromOverlay(HotspotToggle.wirelessSettingsIntent())
        }
    }

    private fun launchFromOverlay(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't open that settings screen", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startForegroundNotification() {
        val channelId = "pixeltouch_overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "PixelTouch", NotificationManager.IMPORTANCE_MIN)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val stopIntent = Intent(this, OverlayService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("PixelTouch is running")
            .setContentText("Tap the floating bubble for quick toggles")
            .setSmallIcon(R.drawable.ic_bubble)
            .addAction(0, "Stop", stopPending)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    companion object {
        const val ACTION_STOP = "com.shihan.pixeltouch.STOP"
    }
}
