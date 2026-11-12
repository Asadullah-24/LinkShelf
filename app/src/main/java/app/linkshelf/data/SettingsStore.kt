package app.linkshelf.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Small key-value settings backed by SharedPreferences. */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Whether the user wants the floating save button running. */
    var bubbleEnabled: Boolean
        get() = prefs.getBoolean(KEY_BUBBLE, false)
        set(value) = prefs.edit { putBoolean(KEY_BUBBLE, value) }

    /** Floating button opacity, 0.3 (faint) to 1.0 (solid). */
    var bubbleOpacity: Float
        get() = prefs.getFloat(KEY_BUBBLE_OPACITY, 1f)
        set(value) = prefs.edit { putFloat(KEY_BUBBLE_OPACITY, value.coerceIn(MIN_OPACITY, 1f)) }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    companion object {
        const val KEY_BUBBLE = "bubble_enabled"
        const val KEY_BUBBLE_OPACITY = "bubble_opacity"
        const val MIN_OPACITY = 0.3f
    }
}
