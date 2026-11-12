package app.linkshelf.capture

import android.content.ClipboardManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import app.linkshelf.linkShelf
import kotlinx.coroutines.launch

/**
 * Invisible screen opened by the floating button, the Quick Settings tile and auto-grab.
 * Android 10+ only lets the focused app read the clipboard, so this activity
 * waits for window focus, reads the copied text, saves any links and closes.
 */
class ClipboardCaptureActivity : ComponentActivity() {

    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Safety net: if focus never arrives, try anyway and close instead of hanging.
        window.decorView.postDelayed({
            if (!handled) {
                handled = true
                captureClipboard()
            }
        }, 1500)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !handled) {
            handled = true
            captureClipboard()
        }
    }

    private fun captureClipboard() {
        // When auto-grab opened us, only accept a clip copied after it started,
        // so an old clipboard entry is never saved by mistake.
        val copiedAfter = intent.getLongExtra(EXTRA_COPIED_AFTER, 0L)
        val clipboard = getSystemService(ClipboardManager::class.java)
        val clip = clipboard?.primaryClip
        val isFresh = copiedAfter == 0L ||
            (clip?.description?.timestamp ?: 0L) >= copiedAfter - CLOCK_TOLERANCE_MS
        val text = if (clip != null && clip.itemCount > 0 && isFresh) {
            clip.getItemAt(0).coerceToText(this)?.toString()
        } else {
            null
        }

        lifecycleScope.launch {
            val result = linkShelf.repository.saveFromText(text)
            val message = when {
                result.found > 0 -> result.message
                copiedAfter != 0L -> "Couldn't copy the link. Try again or use Share → Save to LinkShelf."
                else -> "No link copied. Tap \"Copy link\" first."
            }
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    companion object {
        const val EXTRA_COPIED_AFTER = "copied_after"
        private const val CLOCK_TOLERANCE_MS = 1_000L
    }
}
