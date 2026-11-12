package app.linkshelf.capture

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import app.linkshelf.linkShelf
import kotlinx.coroutines.launch

/**
 * Receives "Share → Save to LinkShelf" from other apps.
 * Saves silently, shows a short toast and closes, so the user stays in the app they were using.
 */
class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = when (intent?.action) {
            Intent.ACTION_SEND -> listOfNotNull(
                intent.getStringExtra(Intent.EXTRA_TEXT),
                intent.getStringExtra(Intent.EXTRA_SUBJECT),
            ).joinToString(" ")
            Intent.ACTION_PROCESS_TEXT ->
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> null
        }
        lifecycleScope.launch {
            val result = linkShelf.repository.saveFromText(text)
            Toast.makeText(applicationContext, result.message, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
