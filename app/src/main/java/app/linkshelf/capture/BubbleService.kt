package app.linkshelf.capture

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import app.linkshelf.R
import app.linkshelf.data.SettingsStore
import app.linkshelf.linkShelf
import app.linkshelf.ui.MainActivity
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Draws the floating save button over other apps.
 * Tap: save the reel/post on screen (auto-grab) or the copied link.
 * Long-press: open LinkShelf. Drag: move it; drop it on the ✕ at the bottom to turn it off.
 */
class BubbleService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var windowManager: WindowManager
    private var bubble: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var closeTarget: View? = null
    private var closeTargetParams: WindowManager.LayoutParams? = null

    private val density get() = resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        linkShelf.settings.registerListener(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            linkShelf.settings.bubbleEnabled = false
            stopSelf()
            return START_NOT_STICKY
        }
        startAsForeground()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (bubble == null) addBubble()
        return START_STICKY
    }

    override fun onDestroy() {
        linkShelf.settings.unregisterListener(this)
        hideCloseTarget()
        bubble?.let { runCatching { windowManager.removeView(it) } }
        bubble = null
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        if (key == SettingsStore.KEY_BUBBLE_OPACITY) bubble?.alpha = linkShelf.settings.bubbleOpacity
    }

    private fun startAsForeground() {
        val notification = Notifications.bubbleNotification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                Notifications.BUBBLE_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(Notifications.BUBBLE_NOTIFICATION_ID, notification)
        }
    }

    private fun screenSize(): Point =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            Point(bounds.width(), bounds.height())
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            Point(metrics.widthPixels, metrics.heightPixels)
        }

    private fun overlayParams(size: Int, extraFlags: Int = 0) = WindowManager.LayoutParams(
        size,
        size,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or extraFlags,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    @SuppressLint("ClickableViewAccessibility")
    private fun addBubble() {
        val size = dp(BUBBLE_DP)
        val view = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    ContextCompat.getColor(context, R.color.accent),
                    ContextCompat.getColor(context, R.color.accent_pink),
                ),
            ).apply { shape = GradientDrawable.OVAL }
            elevation = 8 * density
            alpha = linkShelf.settings.bubbleOpacity
            contentDescription = "Save this link"
            val pad = dp(15)
            addView(
                ImageView(context).apply {
                    setImageResource(R.drawable.ic_bookmark)
                    setPadding(pad, pad, pad, pad)
                },
                FrameLayout.LayoutParams(size, size),
            )
        }
        val screen = screenSize()
        val params = overlayParams(size).apply {
            x = screen.x - size - dp(8)
            y = screen.y / 3
        }
        view.setOnTouchListener(BubbleTouchListener(params, size))
        windowManager.addView(view, params)
        bubble = view
        bubbleParams = params
    }

    // ---- Close target ("drag here to turn off") ----

    private fun showCloseTarget() {
        if (closeTarget != null) return
        val size = dp(TARGET_DP)
        val view = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xCC1A1F2B.toInt())
                setStroke(dp(2), 0x66FFFFFF)
            }
            val pad = dp(18)
            addView(
                ImageView(context).apply {
                    setImageResource(R.drawable.ic_close)
                    setPadding(pad, pad, pad, pad)
                },
                FrameLayout.LayoutParams(size, size),
            )
            alpha = 0f
            animate().alpha(1f).setDuration(150).start()
        }
        val screen = screenSize()
        val params = overlayParams(size, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE).apply {
            x = (screen.x - size) / 2
            y = screen.y - size - dp(TARGET_BOTTOM_MARGIN_DP)
        }
        windowManager.addView(view, params)
        closeTarget = view
        closeTargetParams = params
    }

    private fun hideCloseTarget() {
        closeTarget?.let { runCatching { windowManager.removeView(it) } }
        closeTarget = null
        closeTargetParams = null
    }

    /** Center of the close target, or null when it is not shown. */
    private fun closeTargetCenter(): Point? {
        val params = closeTargetParams ?: return null
        val half = dp(TARGET_DP) / 2
        return Point(params.x + half, params.y + half)
    }

    private fun turnOff() {
        hideCloseTarget()
        linkShelf.settings.bubbleEnabled = false
        Toast.makeText(applicationContext, "Floating button turned off", Toast.LENGTH_SHORT).show()
        stopSelf()
    }

    // ---- Actions ----

    private fun onBubbleTap() {
        bubble?.animate()?.scaleX(0.85f)?.scaleY(0.85f)?.setDuration(80)?.withEndAction {
            bubble?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(80)?.start()
        }?.start()

        val grabber = AutoGrabService.instance
        if (grabber != null) {
            // Hide the bubble while auto-grab taps through the other app.
            setBubbleHidden(true)
            grabber.grab { setBubbleHidden(false) }
        } else {
            startActivity(
                Intent(this, ClipboardCaptureActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
            )
        }
    }

    private fun setBubbleHidden(hidden: Boolean) {
        val view = bubble ?: return
        val params = bubbleParams ?: return
        params.flags = if (hidden) {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        view.alpha = if (hidden) 0f else linkShelf.settings.bubbleOpacity
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun onBubbleLongPress() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        )
    }

    /** Tap, long-press, drag with edge snap, and drop-on-✕ to turn off. */
    private inner class BubbleTouchListener(
        private val params: WindowManager.LayoutParams,
        private val size: Int,
    ) : View.OnTouchListener {

        private val touchSlop = ViewConfiguration.get(this@BubbleService).scaledTouchSlop
        private val longPressMs = ViewConfiguration.getLongPressTimeout().toLong()
        private var startX = 0
        private var startY = 0
        private var downRawX = 0f
        private var downRawY = 0f
        private var downTime = 0L
        private var dragging = false
        private var overTarget = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    downRawX = event.rawX
                    downRawY = event.rawY
                    downTime = System.currentTimeMillis()
                    dragging = false
                    overTarget = false
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        dragging = true
                        showCloseTarget()
                    }
                    if (dragging) moveBubble(v, startX + dx.toInt(), startY + dy.toInt())
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    when {
                        dragging && overTarget -> turnOff()
                        dragging -> {
                            hideCloseTarget()
                            snapToEdge(v)
                        }
                        event.actionMasked == MotionEvent.ACTION_CANCEL -> Unit
                        System.currentTimeMillis() - downTime >= longPressMs -> onBubbleLongPress()
                        else -> onBubbleTap()
                    }
                    dragging = false
                }
            }
            return true
        }

        private fun moveBubble(v: View, x: Int, y: Int) {
            val target = closeTargetCenter()
            val half = size / 2
            val near = target != null &&
                hypot((x + half - target.x).toFloat(), (y + half - target.y).toFloat()) < dp(MAGNET_DP)
            if (near && target != null) {
                // Pull the bubble onto the ✕ so it is clear that letting go will turn it off.
                params.x = target.x - half
                params.y = target.y - half
            } else {
                params.x = x
                params.y = y
            }
            if (near != overTarget) {
                overTarget = near
                closeTarget?.animate()?.scaleX(if (near) 1.25f else 1f)?.scaleY(if (near) 1.25f else 1f)
                    ?.setDuration(120)?.start()
                if (near) v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
            windowManager.updateViewLayout(v, params)
        }

        private fun snapToEdge(v: View) {
            val screen = screenSize()
            val margin = dp(8)
            params.x = if (params.x + size / 2 < screen.x / 2) margin else screen.x - size - margin
            params.y = params.y.coerceIn(margin, screen.y - size - margin)
            windowManager.updateViewLayout(v, params)
        }
    }

    companion object {
        const val ACTION_STOP = "app.linkshelf.action.STOP_BUBBLE"
        private const val BUBBLE_DP = 56
        private const val TARGET_DP = 64
        private const val TARGET_BOTTOM_MARGIN_DP = 110
        private const val MAGNET_DP = 80

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, BubbleService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BubbleService::class.java))
        }
    }
}
