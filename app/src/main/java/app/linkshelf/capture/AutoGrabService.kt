package app.linkshelf.capture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import kotlin.math.abs

/**
 * One-tap save. When the user taps the floating button, this service presses
 * "Share" and then "Copy link" in the app on screen (Instagram, YouTube, Facebook…),
 * and hands over to [ClipboardCaptureActivity] to save the copied link.
 *
 * It only acts when the user taps the button. It does not watch or record anything else.
 */
class AutoGrabService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var busy = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    /** Runs Share → Copy link → save. [onFinished] is always called on the main thread. */
    fun grab(onFinished: () -> Unit) {
        if (busy) return
        busy = true
        val startedAt = System.currentTimeMillis()

        val root = findAppRoot()
        val share = root?.let { findBest(it, ::isShareLabel) }
        if (share == null) {
            finish(onFinished, "No Share button found here. Open a reel or post, or use Share → Save to LinkShelf.")
            return
        }
        click(share)
        waitForCopyButton(attempt = 0, startedAt = startedAt, onFinished = onFinished)
    }

    private fun waitForCopyButton(attempt: Int, startedAt: Long, onFinished: () -> Unit) {
        handler.postDelayed({
            val copy = allRoots().firstNotNullOfOrNull { findBest(it, ::isCopyLabel) }
            when {
                copy != null -> {
                    click(copy)
                    handler.postDelayed({
                        startActivity(
                            Intent(this, ClipboardCaptureActivity::class.java)
                                .putExtra(ClipboardCaptureActivity.EXTRA_COPIED_AFTER, startedAt)
                                .addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                                )
                        )
                        finish(onFinished, null)
                    }, COPY_SETTLE_MS)
                }
                attempt < MAX_ATTEMPTS -> waitForCopyButton(attempt + 1, startedAt, onFinished)
                else -> {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    finish(onFinished, "\"Copy link\" not found. Use Share → Save to LinkShelf instead.")
                }
            }
        }, POLL_MS)
    }

    private fun finish(onFinished: () -> Unit, message: String?) {
        busy = false
        message?.let { Toast.makeText(applicationContext, it, Toast.LENGTH_LONG).show() }
        onFinished()
    }

    /** Root of the app the user is looking at (never LinkShelf itself or system UI). */
    private fun findAppRoot(): AccessibilityNodeInfo? {
        val appWindows = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        val ordered = appWindows.sortedByDescending { (if (it.isActive) 2 else 0) + (if (it.isFocused) 1 else 0) }
        for (window in ordered) {
            val root = window.root ?: continue
            if (root.packageName?.toString() != packageName) return root
        }
        return rootInActiveWindow?.takeIf { it.packageName?.toString() != packageName }
    }

    private fun allRoots(): List<AccessibilityNodeInfo> =
        (windows.mapNotNull { it.root } + listOfNotNull(rootInActiveWindow))
            .filter { it.packageName?.toString() != packageName }

    /** Visible node whose label matches, closest to the middle of the screen. */
    private fun findBest(root: AccessibilityNodeInfo, matches: (String) -> Boolean): AccessibilityNodeInfo? {
        val screenMiddle = resources.displayMetrics.heightPixels / 2
        val bounds = Rect()
        var best: AccessibilityNodeInfo? = null
        var bestDistance = Int.MAX_VALUE
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++
            if (node.isVisibleToUser && labelsOf(node).any(matches)) {
                node.getBoundsInScreen(bounds)
                val distance = abs(bounds.centerY() - screenMiddle)
                if (!bounds.isEmpty && distance < bestDistance) {
                    best = node
                    bestDistance = distance
                }
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return best
    }

    private fun labelsOf(node: AccessibilityNodeInfo): List<String> =
        listOfNotNull(node.contentDescription, node.text)
            .map { it.toString().trim().lowercase() }
            .filter { it.isNotEmpty() && it.length < 40 }

    private fun isShareLabel(label: String): Boolean =
        label == "send" || label.startsWith("share") || label.startsWith("send post") ||
            label.startsWith("send reel")

    private fun isCopyLabel(label: String): Boolean =
        label == "copy" || label.startsWith("copy link") || label == "copy url"

    /** Clicks the node (or its nearest clickable parent); falls back to a tap gesture. */
    private fun click(node: AccessibilityNodeInfo) {
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isClickable) target = target.parent
        if (target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return

        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val path = Path().apply { moveTo(bounds.exactCenterX(), bounds.exactCenterY()) }
        dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
                .build(),
            null,
            null,
        )
    }

    companion object {
        private const val POLL_MS = 250L
        private const val MAX_ATTEMPTS = 12
        private const val COPY_SETTLE_MS = 450L
        private const val MAX_NODES = 4000

        /** Set while the user has the service turned on in Accessibility settings. */
        @Volatile
        var instance: AutoGrabService? = null
            private set

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val me = ComponentName(context, AutoGrabService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
