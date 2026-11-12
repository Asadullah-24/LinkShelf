package app.linkshelf.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import app.linkshelf.domain.Platform

val Accent = Color(0xFF7C5CFF)
val AccentPink = Color(0xFFFF5CA8)
val Background = Color(0xFF0B0D12)
val Panel = Color(0xFF141821)
val Panel2 = Color(0xFF1A1F2B)
val Line = Color(0xFF252C3A)
val TextMain = Color(0xFFE8ECF3)
val TextMuted = Color(0xFF8B95A7)
val Danger = Color(0xFFFF5C6C)

private val colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = AccentPink,
    background = Background,
    onBackground = TextMain,
    surface = Panel,
    onSurface = TextMain,
    surfaceVariant = Panel2,
    onSurfaceVariant = TextMuted,
    surfaceContainer = Panel,
    surfaceContainerHigh = Panel2,
    surfaceContainerLow = Panel,
    outline = Line,
    outlineVariant = Line,
    error = Danger,
)

@Composable
fun LinkShelfTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}

fun Platform.brush(): Brush = Brush.linearGradient(gradient.map { Color(it) })

val AllBrush: Brush = Brush.linearGradient(listOf(Accent, Color(0xFF3B2A99)))
