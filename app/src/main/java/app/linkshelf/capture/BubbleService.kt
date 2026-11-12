package app.linkshelf.capture

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import app.linkshelf.R
import app.linkshelf.linkShelf
import app.linkshelf.ui.MainActivity
import kotlin.math.abs

/**
 * Draws the floating save button over other apps.
 * Tap: save the copied link. Long-press: open LinkShelf. Drag: move it; it snaps to the nearest edge.
 */
class BubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubble: View? = null
    private var params: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
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
        bubble?.let { runCatching { windowManager.removeView(it) } }
        bubble = null
        super.onDestroy()
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

    @SuppressLint("ClickableViewAccessibility")
    private fun addBubble() {
        val density = resources.displayMetrics.density
        val sizePx = (56 * density).toInt()
        val iconPadding = (15 * density).toInt()

        val view = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    ContextCompat.getColor(context, R.color.accent),
                    ContextCompat.getColor(context, R.color.accent_pink),
                ),
            ).apply { shape = GradientDrawable.OVAL }
            elevation = 8 * density
            contentDescription = "Save copied link"
            addView(
                ImageView(context).apply {
                    setImageResource(R.drawable.ic_bookmark)
                    setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
                },
                FrameLayout.LayoutParams(sizePx, sizePx),
            )
        }

        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val lp = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth - sizePx - (8 * density).toInt()
            y = screenHeight / 3
        }

        view.setOnTouchListener(DragTouchListener(lp, sizePx))
        windowManager.addView(view, lp)
        bubble = view
        params = lp
    }

    private fun onBubbleTap() {
        bubble?.animate()?.scaleX(0.85f)?.scaleY(0.85f)?.setDuration(80)?.withEndAction {
            bubble?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(80)?.start()
        }?.start()
        startActivity(
            Intent(this, ClipboardCaptureActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
        )
    }

    private fun onBubbleLongPress() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        )
    }

    /** Handles tap, long-press and drag-with-edge-snap for the bubble. */
    private inner class DragTouchListener(
        private val lp: WindowManager.LayoutParams,
        private val sizePx: Int,
    ) : View.OnTouchListener {

        private val touchSlop = ViewConfiguration.get(this@BubbleService).scaledTouchSlop
        private val longPressMs = ViewConfiguration.getLongPressTimeout().toLong()
        private var startX = 0
        private var startY = 0
        private var downRawX = 0f
        private var downRawY = 0f
        private var downTime = 0L
        private var dragging = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x
                    startY = lp.y
                    downRawX = event.rawX
                    downRawY = event.rawY
                    downTime = System.currentTimeMillis()
                    dragging = false
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) dragging = true
                    if (dragging) {
                        lp.x = startX + dx.toInt()
                        lp.y = startY + dy.toInt()
                        windowManager.updateViewLayout(v, lp)
                    }
                }

                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        snapToEdge(v)
                    } else if (System.currentTimeMillis() - downTime >= longPressMs) {
                        onBubbleLongPress()
                    } else {
                        onBubbleTap()
                    }
                }
            }
            return true
        }

        private fun snapToEdge(v: View) {
            val width = resources.displayMetrics.widthPixels
            val height = resources.displayMetrics.heightPixels
            val margin = (8 * resources.displayMetrics.density).toInt()
            lp.x = if (lp.x + sizePx / 2 < width / 2) margin else width - sizePx - margin
            lp.y = lp.y.coerceIn(margin, height - sizePx - margin)
            windowManager.updateViewLayout(v, lp)
        }
    }

    companion object {
        const val ACTION_STOP = "app.linkshelf.action.STOP_BUBBLE"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, BubbleService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BubbleService::class.java))
        }
    }
}
