package app.linkshelf.capture

import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import app.linkshelf.linkShelf
import kotlinx.coroutines.launch

/**
 * Invisible screen opened by the floating button and the Quick Settings tile.
 * Android 10+ only lets the focused app read the clipboard, so this activity
 * waits for window focus, reads the copied text, saves any links and closes.
 */
class ClipboardCaptureActivity : ComponentActivity() {

    private var handled = false

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
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
        val clipboard = getSystemService(ClipboardManager::class.java)
        val clip = clipboard?.primaryClip
        val text = if (clip != null && clip.itemCount > 0) {
            clip.getItemAt(0).coerceToText(this)?.toString()
        } else {
            null
        }
        lifecycleScope.launch {
            val result = linkShelf.repository.saveFromText(text)
            val message = if (result.found == 0) {
                "No link copied. Tap \"Copy link\" first."
            } else {
                result.message
            }
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
