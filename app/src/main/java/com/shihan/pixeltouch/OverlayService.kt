package com.shihan.pixeltouch

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.DragEvent
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.VelocityTracker
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.GridLayout
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
    private var closeTargetView: View? = null
    private var menuParams: WindowManager.LayoutParams? = null
    private lateinit var bubbleParams: WindowManager.LayoutParams

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private var isNearDismissTarget = false
    private var isPointerDown = false
    private var velocityTracker: VelocityTracker? = null
    private var closeTargetHideRunnable: Runnable? = null
    private var holdToDismissRunnable: Runnable? = null

    private val touchSlop by lazy { android.view.ViewConfiguration.get(this).scaledTouchSlop }
    private val dismissTargetCenterY: Float
        get() = resources.displayMetrics.heightPixels - dp(76).toFloat()

    private val controlIds = listOf(
        R.id.action_wifi,
        R.id.action_data,
        R.id.action_sound,
        R.id.action_torch,
        R.id.action_hotspot,
        R.id.action_battery,
        R.id.action_lock,
        R.id.action_bluetooth,
        R.id.action_display,
        R.id.action_settings,
        R.id.action_app_info,
        R.id.action_stop
    )

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
        hideCloseTarget()
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
                    closeTargetHideRunnable?.let { bubbleView?.removeCallbacks(it) }
                    initialX = bubbleParams.x
                    initialY = bubbleParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    isNearDismissTarget = false
                    isPointerDown = true
                    velocityTracker?.recycle()
                    velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                    holdToDismissRunnable = Runnable {
                        if (isPointerDown) showCloseTarget()
                    }.also { view.postDelayed(it, 180) }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    velocityTracker?.addMovement(event)
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (hypot(dx.toDouble(), dy.toDouble()) > touchSlop && !isDragging) {
                        isDragging = true
                        showCloseTarget()
                    }
                    if (!isDragging) return@setOnTouchListener true

                    val desiredX = initialX + dx.toInt()
                    val desiredY = initialY + dy.toInt()
                    val targetDistance = hypot(
                        (event.rawX - resources.displayMetrics.widthPixels / 2f).toDouble(),
                        (event.rawY - dismissTargetCenterY).toDouble()
                    ).toFloat()
                    val magneticRadius = dp(82).toFloat()
                    isNearDismissTarget = targetDistance < magneticRadius
                    if (isNearDismissTarget) {
                        // A light magnetic pull makes the delete affordance feel deliberate.
                        bubbleParams.x = resources.displayMetrics.widthPixels / 2 - bubbleWidth() / 2
                        bubbleParams.y = (dismissTargetCenterY - bubbleHeight() / 2f).toInt()
                        bubbleView?.animate()?.scaleX(0.72f)?.scaleY(0.72f)?.setDuration(90)?.start()
                        closeTargetView?.animate()?.scaleX(1.12f)?.scaleY(1.12f)?.setDuration(90)?.start()
                    } else {
                        bubbleParams.x = desiredX.coerceIn(0, maxBubbleX())
                        bubbleParams.y = desiredY.coerceIn(0, maxBubbleY())
                        bubbleView?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(90)?.start()
                        closeTargetView?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(90)?.start()
                    }
                    runCatching { windowManager.updateViewLayout(bubbleView, bubbleParams) }
                    updateMenuPosition()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    isPointerDown = false
                    holdToDismissRunnable?.let { view.removeCallbacks(it) }
                    holdToDismissRunnable = null
                    velocityTracker?.addMovement(event)
                    velocityTracker?.computeCurrentVelocity(1000)
                    val velocityX = velocityTracker?.xVelocity ?: 0f
                    val shouldDismiss = isDragging && (isNearDismissTarget || isOverCloseTarget(event.rawX, event.rawY))
                    velocityTracker?.recycle()
                    velocityTracker = null
                    bubbleView?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(100)?.start()
                    hideCloseTarget()
                    if (shouldDismiss) {
                        stopSelf()
                    } else if (isDragging) {
                        snapToEdge(velocityX)
                    } else {
                        toggleMenu()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    isPointerDown = false
                    holdToDismissRunnable?.let { view.removeCallbacks(it) }
                    holdToDismissRunnable = null
                    velocityTracker?.recycle()
                    velocityTracker = null
                    bubbleView?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(100)?.start()
                    hideCloseTarget()
                    false
                }
                else -> false
            }
        }
    }

    private fun snapToEdge(velocityX: Float) {
        val screenWidth = resources.displayMetrics.widthPixels
        val bubbleWidth = bubbleWidth()
        val middle = screenWidth / 2
        val flingThreshold = dp(420)
        val targetX = when {
            velocityX > flingThreshold -> maxBubbleX()
            velocityX < -flingThreshold -> 0
            bubbleParams.x + bubbleWidth / 2 < middle -> 0
            else -> maxBubbleX()
        }
        val startX = bubbleParams.x
        ValueAnimator.ofInt(startX, targetX).apply {
            duration = (180 + (kotlin.math.abs(targetX - startX) * 0.22f).toLong()).coerceAtMost(420)
            interpolator = DecelerateInterpolator(2.2f)
            addUpdateListener {
                bubbleParams.x = it.animatedValue as Int
                runCatching { windowManager.updateViewLayout(bubbleView, bubbleParams) }
                updateMenuPosition()
            }
            start()
        }
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
        applyControlOrder(view)
        view.findViewById<SwipePager>(R.id.menu_pager).onPageChanged = { _ -> }
        view.findViewById<SwipePager>(R.id.menu_pager).setPage(0, animate = false)
        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                removeMenu()
                true
            } else {
                false
            }
        }
        windowManager.addView(view, params)
        // Reposition with the measured size, keeping the panel inside small screens too.
        view.post { updateMenuPosition() }
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

    private fun showCloseTarget() {
        closeTargetHideRunnable?.let { bubbleView?.removeCallbacks(it) }
        closeTargetView?.let {
            it.animate().cancel()
            it.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(90).start()
            return
        }
        val view = LayoutInflater.from(this).inflate(R.layout.overlay_close_target, null)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (28 * resources.displayMetrics.density).toInt()
        }
        closeTargetView = view
        view.alpha = 0f
        view.scaleX = 0.55f
        view.scaleY = 0.55f
        runCatching { windowManager.addView(view, params) }
        view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180)
            .setInterpolator(DecelerateInterpolator(2.4f)).start()
    }

    private fun hideCloseTarget() {
        val view = closeTargetView ?: return
        closeTargetHideRunnable?.let { bubbleView?.removeCallbacks(it) }
        val removal = Runnable {
            if (closeTargetView === view) {
                runCatching { windowManager.removeView(view) }
                closeTargetView = null
            }
        }
        closeTargetHideRunnable = removal
        view.animate().alpha(0f).scaleX(0.6f).scaleY(0.6f).setDuration(120).withEndAction(removal).start()
    }

    private fun isOverCloseTarget(rawX: Float, rawY: Float): Boolean {
        val density = resources.displayMetrics.density
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val centerX = screenWidth / 2f
        val centerY = screenHeight - (76 * density)
        return hypot((rawX - centerX).toDouble(), (rawY - centerY).toDouble()) < 58 * density
    }

    private fun bubbleWidth(): Int = bubbleView?.width?.takeIf { it > 0 } ?: dp(62)

    private fun bubbleHeight(): Int = bubbleView?.height?.takeIf { it > 0 } ?: dp(62)

    private fun maxBubbleX(): Int = (resources.displayMetrics.widthPixels - bubbleWidth()).coerceAtLeast(0)

    private fun maxBubbleY(): Int = (resources.displayMetrics.heightPixels - bubbleHeight()).coerceAtLeast(0)

    private fun showPage(view: View, page: Int) {
        view.findViewById<SwipePager>(R.id.menu_pager).setPage(page)
    }

    private fun applyControlOrder(view: View) {
        val controls = controlIds.associateWith { view.findViewById<View>(it) }
        val firstPage = view.findViewById<GridLayout>(R.id.menu_page_one)
        val secondPage = view.findViewById<GridLayout>(R.id.menu_page_two)
        firstPage.removeAllViews()
        secondPage.removeAllViews()

        loadControlOrder().forEachIndexed { index, controlId ->
            val tile = controls[controlId] ?: return@forEachIndexed
            val parent = if (index < 6) firstPage else secondPage
            tile.layoutParams = GridLayout.LayoutParams().apply {
                width = dp(84)
                height = dp(100)
                setMargins(dp(2), dp(2), dp(2), dp(2))
            }
            parent.addView(tile)
        }
    }

    private fun loadControlOrder(): MutableList<Int> {
        val preferences = getSharedPreferences(BootReceiver.PREFS, MODE_PRIVATE)
        if (preferences.getInt(KEY_MENU_LAYOUT_VERSION, 0) < MENU_LAYOUT_VERSION) {
            preferences.edit().putInt(KEY_MENU_LAYOUT_VERSION, MENU_LAYOUT_VERSION).apply()
            return controlIds.toMutableList()
        }
        val saved = preferences
            .getString(KEY_MENU_ORDER, null)
            ?.split(',')
            ?.mapNotNull { it.toIntOrNull() }
            ?.filter { it in controlIds }
            ?.distinct()
            ?.toMutableList()
            ?: mutableListOf()
        controlIds.filterNot { it in saved }.forEach(saved::add)
        return saved
    }

    private fun saveControlOrder(order: List<Int>) {
        getSharedPreferences(BootReceiver.PREFS, MODE_PRIVATE)
            .edit()
            .putString(KEY_MENU_ORDER, order.joinToString(","))
            .apply()
    }

    private fun setupReorderHandlers(view: View) {
        controlIds.forEach { controlId ->
            val tile = view.findViewById<View>(controlId)
            tile.setOnLongClickListener {
                val data = ClipData.newPlainText("control", controlId.toString())
                val started = it.startDragAndDrop(data, View.DragShadowBuilder(it), controlId, 0)
                if (started) {
                    it.alpha = 0.38f
                    it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
                started
            }
            tile.setOnDragListener { target, event ->
                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> event.localState is Int
                    DragEvent.ACTION_DRAG_ENTERED -> {
                        if (target.id != (event.localState as? Int)) {
                            target.animate().scaleX(1.06f).scaleY(1.06f).setDuration(90).start()
                        }
                        true
                    }
                    DragEvent.ACTION_DRAG_EXITED -> {
                        target.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
                        true
                    }
                    DragEvent.ACTION_DROP -> {
                        target.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
                        val sourceId = event.localState as? Int ?: return@setOnDragListener false
                        val targetId = target.id
                        if (sourceId != targetId) {
                            val order = loadControlOrder()
                            val sourceIndex = order.indexOf(sourceId)
                            val targetIndex = order.indexOf(targetId)
                            if (sourceIndex >= 0 && targetIndex >= 0) {
                                // Insert rather than swap: every following tile shifts one
                                // place, so the sixth tile naturally flows onto page two.
                                order.removeAt(sourceIndex)
                                val insertAt = if (sourceIndex < targetIndex) {
                                    targetIndex - 1
                                } else {
                                    targetIndex
                                }
                                order.add(insertAt, sourceId)
                                saveControlOrder(order)
                                applyControlOrder(view)
                            }
                        }
                        true
                    }
                    DragEvent.ACTION_DRAG_ENDED -> {
                        target.alpha = 1f
                        target.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
                        true
                    }
                    else -> true
                }
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun wireMenuActions(view: View) {
        updateHotspotVisual(view)

        view.findViewById<View>(R.id.action_wifi).setOnClickListener {
            if (!WifiToggle.tryDirectToggle(this)) {
                launchFromOverlay(WifiToggle.quickPanelIntent())
            }
        }
        view.findViewById<View>(R.id.action_data).setOnClickListener {
            launchFromOverlay(MobileDataToggle.quickPanelIntent())
        }
        view.findViewById<View>(R.id.action_torch).setOnClickListener {
            when (TorchToggle.toggle(this)) {
                TorchToggle.Result.ON -> Toast.makeText(this, "Torch on", Toast.LENGTH_SHORT).show()
                TorchToggle.Result.OFF -> Toast.makeText(this, "Torch off", Toast.LENGTH_SHORT).show()
                TorchToggle.Result.PERMISSION_REQUIRED -> {
                    Toast.makeText(this, "Grant Camera permission to use Torch", Toast.LENGTH_SHORT).show()
                    launchFromOverlay(Intent(this, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_REQUEST_CAMERA, true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                TorchToggle.Result.UNAVAILABLE -> Toast.makeText(this, "Torch is unavailable on this device", Toast.LENGTH_SHORT).show()
            }
        }
        view.findViewById<View>(R.id.action_sound).setOnClickListener {
            val result = SoundToggle.cycleRingerMode(this)
            if (result == "no_access") {
                Toast.makeText(this, "Grant Do Not Disturb access first (opening settings)", Toast.LENGTH_LONG).show()
                launchFromOverlay(SoundToggle.requestDndAccessIntent())
            } else {
                Toast.makeText(this, "Sound: $result", Toast.LENGTH_SHORT).show()
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
        view.findViewById<View>(R.id.action_lock).setOnClickListener { lockScreen() }
        view.findViewById<View>(R.id.action_bluetooth).setOnClickListener {
            launchFromOverlay(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        view.findViewById<View>(R.id.action_display).setOnClickListener {
            launchFromOverlay(Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        view.findViewById<View>(R.id.action_settings).setOnClickListener {
            launchFromOverlay(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        view.findViewById<View>(R.id.action_app_info).setOnClickListener {
            launchFromOverlay(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(android.net.Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        setupReorderHandlers(view)
    }

    private fun lockScreen() {
        if (ScreenLockAccessibilityService.lockScreen()) {
            removeMenu()
            return
        }
        val admin = ComponentName(this, ScreenLockAdminReceiver::class.java)
        val policyManager = getSystemService(DevicePolicyManager::class.java)
        if (policyManager.isAdminActive(admin) && runCatching { policyManager.lockNow() }.isSuccess) {
            removeMenu()
            return
        }
        removeMenu()
        Toast.makeText(
            this,
            "Turn on PixelTouch in Accessibility to use Lock screen",
            Toast.LENGTH_LONG
        ).show()
        launchFromOverlay(ScreenLockAccessibilityService.settingsIntent())
    }

    private fun updateHotspotVisual(view: View) {
        view.findViewById<View>(R.id.action_hotspot).setBackgroundResource(
            if (HotspotToggle.isActive) R.drawable.bg_menu_tile_accent else R.drawable.bg_menu_tile
        )
    }

    private fun menuX(): Int {
        val screenWidth = resources.displayMetrics.widthPixels
        val menuWidth = menuView?.width?.takeIf { it > 0 } ?: dp(286)
        val proposed = bubbleParams.x - (menuWidth / 2) + ((bubbleView?.width ?: 62) / 2)
        return proposed.coerceIn(8, (screenWidth - menuWidth - 8).coerceAtLeast(8))
    }

    private fun menuY(): Int {
        val screenHeight = resources.displayMetrics.heightPixels
        val bubbleHeight = bubbleView?.height?.takeIf { it > 0 } ?: 150
        val menuHeight = menuView?.height?.takeIf { it > 0 } ?: dp(228)
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
        private const val KEY_MENU_ORDER = "menu_control_order"
        private const val KEY_MENU_LAYOUT_VERSION = "menu_layout_version"
        private const val MENU_LAYOUT_VERSION = 4
    }
}
