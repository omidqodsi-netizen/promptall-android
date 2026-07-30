package ir.promptall.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA85CFF),
    secondary = Color(0xFFC084FF),
    background = Color(0xFF050608),
    surface = Color(0xFF101116),
    surfaceVariant = Color(0xFF17191D),
    onBackground = Color(0xFFF7F5FA),
    onSurface = Color(0xFFF7F5FA),
)

@Composable
fun PromptAllTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
