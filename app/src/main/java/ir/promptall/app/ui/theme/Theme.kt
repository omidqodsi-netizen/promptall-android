package ir.promptall.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8D9AFF),
    secondary = Color(0xFFB4BDFF),
    background = Color(0xFF090A0E),
    surface = Color(0xFF121319),
    surfaceVariant = Color(0xFF1B1D25),
    onBackground = Color(0xFFF4F4F7),
    onSurface = Color(0xFFF4F4F7),
)

@Composable
fun PromptAllTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
