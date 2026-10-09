package ir.promptall.app.ui.theme

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA85CFF),
    onPrimary = Color.White,
    secondary = Color(0xFFC084FF),
    background = Color(0xFF07080B),
    surface = Color(0xFF101116),
    surfaceVariant = Color(0xFF17191F),
    outline = Color(0xFF2B2D35),
    onBackground = Color(0xFFF8F7FA),
    onSurface = Color(0xFFF8F7FA),
    onSurfaceVariant = Color(0xFFAAABB3),
)

// Use Android's optimized sans-serif family instead of shipping a heavy font asset.
// On Persian devices this resolves to the platform Arabic/Persian sans family and
// keeps the release size essentially unchanged.
private val PromptTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
)

@Composable
fun PromptAllTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = PromptTypography,
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides PromptTypography.bodyMedium,
            content = content,
        )
    }
}
