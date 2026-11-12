package app.linkshelf.data

import android.content.Context
import androidx.core.content.edit

/** Small key-value settings backed by SharedPreferences. */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Whether the user wants the floating save button running. */
    var bubbleEnabled: Boolean
        get() = prefs.getBoolean(KEY_BUBBLE, false)
        set(value) = prefs.edit { putBoolean(KEY_BUBBLE, value) }

    private companion object {
        const val KEY_BUBBLE = "bubble_enabled"
    }
}
